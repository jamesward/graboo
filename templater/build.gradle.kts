import java.util.*

plugins {
    alias(universe.plugins.kotlin.multiplatform)
}

kotlin {
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    jvm()

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
