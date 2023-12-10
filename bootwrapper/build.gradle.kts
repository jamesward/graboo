plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

dependencies {
    implementation(universe.gradle.wrapper.shared)

}

application {
    mainClass.set("dev.graboo.BootWrapper")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events(org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED, org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED, org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
    }
}

tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")
    manifest.attributes["Main-Class"] = application.mainClass.get()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}