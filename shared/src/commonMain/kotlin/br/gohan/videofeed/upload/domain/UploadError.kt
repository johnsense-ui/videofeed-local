package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.Error

sealed interface UploadError : Error {
    data object Presign : UploadError
    data object Upload : UploadError
    data object Register : UploadError
}
