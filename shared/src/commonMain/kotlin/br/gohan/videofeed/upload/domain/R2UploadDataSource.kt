package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.Result
import kotlinx.coroutines.flow.Flow

interface R2UploadDataSource {
    fun upload(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String
    ): Flow<Result<Float, UploadError>>
}
