import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
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
    alias(universe.plugins.kotlin.power.assert)
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

    // don't setup this platform unless on Linux
    if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
        jvm {
            jvmToolchain(17)
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            mainRun {
                mainClass = "MainKt"
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate {
        withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

        common {
            withCompilations { true }

            group("linuxAndJvm") {
                group("linux") {
                    withLinux()
                }
                group("jvm") {
                    withJvm()
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":templater"))
                implementation(universe.ktor.server.core)
                implementation(universe.ktor.server.cio)
                implementation(universe.ktor.client.core)
                implementation(universe.arrow.kt.suspendapp)
                implementation(universe.arrow.kt.suspendapp.ktor)
                implementation(universe.benasher44.uuid)
                implementation(universe.ktor.server.html.builder)
                implementation(universe.kgit2.kommand)
            }
        }

        commonTest {
            dependencies {
                implementation(universe.kotlin.test)
            }
        }

        linuxMain {
            dependencies {
                implementation(universe.ktor.client.curl)
            }
        }

        jvmMain {
            dependencies {
                implementation(universe.ktor.client.java)
                runtimeOnly(universe.slf4j.simple)
            }
        }
    }
}

tasks.withType<AbstractTestTask> {
    testLogging {
        showStandardStreams = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        )
    }
}

configure<com.bnorm.power.PowerAssertGradleExtension> {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue")
}

/*
tasks.register("addJsDev") {
    inputs.files(project(":server-js").tasks.getByName("wasmJsBrowserDevelopmentExecutableDistribution").outputs.files.singleFile)
    outputs.file(layout.buildDirectory.file("generated/StaticFiles.kt"))

    doLast {
        val wasm = File(inputs.files.singleFile, "graboo-server-js-wasm-js.wasm")
        val js = File(inputs.files.singleFile, "server-js.js")
        val encoder = Base64.getEncoder()
        val wasmEncoded = encoder.encodeToString(wasm.readBytes())
        val jsEncoded = encoder.encodeToString(js.readBytes())

        val contents = """
            object StaticFiles {
                val wasm = "$wasmEncoded"
                val js = "$jsEncoded"
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("addJsDev")
}

tasks.register("addJsProd") {
    inputs.files(project(":server-js").tasks.getByName("wasmJsBrowserDistribution").outputs.files.singleFile)
    outputs.file(layout.buildDirectory.file("generated/StaticFiles.kt"))

    doLast {
        val wasm = File(inputs.files.singleFile, "graboo-server-js-wasm-js.wasm")
        val js = File(inputs.files.singleFile, "server-js.js")
        val encoder = Base64.getEncoder()
        val wasmEncoded = encoder.encodeToString(wasm.readBytes())
        val jsEncoded = encoder.encodeToString(js.readBytes())

        val contents = """
            object StaticFiles {
                val wasm = "$wasmEncoded"
                val js = "$jsEncoded"
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("addJsProd")
}
 */

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
//        image = "debian:stable-slim"
        image = "ghcr.io/jamesward/graboo-server-base:main"
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
