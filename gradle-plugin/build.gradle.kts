import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.nio.file.Files
import java.util.*

plugins {
    `kotlin-dsl`
    alias(universe.plugins.gradle.plugin.publish)
    alias(universe.plugins.qoomon.git.versioning)
}

group = "com.jamesward"

gitVersioning.apply {
    refs {
        tag("v(?<version>.*)") {
            version = "\${ref.version}"
        }
    }

    rev {
        version = "\${commit}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // todo: temporary down-bump for compose
    //implementation(universe.kotlin.gradle.plugin)
    //implementation(universe.kotlin.allopen)
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")

    implementation(universe.foojay.resolver)

    // this is the kotlin-universe-catalog used by graboo projects
    implementation("com.jamesward.kotlin-universe-catalog:gradle-plugin:2023.12.06-4")

    // maybe another way to being version catalog pre-compiled stuff in
    //compileOnly(files(universe.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(universe.kotlin.power.assert.gradle)
    implementation(universe.spring.boot.gradle.plugin)
    implementation(universe.spring.gradle.dependency.management.plugin)
    implementation(universe.graalvm.buildtools.native.gradle.plugin)

    implementation(universe.android.gradle)

    testImplementation(gradleTestKit())
    testImplementation(universe.junit.jupiter)
    testRuntimeOnly(universe.junit.platform.launcher)
}

/*
tasks.withType<Jar> {
    from(file(universe.javaClass.superclass.protectionDomain.codeSource.location))
}
 */

// todo: cleanup tmp or move to test
val copyExamples = tasks.create<Copy>("copyExamples") {
    from(rootProject.layout.projectDirectory.dir("examples"))
    into(Files.createTempDirectory("gradle-plugin-test"))
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
