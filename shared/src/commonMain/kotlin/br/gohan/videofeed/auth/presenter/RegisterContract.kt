package br.gohan.videofeed.auth.presenter

data class RegisterState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface RegisterAction {
    data class OnEmailChange(val email: String) : RegisterAction
    data class OnPasswordChange(val password: String) : RegisterAction
    data object OnSubmit : RegisterAction
    data object OnNavigateToLogin : RegisterAction
}

sealed interface RegisterEvent {
    data object NavigateToFeed : RegisterEvent
    data object NavigateToLogin : RegisterEvent
}
