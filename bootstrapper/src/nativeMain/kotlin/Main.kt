import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.exit
import platform.posix.fgets
import platform.posix.popen
import platform.posix.pclose

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
@kotlinx.cinterop.ExperimentalForeignApi
suspend fun saveToTempExtractAndDelete(filename: String, to: Path, bytes: ByteArray) {
    val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

    try {
        FileSystem.SYSTEM.createDirectory(tmpDir)
        val archive = tmpDir / filename
        FileSystem.SYSTEM.write(archive, false) {
            write(bytes)
        }

        FileSystem.SYSTEM.createDirectory(to, false)

        val cmd = "/usr/bin/tar -x -f $archive -C $to --strip-components=1"
        val fp = popen(cmd, "r")
        val statusCode = pclose(fp)

        if (statusCode != 0) {
            coroutineScope {
                cancel("Could not run: $cmd")
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
    else {
        val contentDisposition = downloadGet.headers[HttpHeaders.ContentDisposition]
        val filename = contentDisposition?.removePrefix("attachment; filename=") ?: url.substringAfterLast('/')
        filename to theseBytes
    }
}


// todo: Download stops at 10MB so we chunk it
suspend fun getLatestJdk(): Pair<String, ByteArray> = run {
    val availableReleasesUrl = "https://api.adoptium.net/v3/info/available_releases"
    val availableReleases = client.get(availableReleasesUrl).body<AvailableReleases>()
    val os = "linux"
    val arch = "x64"
    val downloadUrl = "https://api.adoptium.net/v3/binary/latest/${availableReleases.most_recent_lts}/ga/$os/$arch/jdk/hotspot/normal/eclipse?project=jdk"

    chunkedDownload(downloadUrl)
}

// todo: stream output
@kotlinx.cinterop.ExperimentalForeignApi
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
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent())
    }

    val javaExec = jdk / "bin" / "java"
    val argsString = args.joinToString(" ")
    val cmd = "$javaExec -Dgraboo.dir=$grabooDir -jar $bootwrapperJar $argsString"
    val fp = popen(cmd, "r")

    val stdout = buildString {
        val buffer = ByteArray(1024)
        while (true) {
            val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
            append(input.toKString())
        }
    }

    val status = pclose(fp)
    if (status != 0) {
        println("`$cmd` failed with $status - $stdout")
        exit(status)
    }
    else {
        println(stdout)
    }
}

// bug: entrypoint can't be a suspend fun
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>): Unit = runBlocking {
    val home = platform.posix.getenv("HOME")?.toKString()
    if (home == null) {
        println("Could not read HOME dir")
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

            val version = filename.removeSuffix(".tar.gz").substringAfter("hotspot_")

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