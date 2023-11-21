boot.kotlinApp {
    mainClass = "MainKt"

    targets {
        common {
            dependencies {
                implementation(universe.ktor.client.core)
                testImplementation(universe.ktor.client.mock)
            }
        }

        jvm(17) {
            dependencies {
                implementation(universe.ktor.client.java)
                runtimeOnly(universe.slf4j.simple)
            }
        }

        linux {
            dependencies {
                implementation(universe.ktor.client.curl)
            }
        }
    }
}
