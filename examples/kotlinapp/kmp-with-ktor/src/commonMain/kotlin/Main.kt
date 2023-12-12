import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

val client = HttpClient()

expect fun env(s: String): String?

fun main() = runBlocking {
    val tags = client.get("https://api.github.com/repos/jetbrains/kotlin/tags") {
        env("GITHUB_TOKEN")?.let {
            bearerAuth(it)
        }
    }.bodyAsText()
    println(tags)
}