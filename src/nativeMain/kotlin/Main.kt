import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.chdir
import platform.posix.chmod
import platform.posix.fgets
import platform.posix.popen
import platform.posix.pclose
import platform.posix.fchmod


val fs = FileSystem.SYSTEM

fun isProject(): Boolean = run {
    fs.list(".".toPath()).contains("src".toPath())
}

// from: https://github.com/jmfayard/kotlin-cli-starter
data class ExecuteCommandOptions(
        val directory: String,
        val abortOnError: Boolean,
        val redirectStderr: Boolean,
        val trim: Boolean
)

suspend fun executeCommandAndCaptureOutput(
        command: List<String>, // "find . -name .git"
        options: ExecuteCommandOptions
): String {
    chdir(options.directory)
    val commandToExecute = command.joinToString(separator = " ") { arg ->
        if (arg.contains(" ")) "'$arg'" else arg
    }
    val redirect = if (options.redirectStderr) " 2>&1 " else ""
    val fp = popen("$commandToExecute $redirect", "r") ?: error("Failed to run command: $command")

    val stdout = buildString {
        val buffer = ByteArray(4096)
        while (true) {
            val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
            append(input.toKString())
        }
    }

    val status = pclose(fp)
    if (status != 0 && options.abortOnError) {
        println(stdout)
        throw Exception("Command `$command` failed with status $status${if (options.redirectStderr) ": $stdout" else ""}")
    }

    return if (options.trim) stdout.trim() else stdout
}

val gradleKts = """
    plugins {
        java
        application
    }

    repositories {
        mavenCentral()
    }
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    application {
        mainClass.set("Main")
    }
""".trimIndent()

val javaMain = """
    public class Main {
        public static void main(String[] args) {
            System.out.println("hello, world");
        }
    }
""".trimIndent()



//val gradleWrapperPath = "C:".toPath() / "Users" / "james" / "IdeaProjects" / "graboo"
val gradleWrapperPath = "/home/jw/projects/graboo".toPath()

val grabooDir = ".graboo".toPath()

fun main() {
    if (!isProject()) {
        // todo: offer templates
        fs.createDirectories("src/main/java".toPath(), true)
        fs.write("src/main/java/Main.java".toPath(), true) {
            writeUtf8(javaMain)
        }
    }

    // we are in a project

    val command = "run"

    if (command == "run") {
        // create the gradle project if not exists
        // todo: do not overwrite stuff

        fs.createDirectory(grabooDir, false)


        fs.write("build.gradle.kts".toPath(), false) {
            writeUtf8(gradleKts)
        }

        fs.createDirectories(grabooDir / "gradle" / "wrapper", false)
        fs.copy(gradleWrapperPath / "gradle" / "wrapper" / "gradle-wrapper.jar", grabooDir / "gradle" / "wrapper" / "gradle-wrapper.jar")
        fs.copy(gradleWrapperPath / "gradle" / "wrapper" / "gradle-wrapper.properties", grabooDir / "gradle" / "wrapper" / "gradle-wrapper.properties")

        fs.write(grabooDir / "gradlew", false) {
            write(GradleWrapper.gradlewBase64.decodeBase64()!!)
        }

        fs.copy(gradleWrapperPath / "gradlew.bat", grabooDir / "gradlew.bat")

        chmod((grabooDir / "gradlew").toString(), 0b111_101_101) // rwx r-x r-x

        val eco = ExecuteCommandOptions(".", abortOnError = true, redirectStderr = false, trim = false)
        runBlocking {
            val out = executeCommandAndCaptureOutput(listOf((grabooDir / "gradlew").toString()), eco)
            println(out)
        }

        //execlp()
        // discover mains
        // if no mains then create
        // if one main then run it
        // if more than one main give menu
    }
}
