package br.gohan.videofeed.routes

import br.gohan.videofeed.service.VideoService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.feedRoutes(videoService: VideoService) {
    get("/feed") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceAtMost(50)
        call.respond(videoService.getFeed(page, limit))
    }
}
