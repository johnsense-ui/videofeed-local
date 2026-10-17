package br.gohan.videofeed.core.network

import br.gohan.videofeed.auth.data.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

object HttpClientFactory {
    fun create(engine: HttpClientEngine, tokenStorage: TokenStorage): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    KermitLogger.d("HttpClient") { message }
                }
            }
            level = LogLevel.HEADERS
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val token = tokenStorage.getToken() ?: return@loadTokens null
                    BearerTokens(accessToken = token, refreshToken = "")
                }
            }
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}
