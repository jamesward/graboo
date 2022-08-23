import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

// todo: JavaHome
object Gradler {

    fun wrapper(dir: ProjectConfig.ProjectDir): Unit = run {
        GradleConnector.newConnector().forProjectDirectory(dir.path.toFile()).connect().use { connection ->
            val build = connection.newBuild()
            build.forTasks("wrapper")
            //build.setJavaHome(File("/path/to/java"))
            build.run()
        }
    }

    fun runTaskAndCaptureOutput(dir: ProjectConfig.ProjectDir, task: String): String = run {
        GradleConnector.newConnector().forProjectDirectory(dir.path.toFile()).connect().use { connection ->
            val build = connection.newBuild()
            build.forTasks(task)
            ByteArrayOutputStream().use { baos ->
                build.setStandardOutput(baos)
                //build.setJavaHome(File("/path/to/java"))
                build.run()

                baos.toByteArray().decodeToString()
            }
        }
    }

    data class Dependency(val groupId: String, val artifactId: String, val version: String? = null) {
        companion object {
            fun parse(dep: String): Dependency? =
                try {
                    val (groupId, artifactId) = dep.split(':')
                    Dependency(groupId, artifactId)
                } catch (_: Exception) {
                    null
                }
        }
    }

    fun addDependency(buildFile: ProjectConfig.BuildFile, dependency: Dependency): Unit = run {
        val lines = buildFile.path.readLines()

        val newLine = if (dependency.version != null) {
            "    implementation(\"${dependency.groupId}:${dependency.artifactId}:${dependency.version}\")"
        }
        else {
            // todo: this requires some other config to work
            "    implementation(\"${dependency.groupId}:${dependency.artifactId}\")"
        }

        val dependencyLine = lines.indexOf("dependencies {")
        val newLines = if (dependencyLine >= 0) {
            val before = lines.take(dependencyLine + 1)
            val after = lines.drop(dependencyLine + 1)
            before + newLine + after
        }
        else {
            listOf("dependencies {", newLine, "}", "") + lines
        }

        buildFile.path.writeLines(newLines)
    }

    fun hasDependency(buildFile: ProjectConfig.BuildFile, dependency: Dependency): Boolean =
        buildFile.path.readLines().any {
            it.contains("\"${dependency.groupId}:${dependency.artifactId}")
        }


    // import kotlinx.coroutines
}