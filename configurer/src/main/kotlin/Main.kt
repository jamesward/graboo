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


fun main() {
    /*

    1. If no project, create one from various templates
    2. Configure the project's build based on file structure, etc
    3. Run gradle
       a. If build fails with errors related to missing deps, then prompt for dependency
       b. Auto-restart build when it changes

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