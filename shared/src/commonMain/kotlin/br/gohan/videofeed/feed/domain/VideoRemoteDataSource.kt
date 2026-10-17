package br.gohan.videofeed.feed.domain

import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result

interface VideoRemoteDataSource {
    suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network>
}
