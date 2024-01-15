import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.nio.file.Files
import java.util.*

plugins {
    `kotlin-dsl`
    alias(universe.plugins.gradle.plugin.publish)
    alias(universe.plugins.kotlin.power.assert)
}

group = "com.jamesward"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(project(":core"))
    implementation(universe.kotlin.gradle.plugin)
    implementation(universe.kotlin.allopen)

    implementation(universe.foojay.resolver)

    // this is the kotlin-universe-catalog used by graboo projects
    implementation("com.jamesward.kotlin-universe-catalog:gradle-plugin:2024.01.14-2")

    // maybe another way to being version catalog pre-compiled stuff in
    //compileOnly(files(universe.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(universe.kotlin.power.assert.gradle)
    implementation(universe.spring.boot.gradle.plugin)
    implementation(universe.spring.gradle.dependency.management.plugin)
    implementation(universe.graalvm.buildtools.native.gradle.plugin)

    implementation(universe.android.gradle)

    testImplementation(project(":core"))
    testImplementation(gradleTestKit())
    testImplementation(universe.junit.jupiter)
    testRuntimeOnly(universe.junit.platform.launcher)
}

/*
tasks.withType<Jar> {
    from(file(universe.javaClass.superclass.protectionDomain.codeSource.location))
}
 */
tasks.named<Jar>("jar") {
    // include the core dep contents since I'm not sure how to publish the dep
    from(zipTree(project(":core").tasks["jvmJar"].outputs.files.singleFile))
}

// todo: cleanup tmp or move to test
val copyExamples = tasks.create<Copy>("copyExamples") {
    from(rootProject.layout.projectDirectory.dir("examples"))
    into(Files.createTempDirectory("gradle-plugin-test"))
}

configure<com.bnorm.power.PowerAssertGradleExtension> {
    functions = listOf("org.junit.jupiter.api.Assertions.assertTrue")
}

tasks.named<Test>("test") {
    dependsOn("publishAllPublicationsToMavenRepository")
    dependsOn(copyExamples)

    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events(STARTED, PASSED, SKIPPED, FAILED)
    }

    systemProperties["examples-dir"] = copyExamples.destinationDir
    systemProperties["test-repo"] = (publishing.repositories.getByName("maven") as MavenArtifactRepository).url
    systemProperties["plugin-version"] = project.version

    val properties = Properties()
    if (rootProject.file("local.properties").exists()) {
        properties.load(rootProject.file("local.properties").inputStream())
        environment("ANDROID_HOME", properties.getProperty("sdk.dir"))
    }

    // todo: cleanup copyExamples.destinationDir or move copy to tests
}

val pluginName = "Graboo"
val pluginDescription = "Bootiful Gradle - Build DSLs for common archetypes"
val pluginUrl = "https://github.com/jamesward/graboo"

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = pluginUrl
    vcsUrl = "$pluginUrl.git"
    plugins {
        create("GradleBootSettings") {
            id = "com.jamesward.gradleboot"
            implementationClass = "com.jamesward.gradleboot.GradleSettingsPlugin"
            displayName = pluginName
            description = pluginDescription
            tags = listOf("graboo")
        }
    }
}

publishing {
    publications {
        configureEach {
            (this as MavenPublication).pom {
                name = pluginName
                description = pluginDescription
                url = pluginUrl

                scm {
                    connection = "scm:git:$pluginUrl.git"
                    developerConnection = "scm:git:git@github.com:jamesward/gradleboot.git"
                    url = pluginUrl
                }

                licenses {
                    license {
                        name = "Apache 2.0"
                        url = "https://opensource.org/licenses/Apache-2.0"
                    }
                }

                developers {
                    developer {
                        id = "jamesward"
                        name = "James Ward"
                        email = "james@jamesward.com"
                        url = "https://jamesward.com"
                    }
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("maven-repo"))
        }
    }
}
