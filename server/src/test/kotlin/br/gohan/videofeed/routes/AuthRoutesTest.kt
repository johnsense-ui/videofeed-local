package br.gohan.videofeed.routes

import br.gohan.videofeed.TestDatabaseConfig
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.module
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

class AuthRoutesTest {

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
    fun `register returns 201 with token`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"register@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `register with duplicate email returns 409`() = testApplication {
        application { module(useTestDb = true) }
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@test.com","password":"password123"}""")
        }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with valid credentials returns 200 with token`() = testApplication {
        application { module(useTestDb = true) }
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"login@test.com","password":"password123"}""")
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"login@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login with wrong password returns 401`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@test.com","password":"wrongpass"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
