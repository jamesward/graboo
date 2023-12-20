import com.benasher44.uuid.uuid4
import com.kgit2.process.Command
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.AfterClass
import kotlin.test.BeforeClass
import kotlin.test.assertTrue

expect val testCompressedBase64: String
expect val testCompressedFilename: String

@kotlinx.cinterop.ExperimentalForeignApi
class MainTest {

    companion object {
        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()

        @BeforeClass
        fun before() {
            FileSystem.SYSTEM.createDirectories(tmpDir)
        }
        @AfterClass
        fun after() {
            FileSystem.SYSTEM.deleteRecursively(tmpDir)
        }
    }

    @Test
    fun saveToTempExtractAndDelete_works() = runBlocking {
        val to = tmpDir / "extracted"
        FileSystem.SYSTEM.createDirectory(to)
        val bytes = testCompressedBase64.decodeBase64Bytes()
        saveToTempExtractAndDelete(testCompressedFilename, to, bytes)

        val extractedFile = to / "file.txt"
        val line = FileSystem.SYSTEM.read(extractedFile) {
            readUtf8Line()
        }

        assertTrue(line == "hello, world")
    }

    @Test
    fun chunker_works_with_one_chunk() = runBlocking {
        val (filename, bytes) = chunkedDownload("http://ipv4.download.thinkbroadband.com/5MB.zip")
        assertTrue(filename == "5MB.zip")
        assertTrue(bytes.size == 5242880)
    }

    @Test
    fun chunker_works_with_offset_multiple_chunks() = runBlocking {
        val (filename, bytes) = chunkedDownload("http://ipv4.download.thinkbroadband.com/5MB.zip", chunkSize = 876 * 1000)
        assertTrue(filename == "5MB.zip")
        assertTrue(bytes.size == 5242880)
    }

    @Test
    fun get_latest_jdk() = runBlocking {
        val (filename, bytes) = getLatestJdk()
        assertTrue(filename.isNotBlank())
        assertTrue(bytes.size > 100_000_000)
    }

    @Test
    fun get_and_run_jdk() = runBlocking {
        val jdkDir = tmpDir / "jdk"
        FileSystem.SYSTEM.createDirectories(jdkDir, false)

        val jdk = setupJdk("21.0.1_12", jdkDir, jdkDir / "current.properties")
        assertTrue(FileSystem.SYSTEM.exists(jdk), "$jdk exists")

        val javaExe = javaExe(jdk)
        assertTrue(FileSystem.SYSTEM.exists(javaExe), "$javaExe exists")

        val result = Command(javaExe.toString())
                .args("-version")
                .spawn()
                .wait()

        assertTrue(result.code == 0)
    }

    // todo: test for runGradleWrapper

}
