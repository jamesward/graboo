import com.benasher44.uuid.uuid4
import com.kgit2.process.Command
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path


fun createZip(dir: Path, archetype: Archetype, name: String): Path = runBlocking(Dispatchers.IO) {
    val templateDir = dir / name
    val zipFile = dir / "$name.zip"

    val contents = Templater.contents(archetype)
    Templater.write(contents, templateDir)

    Command("zip")
        .args("-r", zipFile.toString(), name)
        .cwd(dir.toString())
        .spawn()
        .wait()

    FileSystem.SYSTEM.deleteRecursively(templateDir)

    zipFile
}

expect suspend fun ApplicationCall.respondPath(path: Path)

fun main() {
//fun main() = SuspendApp {
    // resourceScope {
        //server(CIO, port = 8080) {
    val server = embeddedServer(CIO, port = 8080) {
            routing {
                get("/") {
                    call.respondHtml(HttpStatusCode.OK, UI.index)
                }
                get("/{archetype}/{name}.zip") {
                    val archetype = call.parameters["archetype"]?.let { Archetype(it) }
                    val name = call.parameters["name"] ?: "demo"

                    if (archetype == null) {
                        call.respond(HttpStatusCode.NotFound, "The specified project type is not valid")
                    }
                    else {
                        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / uuid4().toString()
                        val zipFile = createZip(tmpDir, archetype, name)
                        call.respondPath(zipFile)
                    }
                }
                post("/download") {
                    val formParameters = call.receiveParameters()
                    val name = formParameters.getOrFail("project_name")
                    Archetype(formParameters.getOrFail("project_type"))?.let { archetype ->
                        call.respondRedirect("/$archetype/$name.zip")
                    } ?: call.respond(HttpStatusCode.BadRequest)
                }
                /*
                get("/graboo-server-js-wasm-js.wasm") {
                    call.respondBytes(
                        contentType = ContentType.parse("application/wasm"),
                        bytes = StaticFiles.wasm.decodeBase64Bytes()
                    )
                }
                get ("/graboo.js") {
                    call.respondText(StaticFiles.js.decodeBase64String(), contentType = ContentType.Text.JavaScript)
                }
                 */
            }
        }
        //awaitCancellation()
    //}
    server.start(wait = true)
}