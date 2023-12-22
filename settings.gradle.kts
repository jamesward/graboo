rootProject.name = "graboo"

include("core", "templater", "bootstrapper", "gradle-plugin", "bootwrapper", "server") //, "server-js")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
    id("com.jamesward.kotlin-universe-catalog") version "2023.12.22-3"
}