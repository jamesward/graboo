boot.springApp {
    jvmVersion = 17
    kotlin = true

    dependencies {
        implementation(universe.spring.boot.starter.webflux)
    }
}