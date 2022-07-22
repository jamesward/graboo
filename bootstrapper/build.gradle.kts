plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

kotlin {
    linuxX64 {
        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
            }
        }
    }

    mingwX64 {
        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio:3.2.0")
                //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4-native-mt")
                implementation("io.ktor:ktor-client-core:2.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:2.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
            /*
            dependencies {
                implementation("io.ktor:ktor-client-core:2.0.3")
            }
             */
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain)
            dependencies {
                implementation("io.ktor:ktor-client-curl:2.0.3")
            }
        }
    }
}
