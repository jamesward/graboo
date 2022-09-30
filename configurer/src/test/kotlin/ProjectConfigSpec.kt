import ProjectConfig.SourceSet
import ProjectConfig.SourceLanguage
import ProjectConfig.SourceType
import ProjectConfig.findImports
import ProjectConfig.findMains
import ProjectConfig.sourceConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class ProjectConfigSpec : StringSpec({

    "must parse src dirs" {
        val dir = Files.createTempDirectory("tmp")
        val projectDir = ProjectConfig.ProjectDir(dir)

        val mainKotlin = dir / "src/main/kotlin"
        mainKotlin.createDirectories()
        sourceConfig(projectDir).sourceSets shouldHaveSingleElement SourceSet(SourceLanguage.KOTLIN, SourceType.MAIN, mainKotlin)

        val mainFoo = dir / "src/main/foo"
        mainFoo.createDirectories()
        sourceConfig(projectDir).sourceSets shouldContain SourceSet(null, SourceType.MAIN, mainFoo)

        val testJava = dir / "src/test/java"
        testJava.createDirectories()
        sourceConfig(projectDir).sourceSets shouldContain SourceSet(SourceLanguage.JAVA, SourceType.TEST, testJava)

        val fooBar = dir / "src/foo/bar"
        fooBar.createDirectories()
        sourceConfig(projectDir).sourceSets shouldContain SourceSet(null, null, fooBar)

        sourceConfig(projectDir).sourceSets shouldHaveSize 4
    }

    "find mains" {
        val dir = Files.createTempDirectory("tmp")

        (dir / "Foo.java").writeText("""
            public class Foo {
                public static void main(String... args) {
                }
            }
        """.trimIndent())

        val sourceSet = SourceSet(SourceLanguage.JAVA, SourceType.MAIN, dir)
        val mains = findMains(sourceSet)
        mains.first().className shouldBe "Foo"
    }

    "source imports" {
        val dir = Files.createTempDirectory("tmp")

        (dir / "Foo.java").writeText("""
            package foo;
            
            import asdf.Foo;
            import static zxcv.Bar;
        """.trimIndent())

        val sourceSet = SourceSet(SourceLanguage.JAVA, SourceType.MAIN, dir)
        val imports = findImports(sourceSet)
        imports.size shouldBe 2
        imports shouldBe setOf(ProjectConfig.SourceImport(sourceSet, "asdf.Foo"), ProjectConfig.SourceImport(sourceSet, "zxcv.Bar"))
    }

    /*
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
     */

})