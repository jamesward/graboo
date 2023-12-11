import java.util.Base64

plugins {
    alias(universe.plugins.kotlin.multiplatform)
}

kotlin {
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        nativeMain {
            dependencies {
                api(universe.okio)
            }

            kotlin {
                srcDir(layout.buildDirectory.dir("generated/scripts"))
            }
        }
    }
}

tasks.register("addScripts") {
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("addScripts")
}
