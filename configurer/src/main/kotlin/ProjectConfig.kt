import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

object ProjectConfig {
    @JvmInline
    value class ProjectDir(val path: Path)

    @JvmInline
    value class BuildFile(val path: Path)
    fun ProjectDir.buildFile(): BuildFile = BuildFile(path / "build.gradle.kts")

    fun hasPlugin(projectDir: ProjectDir, plugin: String): Boolean = run {
        projectDir.buildFile().path.readLines().any {
            // todo: check in plugins block
            it.contains(plugin)
        }
    }

    fun createBuild(buildFile: BuildFile): Unit = run {
        /*
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }
         */
        buildFile.path.writeText("""

            repositories {
                mavenCentral()
            }
            
        """.trimIndent())
    }

    fun injectPlugin(buildFile: BuildFile, s: String): Unit = run {
        if (!buildFile.path.exists()) {
            createBuild(buildFile)
        }

        val lines = buildFile.path.readLines()

        val pluginsLine = lines.indexOf("plugins {")
        val newLines = if (pluginsLine >= 0) {
            val before = lines.take(pluginsLine + 1)
            val after = lines.drop(pluginsLine + 1)
            before + "    $s" + after
        }
        else {
            listOf("plugins {", "    $s", "}", "") + lines
        }

        buildFile.path.writeLines(newLines)
    }


    // todo
    //   - text parsing replacement
    //   - adding dependencies
    //   - java toolchain
    //   - main runner
    //   - background compiler
    //   - background build updater
    //   - testing support
    //   - java bootstrapper
    //   - kotter tui
    fun run(dir: ProjectDir): Unit = run {
        if (!dir.buildFile().path.exists()) {
            createBuild(dir.buildFile())
        }

        if (!(dir.path / "gradle/wrapper/gradle-wrapper.properties").exists()) {
            Gradler.wrapper(dir)
        }

        if ((dir.path / "src/main/kotlin").exists()) {
            injectPlugin(dir.buildFile(), """kotlin("jvm") version "1.7.10"""")
        }

        val containsKotlinMain = (dir.path / "src/main/kotlin").toFile().walkBottomUp().any {
            it.extension == "kt" &&
            it.readLines().any { line ->
                line.contains("fun main(")
            }
        }

        if (containsKotlinMain) {
            injectPlugin(dir.buildFile(), "application")

            dir.buildFile().path.appendText("""
                
                application {
                    mainClass.set("MainKt")
                }
                
            """.trimIndent())
        }
    }

}