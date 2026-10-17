package br.gohan.videofeed

import br.gohan.videofeed.config.DatabaseConfig
import br.gohan.videofeed.config.JwtConfig
import br.gohan.videofeed.routes.authRoutes
import br.gohan.videofeed.routes.feedRoutes
import br.gohan.videofeed.routes.videoRoutes
import br.gohan.videofeed.service.AuthService
import br.gohan.videofeed.service.ThumbnailService
import br.gohan.videofeed.service.VideoService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

const val SERVER_PORT = 8081

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(useTestDb: Boolean = false) {
    if (!useTestDb) {
        DatabaseConfig.init()
    }

    install(ContentNegotiation) { json() }

    install(Authentication) {
        jwt("jwt") {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }

    val authService = AuthService()
    val thumbnailService = ThumbnailService()
    val videoService = VideoService(thumbnailService)

    routing {
        authRoutes(authService)
        feedRoutes(videoService)
        videoRoutes(videoService)
    }
}
