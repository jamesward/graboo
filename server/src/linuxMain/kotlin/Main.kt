import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.awaitCancellation
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.coroutineScope
import kotlinx.html.*
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import platform.posix.chdir
import platform.posix.chmod
import platform.posix.execlp
import platform.posix.execvp
import platform.posix.fork
import platform.posix.system

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
fun main() {
//fun main() = SuspendApp {
    // resourceScope {
        //server(CIO, port = 8080) {
    val server = embeddedServer(CIO, port = 8080) {
            routing {
                get("/") {
                    call.respondHtml(HttpStatusCode.OK) {
                        head {
                            title {
                                +"hello, world"
                            }
                        }
                        body {
                            h1 {
                                +"Hello from ktor!"
                            }
                        }
                    }
                }
                get("/{archetype}/{name}.zip") {
                    val archetype = call.parameters["archetype"]?.let { Archetype(it) }
                    val name = call.parameters["name"] ?: "demo"

                    if (archetype == null) {
                        call.respond(HttpStatusCode.NotFound, "The specified project type is not valid")
                    }
                    else {
                        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / archetype.toString()
                        val tmpZip = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("$archetype.zip")

                        if (!FileSystem.SYSTEM.exists(tmpZip)) {
                            // todo: thread safe
                            val contents = Templater.contents(archetype)
                            Templater.write(contents, tmpDir)

                            val childPid = fork()
                            if (childPid == 0) {
                                chdir(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString())
                                memScoped {
                                    execlp("zip", "", "-r", tmpZip.toString(), tmpDir.name)
                                }
                            }
                        }
                        else {
                            call.respond(LocalFileContent(tmpZip, "$name.zip"))
                        }
                    }

                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }
        //awaitCancellation()
    //}
    server.start(wait = true)
}