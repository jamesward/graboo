package com.jamesward.gradleboot

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import kotlin.io.path.writeLines

// todo: split for each plugin but need to deal with examples copy & update
class GradlePluginTest {

    companion object {
        val examplesDir = File(System.getProperty("examples-dir"))
        val testRepo = System.getProperty("test-repo")
        val pluginVersion = System.getProperty("plugin-version")

        @JvmStatic
        @BeforeAll
        fun setupPlugins() {
            val settingsFiles = examplesDir.walk().filter { it.name == "settings.gradle.kts" }
            settingsFiles.forEach { settingsFile ->
                val originalLines = settingsFile.readLines()
                val updatedPluginLines = originalLines.map { line ->
                    if (line.contains("id(\"com.jamesward.gradleboot\")")) {
                        """
                            id("com.jamesward.gradleboot") version "$pluginVersion"
                        """.trimIndent()
                    } else {
                        line
                    }
                }

                val lines = updatedPluginLines.flatMap { line ->
                    if (line.contains("repositories {")) {
                        listOf(
                                line,
                                "maven(uri(\"$testRepo\"))"
                        )
                    }
                    else {
                        listOf(line)
                    }
                }

                settingsFile.toPath().writeLines(lines)
            }
        }
    }

    @Test
    fun jvm_only() {
        val jvmOnly = File(examplesDir, "kotlinapp/jvmonly")
        val jvmRunResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("run")
            .build()

        assertTrue(jvmRunResult.output.contains("hello, world"))

        val testResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("my_test()[jvm] PASSED"))
    }

    // todo: test power assert on failing test

    @Test
    fun kmp_hello_world_jvm() {
        val kmpHelloWorld = File(examplesDir, "kotlinapp/kmp-hello-world")
        val jvmRunResult = GradleRunner.create()
            .withProjectDir(kmpHelloWorld)
            .withArguments("jvmRun")
            .build()

        assertTrue(jvmRunResult.output.contains("hello, world"))
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun kmp_hello_world_linux() {
        val kmpHelloWorld = File(examplesDir, "kotlinapp/kmp-hello-world")

        val linuxRunResult = GradleRunner.create()
            .withProjectDir(kmpHelloWorld)
            .withArguments("runDebugExecutable")
            .build()

        assertTrue(linuxRunResult.output.contains("hello, world"))
    }

    @Test
    fun kmp_with_ktor_jvm() {
        val kmpWithKtor = File(examplesDir, "kotlinapp/kmp-with-ktor")

        val jvmRunResult = GradleRunner.create()
            .withProjectDir(kmpWithKtor)
            .withArguments("jvmRun")
            .build()

        assertTrue(jvmRunResult.output.contains("1.9.10"))
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun kmp_with_ktor_linux() {
        val kmpWithKtor = File(examplesDir, "kotlinapp/kmp-with-ktor")

        val linuxRunResult = GradleRunner.create()
            .withProjectDir(kmpWithKtor)
            .withArguments("runDebugExecutable")
            .build()

        assertTrue(linuxRunResult.output.contains("1.9.10"))

        val testResult = GradleRunner.create()
            .withProjectDir(kmpWithKtor)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("fake_test()[jvm] PASSED"))
        assertTrue(testResult.output.contains("fake_test[linuxX64] PASSED"))
    }

    @Test
    fun springapp_java_webflux() {
        val javaWebflux = File(examplesDir, "springapp/java-webflux")

        // todo: run that exits?

        val testResult = GradleRunner.create()
            .withProjectDir(javaWebflux)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("PASSED"))
    }

    @Test
    fun springapp_kotlin_webflux() {
        val kotlinWebflux = File(examplesDir, "springapp/kotlin-webflux")

        // todo: run that exits?

        val testResult = GradleRunner.create()
            .withProjectDir(kotlinWebflux)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("PASSED"))
    }

    @Test
    fun javaapp_hello_world() {
        val jvmOnly = File(examplesDir, "javaapp/hello-world")
        val jvmRunResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("run")
            .build()

        assertTrue(jvmRunResult.output.contains("hello, world"))

        val testResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("MyTest > myTest() PASSED"))
    }

    @Test
    fun javaapp_with_guava() {
        val jvmOnly = File(examplesDir, "javaapp/with-guava")
        val jvmRunResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("run")
            .build()

        assertTrue(jvmRunResult.output.contains("size = 6"))

        val testResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("test")
            .build()

        assertTrue(testResult.output.contains("MyTest > myTest() PASSED"))
    }

    @Test
    fun androidapp_hello_world() {
        val jvmOnly = File(examplesDir, "androidapp/hello-world")

        val buildResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("build")
            .build()

        assertTrue(buildResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    //@EnabledOnOs(OS.MAC, OS.LINUX)
    fun androidapp_hello_world_device_test() {
        val hasKvmSupport = System.getProperty("os.name").contains("Mac") || (
                System.getProperty("os.name").contains("Linux") &&
                        Runtime.getRuntime().exec("test -w /dev/kvm").onExit().get().exitValue() == 0
                )

        assumeTrue(hasKvmSupport)

        val hasGpu = System.getenv("CI").isNullOrEmpty()

        val jvmOnly = File(examplesDir, "androidapp/hello-world")

        val baseArgs = listOf("atdDebugAndroidTest", "--info")
        val args = if (hasGpu) {
            baseArgs
        }
        else {
            baseArgs + """-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"""
        }

        val testResult = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments(args)
            .build()

        // line contains ansi
        val line = testResult.output.lines().find { it.contains("GreetingTest > myTest[") }
        assertTrue(line?.contains("SUCCESS") ?: false)
    }

    /*
    @Test
    fun ide_task() {
        val jvmOnly = File(examplesDir, "javaapp/hello-world")
        val result = GradleRunner.create()
            .withProjectDir(jvmOnly)
            .withArguments("ide", "--stacktrace")
            .build()

        println(result.output)
    }
     */

}
