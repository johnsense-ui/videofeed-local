package br.gohan.videofeed.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideoDto(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)

@Serializable
data class PresignRequest(val filename: String)

@Serializable
data class PresignResponse(val uploadUrl: String, val videoKey: String)

@Serializable
data class CreateVideoRequest(val videoKey: String, val title: String)

@Serializable
data class FeedResponse(val videos: List<VideoDto>, val page: Int, val hasMore: Boolean)
