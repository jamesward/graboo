import com.benasher44.uuid.uuid4
import com.kgit2.process.Command
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.logging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path


suspend fun createZip(dir: Path, archetype: Archetype, name: String, grabooVersion: String?): Path = run {
    val templateDir = dir / name
    val zipFile = dir / "$name.zip"

    val contents = Templater.contents(archetype, grabooVersion)
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
    val client = HttpClient()

    // todo: dev support
    val latestGraboo: String? = runBlocking {
        client.get("https://plugins.gradle.org/m2/com/jamesward/gradleboot/com.jamesward.gradleboot.gradle.plugin/maven-metadata.xml").bodyAsText().lines().find {
            it.contains("<latest>")
        }?.replace("<latest>", "")?.replace("</latest>", "")?.trim()?.removePrefix("v")
    }

//fun main() = SuspendApp {
    // resourceScope {
        //server(CIO, port = 8080) {
    val callLogging: ApplicationPlugin<Unit> = createApplicationPlugin("CallLogging") {
        on(ResponseSent) {
            println(it.request.toLogString() to it.response.status())
        }
        on(CallFailed) { call, cause ->
            println(call.request.toLogString() to call.response.status())
            cause.printStackTrace()
        }
    }

    // from: https://stackoverflow.com/a/46890009/77409
    suspend fun <T> retryIO(
        times: Int = Int.MAX_VALUE,
        initialDelay: Long = 100, // 0.1 second
        maxDelay: Long = 1000,    // 1 second
        factor: Double = 2.0,
        block: suspend () -> T): T
    {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block() // last attempt
    }

    val server = embeddedServer(CIO, port = 8080) {
        install(callLogging)

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
                    val zipFile = retryIO { createZip(tmpDir, archetype, name, latestGraboo) }
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