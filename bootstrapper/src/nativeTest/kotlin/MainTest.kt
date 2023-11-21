import com.benasher44.uuid.uuid4
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.AfterClass
import kotlin.test.BeforeClass
import kotlin.test.assertTrue

@kotlinx.cinterop.ExperimentalForeignApi
class MainTest {

    private val testTarGz = """
        H4sIAAAAAAAAA+3RQQrCMBCF4Vl7ihxA0mRokvMIVlQChRqpx7ddFHWh4iKI+H+bgUlgHjzbSHVu
        klKYp0/B3c+F+DZoTBrV67RPro1iQv1oIudT2QzGyHF8/e/d+4+yze6QO1supd6NueAY2+f9+/DY
        v3dBVYyrF+nmz/vfdzn3azP2Q96uvh0GAAAAAAAAAAAAAAAAwEeubGzvnQAoAAA=
    """.trimIndent().replace("\n", "")

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
    fun extract_tar_gz() = runBlocking {
        val to = tmpDir / "extracted"
        FileSystem.SYSTEM.createDirectory(to)
        val bytes = testTarGz.decodeBase64Bytes()
        saveToTempExtractAndDelete("test.tar.gz", to, bytes)

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

}