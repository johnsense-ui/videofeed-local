package br.gohan.videofeed.auth.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.auth.domain.AuthRemoteDataSource
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.onFailure
import br.gohan.videofeed.core.error.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class LoginViewModel(
    private val authDataSource: AuthRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    @kotlin.native.HiddenFromObjC
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    @kotlin.native.HiddenFromObjC
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange -> _state.update { it.copy(email = action.email) }
            is LoginAction.OnPasswordChange -> _state.update { it.copy(password = action.password) }
            is LoginAction.OnSubmit -> login()
            is LoginAction.OnNavigateToRegister -> {
                viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
            }
        }
    }

    fun observeState(block: (LoginState) -> Unit) {
        viewModelScope.launch { state.collect { block(it) } }
    }

    fun observeEvents(block: (LoginEvent) -> Unit) {
        viewModelScope.launch { events.collect { block(it) } }
    }

    fun currentState(): LoginState = _state.value

    fun dispose() { viewModelScope.cancel() }

    private fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authDataSource.login(_state.value.email, _state.value.password)
                .onSuccess { _events.send(LoginEvent.NavigateToFeed) }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    private fun DataError.Network.toMessage(): String = when (this) {
        DataError.Network.UNAUTHORIZED -> "Invalid email or password"
        DataError.Network.NO_INTERNET -> "No internet connection"
        DataError.Network.SERVER_ERROR -> "Server error, please try again"
        else -> "Something went wrong"
    }
}
