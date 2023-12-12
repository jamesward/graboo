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

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":templater"))
                implementation(universe.ktor.server.core)
                implementation(universe.ktor.server.cio)
                implementation(universe.arrow.kt.suspendapp)
                implementation(universe.arrow.kt.suspendapp.ktor)
                implementation(universe.benasher44.uuid)
                // todo: to universe catalog
                implementation("io.ktor:ktor-server-html-builder:2.3.7")
            }
        }
    }
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
        image = "gcr.io/distroless/base"
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
}

sourceSets.create("main")
