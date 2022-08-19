import ProjectConfig.buildFile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists

class GradlerSpec : StringSpec({

    "add a dependency" {
        val dir = Files.createTempDirectory("tmp")
        val projectDir = ProjectConfig.ProjectDir(dir)
        ProjectConfig.createBuild(projectDir.buildFile())
        ProjectConfig.injectPlugin(projectDir.buildFile(), """kotlin("jvm") version "1.7.10"""")

        val dependency = Gradler.Dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.6.4")
        Gradler.addDependency(projectDir.buildFile(), dependency)

        Gradler.hasDependency(projectDir.buildFile(), dependency) shouldBe true

        val out = Gradler.runTaskAndCaptureOutput(projectDir, "dependencies")

        out.contains("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.7.10") shouldBe true

        // todo: cleanup correctly
        dir.toFile().deleteRecursively()
    }

})