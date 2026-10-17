package br.gohan.videofeed.feed.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.core.error.onFailure
import br.gohan.videofeed.core.error.onSuccess
import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FeedViewModel(
    private val videoDataSource: VideoRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    @kotlin.native.HiddenFromObjC
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _events = Channel<FeedEvent>()
    @kotlin.native.HiddenFromObjC
    val events: Flow<FeedEvent> = _events.receiveAsFlow()

    private var currentPage = 1
    private var isLastPage = false
    private var isLoadingNextPage = false

    init {
        loadFeed()
    }

    fun onAction(action: FeedAction) {
        when (action) {
            is FeedAction.OnVideoVisible -> onVideoVisible(action.index)
            is FeedAction.OnRefresh -> refresh()
            is FeedAction.OnUploadClick -> viewModelScope.launch { _events.send(FeedEvent.NavigateToUpload) }
            is FeedAction.OnLoginClick -> viewModelScope.launch { _events.send(FeedEvent.NavigateToLogin) }
        }
    }

    fun observeState(block: (FeedState) -> Unit) {
        viewModelScope.launch { state.collect { block(it) } }
    }

    fun observeEvents(block: (FeedEvent) -> Unit) {
        viewModelScope.launch { events.collect { block(it) } }
    }

    fun currentState(): FeedState = _state.value

    fun dispose() { viewModelScope.cancel() }

    private fun onVideoVisible(index: Int) {
        _state.update { it.copy(currentIndex = index) }
        val shouldLoadMore = !isLastPage &&
            !isLoadingNextPage &&
            index >= _state.value.videos.size - 3
        if (shouldLoadMore) loadNextPage()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            videoDataSource.getFeed(page = 1, limit = 10)
                .onSuccess { result ->
                    if (result.videos.isEmpty()) {
                        _state.update { it.copy(isLoading = false) }
                        _events.send(FeedEvent.NavigateToUpload)
                        return@launch
                    }
                    _state.update { it.copy(
                        videos = result.videos.map { it.toVideoUi() },
                        isLoading = false
                    )}
                    isLastPage = !result.hasMore
                    currentPage = 2
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, error = "Failed to load feed") }
                }
        }
    }

    private fun loadNextPage() {
        isLoadingNextPage = true
        viewModelScope.launch {
            try {
                videoDataSource.getFeed(page = currentPage, limit = 10)
                    .onSuccess { result ->
                        _state.update { it.copy(
                            videos = it.videos + result.videos.map { v -> v.toVideoUi() }
                        )}
                        isLastPage = !result.hasMore
                        currentPage++
                    }
                    .onFailure { /* silently ignore pagination errors */ }
            } finally {
                isLoadingNextPage = false
            }
        }
    }

    private fun refresh() {
        currentPage = 1
        isLastPage = false
        isLoadingNextPage = false
        loadFeed()
    }
}
