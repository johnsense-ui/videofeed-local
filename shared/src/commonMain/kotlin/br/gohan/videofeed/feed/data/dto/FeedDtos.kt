package br.gohan.videofeed.feed.data.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class VideoDtoData(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)

@Serializable
internal data class FeedResponseDto(
    val videos: List<VideoDtoData>,
    val page: Int,
    val hasMore: Boolean
)
