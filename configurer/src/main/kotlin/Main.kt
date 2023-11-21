import ProjectConfig.buildFile
import com.varabyte.kotter.foundation.collections.liveListOf
import com.varabyte.kotter.foundation.input.Completions
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.runUntilSignal
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.bold
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.foundation.timer.addTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchService
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText


/*
object Utils {
    fun copyTemplate(dir: File, template: String) {
        javaClass.getResourceAsStream("templates/$template/build.gradle.kts")?.use { b ->
            Files.copy(b, File(dir, "build.gradle.kts").toPath())
        }
    }
}
 */


data class BuildActions(
    val compileEnabled: Boolean,
    val compileOut: Flow<String>,
    val compileErr: Flow<String>,
    val testEnabled: Boolean,
    val testOut: Flow<String>,
    val testErr: Flow<String>,
    val testFilter: String?,
    val runEnabled: Boolean,
    val runOut: Flow<String>,
    val runErr: Flow<String>,
    val runIn: Flow<String>,
    val runClass: String?,
)

fun main() {

    // todo: graboo & gradle updates
    val cwd = ProjectConfig.ProjectDir(Paths.get(""))
    ProjectConfig.setupGradle(cwd)
    val src = (cwd.path / "src")
    if ( !src.exists()) {
        src.createDirectories()
    }

    session {

        var buildActions by liveVarOf(
            BuildActions(
                false,
                emptyFlow(),
                emptyFlow(),
                false,
                emptyFlow(),
                emptyFlow(),
                null,
                false,
                emptyFlow(),
                emptyFlow(),
                emptyFlow(),
                null
            )
        )

        val watcher = FileSystems.getDefault().newWatchService()
        val cwdWatchKey = cwd.path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

        fun loop() {
            val sourceConfig = ProjectConfig.sourceConfig(cwd)
            buildActions = if (sourceConfig.languages.isEmpty()) {
                buildActions.copy(compileEnabled = false, testEnabled = false, runEnabled = false)
            } else {
                ProjectConfig.setupSourceSets(cwd, sourceConfig)
                buildActions.copy(compileEnabled = true, testEnabled = sourceConfig.hasTest, runEnabled = sourceConfig.mains.isNotEmpty())
            }
        }

        loop()

        var menuIndex: Int by liveVarOf(0)
        var menuAction: Boolean by liveVarOf(false)
        var message: String? by liveVarOf(null)
        val watchEvents = liveListOf<WatchEvent<*>>()

        val f = emptyFlow<String>()

        section {
            if (watchEvents.isNotEmpty()) {
                watchEvents.map { watchEvent ->
                    val path = (watchEvent.context() as Path)
                    val s = when(watchEvent.kind()) {
                        ENTRY_CREATE -> "Create $path"
                        ENTRY_MODIFY -> "Modify $path"
                        ENTRY_DELETE -> "Delete $path"
                        else -> "Unknown"
                    }
                    textLine(s)
                }
            }

            if (!buildActions.compileEnabled) {
                text("No known sources found")
            } else {
                text("Compiling")
            }

            // main menu
            textLine()
            textLine()
            bold { textLine("What would you like to do?") }
            if (menuIndex == 0) {
                text("> "); textLine("Add a programming language")

                if (menuAction) {
                    text("    Java or Kotlin? "); input(Completions("java", "kotlin"))
                    textLine()
                }
            } else {
                textLine("Add a programming language")
            }

            if (menuIndex == 1) {
                text("> "); textLine("Open in an IDE")
            } else {
                textLine("Open in an IDE")
            }

            if (menuIndex == 2) {
                text("> "); textLine("Help")
            } else {
                textLine("Help")
            }

            message?.let {
                textLine(it)
            }

        }.runUntilSignal {

            addTimer(Duration.ofMillis(500), true) {
                /*
                watcher
                f.collect { s ->
                    println(s)
                }
                 */
                /*
                val events = cwdWatchKey.pollEvents()
                if (events.isNotEmpty()) {
                    watchEvents.withWriteLock {
                        watchEvents.clear()
                        watchEvents.addAll(events)
                    }
                }
                 */
            }

            onKeyPressed {
                when (key) {
                    Keys.UP -> if (menuIndex > 0) menuIndex--
                    Keys.DOWN -> if (menuIndex < 2) menuIndex++
                    Keys.ENTER -> if (!menuAction) menuAction = true
                }
            }

            onInputEntered {
                // we should only ever create directories & files here
                // the gradle configuration generator should happen on the loop
                // (ie when a file is created or when graboo creates a file)
                if (menuIndex == 0) {
                    menuAction = false

                    if (input.lowercase().startsWith("k")) {
                        (cwd.path / "src/main/kotlin").createDirectories()
                        (cwd.path / "src/test/kotlin").createDirectories()

                        // todo: add App.kt & AppTest.kt
                    }
                    else {
                        val mainDir = (cwd.path / "src/main/java")
                        mainDir.createDirectories()

                        (mainDir / "App.java").writeText("""
                            public class App {
                                public static void main(String... args) {
                                    System.out.println("hello, world");
                                }
                            }
                        """.trimIndent())

                        val testDir = (cwd.path / "src/test/java")
                        testDir.createDirectories()

                        (testDir / "AppTest.java").writeText("""
                            import static org.junit.jupiter.api.Assertions.assertTrue;

                            import org.junit.jupiter.api.Test;

                            class AppTest {
                                @Test
                                void trueTest() {
                                    assertTrue(true);
                                }
                            }
                        """.trimIndent())
                    }

                    loop()
                }
            }

        }


    }

    // file watch

    // run

    // loop on change

}