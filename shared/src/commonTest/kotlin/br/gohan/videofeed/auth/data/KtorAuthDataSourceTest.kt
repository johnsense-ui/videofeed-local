package br.gohan.videofeed.auth.data

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorAuthDataSourceTest {

    private val fakeTokenStorage = FakeTokenStorage()

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `login returns token on 200 and saves it`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.OK, """{"token":"jwt-token-123"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.login("user@test.com", "password123")
        assertIs<Result.Success<String>>(result)
        assertEquals("jwt-token-123", result.data)
        assertEquals("jwt-token-123", fakeTokenStorage.getToken())
    }

    @Test
    fun `login returns UNAUTHORIZED on 401`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, """{"error":"Invalid credentials"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.login("user@test.com", "wrong")
        assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
    }

    @Test
    fun `register returns token on 201 and saves it`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Created, """{"token":"new-jwt-token"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.register("new@test.com", "password123")
        assertIs<Result.Success<String>>(result)
        assertEquals("new-jwt-token", result.data)
        assertEquals("new-jwt-token", fakeTokenStorage.getToken())
    }

    @Test
    fun `register returns CONFLICT on 409`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Conflict, """{"error":"Email already registered"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.register("existing@test.com", "password123")
        assertEquals(Result.Error(DataError.Network.CONFLICT), result)
    }
}

class FakeTokenStorage : TokenStorage {
    private var token: String? = null
    override suspend fun getToken() = token
    override suspend fun saveToken(token: String) { this.token = token }
    override suspend fun clearToken() { token = null }
}
