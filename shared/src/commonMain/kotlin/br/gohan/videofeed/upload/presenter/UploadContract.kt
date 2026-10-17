package br.gohan.videofeed.upload.presenter

data class UploadState(
    val title: String = "",
    val selectedFilename: String? = null,
    val status: UploadStatus = UploadStatus.Idle
)

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Presigning : UploadStatus
    data class Uploading(val progress: Float) : UploadStatus
    data object Finalizing : UploadStatus
    data object Done : UploadStatus
    data class Error(val message: String) : UploadStatus
}

sealed interface UploadAction {
    data class OnFileSelected(
        val bytes: ByteArray,
        val filename: String,
        val mimeType: String
    ) : UploadAction
    data class OnTitleChange(val title: String) : UploadAction
    data object OnSubmit : UploadAction
}

sealed interface UploadEvent {
    data object NavigateToFeed : UploadEvent
}
