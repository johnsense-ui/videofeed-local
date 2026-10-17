package br.gohan.videofeed.routes

import br.gohan.videofeed.TestDatabaseConfig
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedRoutesTest {

    @BeforeTest
    fun setup() {
        TestDatabaseConfig.init()
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(VideoTable, UserTable)
            SchemaUtils.create(UserTable, VideoTable)
        }
    }

    @Test
    fun `GET feed returns 200 with empty videos list`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/feed")
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["videos"])
        assertEquals("1", json["page"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET feed respects limit param`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/feed?page=1&limit=5")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET videos by id returns 404 for unknown id`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/videos/00000000-0000-0000-0000-000000000000")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST videos presign requires auth`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/videos/presign") {
            contentType(ContentType.Application.Json)
            setBody("""{"filename":"test.mp4"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
