plugins {
    alias(universe.plugins.kotlin.multiplatform)
}

kotlin {
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    jvm {
        jvmToolchain(17)
    }
}
