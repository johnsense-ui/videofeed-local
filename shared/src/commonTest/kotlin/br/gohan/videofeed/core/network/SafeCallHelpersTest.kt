package br.gohan.videofeed.core.network

import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SafeCallHelpersTest {

    @Serializable
    data class TestBody(val value: String)

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
    }

    @Test
    fun `200 response maps to Result Success`() = runTest {
        val client = mockClient(HttpStatusCode.OK, """{"value":"hello"}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertIs<Result.Success<TestBody>>(result)
        assertEquals("hello", result.data.value)
    }

    @Test
    fun `401 maps to UNAUTHORIZED`() = runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, """{}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
    }

    @Test
    fun `500 maps to SERVER_ERROR`() = runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, """{}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertEquals(Result.Error(DataError.Network.SERVER_ERROR), result)
    }
}
