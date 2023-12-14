import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import okio.FileSystem
import okio.Path


expect suspend fun createAndServe(dir: Path, zip: Path, archetype: Archetype, name: String, call: ApplicationCall)

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
                        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / archetype.toString()
                        val tmpZip = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("$archetype.zip")

                        createAndServe(tmpDir, tmpZip, archetype, name, call)
                    }
                }
                post("/download") {
                    val formParameters = call.receiveParameters()
                    val name = formParameters.getOrFail("project_name")
                    Archetype(formParameters.getOrFail("project_type"))?.let { archetype ->
                        call.respondRedirect("/$archetype/$name.zip")
                    } ?: call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
        //awaitCancellation()
    //}
    server.start(wait = true)
}