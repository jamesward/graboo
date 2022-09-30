import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

// todo: JavaHome
object Gradler {

    fun wrapper(dir: ProjectConfig.ProjectDir): Unit = run {
        GradleConnector.newConnector().forProjectDirectory(dir.path.toFile()).connect().use { connection ->
            val build = connection.newBuild()
            build.addArguments("--console=plain")

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
                    val parts = dep.split(':')
                    when(parts.size) {
                        2 -> Dependency(parts[0], parts[1])
                        3 -> Dependency(parts[0], parts[1], parts[2])
                        else -> null
                    }
                } catch (_: Exception) {
                    null
                }
        }
    }

    fun addDependency(buildFile: ProjectConfig.BuildFile, dependency: Dependency, sourceType: ProjectConfig.SourceType): Unit = run {
        val lines = buildFile.path.readLines()

        val depScope = when(sourceType) {
            ProjectConfig.SourceType.MAIN -> "implementation"
            ProjectConfig.SourceType.TEST -> "testImplementation"
        }

        val newLine = if (dependency.version != null) {
            "    $depScope(\"${dependency.groupId}:${dependency.artifactId}:${dependency.version}\")"
        }
        else {
            // todo: this requires some other config to work
            "    $depScope(\"${dependency.groupId}:${dependency.artifactId}\")"
        }

        val dependencyLine = lines.indexOf("dependencies {")
        val newLines = if (dependencyLine >= 0) {
            val before = lines.take(dependencyLine + 1)
            val after = lines.drop(dependencyLine + 1)
            before + newLine + after
        }
        else {
            lines + listOf("dependencies {", newLine, "}", "")
        }

        buildFile.path.writeLines(newLines)
    }

    fun hasDependency(buildFile: ProjectConfig.BuildFile, dependency: Dependency): Boolean =
        buildFile.path.readLines().any {
            it.contains("\"${dependency.groupId}:${dependency.artifactId}")
        }


    // import kotlinx.coroutines
}