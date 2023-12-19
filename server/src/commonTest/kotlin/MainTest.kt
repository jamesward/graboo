import com.benasher44.uuid.uuid4
import com.kgit2.process.Command
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MainTest {

    private val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

    @BeforeTest
    fun before() {
        FileSystem.SYSTEM.createDirectories(tmpDir)
    }

    @AfterTest
    fun after() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun createZip_works() = runBlocking {
        val zipFile = createZip(tmpDir, Archetype.KOTLINAPP, "foo")
        assertTrue(zipFile.name == "foo.zip")

        Command("unzip")
            .args(zipFile.toString())
            .cwd(tmpDir.toString())
            .spawn()
            .wait()

        assertTrue(isExecutable(tmpDir / "foo" / "graboo"))
    }

}