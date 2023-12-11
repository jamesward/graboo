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
@OptIn(ExperimentalNativeApi::class)
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

    val javaExec = if (Platform.osFamily == OsFamily.MACOSX) {
        jdk / "Contents" / "Home" / "bin" / "java"
    }
    else {
        jdk / "bin" / "java"
    }

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

// todo: Archetype options, ie Spring w/ Kotlin, Kotlin targets
enum class Archetype {
    SPRINGAPP,
    KOTLINAPP,
    JAVAAPP,
    ANDROIDAPP;

    override fun toString(): String =
        when(this) {
            SPRINGAPP -> "springApp"
            KOTLINAPP -> "kotlinApp"
            JAVAAPP -> "javaApp"
            ANDROIDAPP -> "androidApp"
        }

    companion object {
        operator fun invoke(s: String): Archetype? =
            when(s.lowercase()) {
                "springapp" -> SPRINGAPP
                "kotlinapp" -> KOTLINAPP
                "javaapp" -> JAVAAPP
                "androidapp" -> ANDROIDAPP
                else -> null
            }
    }
}

// bug: entrypoint can't be a suspend fun
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun main(args: Array<String>): Unit = runBlocking {
    // todo: windows & *nix graboo examples (i.e `./graboo`)

    if (args.firstOrNull() == "--help") {
        println("Create a new project:")
        println("  graboo new <type> <dir>")
        println()
        println("Run Gradle task:")
        println("  graboo <task>")
        println()
        println("Run the Graboo Shell:")
        println("  graboo")
        exit(1)
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

        FileSystem.SYSTEM.createDirectory(projectDir)

        val buildGradleKtsBody = when(archetype!!) {
            Archetype.SPRINGAPP ->
                """
                jvmVersion = 17
                """

            Archetype.KOTLINAPP ->
                """
                mainClass = "MainKt"
                targets {
                    jvm(17)
                }
                """

            Archetype.JAVAAPP ->
                """
                mainClass = "Main"
                jvmVersion = 17
                """

            Archetype.ANDROIDAPP ->
                """
                namespace = "com.example.myapplication"
                compileSdk = 34
                """
        }

        val buildGradleKts = """
            boot.$archetype {
                $buildGradleKtsBody
            }
        """.trimIndent()

        FileSystem.SYSTEM.write(projectDir / "build.gradle.kts") {
            writeUtf8(buildGradleKts)
        }

        // todo: externalize version
        val settingsGradleKts = """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }

            plugins {
                id("com.jamesward.gradleboot") version "0.0.48"
            }
        """.trimIndent()

        FileSystem.SYSTEM.write(projectDir / "settings.gradle.kts") {
            writeUtf8(settingsGradleKts)
        }

        val gitignore = """
            /.idea/
            build/
            /.gradle/
        """.trimIndent()

        FileSystem.SYSTEM.write(projectDir / ".gitignore") {
            writeUtf8(gitignore)
        }

        FileSystem.SYSTEM.write(projectDir / "graboo") {
            write(BootScripts.shScript.decodeBase64Bytes())
        }

        // todo: move to kotlin native
        if (Platform.osFamily != OsFamily.WINDOWS) {
            Command("chmod")
                .args("+x", (projectDir / "graboo").toString())
                .spawn()
                .wait()
        }

        FileSystem.SYSTEM.write(projectDir / "graboo.cmd") {
            write(BootScripts.cmdScript.decodeBase64Bytes())
        }

        when(archetype) {
            Archetype.SPRINGAPP -> {
                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "main" / "java" / "com" / "example")
                FileSystem.SYSTEM.write(projectDir / "src" / "main" / "java" / "com" / "example" / "MyApplication.java") {
                    writeUtf8(
                        """
                        package com.example;

                        import org.springframework.boot.SpringApplication;
                        import org.springframework.boot.autoconfigure.SpringBootApplication;

                        @SpringBootApplication
                        public class MyApplication {
                            public static void main(String[] args) {
                                SpringApplication.run(MyApplication.class, args);
                            }
                        }
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "test" / "java" / "com" / "example")
                FileSystem.SYSTEM.write(projectDir / "src" / "test" / "java" / "com" / "example" / "MyApplicationTests.java") {
                    writeUtf8(
                        """
                        package com.example;
                        
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        import org.springframework.boot.test.context.SpringBootTest;
                        
                        @SpringBootTest
                        public class MyApplicationTests {
                        
                            @Test
                            void basic() {
                                Assertions.assertEquals("asdf", "asdf");
                            }
                        
                        }
                        """.trimIndent()
                    )
                }
            }

            Archetype.KOTLINAPP -> {
                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "jvmMain" / "kotlin")
                FileSystem.SYSTEM.write(projectDir / "src" / "jvmMain" / "kotlin" / "Main.kt") {
                    writeUtf8(
                        """
                        fun main() {
                            println("hello, world")
                        }
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "jvmTest" / "kotlin")
                FileSystem.SYSTEM.write(projectDir / "src" / "jvmTest" / "kotlin" / "MyTest.kt") {
                    writeUtf8(
                        """
                        import kotlin.test.Test
                        
                        class MyTest {
                            @Test
                            fun my_test() {
                                assert("asdf" == "asdf")
                            }
                        }
                        """.trimIndent()
                    )
                }
            }

            Archetype.JAVAAPP -> {
                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "main" / "java")
                FileSystem.SYSTEM.write(projectDir / "src" / "main" / "java" / "Main.java") {
                    writeUtf8(
                        """
                        class Main {
                            public static void main(String[] args) {
                                System.out.println("hello, world");
                            }
                        }
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "test" / "java")
                FileSystem.SYSTEM.write(projectDir / "src" / "test" / "java" / "MyTest.java") {
                    writeUtf8(
                        """
                        import static org.junit.jupiter.api.Assertions.assertEquals;

                        import org.junit.jupiter.api.Test;
                        
                        class MyTest {
                        
                            @Test
                            void myTest() {
                                assertEquals("asdf", "asdf");
                            }
                        
                        }
                        """.trimIndent()
                    )
                }
            }

            Archetype.ANDROIDAPP -> {
                FileSystem.SYSTEM.write(projectDir / "gradle.properties") {
                    writeUtf8(
                        """
                        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "main" / "kotlin" / "com" / "example" / "myapplication")
                FileSystem.SYSTEM.write(projectDir / "src" / "main" / "kotlin" / "com" / "example" / "myapplication" / "MainActivity.kt") {
                    writeUtf8(
                        """
                        package com.example.myapplication

                        import android.os.Bundle
                        import androidx.activity.ComponentActivity
                        import androidx.activity.compose.setContent
                        import androidx.compose.material3.Text
                        import androidx.compose.runtime.Composable
                        import androidx.compose.ui.tooling.preview.Preview
                        
                        class MainActivity : ComponentActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContent {
                                    Greeting("World")
                                }
                            }
                        }
                        
                        @Composable
                        fun Greeting(name: String) {
                            Text(text = "Hello ${"$"}name!")
                        }
                        
                        @Preview(showBackground = true)
                        @Composable
                        fun DefaultPreview() {
                            Greeting("Android")
                        }
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.write(projectDir / "src" / "main" / "AndroidManifest.xml") {
                    writeUtf8(
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                            <application android:icon="@android:drawable/btn_star">
                                <activity android:name=".MainActivity"
                                          android:exported="true">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>

                        </manifest>
                        """.trimIndent()
                    )
                }

                FileSystem.SYSTEM.createDirectories(projectDir / "src" / "androidTest" / "kotlin" / "com" / "example" / "myapplication")
                FileSystem.SYSTEM.write(projectDir / "src" / "androidTest" / "kotlin" / "com" / "example" / "myapplication" / "GreetingTest.kt") {
                    writeUtf8(
                        """
                        package com.example.myapplication

                        import androidx.compose.ui.test.assertIsDisplayed
                        import androidx.compose.ui.test.junit4.createComposeRule
                        import androidx.compose.ui.test.onNodeWithText
                        import org.junit.Rule
                        import org.junit.Test

                        class GreetingTest {

                            @get:Rule
                            val composeTestRule = createComposeRule()

                            @Test
                            fun myTest() {
                                composeTestRule.setContent {
                                    Greeting(name = "Test")
                                }

                                composeTestRule.onNodeWithText("Hello Test!").assertIsDisplayed()
                            }

                        }
                        """.trimIndent()
                    )
                }
            }
        }

        println()
        println("Your project is ready!")
        println()
        println("First change to your new project directory:")
        println("  cd $projectDir")
        println()

        // todo: same as help
        println("Then run Gradle tasks:")
        println("  graboo <task>")
        println()
        println("Or enter the Graboo Shell:")
        println("  graboo")
        exit(0)
    }
    else {
        val storageDirEnv = when(Platform.osFamily) {
            OsFamily.WINDOWS -> "LOCALAPPDATA"
            else -> "HOME"
        }

        val home = getenv(storageDirEnv)?.toKString()
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

            if (args.isNotEmpty()) {
                println("Launching Gradle")
                runGradleWrapper(grabooDir, jdk, args)
            }
            else {
                println("Entering Gradle Shell")
            }
        }
    }
}
