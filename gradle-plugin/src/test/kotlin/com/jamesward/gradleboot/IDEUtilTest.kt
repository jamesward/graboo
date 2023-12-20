package com.jamesward.gradleboot

import com.jamesward.gradleboot.IDEUtil.location
import com.jamesward.gradleboot.IDEUtil.findLatest
import com.jamesward.gradleboot.IDEUtil.parseVersion
import org.gradle.platform.OperatingSystem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IDEUtilTest {

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun parseVersion_works_unix() {
        File("/home/foo/.cache/JetBrains/IntelliJIdea2023.2").parseVersion().let {
            assertTrue(it.product == "IntelliJIdea")
            assertTrue(it.year == 2023)
            assertTrue(it.sub == 2)
        }

        File("/home/foo/.cache/JetBrains/AndroidStudio2021.1").parseVersion().let {
            assertTrue(it.product == "AndroidStudio")
            assertTrue(it.year == 2021)
            assertTrue(it.sub == 1)
        }

        File("/home/foo/.cache/JetBrains/AndroidStudioPreview2023.1").parseVersion().let {
            assertTrue(it.product == "AndroidStudioPreview")
            assertTrue(it.year == 2023)
            assertTrue(it.sub == 1)
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun parseVersion_works_windows() {
        File("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2020.1").parseVersion().let {
            assertTrue(it.product == "IntelliJ IDEA")
            assertTrue(it.year == 2020)
            assertTrue(it.sub == 1)
        }

        File("C:\\Program Files\\Google\\Android Studio 2022.3").parseVersion().let {
            assertTrue(it.product == "Android Studio")
            assertTrue(it.year == 2022)
            assertTrue(it.sub == 3)
        }

        File("C:\\Program Files\\Google\\Android Studio Preview 2021.2").parseVersion().let {
            assertTrue(it.product == "Android Studio Preview")
            assertTrue(it.year == 2021)
            assertTrue(it.sub == 2)
        }
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun findLatest_works_unix(@TempDir tmpDir: File) {
        File(tmpDir, "JetBrains/IntelliJIdea2021.1").mkdirs()
        File(tmpDir, "JetBrains/IntelliJIdea2023.1").mkdirs()
        File(tmpDir, "JetBrains/IntelliJIdea2023.2").mkdirs()

        val latest = tmpDir.findLatest("JetBrains", "IntelliJIdea")
        assertTrue(latest?.name == "IntelliJIdea2023.2")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun findLatest_works_windows(@TempDir tmpDir: File) {
        File(tmpDir, "JetBrains\\IntelliJIdea2021.1").mkdirs()
        File(tmpDir, "JetBrains\\IntelliJIdea2023.1").mkdirs()
        File(tmpDir, "JetBrains\\IntelliJIdea2023.2").mkdirs()

        val latest = tmpDir.findLatest("JetBrains", "IntelliJIdea")
        assertTrue(latest?.name == "IntelliJIdea2023.2")
    }

    @Suppress("UnstableApiUsage")
    private fun createCaches(operatingSystem: OperatingSystem, baseDir: File, vendor: String, product: String) = run {
        val vendorDir = when(operatingSystem) {
            OperatingSystem.WINDOWS ->
                File(baseDir, vendor)
            OperatingSystem.MAC_OS ->
                File(baseDir, "Library/Caches/$vendor")
            OperatingSystem.LINUX ->
                File(baseDir, ".cache/$vendor")
            else ->
                throw Exception()
        }

        vendorDir.mkdirs()

        val dir1 = File(vendorDir, "${product}2022.3")
        dir1.mkdir()
        val home1 = File(dir1, ".home")
        home1.writeText("not/the/one")

        val dir2 = File(vendorDir, "${product}2023.1")
        dir2.mkdir()
        val home2 = File(dir2, ".home")
        home2.writeText("not/the/one")

        val dir3 = File(vendorDir, "${product}2023.3")
        dir3.mkdir()
        val home3 = File(dir3, ".home")
        val ideDir = File(baseDir, "IDE")
        val ideBinDir = File(ideDir, "bin")
        ideBinDir.mkdirs()
        val ideash = File(ideBinDir, "idea.sh")
        ideash.writeText("this")
        val studiosh = File(ideBinDir, "studio.sh")
        studiosh.writeText("this")
        val ideaexe = File(ideBinDir, "idea64.exe")
        ideaexe.writeText("this")
        val studioexe = File(ideBinDir, "studio64.exe")
        studioexe.writeText("this")

        home3.writeText(ideDir.toString())

        ideDir
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @Suppress("UnstableApiUsage")
    fun locations_linux(@TempDir tmpDir: File) {
        val intellij = createCaches(OperatingSystem.LINUX, tmpDir, "JetBrains", "IntelliJIdea")
        assertTrue(location(Archetype.ANDROIDAPP, tmpDir, tmpDir) == intellij)

        val androidStudio = createCaches(OperatingSystem.LINUX, tmpDir, "Google", "AndroidStudio")
        assertTrue(location(Archetype.ANDROIDAPP, tmpDir, tmpDir) == androidStudio)
        assertTrue(location(Archetype.KOTLINAPP, tmpDir, tmpDir) == intellij)

        val androidStudioPreview = createCaches(OperatingSystem.LINUX, tmpDir, "Google", "AndroidStudioPreview")
        assertTrue(location(Archetype.ANDROIDAPP, tmpDir, tmpDir) == androidStudioPreview)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    @Suppress("UnstableApiUsage")
    fun locations_mac(@TempDir cachesDir: File, @TempDir appDir: File) {
        val intellij = createCaches(OperatingSystem.MAC_OS, cachesDir, "JetBrains", "IntelliJIdea")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == intellij)

        val androidStudio = createCaches(OperatingSystem.MAC_OS, cachesDir, "Google", "AndroidStudio")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudio)
        assertTrue(location(Archetype.KOTLINAPP, appDir, cachesDir) == intellij)

        val androidStudioPreview = createCaches(OperatingSystem.MAC_OS, cachesDir, "Google", "AndroidStudioPreview")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudioPreview)

        val intellijAppDir = File(appDir, "IntelliJ IDEA.app")
        intellijAppDir.mkdirs()
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == intellijAppDir)

        val androidStudioAppDir = File(appDir, "Android Studio.app")
        androidStudioAppDir.mkdirs()
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudioAppDir)

        val androidStudioPreviewAppDir = File(appDir, "Android Studio Preview.app")
        androidStudioPreviewAppDir.mkdirs()
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudioPreviewAppDir)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Suppress("UnstableApiUsage")
    fun locations_windows(@TempDir cachesDir: File, @TempDir appDir: File) {
        val intellij = createCaches(OperatingSystem.WINDOWS, cachesDir, "JetBrains", "IntelliJIdea")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == intellij)

        val androidStudio = createCaches(OperatingSystem.WINDOWS, cachesDir, "Google", "AndroidStudio")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudio)
        assertTrue(location(Archetype.KOTLINAPP, appDir, cachesDir) == intellij)

        val androidStudioPreview = createCaches(OperatingSystem.WINDOWS, cachesDir, "Google", "AndroidStudioPreview")
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudioPreview)

        val intellijAppDir = File(appDir, "JetBrains\\IntelliJ IDEA 2023.3")
        intellijAppDir.mkdirs()
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == intellijAppDir)

        val androidStudioAppDir = File(appDir, "Android\\Android Studio")
        androidStudioAppDir.mkdirs()
        assertTrue(location(Archetype.ANDROIDAPP, appDir, cachesDir) == androidStudioAppDir)
    }

}