import com.varabyte.kotter.foundation.input.Completions
import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.runUntilInputEntered
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.cyan
import com.varabyte.kotter.foundation.text.p
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.foundation.text.yellow
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object Utils {
    fun copyTemplate(dir: File, template: String) {
        javaClass.getResourceAsStream("templates/$template/build.gradle.kts")?.use { b ->
            Files.copy(b, File(dir, "build.gradle.kts").toPath())
        }
    }
}

fun main() {

    Utils.copyTemplate(File("tmp"), "kotlin")

    /*

    1. If no project
        a. If cwd/*.java -> ask user if they want to standardize project, if yes -> move to src/main/java else their own their own
        b. If cwd/*.kt -> ditto with src/main/kotlin
        c. Else -> ask the user what type of project they want and bootstrap accordingly

    2. Configure the project's build based on file structure, etc
        a. if cwd/src/main/java -> bootstrap a Java project
        b. if cwd/src/main/kotlin -> bootstrap a Kotlin project

        - If src contains a runnable app (main) then add application

    3. Run gradle
       a. If build fails with errors related to missing deps, then prompt for dependency
       b. Auto-restart build when it changes

    4. Enable launching IntelliJ from graboo

    5. Menu for adding dependencies & plugins
        a. Searchable
        "would you like to enable x feature?"
     */

    session {
        var counter by liveVarOf(0)
        section {
            text("count = $counter")
        }.run {
            repeat(10) {
                delay(1000)
                counter++
            }
        }
    }
}