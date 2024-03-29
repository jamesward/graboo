package com.jamesward.gradleboot

import Archetype
import org.gradle.platform.OperatingSystem
import java.io.File


@Suppress("UnstableApiUsage")
object IDEUtil {

    fun org.gradle.internal.os.OperatingSystem.get(): OperatingSystem? =
        if (this.isWindows) {
            OperatingSystem.WINDOWS
        }
        else if (this.isMacOsX) {
            OperatingSystem.MAC_OS
        }
        else if (this.isLinux) {
            OperatingSystem.LINUX
        }
        else {
            null
        }

    object OS {
        fun current(): OperatingSystem? =
            org.gradle.internal.os.OperatingSystem.current().get()
    }

    data class Detections(val androidStudio: File?, val androidStudioPreview: File?, val intellij: File?, val vscode: File?)

    private fun File.existsOrNull(): File? =
        if (this.exists()) {
            this
        }
        else {
            null
        }

    private fun defaultTypicalBase(): File? =
        when(OS.current()) {
            OperatingSystem.WINDOWS ->
                File("C:\\Program Files")

            OperatingSystem.MAC_OS ->
                File("/Applications")

            else ->
                null
        }

    data class Version(val product: String, val year: Int, val sub: Int)

    fun File.parseVersion(): Version? = run {
        val product = this.name.takeWhile { !it.isDigit() }.trimEnd()
        this.name.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.notNullOrEmpty()?.let { year ->
            this.name.takeLastWhile { it.isDigit() }.notNullOrEmpty()?.let { sub ->
                Version(product, year.toInt(), sub.toInt())
            }
        }
    }

    private val compareVersion = compareBy<Pair<File, Version>>({it.second.year}, {it.second.sub}).reversed()

    fun File.findLatest(vendor: String, product: String): File? = run {
        this.existsOrNull()?.let { baseDir ->
            File(baseDir, vendor).existsOrNull()?.let { vendorDir ->
                vendorDir.listFiles()
                    ?.mapNotNull { file -> file.parseVersion()?.let { file to it } }
                    ?.filter { it.second.product == product }
                    ?.sortedWith(compareVersion)
                    ?.firstOrNull()
                    ?.first
            }
        }
    }

    private fun locationFromTypicals(baseDir: File? = defaultTypicalBase()): Detections = run {
        when(OS.current()) {
            OperatingSystem.WINDOWS -> {
                Detections(
                    File(baseDir, "Android\\Android Studio").existsOrNull(),
                    null,
                    baseDir?.findLatest("JetBrains", "IntelliJ IDEA")?.existsOrNull(),
                    null,
                )
            }

            OperatingSystem.MAC_OS -> {
                val androidStudio = File(baseDir, "Android Studio.app")
                val androidStudioPreview = File(baseDir, "Android Studio Preview.app")
                val intellij = File(baseDir, "IntelliJ IDEA.app")

                Detections(
                    androidStudio.existsOrNull(),
                    androidStudioPreview.existsOrNull(),
                    intellij.existsOrNull(),
                    null
                )
            }

            else ->
                Detections(null, null, null, null)
        }
    }

    // strings from Java can be null but not String?
    private fun String.notNullOrEmpty(): String? =
        if (!this.isNullOrEmpty()) {
            this
        }
        else {
            null
        }

    private fun File?.findIde(vendor: String, product: String): File? =
        this?.existsOrNull()?.let { baseDir ->
            when (OS.current()) {
                OperatingSystem.LINUX ->
                    File(baseDir, ".cache").existsOrNull()

                OperatingSystem.MAC_OS ->
                    File(baseDir, "Library/Caches").existsOrNull()

                OperatingSystem.WINDOWS -> {
                    baseDir
                }

                else ->
                    null
            }?.let { cacheDir ->
                cacheDir.findLatest(vendor, product)?.let { versionDir ->
                    val ideDirString = File(versionDir, ".home").readText()
                    File(ideDirString).existsOrNull()
                }
            }
        }

    private fun defaultCachesBase(): File? =
        when(OS.current()) {
            OperatingSystem.WINDOWS ->
                System.getenv("LOCALAPPDATA").notNullOrEmpty()?.let { appData ->
                    File(appData).existsOrNull()
                }

            OperatingSystem.LINUX, OperatingSystem.MAC_OS ->
                File(System.getProperty("user.home")).existsOrNull()

            else ->
                null
        }

    private fun locationFromCachess(baseDir: File? = defaultCachesBase()): Detections = run {
        Detections(
            baseDir.findIde("Google", "AndroidStudio"),
            baseDir.findIde("Google", "AndroidStudioPreview"),
            baseDir.findIde("JetBrains", "IntelliJIdea"),
            null
        )
    }

    // todo: validate bin exe
    fun location(archetype: Archetype?, baseTypicalDir: File? = defaultTypicalBase(), baseCachesDir: File? = defaultCachesBase()): File? = run {
        val fromTypicals = locationFromTypicals(baseTypicalDir)
        val fromCaches = locationFromCachess(baseCachesDir)
        when (archetype) {
            Archetype.ANDROIDAPP -> {
                fromTypicals.androidStudioPreview ?: fromTypicals.androidStudio ?: fromTypicals.intellij ?: fromTypicals.vscode ?:
                fromCaches.androidStudioPreview ?: fromCaches.androidStudio ?: fromCaches.intellij ?: fromCaches.vscode
            }

            else -> {
                fromTypicals.intellij ?: fromTypicals.androidStudioPreview ?: fromTypicals.androidStudio ?: fromTypicals.vscode ?:
                fromCaches.intellij ?: fromCaches.androidStudioPreview ?: fromCaches.androidStudio ?: fromCaches.vscode
            }
        }
    }

    fun exe(ideDir: File): File? =
        when(OS.current()) {
            OperatingSystem.WINDOWS -> {
                File(ideDir, "bin").existsOrNull()?.let { binDir ->
                    File(binDir, "idea.bat").existsOrNull() ?: File(binDir, "studio.bat").existsOrNull()
                }
            }

            OperatingSystem.MAC_OS -> {
                File(ideDir, "Contents/MacOS/idea").existsOrNull() ?: File(ideDir, "Contents/MacOS/idea").existsOrNull()
            }

            OperatingSystem.LINUX -> {
                File(ideDir, "bin/idea.sh").existsOrNull() ?: File(ideDir, "bin/studio.sh").existsOrNull()
            }

            else ->
                null
        }

}
