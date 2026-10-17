package br.gohan.videofeed.auth.presenter

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface LoginAction {
    data class OnEmailChange(val email: String) : LoginAction
    data class OnPasswordChange(val password: String) : LoginAction
    data object OnSubmit : LoginAction
    data object OnNavigateToRegister : LoginAction
}

sealed interface LoginEvent {
    data object NavigateToFeed : LoginEvent
    data object NavigateToRegister : LoginEvent
}
