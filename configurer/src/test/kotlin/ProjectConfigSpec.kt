import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists

class ProjectConfigSpec : StringSpec({

    "must configure kotlin compiler" {
        val dir = Files.createTempDirectory("tmp")
        val projectDir = ProjectConfig.ProjectDir(dir)

        (dir / "src/main/kotlin").createDirectories()

        ProjectConfig.run(projectDir)

        (dir / "gradle/wrapper/gradle-wrapper.properties").exists() shouldBe true
        (dir / "gradlew").exists() shouldBe true

        (dir / "build.gradle.kts").exists() shouldBe true

        ProjectConfig.hasPlugin(projectDir, """kotlin("jvm")""") shouldBe true

        // todo: cleanup correctly
        dir.toFile().deleteRecursively()
    }

    "must configure kotlin and application" {
        val dir = Files.createTempDirectory("tmp")
        val src = (dir / "src/main/kotlin")
        src.createDirectories()
        val projectDir = ProjectConfig.ProjectDir(dir)
        val main = (src / "Main.kt")
        main.createFile()
        main.appendText("""
            fun main() {
                println("hello from Gradle Boot")
            }
        """.trimIndent())

        ProjectConfig.run(projectDir)

        ProjectConfig.hasPlugin(projectDir, "application") shouldBe true

        val output = Gradler.runTaskAndCaptureOutput(projectDir, "run")
        output.contains("hello from Gradle Boot") shouldBe true

        // todo: cleanup correctly
        dir.toFile().deleteRecursively()
    }

})