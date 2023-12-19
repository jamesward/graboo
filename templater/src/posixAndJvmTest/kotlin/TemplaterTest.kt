import com.benasher44.uuid.uuid4
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TemplaterPosixAndJVMTest {
    private val tmpFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

    @BeforeTest
    fun before() {
        FileSystem.SYSTEM.write(tmpFile) {
            writeUtf8("hello, world")
        }
    }

    @AfterTest
    fun after() {
        FileSystem.SYSTEM.delete(tmpFile)
    }

    @Test
    fun makeExecutable_works() {
        assertTrue(!isExecutable(tmpFile))
        makeExecutable(tmpFile)
        assertTrue(isExecutable(tmpFile))
    }
}