package br.gohan.videofeed.feed.presenter

import br.gohan.videofeed.core.model.Video

data class VideoUi(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val likeCount: Int = 0,
    val authorId: String = ""
)

fun Video.toVideoUi() = VideoUi(
    id = id, title = title, cdnUrl = cdnUrl,
    thumbnailUrl = thumbnailUrl, uploaderName = uploaderName,
    likeCount = likeCount, authorId = authorId
)
