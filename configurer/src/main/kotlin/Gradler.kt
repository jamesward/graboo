import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path

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

    fun runMain(dir: ProjectConfig.ProjectDir): String = run {
        GradleConnector.newConnector().forProjectDirectory(dir.path.toFile()).connect().use { connection ->
            val build = connection.newBuild()
            build.forTasks("run")
            ByteArrayOutputStream().use { baos ->
                build.setStandardOutput(baos)
                //build.setJavaHome(File("/path/to/java"))
                build.run()

                baos.toByteArray().decodeToString()
            }
        }
    }

}