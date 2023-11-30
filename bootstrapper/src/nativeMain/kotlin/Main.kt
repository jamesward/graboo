import com.benasher44.uuid.uuid4
import com.kgit2.process.Command
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.exit
import platform.posix.getenv
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@kotlinx.serialization.Serializable
data class AvailableReleases(val most_recent_lts: Int)

private val client = HttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }
}

// todo: progress indicator
// suspend indicates the side-effect
@ExperimentalForeignApi
suspend fun saveToTempExtractAndDelete(filename: String, to: Path, bytes: ByteArray) {
    // GitHub Actions has the temp dir as c:\RUNNER~1\blah which doesn't work
    val systemTempDir = getenv("LOCALAPPDATA")?.toKString()?.toPath()?.div("Temp") ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY

    val tmpDir = systemTempDir / uuid4().toString()

    println(tmpDir)

    try {
        FileSystem.SYSTEM.createDirectory(tmpDir, true)
        val archive = tmpDir / filename
        println(archive)
        FileSystem.SYSTEM.write(archive, true) {
            write(bytes)
        }

        FileSystem.SYSTEM.createDirectory(to, false)

        val (command, args) = if (filename.endsWith(".zip")) {
            "powershell" to arrayOf("-command", "\"Expand-Archive '$archive' '$to'\"")
        }
        else if (filename.endsWith(".tar.gz")) {
            "tar" to "-x -f $archive -C $to --strip-components=1".split("\\s+".toRegex()).toTypedArray()
        }
        else {
            throw Exception("Could not run: unsupported file $filename")
        }

        val exitStatus = Command(command)
            .args(*args)
            .spawn()
            .wait()

        if (exitStatus.code != 0) {
            coroutineScope {
                cancel("Could not run: $command ${args.joinToString(" ")}")
            }
        }
    }
    finally {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }
}


suspend fun chunkedDownload(url: String, bytes: ByteArray = byteArrayOf(), start: Int = 0, chunkSize: Long = 8192000): Pair<String, ByteArray> = run {
    val downloadGet = client.get(url) {
        headers {
            append(HttpHeaders.Accept, "*/*")
            append("Range", "bytes=$start-${start + chunkSize - 1}")
        }
    }

    // todo: validate response range & status code

    val theseBytes = bytes + downloadGet.readBytes()
    val hasMore = (downloadGet.contentLength() ?: 0) >= chunkSize

    if (downloadGet.status == HttpStatusCode.PartialContent && hasMore) {
        chunkedDownload(url, theseBytes, theseBytes.size, chunkSize)
    }
    else if (downloadGet.status == HttpStatusCode.PartialContent) {
        val contentDisposition = downloadGet.headers[HttpHeaders.ContentDisposition]
        val filename = contentDisposition?.removePrefix("attachment; filename=") ?: url.substringAfterLast('/')
        filename to theseBytes
    }
    else {
        throw Exception("Could not fetch $url - ${downloadGet.status}")
    }
}


// todo: Download stops at 10MB so we chunk it

@OptIn(ExperimentalNativeApi::class)
suspend fun getLatestJdk(): Pair<String, ByteArray> = run {
    val availableReleasesUrl = "https://api.adoptium.net/v3/info/available_releases"
    val availableReleases = client.get(availableReleasesUrl).body<AvailableReleases>()

    val (os, arch) = when (Platform.osFamily to Platform.cpuArchitecture) {
        (OsFamily.MACOSX to CpuArchitecture.ARM64) -> "mac" to "aarch64"
        (OsFamily.MACOSX to CpuArchitecture.X64) -> "mac" to "x64"
        (OsFamily.LINUX to CpuArchitecture.X64) -> "linux" to "x64"
        (OsFamily.WINDOWS to CpuArchitecture.X64) -> "windows" to "x64"
        else -> throw Exception("Could not run: unsupported OS ${Platform.osFamily} or architecture ${Platform.cpuArchitecture}")
    }

    val downloadUrl = "https://api.adoptium.net/v3/binary/latest/${availableReleases.most_recent_lts}/ga/$os/$arch/jdk/hotspot/normal/eclipse?project=jdk"

    chunkedDownload(downloadUrl)
}

// todo: stream output
@ExperimentalForeignApi
suspend fun runGradleWrapper(grabooDir: Path, jdk: Path, args: Array<String>) = run {
    val bootwrapperJar = grabooDir / "bootwrapper.jar"

    val bootwrapperBytes = BootWrapper.encodedJar.decodeBase64Bytes()

    // todo: sha check
    // write every time
    FileSystem.SYSTEM.write(bootwrapperJar, false) {
        write(bootwrapperBytes)
    }

    val bootwrapperGradleProperties = grabooDir / "bootwrapper.properties"

    // todo: better gradle version management
    FileSystem.SYSTEM.write(bootwrapperGradleProperties, false) {
        writeUtf8("""
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent())
    }

    val javaExec = jdk / "bin" / "java"

    val fullArgs = arrayOf("-Dgraboo.dir=$grabooDir", "-jar", bootwrapperJar.toString()) + args

    val exitStatus = Command(javaExec.toString())
        .args(*fullArgs)
        .spawn()
        .wait()

    if (exitStatus.code != 0) {
        coroutineScope {
            println("`$javaExec ${fullArgs.joinToString(" ")}` failed with ${exitStatus.code}")
            exit(exitStatus.code)
        }
    }
}

// bug: entrypoint can't be a suspend fun
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun main(args: Array<String>): Unit = runBlocking {
    val storageDirEnv = when(Platform.osFamily) {
        OsFamily.WINDOWS -> "LOCALAPPDATA"
        else -> "HOME"
    }

    val home = platform.posix.getenv(storageDirEnv)?.toKString()
    if (home == null) {
        println("Could not read $storageDirEnv dir")
        exit(1)
    }
    else {
        val homePath = home.toPath()

        if (!FileSystem.SYSTEM.exists(homePath)) {
            println("Home dir ($home) does not exist")
            exit(1)
        }

        val grabooDir = homePath / ".graboo"

        val grabooJdk = grabooDir / "jdk"
        FileSystem.SYSTEM.createDirectories(grabooJdk, false)

        val currentProps = grabooJdk / "current.properties"
        val currentVersion = if (FileSystem.SYSTEM.exists(currentProps)) {
            val contents = FileSystem.SYSTEM.read(currentProps) {
                readUtf8()
            }
            val maybeLine = contents.lines().find { it.startsWith("version=") }
            maybeLine?.removePrefix("version=")
        }
        else {
            null
        }

        // todo: update JDK to newer

        val jdk = if ((currentVersion == null) || (!FileSystem.SYSTEM.exists(grabooJdk / currentVersion))) {
            println("Downloading a JDK")

            val (filename, bytes) = getLatestJdk()

            // todo: check file hash

            val version = filename.removeSuffix(".tar.gz").removeSuffix(".zip").substringAfter("hotspot_")

            FileSystem.SYSTEM.delete(currentProps)
            FileSystem.SYSTEM.write(currentProps, true) {
                writeUtf8("version=$version")
            }

            val jdkDir = grabooJdk / version

            // todo: stream write instead of in-memory buffer whole file
            saveToTempExtractAndDelete(filename, jdkDir, bytes)

            jdkDir
        }
        else {
            val jdkDir = grabooJdk / currentVersion
            println("Using JDK from $jdkDir")
            jdkDir
        }

        // todo: check gradle version
        // maybe use: https://services.gradle.org/versions/

        if (args.firstOrNull() == "init") {
            println("Init new project")
        }
        else if (args.isNotEmpty()) {
            println("Launching Gradle")
            runGradleWrapper(grabooDir, jdk, args)
        }
        else {
            println("Entering Gradle Shell")
        }
    }
}
