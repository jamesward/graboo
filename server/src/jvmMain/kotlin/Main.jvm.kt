import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.use
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual suspend fun createAndServe(
    dir: Path,
    zip: Path,
    archetype: Archetype,
    name: String,
    call: ApplicationCall
) {

    if (!FileSystem.SYSTEM.exists(zip)) {
        // todo: thread safe
        val contents = Templater.contents(archetype)
        Templater.write(contents, dir)

        withContext(Dispatchers.IO) {
            FileOutputStream(zip.toFile()).use {
                ZipOutputStream(it).use { zipOutputStream ->
                    dir.toFile().walkTopDown().forEach { file ->
                        val relativeFile = file.relativeTo(zip.parent!!.toFile())

                        if (file.isDirectory()) {
                            val zipEntry = ZipEntry("$relativeFile/")
                            zipOutputStream.putNextEntry(zipEntry)
                            zipOutputStream.closeEntry()
                        } else {
                            val zipEntry = ZipEntry(relativeFile.toString())
                            zipOutputStream.putNextEntry(zipEntry)
                            zipOutputStream.write(file.readBytes())
                            zipOutputStream.closeEntry()
                        }
                    }
                }
            }
        }
    }

    call.respondFile(zip.toFile()) {
        headersOf(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$name.zip").toString())
    }
}