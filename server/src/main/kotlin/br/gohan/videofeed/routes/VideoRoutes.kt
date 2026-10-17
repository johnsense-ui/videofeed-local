package br.gohan.videofeed.routes

import br.gohan.videofeed.dto.CreateVideoRequest
import br.gohan.videofeed.dto.PresignRequest
import br.gohan.videofeed.service.VideoService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.videoRoutes(videoService: VideoService) {
    authenticate("jwt") {
        post("/videos/presign") {
            val request = call.receive<PresignRequest>()
            call.respond(videoService.presign(request.filename))
        }

        post("/videos") {
            val uploaderId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val request = call.receive<CreateVideoRequest>()
            call.respond(HttpStatusCode.Created, videoService.createVideo(request.videoKey, request.title, uploaderId))
        }
    }

    get("/videos/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val video = videoService.getVideo(id) ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respond(video)
    }
}
