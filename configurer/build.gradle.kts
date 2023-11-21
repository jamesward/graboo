plugins {
    application
    kotlin("jvm")
    //id("com.github.johnrengelman.shadow") version "7.1.2"
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
    implementation("com.varabyte.kotter:kotter:0.9.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.gradle:gradle-tooling-api:7.5.1")

    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")

    testImplementation("io.kotest:kotest-runner-junit5:5.4.2")
    testImplementation("io.kotest:kotest-assertions-core:5.4.2")

    // https://github.com/gradle/gradle/issues/778
    // https://github.com/fusesource/jansi/issues/66
    runtimeOnly("org.fusesource.jansi:jansi") {
        version {
            strictly("1.18")
        }
    }

    //testImplementation("org.slf4j:slf4j-simple:1.7.36")
}

application {
    mainClass.set("MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.languageVersion = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events(org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED, org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED, org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
    }
}

/*
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").configure {
    minimize()
}
 */

tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")
    manifest.attributes["Main-Class"] = "MainKt"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}