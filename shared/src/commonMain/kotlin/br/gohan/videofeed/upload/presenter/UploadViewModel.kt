package br.gohan.videofeed.upload.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class UploadViewModel(
    private val remoteDataSource: UploadRemoteDataSource,
    private val r2DataSource: R2UploadDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(UploadState())
    @kotlin.native.HiddenFromObjC
    val state: StateFlow<UploadState> = _state.asStateFlow()

    private val _events = Channel<UploadEvent>()
    @kotlin.native.HiddenFromObjC
    val events: Flow<UploadEvent> = _events.receiveAsFlow()

    private var selectedBytes: ByteArray? = null
    private var selectedMimeType: String = "video/mp4"

    fun onAction(action: UploadAction) {
        when (action) {
            is UploadAction.OnFileSelected -> {
                selectedBytes = action.bytes
                selectedMimeType = action.mimeType
                _state.update { it.copy(selectedFilename = action.filename) }
            }
            is UploadAction.OnTitleChange -> _state.update { it.copy(title = action.title) }
            is UploadAction.OnSubmit -> upload()
        }
    }

    fun observeState(block: (UploadState) -> Unit) {
        viewModelScope.launch { state.collect { block(it) } }
    }

    fun observeEvents(block: (UploadEvent) -> Unit) {
        viewModelScope.launch { events.collect { block(it) } }
    }

    fun currentState(): UploadState = _state.value

    fun dispose() { viewModelScope.cancel() }

    private fun upload() {
        val bytes = selectedBytes ?: return
        val filename = _state.value.selectedFilename ?: return
        val title = _state.value.title.trim()
        if (title.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(status = UploadStatus.Presigning) }
            val presignResult = remoteDataSource.presign(filename)
            if (presignResult is Result.Error) {
                _state.update { it.copy(status = UploadStatus.Error("Failed to get upload URL")) }
                return@launch
            }
            val presign = (presignResult as Result.Success).data

            r2DataSource.upload(presign.uploadUrl, bytes, selectedMimeType).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(status = UploadStatus.Uploading(result.data)) }
                    is Result.Error -> {
                        _state.update { it.copy(status = UploadStatus.Error("Upload failed")) }
                        return@collect
                    }
                }
            }
            if (_state.value.status is UploadStatus.Error) return@launch

            _state.update { it.copy(status = UploadStatus.Finalizing) }
            val registerResult = remoteDataSource.registerVideo(presign.videoKey, title)
            if (registerResult is Result.Error) {
                _state.update { it.copy(status = UploadStatus.Error("Failed to save video")) }
                return@launch
            }

            _state.update { it.copy(status = UploadStatus.Done) }
            _events.send(UploadEvent.NavigateToFeed)
        }
    }
}
