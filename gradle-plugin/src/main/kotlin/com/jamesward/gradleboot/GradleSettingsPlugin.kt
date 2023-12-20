package com.jamesward.gradleboot

import Archetype
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dependency.AndroidXDependencyCheck
import com.jamesward.kotlinuniversecatalog.GradlePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maybeCreate
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.util.*

@Suppress("UnstableApiUsage")
class GradleSettingsPlugin : Plugin<Settings> {

    class Dependencies {
        val implementations = mutableListOf<String>()
        fun implementation(s: String) {
            implementations += s
        }
        fun implementation(p: Provider<MinimalExternalModuleDependency>) {
            p.orNull?.let {
                implementations += it.toString()
            }
        }

        val runtimeOnlys = mutableListOf<String>()
        fun runtimeOnly(s: String) {
            runtimeOnlys += s
        }
        fun runtimeOnly(p: Provider<MinimalExternalModuleDependency>) {
            p.orNull?.let {
                runtimeOnlys += it.toString()
            }
        }

        val testImplementations = mutableListOf<String>()
        fun testImplementation(s: String) {
            testImplementations += s
        }
        fun testImplementation(p: Provider<MinimalExternalModuleDependency>) {
            p.orNull?.let {
                testImplementations += it.toString()
            }
        }

        val testRuntimeOnlys = mutableListOf<String>()
        fun testRuntimeOnly(s: String) {
            testRuntimeOnlys += s
        }
        fun testRuntimeOnly(p: Provider<MinimalExternalModuleDependency>) {
            p.orNull?.let {
                testRuntimeOnlys += it.toString()
            }
        }
    }

    class Target {
        internal var dependencies: Dependencies? = null

        fun dependencies(configure: Dependencies.() -> Unit) {
            dependencies = Dependencies()
            dependencies?.configure()
        }
    }

    class Targets {
        internal var commonTarget: Target? = null
        internal var jvmTarget: Pair<Int, Target>? = null
        internal var linuxTarget: Target? = null

        fun common(configure: Target.() -> Unit) {
            commonTarget = Target()
            commonTarget?.configure()
        }

        fun jvm(version: Int) {
            jvmTarget = version to Target()
        }

        fun jvm(version: Int, configure: Target.() -> Unit) {
            jvmTarget = version to Target()
            jvmTarget?.second?.configure()
        }

        // todo: architecture?
        fun linux() {
            linuxTarget = Target()
        }

        fun linux(configure: Target.() -> Unit) {
            linuxTarget = Target()
            linuxTarget?.configure()
        }
    }

    class SpringApp {
        var jvmVersion: Int = 17
        var kotlin: Boolean = false

        internal var dependencies: Dependencies? = null

        fun dependencies(configure: Dependencies.() -> Unit) {
            dependencies = Dependencies()
            dependencies?.configure()
        }
    }

    class KotlinApp {
        internal val targets: Targets = Targets()

        var mainClass: String? = null

        fun targets(configure: Targets.() -> Unit) {
            targets.configure()
        }
    }

    class JavaApp {
        var mainClass: String? = null

        var jvmVersion: Int = 17

        internal var dependencies: Dependencies? = null

        fun dependencies(configure: Dependencies.() -> Unit) {
            dependencies = Dependencies()
            dependencies?.configure()
        }
    }

    class AndroidApp {
        var namespace: String? = null
        var compileSdk: Int? = null
        var jvmVersion: Int = 8

        internal var dependencies: Dependencies? = null

        fun dependencies(configure: Dependencies.() -> Unit) {
            dependencies = Dependencies()
            dependencies?.configure()
        }
    }

    class Boot(private val project: Project) {
        // todo: to enum
        var archetype: String? = null

        fun springApp(configure: SpringApp.() -> Unit) {
            val springApp = SpringApp()
            springApp.configure()

            // todo: to class
            project.plugins.apply("org.springframework.boot")
            project.plugins.apply("io.spring.dependency-management")

            /*
            if (native.getOrElse(false)) {
                project.plugins.apply("org.graalvm.buildtools.native")
            }
             */

            if (springApp.kotlin) {
                // todo: to class
                project.plugins.apply("org.jetbrains.kotlin.jvm")
                project.plugins.apply("org.jetbrains.kotlin.plugin.spring")
                project.plugins.apply("com.bnorm.power.kotlin-power-assert")

                val kotlinTopLevelExtension = project.the<KotlinTopLevelExtension>()
                kotlinTopLevelExtension.jvmToolchain(springApp.jvmVersion)
            } else {
                project.plugins.apply("java")
                val javaPluginExtension = project.the<JavaPluginExtension>()
                javaPluginExtension.toolchain {
                    languageVersion.set(JavaLanguageVersion.of(springApp.jvmVersion))
                }
            }

            project.tasks.withType<Test> {
                useJUnitPlatform()

                testLogging {
                    showStandardStreams = true
                    showExceptions = true
                    exceptionFormat = TestExceptionFormat.FULL
                    events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
            }

            project.dependencies {
                // default dependencies
                add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
                if (springApp.kotlin) {
                    add("implementation", "io.projectreactor.kotlin:reactor-kotlin-extensions")
                    add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
                    add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
                }

                springApp.dependencies?.implementations?.forEach {
                    add("implementation", it)
                }

                springApp.dependencies?.testImplementations?.forEach {
                    add("testImplementation", it)
                }

                springApp.dependencies?.runtimeOnlys?.forEach {
                    add("runtimeOnly", it)
                }
                springApp.dependencies?.testRuntimeOnlys?.forEach {
                    add("testRuntimeOnly", it)
                }
            }

            project.tasks.create("run").dependsOn("bootRun")
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        fun kotlinApp(configure: KotlinApp.() -> Unit) {
            val kotlinApp = KotlinApp()
            kotlinApp.configure()

            //val universe = project.extensions.getByType<VersionCatalogsExtension>().named("universe")
            //universe.libraryAliases.forEach { println(it) }

            // todo: to class
            project.plugins.apply("org.jetbrains.kotlin.multiplatform")
            project.plugins.apply("com.bnorm.power.kotlin-power-assert")

            val kotlinMultiplatformExtension = project.the<KotlinMultiplatformExtension>()

            kotlinMultiplatformExtension.sourceSets.getByName("commonMain") {
                dependencies {
                    kotlinApp.targets.commonTarget?.dependencies?.implementations?.map {
                        implementation(it)
                    }

                    kotlinApp.targets.commonTarget?.dependencies?.runtimeOnlys?.map {
                        runtimeOnly(it)
                    }
                }
            }

            kotlinMultiplatformExtension.sourceSets.getByName("commonTest") {
                dependencies {
                    // todo: don't use short syntax
                    implementation(kotlin("test"))
                    kotlinApp.targets.commonTarget?.dependencies?.testImplementations?.map {
                        implementation(it)
                    }

                    kotlinApp.targets.commonTarget?.dependencies?.testRuntimeOnlys?.map {
                        runtimeOnly(it)
                    }
                }
            }

            project.tasks.create("test").dependsOn("allTests")

            project.tasks.withType<AbstractTestTask>().configureEach {
                testLogging {
                    showStandardStreams = true
                    showExceptions = true
                    exceptionFormat = TestExceptionFormat.FULL
                    events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
            }

            if (kotlinApp.targets.jvmTarget != null) {
                val kmpJvmTarget = kotlinMultiplatformExtension.jvm()

                val kotlinTopLevelExtension = project.the<KotlinTopLevelExtension>()
                kotlinTopLevelExtension.jvmToolchain(kotlinApp.targets.jvmTarget!!.first)

                kmpJvmTarget.mainRun {
                    mainClass.set(kotlinApp.mainClass)
                }

                kotlinMultiplatformExtension.sourceSets.getByName("jvmMain") {
                    dependencies {
                        kotlinApp.targets.jvmTarget?.second?.dependencies?.implementations?.map {
                            implementation(it)
                        }

                        kotlinApp.targets.jvmTarget?.second?.dependencies?.runtimeOnlys?.map {
                            runtimeOnly(it)
                        }
                    }
                }

                kotlinMultiplatformExtension.sourceSets.getByName("jvmTest") {
                    dependencies {
                        kotlinApp.targets.jvmTarget?.second?.dependencies?.testImplementations?.map {
                            implementation(it)
                        }

                        kotlinApp.targets.jvmTarget?.second?.dependencies?.testRuntimeOnlys?.map {
                            runtimeOnly(it)
                        }
                    }
                }

                kmpJvmTarget.testRuns.named("test") {
                    executionTask.configure {
                        useJUnitPlatform()
                    }
                }

                // todo: not target aware
                project.tasks.create("run").dependsOn("jvmRun")
            }

            if (kotlinApp.targets.linuxTarget != null) {
                kotlinMultiplatformExtension.linuxX64 {
                    binaries {
                        executable(listOf(DEBUG, RELEASE)) {
                            // todo: how else from this@KotlinAppExtension.mainClass
                            entryPoint = "main"
                        }
                    }

                    testRuns.named("test")
                }

                kotlinMultiplatformExtension.sourceSets.getByName("linuxX64Main") {
                    dependencies {
                        kotlinApp.targets.linuxTarget?.dependencies?.implementations?.map {
                            implementation(it)
                        }

                        kotlinApp.targets.linuxTarget?.dependencies?.runtimeOnlys?.map {
                            runtimeOnly(it)
                        }
                    }
                }

                kotlinMultiplatformExtension.sourceSets.getByName("linuxX64Test") {
                    dependencies {
                        kotlinApp.targets.linuxTarget?.dependencies?.testImplementations?.map {
                            implementation(it)
                        }

                        kotlinApp.targets.linuxTarget?.dependencies?.testRuntimeOnlys?.map {
                            runtimeOnly(it)
                        }
                    }
                }
            }
        }

        fun javaApp(configure: JavaApp.() -> Unit) {
            archetype = "javaApp"

            val javaApp = JavaApp()
            javaApp.configure()

            val universe = project.extensions.getByType<VersionCatalogsExtension>().named("universe")

            //project.plugins.apply("java")

            // applies the java plugin
            project.plugins.apply(ApplicationPlugin::class.java)

            val javaApplication = project.the<JavaApplication>()
            javaApplication.mainClass.set(javaApp.mainClass)

            val javaPluginExtension = project.the<JavaPluginExtension>()
            javaPluginExtension.toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaApp.jvmVersion))
            }

            project.tasks.withType<Test> {
                useJUnitPlatform()

                testLogging {
                    showStandardStreams = true
                    showExceptions = true
                    exceptionFormat = TestExceptionFormat.FULL
                    events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
            }

            project.dependencies {
                add("testImplementation", platform(universe.findLibrary("junit-bom").get()))
                add("testImplementation", universe.findLibrary("junit-jupiter").get())

                javaApp.dependencies?.implementations?.forEach {
                    add("implementation", it)
                }

                javaApp.dependencies?.testImplementations?.forEach {
                    add("testImplementation", it)
                }

                javaApp.dependencies?.runtimeOnlys?.forEach {
                    add("runtimeOnly", it)
                }
                javaApp.dependencies?.testRuntimeOnlys?.forEach {
                    add("testRuntimeOnly", it)
                }
            }
        }

        fun androidApp(configure: AndroidApp.() -> Unit) {
            val androidApp = AndroidApp()
            androidApp.configure()

            val universe = project.extensions.getByType<VersionCatalogsExtension>().named("universe")

            // todo: maybe no way to set jvmargs early enough
            //project.extraProperties.set("org.gradle.jvmargs", "-Xmx2048m -Dfile.encoding=UTF-8")

            // todo: woah it is terrible but can't find another way to set android.useAndroidX
            project.extensions.extraProperties.set("${AndroidXDependencyCheck.AndroidXDisabledJetifierDisabled::class.java.name}_issue_reported", true)
            //project.extraProperties.set("android.useAndroidX", "true")

            project.plugins.apply(AppPlugin::class)  //(AppPlugin::class.java) //"com.android.application")
            project.plugins.apply(KotlinAndroidPluginWrapper::class) //"org.jetbrains.kotlin.android")

            val kotlinTopLevelExtension = project.the<KotlinTopLevelExtension>()
            kotlinTopLevelExtension.jvmToolchain(androidApp.jvmVersion)

            val androidApplicationExtension = project.the<ApplicationExtension>()
            androidApplicationExtension.namespace = androidApp.namespace
            androidApplicationExtension.compileSdk = androidApp.compileSdk

            androidApplicationExtension.buildFeatures {
                compose = true
            }

            androidApplicationExtension.composeOptions {
                kotlinCompilerExtensionVersion = universe.findLibrary("androidx-compose-compiler").get().get().version
            }

            androidApplicationExtension.defaultConfig {
                minSdk = 24
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            // note: this doesn't get used by connectedAndroidTest
            project.tasks.withType<Test> {
                testLogging {
                    showStandardStreams = true
                    showExceptions = true
                    exceptionFormat = TestExceptionFormat.FULL
                    events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
            }

            // todo:
            //   -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
            //
            // todo: something better
            androidApplicationExtension.testOptions {
                managedDevices {
                    devices.apply {
                        maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("atd").apply {
                            device = "Pixel 2"
                            apiLevel = 30
                            systemImageSource = "aosp-atd"
                        }
                    }
                }
            }

            project.dependencies {
                add("implementation", universe.findLibrary("androidx-core-ktx").get())
                add("implementation", universe.findLibrary("androidx-lifecycle-runtime-kts").get())
                add("implementation", universe.findLibrary("androidx-activity-compose").get())
                add("implementation", universe.findLibrary("androidx-compose-ui").get())
                add("implementation", universe.findLibrary("androidx-compose-ui-tooling-preview").get())
                add("implementation", universe.findLibrary("androidx-compose-material3").get())
                add("debugImplementation", universe.findLibrary("androidx-compose-ui-tooling").get())
                add("debugImplementation", universe.findLibrary("androidx-compose-ui-test-manifest").get())
                add("androidTestImplementation", universe.findLibrary("androidx-compose-ui-test-junit4").get())

                androidApp.dependencies?.implementations?.forEach {
                    add("implementation", it)
                }

                androidApp.dependencies?.testImplementations?.forEach {
                    add("testImplementation", it)
                }

                androidApp.dependencies?.runtimeOnlys?.forEach {
                    add("runtimeOnly", it)
                }
                androidApp.dependencies?.testRuntimeOnlys?.forEach {
                    add("testRuntimeOnly", it)
                }
            }

        }
    }

    override fun apply(settings: Settings) {
        settings.gradle.beforeProject {
            val boot = Boot(this)
            extensions.add("boot", boot)

            tasks.create("ide") {
                doLast {
                    IDEUtil.location(boot.archetype?.let { Archetype(it) })?.let { ideHome ->
                        IDEUtil.exe(ideHome)?.let { exe ->
                            println("Opening this project using $ideHome")
                            ProcessBuilder(exe.toString(), project.projectDir.toString())
                                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                .redirectError(ProcessBuilder.Redirect.INHERIT)
                                .start()
                        }
                    } ?: throw Exception("Could not find a valid IntelliJ or Android Studio")
                }
            }
        }

        settings.gradle.settingsEvaluated {
            settings.plugins.apply("org.gradle.toolchains.foojay-resolver-convention")

            settings.pluginManagement.repositories {
                gradlePluginPortal()
                mavenCentral()
                google()
            }

            settings.dependencyResolutionManagement.repositories {
                if (this.isEmpty()) {
                    mavenCentral()
                    google()
                }
            }

            val props = Properties()
            GradlePlugin::class.java.getResourceAsStream("/META-INF/kotlin-universe-catalog.properties")?.use {
                props.load(it)
            }

            val kotlinVersionCatalogVersion = props.getProperty("version")

            settings.dependencyResolutionManagement.versionCatalogs {
                create("universe") {
                    from("com.jamesward.kotlin-universe-catalog:stables:$kotlinVersionCatalogVersion")
                }

                create("universeunstable") {
                    from("com.jamesward.kotlin-universe-catalog:unstables:$kotlinVersionCatalogVersion")
                }
            }

            // todo: can we pull this dependency in here instead of at plugin load because we need to add the google repo before it can be loaded.
            // settings.buildscript.dependencies.add("classpath", "com.android.application:8.1.4")
        }
    }
}
