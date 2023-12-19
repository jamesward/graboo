import com.benasher44.uuid.uuid4
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TemplaterCommonTest {
    private val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

    @BeforeTest
    fun before() {
        FileSystem.SYSTEM.createDirectory(tmpDir)
    }

    @AfterTest
    fun after() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun write_works() = runBlocking {
        val contents = Templater.contents(Archetype.KOTLINAPP)

        Templater.write(contents, tmpDir)

        val files = FileSystem.SYSTEM.listRecursively(tmpDir).map {
            it.relativeTo(tmpDir)
        }

        contents.forEach {
            assertTrue(files.contains(it.key))
        }
    }

}