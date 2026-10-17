package br.gohan.videofeed.feed.presenter

data class FeedState(
    val videos: List<VideoUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0
)

sealed interface FeedAction {
    data class OnVideoVisible(val index: Int) : FeedAction
    data object OnRefresh : FeedAction
    data object OnUploadClick : FeedAction
    data object OnLoginClick : FeedAction
}

sealed interface FeedEvent {
    data object NavigateToUpload : FeedEvent
    data object NavigateToLogin : FeedEvent
}
