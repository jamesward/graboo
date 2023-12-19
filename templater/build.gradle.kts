import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import java.util.*

plugins {
    alias(universe.plugins.kotlin.multiplatform)
    alias(universe.plugins.kotlin.power.assert)
}

kotlin {
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    if (!org.gradle.internal.os.OperatingSystem.current().isWindows) {
        jvm {
            jvmToolchain(17)
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate {
        withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

        common {
            withCompilations { true }

            group("posixAndJvm") {
                group("posix") {
                    withLinux()
                    withMacos()
                }
                group("jvm") {
                    withJvm()
                }
            }
            group("windows") {
                withMingw()
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(universe.okio)
            }

            kotlin {
                // todo: depend on task
                srcDir(layout.buildDirectory.dir("generated/scripts"))
                srcDir(layout.buildDirectory.dir("generated/other"))
            }
        }
        commonTest {
            dependencies {
                implementation(universe.kotlin.test)
                implementation(universe.benasher44.uuid)
                implementation(universe.kotlinx.coroutines.core)
            }
        }
    }
}

val addScripts = tasks.register("addScripts") {
    inputs.dir("scripts")

    outputs.file(layout.buildDirectory.file("generated/scripts/BootScripts.kt"))

    doLast {
        val encoder = Base64.getEncoder()
        val shScript = "\"" + encoder.encodeToString(file("scripts/graboo").readBytes()) + "\""
        val cmdScript = "\"" + encoder.encodeToString(file("scripts/graboo.cmd").readBytes()) + "\""

        val contents = """
            object BootScripts {
                val shScript = $shScript
                val cmdScript = $cmdScript
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

val addOther = tasks.register("addOther") {
    outputs.file(layout.buildDirectory.file("generated/other/GrabooProperties.kt"))

    doLast {
        val contents = """
            object GrabooProperties {
                val version = "${project.version}"
            }
        """.trimIndent()

        outputs.files.singleFile.writeText(contents)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(addScripts, addOther)
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