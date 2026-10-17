package br.gohan.videofeed.upload.presenter

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.PresignResult
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadError
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeUploadRemoteDataSource(
    private val presignResult: Result<PresignResult, UploadError> = Result.Success(
        PresignResult("https://r2.example.com/upload", "videos/abc.mp4")
    ),
    private val registerResult: EmptyResult<UploadError> = Result.Success(Unit)
) : UploadRemoteDataSource {
    override suspend fun presign(filename: String) = presignResult
    override suspend fun registerVideo(videoKey: String, title: String) = registerResult
}

class FakeR2UploadDataSource(
    private val emissions: List<Result<Float, UploadError>> = listOf(
        Result.Success(0.5f),
        Result.Success(1.0f)
    )
) : R2UploadDataSource {
    override fun upload(uploadUrl: String, bytes: ByteArray, mimeType: String): Flow<Result<Float, UploadError>> =
        flowOf(*emissions.toTypedArray())
}
