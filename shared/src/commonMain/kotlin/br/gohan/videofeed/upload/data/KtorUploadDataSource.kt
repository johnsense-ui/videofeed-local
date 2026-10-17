package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.core.network.post
import br.gohan.videofeed.upload.data.dto.PresignRequestDto
import br.gohan.videofeed.upload.data.dto.PresignResponseDto
import br.gohan.videofeed.upload.data.dto.RegisterVideoRequestDto
import br.gohan.videofeed.upload.data.dto.RegisterVideoResponseDto
import br.gohan.videofeed.upload.domain.PresignResult
import br.gohan.videofeed.upload.domain.UploadError
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import io.ktor.client.HttpClient

class KtorUploadDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : UploadRemoteDataSource {

    override suspend fun presign(filename: String): Result<PresignResult, UploadError> {
        return when (val result = httpClient.post<PresignRequestDto, PresignResponseDto>(
            route = "/videos/presign",
            baseUrl = baseUrl,
            body = PresignRequestDto(filename)
        )) {
            is Result.Success -> Result.Success(PresignResult(result.data.uploadUrl, result.data.videoKey))
            is Result.Error -> Result.Error(UploadError.Presign)
        }
    }

    override suspend fun registerVideo(videoKey: String, title: String): EmptyResult<UploadError> {
        return when (val result = httpClient.post<RegisterVideoRequestDto, RegisterVideoResponseDto>(
            route = "/videos",
            baseUrl = baseUrl,
            body = RegisterVideoRequestDto(videoKey, title)
        )) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> Result.Error(UploadError.Register)
        }
    }
}
