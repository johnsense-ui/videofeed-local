package br.gohan.videofeed.routes

import br.gohan.videofeed.dto.LoginRequest
import br.gohan.videofeed.dto.RegisterRequest
import br.gohan.videofeed.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

fun Route.authRoutes(authService: AuthService) {
    post("/auth/register") {
        val request = call.receive<RegisterRequest>()
        val response = try {
            authService.register(request.email, request.password)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already registered"))
            return@post
        }
        call.respond(HttpStatusCode.Created, response)
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val response = authService.login(request.email, request.password) ?: run {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            return@post
        }
        call.respond(response)
    }
}
