package br.gohan.videofeed.feed.presenter

import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.feed.domain.FeedResult
import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import br.gohan.videofeed.core.model.Video

class FakeVideoDataSource : VideoRemoteDataSource {
    var shouldReturnError = false
    var hasMore = false
    var secondPageVideos: List<Video> = emptyList()
    private var callCount = 0

    override suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network> {
        if (shouldReturnError) return Result.Error(DataError.Network.SERVER_ERROR)
        callCount++
        val videos = if (callCount == 1) listOf(makeVideo("1"), makeVideo("2"))
                     else secondPageVideos
        return Result.Success(FeedResult(videos = videos, page = page, hasMore = hasMore && callCount == 1))
    }
}
