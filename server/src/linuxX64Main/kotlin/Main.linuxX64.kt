import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

// from: https://gist.github.com/Stexxe/4867bbd9b44339f9f9adc39e166894ca
class LocalFileContent(private val path: Path) : OutgoingContent.WriteChannelContent() {

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
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name)
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

actual suspend fun ApplicationCall.respondPath(path: Path) {
    this.respond(LocalFileContent(path))
}
