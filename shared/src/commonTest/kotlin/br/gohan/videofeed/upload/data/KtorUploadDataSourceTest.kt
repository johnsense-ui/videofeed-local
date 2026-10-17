package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class KtorUploadDataSourceTest {

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `presign returns PresignResult on 200`() = runTest {
        val client = mockClient(
            HttpStatusCode.OK,
            """{"uploadUrl":"https://r2.example.com/upload","videoKey":"videos/abc.mp4"}"""
        )
        val result = KtorUploadDataSource(client, "http://localhost").presign("test.mp4")
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `presign returns UploadError_Presign on 500`() = runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, "")
        val result = KtorUploadDataSource(client, "http://localhost").presign("test.mp4")
        assertIs<Result.Error<*>>(result)
        assertIs<UploadError.Presign>(result.error)
    }

    @Test
    fun `registerVideo returns success on 201`() = runTest {
        val client = mockClient(
            HttpStatusCode.Created,
            """{"id":"1","title":"My Video","cdnUrl":"https://cdn/v.mp4","thumbnailUrl":null}"""
        )
        val result = KtorUploadDataSource(client, "http://localhost").registerVideo("videos/abc.mp4", "My Video")
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `registerVideo returns UploadError_Register on 401`() = runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, "")
        val result = KtorUploadDataSource(client, "http://localhost").registerVideo("videos/abc.mp4", "My Video")
        assertIs<Result.Error<*>>(result)
        assertIs<UploadError.Register>(result.error)
    }
}
