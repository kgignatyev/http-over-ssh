import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlin.test.*

class ApplicationTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/ubuntu@ladder.kgignatyev.com/localhost:7070/api/public/mgmt").apply {
            assertEquals(HttpStatusCode.OK, status)
//            assertEquals("Hello World!", bodyAsText())
            println(bodyAsText())
        }
        cleanup.invoke(9)
    }
}
