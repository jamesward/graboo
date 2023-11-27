import java.util.Base64

plugins {
    alias(universe.plugins.kotlin.multiplatform)
    alias(universe.plugins.kotlin.plugin.serialization)
    alias(universe.plugins.kotlin.power.assert)
}

kotlin {
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

    // by default this target is enabled on Linux but doesn't really work with ktor
    if (System.getProperty("os.name").startsWith("Win", ignoreCase = true)) {
        mingwX64 {
            binaries {
                executable(listOf(DEBUG, RELEASE)) {
                    entryPoint = "main"
                }
            }
        }
    }

    macosArm64 {
        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(universe.okio)
                implementation(universe.ktor.client.core)
                implementation(universe.ktor.client.content.negotiation)
                implementation(universe.ktor.serialization.kotlinx.json)
                implementation(universe.benasher44.uuid)
                // todo: to universe
                implementation("com.kgit2:kommand:1.1.0")
            }
        }

        linuxMain {
            dependencies {
                implementation(universe.ktor.client.curl)
            }
        }

        mingwMain {
            dependencies {
                // todo: to universe
                implementation("io.ktor:ktor-client-winhttp:2.3.6")
            }
        }

        macosMain {
            dependencies {
                // todo: to universe
                implementation("io.ktor:ktor-client-darwin:2.3.6")
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

    // todo: make this run when uberJar is not up-to-date
    onlyIf {
        true
    }

    outputs.file("src/nativeMain/kotlin/BootWrapper.kt")

    doLast {
        val uberJar = project(":bootwrapper").file("build/libs/bootwrapper-uber.jar")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("addBootWrapper")
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