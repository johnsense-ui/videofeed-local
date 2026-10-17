package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result

interface UploadRemoteDataSource {
    suspend fun presign(filename: String): Result<PresignResult, UploadError>
    suspend fun registerVideo(videoKey: String, title: String): EmptyResult<UploadError>
}
