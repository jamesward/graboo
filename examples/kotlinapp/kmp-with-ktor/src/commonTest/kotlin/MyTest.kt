import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@kotlin.experimental.ExperimentalNativeApi
class MyTest {

    @Test
    fun fake_test() {
        runBlocking {
            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""hello, world"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = HttpClient(mockEngine)

            val response = client.get("http://doesnotmatter.com")

            assert(response.status.isSuccess())
        }
    }

}