package br.gohan.videofeed.feed.data

import br.gohan.videofeed.feed.data.dto.LocalFeedResponseDto
import br.gohan.videofeed.feed.data.dto.LocalVideoDto
import br.gohan.videofeed.core.network.get
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.core.error.map
import br.gohan.videofeed.feed.domain.FeedResult
import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import br.gohan.videofeed.core.model.Video
import io.ktor.client.HttpClient

/**
 * 原来打的是 Ktor 后端的 /feed；
 * 现在直接打本地视频库的 /api/videos，视频流走 /api/stream/{id}，封面走 /api/poster/{id}。
 *
 * baseUrl 用 provider 传进来，Swift 侧改 ApiConfig.baseUrl 后立刻生效。
 */
class KtorVideoDataSource(
    private val httpClient: HttpClient,
    private val baseUrlProvider: () -> String
) : VideoRemoteDataSource {

    private fun host(): String = baseUrlProvider().trimEnd('/')

    override suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network> {
        val offset = ((page - 1).coerceAtLeast(0)) * limit
        return httpClient.get<LocalFeedResponseDto>(
            route = "/api/videos",
            baseUrl = host(),
            queryParameters = mapOf(
                "limit" to limit,
                "offset" to offset,
                "sort" to "newest"
            )
        ).map { it.toDomain(page) }
    }

    private fun LocalFeedResponseDto.toDomain(page: Int) = FeedResult(
        videos = items.map { it.toDomain() },
        page = page,
        hasMore = (offset + items.size) < total
    )

    private fun LocalVideoDto.toDomain(): Video {
        val base = host()
        val captionText = caption?.takeIf { it.isNotBlank() } ?: title.orEmpty()
        val author = authorName?.takeIf { it.isNotBlank() } ?: "未知作者"
        return Video(
            id = id.toString(),
            title = captionText,
            cdnUrl = "$base/api/stream/$id",
            thumbnailUrl = "$base/api/poster/$id",
            uploaderName = author,
            likeCount = digg,
            authorId = authorId.orEmpty()
        )
    }
}
