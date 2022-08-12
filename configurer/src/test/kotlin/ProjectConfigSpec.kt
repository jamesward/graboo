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
        (dir / "src/main/kotlin").createDirectories()

        ProjectConfig.run(dir)

        (dir / "build.gradle.kts").exists() shouldBe true

        ProjectConfig.hasPlugin((dir / "build.gradle.kts"), """kotlin("jvm")""") shouldBe true

        // todo: cleanup correctly
        dir.toFile().deleteRecursively()
    }

    "must configure kotlin application" {
        val dir = Files.createTempDirectory("tmp")
        val src = (dir / "src/main/kotlin")
        src.createDirectories()
        val main = (src / "Main.kt")
        main.createFile()
        main.appendText("""
            fun main() {
                println("hello, world")
            }
        """.trimIndent())

        ProjectConfig.run(dir)

        ProjectConfig.hasPlugin((dir / "build.gradle.kts"), "application") shouldBe true

        // todo: cleanup correctly
        dir.toFile().deleteRecursively()
    }

})