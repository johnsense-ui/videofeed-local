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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs

/** 对接本地视频库 /api/videos 后的映射测试 */
class KtorVideoDataSourceTest {

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    @Test
    fun `getFeed maps local api items`() = kotlinx.coroutines.test.runTest {
        val body = """
            {
                "total": 2,
                "offset": 0,
                "limit": 10,
                "items": [
                    {"id":1,"title":"Video 1","caption":"cap 1","authorName":"alice","authorId":"a1","digg":100},
                    {"id":2,"title":"Video 2","caption":null,"authorName":"bob","authorId":"a2","digg":20}
                ]
            }
        """.trimIndent()
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client) { "http://localhost" }

        val result = dataSource.getFeed(page = 1, limit = 10)

        assertIs<Result.Success<FeedResult>>(result)
        assertThat(result.data.videos).hasSize(2)
        assertThat(result.data.videos[0].title).isEqualTo("cap 1")
        assertThat(result.data.videos[0].cdnUrl).isEqualTo("http://localhost/api/stream/1")
        assertThat(result.data.videos[0].likeCount).isEqualTo(100)
        assertThat(result.data.videos[0].authorId).isEqualTo("a1")
        assertThat(result.data.hasMore).isFalse()
    }

    @Test
    fun `getFeed returns empty list`() = kotlinx.coroutines.test.runTest {
        val body = """{"total":0,"offset":0,"limit":10,"items":[]}"""
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client) { "http://localhost" }

        val result = dataSource.getFeed(page = 1, limit = 10)

        assertIs<Result.Success<FeedResult>>(result)
        assertThat(result.data.videos).hasSize(0)
        assertThat(result.data.hasMore).isFalse()
    }

    @Test
    fun `getFeed reports SERVER_ERROR on 500`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, """{}""")
        val dataSource = KtorVideoDataSource(client) { "http://localhost" }

        val result = dataSource.getFeed(page = 1, limit = 10)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }
}
