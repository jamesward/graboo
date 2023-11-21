import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

val client = HttpClient()

fun main() = runBlocking {
    val tags = client.get("https://api.github.com/repos/jetbrains/kotlin/tags").bodyAsText()
    println(tags)
}