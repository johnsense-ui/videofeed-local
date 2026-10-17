package br.gohan.videofeed.feed.domain

import br.gohan.videofeed.core.model.Video

data class FeedResult(
    val videos: List<Video>,
    val page: Int,
    val hasMore: Boolean
)
