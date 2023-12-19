import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import okio.Path

actual suspend fun ApplicationCall.respondPath(path: Path) {
    this.respondFile(path.toFile()) {
        headersOf(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name).toString())
    }
}