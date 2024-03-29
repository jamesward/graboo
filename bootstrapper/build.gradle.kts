import java.util.Base64

plugins {
    alias(universe.plugins.kotlin.multiplatform)
    alias(universe.plugins.kotlin.plugin.serialization)
    alias(universe.plugins.kotlin.power.assert)
}

kotlin {
    if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
        linuxX64 {
            binaries {
                executable(listOf(DEBUG, RELEASE)) {
                    entryPoint = "main"

                    // from: https://stackoverflow.com/a/76032383/77409
                    runTask?.run {
                        val args = providers.gradleProperty("runArgs").orNull?.split(' ') ?: emptyList()
                        argumentProviders.add(
                            CommandLineArgumentProvider { args }
                        )
                    }
                }
            }
        }
    }

    // by default this target is enabled on Linux but doesn't really work with ktor
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        mingwX64 {
            binaries {
                executable(listOf(DEBUG, RELEASE)) {
                    entryPoint = "main"
                }
            }
        }
    }

    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        macosArm64 {
            binaries {
                executable(listOf(DEBUG, RELEASE)) {
                    entryPoint = "main"
                }
            }
        }

        macosX64 {
            binaries {
                executable(listOf(DEBUG, RELEASE)) {
                    entryPoint = "main"
                }
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin {
                srcDir(layout.buildDirectory.dir("generated"))
            }

            dependencies {
                implementation(project(":templater"))
                implementation(universe.ktor.client.core)
                implementation(universe.ktor.client.content.negotiation)
                implementation(universe.ktor.serialization.kotlinx.json)
                implementation(universe.benasher44.uuid)
                implementation(universe.kgit2.kommand)
            }
        }

        linuxMain {
            dependencies {
                implementation(universe.ktor.client.curl)
            }
        }

        mingwMain {
            dependencies {
                implementation(universe.ktor.client.winhttp)
            }
        }

        macosMain {
            dependencies {
                implementation(universe.ktor.client.darwin)
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

tasks.register("addBootWrapper") {
    dependsOn(":bootwrapper:uberJar")

    inputs.file(project(":bootwrapper").tasks.getByName("uberJar").outputs.files.singleFile)
    outputs.file(layout.buildDirectory.file("generated/BootWrapper.kt"))

    doLast {
        val uberJar = inputs.files.singleFile
        val encoder = Base64.getEncoder()
        val s = encoder.encodeToString(uberJar.readBytes()) //.chunked(10240).map { """"$it"""" }.joinToString("+ \n")
        val uberJarString = "\"" + s + "\""
        //val uberJarString = uberJar.readBytes().joinToString(", ", "byteArrayOf(", ")")

        val contents = """
            object BootWrapper {
                val encodedJar = $uberJarString
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

tasks.register("addVersion") {
    inputs.property("version", version)
    outputs.files(layout.buildDirectory.file("generated/Version.kt"))

    doLast {
        val contents = """
            object Version {
                operator fun invoke(): String = "$version"
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("addBootWrapper", "addVersion")
}

/*
tasks.register("addConfigurer") {
    dependsOn(":configurer:uberJar")
    //dependsOn(":helloer:uberJar")

    doLast {
        val uberJar = project(":configurer").file("build/libs/configurer-uber.jar")
        //val uberJar = project(":helloer").file("build/libs/helloer-uber.jar")
        val encoder = Base64.getEncoder()
        val s = encoder.encodeToString(uberJar.readBytes()) //.chunked(10240).map { """"$it"""" }.joinToString("+ \n")
        val uberJarString = "\"" + s + "\""
        //val uberJarString = uberJar.readBytes().joinToString(", ", "byteArrayOf(", ")")

        val contents = """
            object Configurer {
                val encodedJar = $uberJarString
            }
        """.trimIndent()

        val srcFile = project.file("src/nativeMain/kotlin/Configurer.kt")
        srcFile.writeText(contents)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("addConfigurer")
}


 */