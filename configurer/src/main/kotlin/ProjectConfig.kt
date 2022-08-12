import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

object ProjectConfig {

    fun hasPlugin(buildFile: Path, plugin: String): Boolean = run {
        buildFile.readLines().any {
            // todo: check in plugins block
            it.contains(plugin)
        }
    }

    fun createBuild(buildFile: Path): Unit = run {
        buildFile.createFile()
    }

    fun injectPlugin(dir: Path, s: String): Unit = run {
        val buildFile = (dir / "build.gradle.kts")
        if (!buildFile.exists()) {
            createBuild(buildFile)
        }

        buildFile.appendText("""
            plugins {
                $s
            }
        """.trimIndent())
    }

    fun run(dir: Path): Unit = run {

        if ((dir / "src/main/kotlin").exists()) {
            injectPlugin(dir, """kotlin("jvm") version "1.7.10"""")
        }

        val containsKotlinMain = (dir / "src/main/kotlin").toFile().walkBottomUp().any {
            it.extension == "kt" &&
            it.readLines().any { line ->
                line.contains("fun main(")
            }
        }

        if (containsKotlinMain) {
            injectPlugin(dir, "application")
        }
    }

}