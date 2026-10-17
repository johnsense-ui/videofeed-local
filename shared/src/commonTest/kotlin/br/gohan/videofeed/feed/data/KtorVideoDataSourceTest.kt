package br.gohan.videofeed.feed.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.feed.domain.FeedResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertIs

class KtorVideoDataSourceTest {

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `getFeed returns mapped videos on 200`() = kotlinx.coroutines.test.runTest {
        val body = """
            {
                "videos": [
                    {"id":"1","title":"Video 1","cdnUrl":"https://cdn/v1.mp4","thumbnailUrl":"https://cdn/t1.jpg","uploaderName":"alice"},
                    {"id":"2","title":"Video 2","cdnUrl":"https://cdn/v2.mp4","thumbnailUrl":null,"uploaderName":"bob"}
                ],
                "page": 1,
                "hasMore": true
            }
        """.trimIndent()
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertIs<Result.Success<FeedResult>>(result)
        assertThat(result.data.videos).hasSize(2)
        assertThat(result.data.videos[0].title).isEqualTo("Video 1")
        assertThat(result.data.hasMore).isTrue()
    }

    @Test
    fun `getFeed returns empty list on 200 with no videos`() = kotlinx.coroutines.test.runTest {
        val body = """{"videos":[],"page":1,"hasMore":false}"""
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertIs<Result.Success<FeedResult>>(result)
        assertThat(result.data.videos).hasSize(0)
        assertThat(result.data.hasMore).isFalse()
    }

    @Test
    fun `getFeed returns SERVER_ERROR on 500`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, """{}""")
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }
}
