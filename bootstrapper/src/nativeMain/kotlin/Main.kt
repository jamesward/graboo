import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val client = HttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }
}

@kotlinx.serialization.Serializable
data class AvailableReleases(val most_recent_lts: Int)

suspend fun getLatestJdk(): Pair<String, ByteArray> = run {
    val availableReleasesUrl = "https://api.adoptium.net/v3/info/available_releases"
    val availableReleases = client.get(availableReleasesUrl).body<AvailableReleases>()
    val os = "linux"
    val arch = "x64"
    val downloadUrl = "https://api.adoptium.net/v3/binary/latest/${availableReleases.most_recent_lts}/ga/$os/$arch/jdk/hotspot/normal/eclipse?project=jdk"
    val downloadGet = client.get(downloadUrl) {
        headers {
            append(HttpHeaders.Accept, "*/*")
        }
    }
    val status = downloadGet.status
    if (status.value != 200) {
        throw Exception("Could not download JDK (status = ${status.value}) for $downloadUrl")
    }
    // todo: look into bodyAsChannel
    val contentDisposition = downloadGet.headers[HttpHeaders.ContentDisposition]!!
    val filename = contentDisposition.removePrefix("attachment; filename=")
    Pair(filename, downloadGet.readBytes())
}

fun saveJdk(): Unit = TODO()

// bug: entrypoint can't be a suspend fun
fun main(): Unit = runBlocking {

    // check that ~/.graboo/jdk/current.properties exists
    // check that the version in current.properties exists (i.e. 17.0.4)
    // check that the java command works
    // if not download the latest lts jdk
    val (filename, bytes) = getLatestJdk()
    // todo: other platforms
    val version = filename.removeSuffix(".tar.gz").split("_hotspot_")[1]
    println(version)

    //   extract it to ~/.graboo/jdk/{version}
    //   write ~/.graboo/jdk/current.properties

    // start the defined java process
}