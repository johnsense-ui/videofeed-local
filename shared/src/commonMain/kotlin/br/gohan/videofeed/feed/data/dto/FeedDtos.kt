package br.gohan.videofeed.feed.data.dto

import kotlinx.serialization.Serializable

/**
 * 本地视频库（Fastify）返回的 /api/videos 结构。
 * 服务端字段很多（compat/size/mtime/...），HttpClient 的 Json 已开 ignoreUnknownKeys。
 */
@Serializable
internal data class LocalFeedResponseDto(
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val items: List<LocalVideoDto> = emptyList()
)

@Serializable
internal data class LocalVideoDto(
    val id: Long,
    val title: String? = null,
    val caption: String? = null,
    val authorName: String? = null,
    val authorId: String? = null,
    val digg: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val hasPoster: Boolean = false
)
