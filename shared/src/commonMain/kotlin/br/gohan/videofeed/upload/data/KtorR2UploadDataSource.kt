package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class KtorR2UploadDataSource(
    private val httpClient: HttpClient
) : R2UploadDataSource {

    override fun upload(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String
    ): Flow<Result<Float, UploadError>> = callbackFlow {
        try {
            val response = httpClient.put(uploadUrl) {
                contentType(ContentType.parse(mimeType))
                setBody(bytes)
                onUpload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0L) {
                        trySendBlocking(
                            Result.Success(bytesSentTotal.toFloat() / contentLength.toFloat())
                        )
                    }
                }
            }
            if (response.status.isSuccess()) {
                trySendBlocking(Result.Success(1.0f))
            } else {
                trySendBlocking(Result.Error(UploadError.Upload))
            }
        } catch (e: Exception) {
            trySendBlocking(Result.Error(UploadError.Upload))
        }
        close()
        awaitClose()
    }
}
