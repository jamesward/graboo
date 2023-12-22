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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.exit
import platform.posix.getenv
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@Serializable
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
    val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

    try {
        FileSystem.SYSTEM.createDirectory(tmpDir)
        val archive = tmpDir / filename
        FileSystem.SYSTEM.write(archive, false) {
            write(bytes)
        }

        val (command, args) = if (filename.endsWith(".zip")) {
            "powershell" to arrayOf("-command", "\"Expand-Archive '$archive' '$tmpDir'\"")
        }
        else if (filename.endsWith(".tar.gz")) {
            "tar" to "-x -f $archive -C $tmpDir".split("\\s+".toRegex()).toTypedArray()
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

        // The contents are in a subdir and we need them not to be
        FileSystem.SYSTEM.delete(archive)

        FileSystem.SYSTEM.list(tmpDir).forEach { file ->
            if (FileSystem.SYSTEM.metadata(file).isDirectory) {
                FileSystem.SYSTEM.atomicMove(file, to)
            }
            else {
                FileSystem.SYSTEM.createDirectory(to, false)
                val relativeFile = file.relativeTo(tmpDir)
                FileSystem.SYSTEM.atomicMove(file, to / relativeFile)
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

    val javaExec = javaExe(jdk)

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


@OptIn(ExperimentalNativeApi::class)
fun javaExe(jdk: Path): Path {
    val javaExec = when(Platform.osFamily) {
        OsFamily.MACOSX -> jdk / "Contents" / "Home" / "bin" / "java"
        OsFamily.WINDOWS -> jdk / "bin" / "java.exe"
        OsFamily.LINUX -> jdk / "bin" / "java"
        else -> throw Exception("Unsupported Platform")
    }
    return javaExec
}

@OptIn(ExperimentalForeignApi::class)
suspend fun setupJdk(currentVersion: String?, grabooJdk: Path, currentProps: Path): Path = run {
    if ((currentVersion == null) || (!FileSystem.SYSTEM.exists(grabooJdk / currentVersion))) {
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
    } else {
        val jdkDir = grabooJdk / currentVersion
        println("Using JDK from $jdkDir")
        jdkDir
    }
}

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
private fun grabooDir(): Path? = run {
    val storageDirEnv = when(Platform.osFamily) {
        OsFamily.WINDOWS -> "LOCALAPPDATA"
        else -> "HOME"
    }

    getenv(storageDirEnv)?.toKString()?.toPath()?.let { home ->
        if (FileSystem.SYSTEM.exists(home)) {
            when (Platform.osFamily) {
                OsFamily.WINDOWS -> home / "graboo"
                else -> home / ".graboo"
            }
        } else {
            null
        }
    }
}

@Serializable
data class Release(val tag_name: String)

data class SemVer(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        // very minimal assuming s = digits.digits.digits
        operator fun invoke(s: String): SemVer? = run {
            val parts = s.removePrefix("v").split(".")
            if (parts.size == 3) {
                parts.component1().toIntOrNull()?.let { major ->
                    parts.component2().toIntOrNull()?.let { minor ->
                        parts.component3().toIntOrNull()?.let { patch ->
                            SemVer(major, minor, patch)
                        }
                    }
                }
            }
            else {
                null
            }
        }

        val compareSemVer = compareBy<SemVer>({it.major}, {it.minor}, {it.patch})
    }

    operator fun compareTo(other: SemVer): Int =
        compareSemVer.compare(this, other)
}

@OptIn(ExperimentalNativeApi::class)
fun getArtifact(): String? =
    when (Platform.osFamily to Platform.cpuArchitecture) {
        (OsFamily.MACOSX to CpuArchitecture.ARM64) -> "graboo-macos-arm64"
        (OsFamily.MACOSX to CpuArchitecture.X64) -> "graboo-macos-x64"
        (OsFamily.LINUX to CpuArchitecture.X64) -> "graboo-linux-x64"
        (OsFamily.WINDOWS to CpuArchitecture.X64) -> "graboo-windows-x64.exe"
        else -> null
    }

suspend fun updateToVersion(thisVersion: String = Version(), forceUpdateTo: String? = null): String? =
    if (forceUpdateTo == null) {
        SemVer(thisVersion)?.let { thisSemVer ->
            val release = client.get("https://api.github.com/repos/jamesward/graboo/releases/latest").body<Release>()
            SemVer(release.tag_name)?.let { latest ->
                if (latest > thisSemVer) {
                    release.tag_name
                } else {
                    null
                }
            }
        }
    }
    else {
        "v${forceUpdateTo.removePrefix("v")}"
    }

suspend fun update(grabooDir: Path? = grabooDir(), forceUpdateTo: String? = null): Pair<String, Path>? =
    updateToVersion(forceUpdateTo = forceUpdateTo)?.let { updateTo ->
        grabooDir?.let {
            val installedGraboo = FileSystem.SYSTEM.list(grabooDir).find { it.name.startsWith("graboo-") && !it.name.endsWith("-new") }
            val maybeGrabooExe: Path? = installedGraboo ?: getArtifact()?.let { grabooDir / it}

            maybeGrabooExe?.let { grabooExe ->
                val url = "https://github.com/jamesward/graboo/releases/download/$updateTo/${grabooExe.name}"
                val response = client.get(url)
                if (response.status.isSuccess()) {
                    println("Updating to $updateTo")
                    val bytes = client.get(url).readBytes()
                    val newFile = grabooDir / "${grabooExe.name}-new"
                    FileSystem.SYSTEM.write(newFile) {
                        write(bytes)
                    }
                    makeExecutable(newFile)

                    updateTo to newFile
                }
                else {
                    println("The release $updateTo with file ${grabooExe.name} does not exist")
                    null
                }
            }
        }
    }

// bug: entrypoint can't be a suspend fun
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>): Unit = runBlocking {
    if (args.firstOrNull() == "update") {
        val updateTo = args.getOrNull(1)
        update(forceUpdateTo = updateTo)?.let { (versionTo, _) ->
            println("Updated to $versionTo")
            exit(0)
        } ?: run {
            println("No newer versions to update to")
            exit(1)
        }
    }

    // todo: only at most one per hour
    update()?.let { (versionTo, newExe) ->
        println("Restarting on new version $versionTo")

        val result = Command(newExe.toString())
            .args(*args)
            .spawn()
            .wait()

        exit(result.exitStatus())
    }

    val helpText =
        """
        Run Gradle task:
          graboo <task>

        Run the Graboo Shell:
          graboo
        """.trimIndent()

    if (args.firstOrNull() == "--help") {
        println(
            """
            Welcome to Graboo ${Version()}

            Create a new project:
              graboo new <type> <dir>

            $helpText
            """.trimIndent()
        )

        exit(0)
    }

    if (args.firstOrNull() == "--version") {
        println(Version().removePrefix("v"))
        exit(0)
    }

    if (args.firstOrNull() == "new") {
        val (archetype, dir) = if (args.size == 2) {
            println("Project directory:")
            Archetype(args[2]) to readlnOrNull()
        }
        else if (args.size == 1) {
            println("Project type (${Archetype.entries.joinToString(", ")}):")
            val archetype = readlnOrNull()?.let { Archetype(it) }
            println("Project directory:")
            archetype to readln()
        }
        else {
            Archetype(args[1]) to args[2]
        }

        if (archetype == null) {
            println("Archetype not specified or invalid. Valid types are:")
            println(Archetype.entries.joinToString(", "))
            exit(1)
        }

        if (dir != null && FileSystem.SYSTEM.exists(dir.toPath())) {
            println("Project dir $dir exists already")
            exit(1)
        }

        val projectDir = dir!!.toPath()

        println("Creating project of type $archetype in $projectDir")

        val contents = Templater.contents(archetype!!)

        runBlocking {
            Templater.write(contents, projectDir)
        }

        println(
            """
                Your project is ready!

                First change to your new project directory:")
                  cd $projectDir

                $helpText
            """.trimIndent()
        )

        exit(0)
    }
    else {
        grabooDir()?.let { grabooDir ->
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

            val jdk = setupJdk(currentVersion, grabooJdk, currentProps)

            // todo: check gradle version
            // maybe use: https://services.gradle.org/versions/

            if (args.isNotEmpty()) {
                println("Launching Gradle")
                runGradleWrapper(grabooDir, jdk, args)
            }
            else {
                println("TODO: Entering Gradle Shell")
            }
        } ?: {
            println("Could not determine a directory to store graboo stuff in.")
        }
    }
}
