import okio.ByteString.Companion.decodeBase64
import okio.Path
import okio.Path.Companion.toPath

// todo: Archetype options, ie Spring w/ Kotlin, Kotlin targets
enum class Archetype {
    SPRINGAPP,
    KOTLINAPP,
    JAVAAPP,
    ANDROIDAPP;

    override fun toString(): String =
        when(this) {
            SPRINGAPP -> "springApp"
            KOTLINAPP -> "kotlinApp"
            JAVAAPP -> "javaApp"
            ANDROIDAPP -> "androidApp"
        }

    companion object {
        operator fun invoke(s: String): Archetype? =
            when(s.lowercase()) {
                "springapp" -> SPRINGAPP
                "kotlinapp" -> KOTLINAPP
                "javaapp" -> JAVAAPP
                "androidapp" -> ANDROIDAPP
                else -> null
            }
    }
}

data class FileContents(val s: String, val setExec: Boolean = false)

object Templater {

    fun contents(archetype: Archetype): Map<Path, FileContents> = run {

        val buildGradleKtsBody = when (archetype) {
            Archetype.SPRINGAPP ->
                """
                jvmVersion = 17
                """

            Archetype.KOTLINAPP ->
                """
                mainClass = "MainKt"
                targets {
                    jvm(17)
                }
                """

            Archetype.JAVAAPP ->
                """
                mainClass = "Main"
                jvmVersion = 17
                """

            Archetype.ANDROIDAPP ->
                """
                namespace = "com.example.myapplication"
                compileSdk = 34
                """
        }

        val buildGradleKts =
            """
            boot.$archetype {
                $buildGradleKtsBody
            }
            """.trimIndent()


        // todo: externalize version
        val settingsGradleKts =
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }

            plugins {
                id("com.jamesward.gradleboot") version "0.0.48"
            }
            """.trimIndent()

        val gitignore =
            """
            /.idea/
            build/
            /.gradle/
            """.trimIndent()

        val sourceFiles = when (archetype) {
            Archetype.SPRINGAPP ->
                mapOf(
                    "src/main/java/com/example/MyApplication.java".toPath() to FileContents(
                        """
                        package com.example;
        
                        import org.springframework.boot.SpringApplication;
                        import org.springframework.boot.autoconfigure.SpringBootApplication;
        
                        @SpringBootApplication
                        public class MyApplication {
                            public static void main(String[] args) {
                                SpringApplication.run(MyApplication.class, args);
                            }
                        }
                        """.trimIndent()
                    ),

                    "src/test/java/com/example/MyApplicationTests.java".toPath() to FileContents(
                        """
                        package com.example;
    
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        import org.springframework.boot.test.context.SpringBootTest;
    
                        @SpringBootTest
                        public class MyApplicationTests {
    
                            @Test
                            void basic() {
                                Assertions.assertEquals("asdf", "asdf");
                            }
    
                        }
                        """.trimIndent()
                    )
                )

            Archetype.KOTLINAPP ->
                mapOf(
                    "src/jvmMain/kotlin/Main.kt".toPath() to FileContents(
                        """
                        fun main() {
                            println("hello, world")
                        }
                        """.trimIndent()
                    ),
                    "src/jvmTest/kotlin/MyTest.kt".toPath() to FileContents(
                        """
                        import kotlin.test.Test
    
                        class MyTest {
                            @Test
                            fun my_test() {
                                assert("asdf" == "asdf")
                            }
                        }
                        """.trimIndent()
                    ),
                )

            Archetype.JAVAAPP ->
                mapOf(
                    "src/main/java/Main.java".toPath() to FileContents(
                        """
                        class Main {
                            public static void main(String[] args) {
                                System.out.println("hello, world");
                            }
                        }
                        """.trimIndent()
                    ),
                    "src/test/java/MyTest.java".toPath() to FileContents(
                        """
                        import static org.junit.jupiter.api.Assertions.assertEquals;

                        import org.junit.jupiter.api.Test;

                        class MyTest {

                            @Test
                            void myTest() {
                                assertEquals("asdf", "asdf");
                            }

                        }
                        """.trimIndent()
                    )
                )

            Archetype.ANDROIDAPP ->
                mapOf(
                    "gradle.properties".toPath() to FileContents(
                        """
                        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                        """.trimIndent()
                    ),

                    "src/main/kotlin/com/example/myapplication/MainActivity.kt".toPath() to FileContents(
                        """
                        package com.example.myapplication

                        import android.os.Bundle
                        import androidx.activity.ComponentActivity
                        import androidx.activity.compose.setContent
                        import androidx.compose.material3.Text
                        import androidx.compose.runtime.Composable
                        import androidx.compose.ui.tooling.preview.Preview

                        class MainActivity : ComponentActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContent {
                                    Greeting("World")
                                }
                            }
                        }

                        @Composable
                        fun Greeting(name: String) {
                            Text(text = "Hello ${"$"}name!")
                        }

                        @Preview(showBackground = true)
                        @Composable
                        fun DefaultPreview() {
                            Greeting("Android")
                        }
                        """.trimIndent()
                    ),

                    "src/main/AndroidManifest.xml".toPath() to FileContents(
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                            <application android:icon="@android:drawable/btn_star">
                                <activity android:name=".MainActivity"
                                          android:exported="true">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>

                        </manifest>
                        """.trimIndent()
                    ),

                    "src/androidTest/kotlin/com/example/myapplication/GreetingTest.kt".toPath() to FileContents(
                        """
                        package com.example.myapplication

                        import androidx.compose.ui.test.assertIsDisplayed
                        import androidx.compose.ui.test.junit4.createComposeRule
                        import androidx.compose.ui.test.onNodeWithText
                        import org.junit.Rule
                        import org.junit.Test

                        class GreetingTest {

                            @get:Rule
                            val composeTestRule = createComposeRule()

                            @Test
                            fun myTest() {
                                composeTestRule.setContent {
                                    Greeting(name = "Test")
                                }

                                composeTestRule.onNodeWithText("Hello Test!").assertIsDisplayed()
                            }

                        }
                        """.trimIndent()
                    )
                )
        }

        mapOf(
            ".gitignore".toPath() to FileContents(gitignore),
            "build.gradle.kts".toPath() to FileContents(buildGradleKts),
            "settings.gradle.kts".toPath() to FileContents(settingsGradleKts),
            "graboo".toPath() to FileContents(BootScripts.shScript.decodeBase64()!!.utf8(), true),
            "graboo.cmd".toPath() to FileContents(BootScripts.cmdScript.decodeBase64()!!.utf8()),
        ) + sourceFiles
    }
}
