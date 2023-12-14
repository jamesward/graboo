import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(universe.jib.native.image.extension)
    }
}

plugins {
    alias(universe.plugins.kotlin.multiplatform)
    alias(universe.plugins.jib)
}

kotlin {
    linuxX64 {
        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
                linkerOpts("--as-needed")
                freeCompilerArgs += "-Xoverride-konan-properties=linkerGccFlags.linux_x64=-lgcc -lgcc_eh -lc"
            }
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass = "MainKt"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":templater"))
                implementation(universe.ktor.server.core)
                implementation(universe.ktor.server.cio)
                implementation(universe.arrow.kt.suspendapp)
                implementation(universe.arrow.kt.suspendapp.ktor)
                implementation(universe.benasher44.uuid)
                implementation(universe.ktor.server.html.builder)
            }
        }
        jvmMain {
            dependencies {
                runtimeOnly(universe.slf4j.simple)
            }
        }
    }
}

@OptIn(InternalKotlinGradlePluginApi::class)
tasks.withType<KotlinJvmRun> {
    jvmArgs("-Dio.ktor.development=true")
}

tasks.register<Copy>("copyBinary") {
    dependsOn(tasks.first { it.name.contains("linkReleaseExecutable") })
    from(layout.buildDirectory.file("bin/linuxX64/releaseExecutable/server.kexe"))
    into(layout.buildDirectory.dir("native/nativeCompile"))
}

tasks.withType<com.google.cloud.tools.jib.gradle.JibTask> {
    dependsOn("copyBinary")
}

jib {
    from {
//        image = "gcr.io/distroless/base"
        image = "debian:stable-slim"
    }
    pluginExtensions {
        pluginExtension {
            implementation = "com.google.cloud.tools.jib.gradle.extension.nativeimage.JibNativeImageExtension"
            properties = mapOf(Pair("imageName", "server.kexe"))
        }
    }
    container {
        mainClass = "MainKt"
    }
    extraDirectories {
        paths {
            path {
                setFrom(file("jib-files"))
                into = "/usr/bin"
            }
        }
        permissions.put("/usr/bin/zip", "755")
    }
}

sourceSets.create("main")
