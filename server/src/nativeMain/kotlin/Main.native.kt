import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

// from: https://gist.github.com/Stexxe/4867bbd9b44339f9f9adc39e166894ca
class LocalFileContent(private val path: Path, private val name: String) : OutgoingContent.WriteChannelContent() {

    private val metadata = FileSystem.SYSTEM.metadata(path)

    override val contentLength: Long? =
        metadata.size

    override val contentType: ContentType =
        ContentType.defaultForFilePath(path.toString())

    // todo: 404 handling
    /*
    override val status: HttpStatusCode? =
        TODO()
     */

    override val headers: Headers =
        Headers.build {
            append(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name)
            )
        }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val source = FileSystem.SYSTEM.source(path)
        source.use { fileSource ->
            fileSource.buffer().use { bufferedFileSource ->
                val buf = ByteArray(4 * 1024)
                while (true) {
                    val read = bufferedFileSource.read(buf)
                    if (read <= 0) break
                    channel.writeFully(buf, 0, read)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun createAndServe(
    dir: Path,
    zip: Path,
    archetype: Archetype,
    name: String,
    call: ApplicationCall
) {
    println(dir)
    println(zip)

    if (!FileSystem.SYSTEM.exists(zip)) {
        println("creating zip")
        // todo: thread safe
        val contents = Templater.contents(archetype)
        Templater.write(contents, dir)

        FileSystem.SYSTEM.listRecursively(dir).forEach {
            println(it)
        }

        val cmd = "cd ${dir.parent} && zip -r $zip ${dir.name}"
        println(cmd)
        val fp = popen(cmd, "r")

        val stdout = buildString {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
                append(input.toKString())
            }
        }

        println(stdout)

        val status = pclose(fp)
        println(status)
    }

    println("responding")

    println(FileSystem.SYSTEM.exists(zip))

    call.respond(LocalFileContent(zip, "$name.zip"))
}