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

class RegisterViewModel(
    private val authDataSource: AuthRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    @kotlin.native.HiddenFromObjC
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    private val _events = Channel<RegisterEvent>()
    @kotlin.native.HiddenFromObjC
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.OnEmailChange -> _state.update { it.copy(email = action.email) }
            is RegisterAction.OnPasswordChange -> _state.update { it.copy(password = action.password) }
            is RegisterAction.OnSubmit -> register()
            is RegisterAction.OnNavigateToLogin -> {
                viewModelScope.launch { _events.send(RegisterEvent.NavigateToLogin) }
            }
        }
    }

    fun observeState(block: (RegisterState) -> Unit) {
        viewModelScope.launch { state.collect { block(it) } }
    }

    fun observeEvents(block: (RegisterEvent) -> Unit) {
        viewModelScope.launch { events.collect { block(it) } }
    }

    fun currentState(): RegisterState = _state.value

    fun dispose() { viewModelScope.cancel() }

    private fun register() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authDataSource.register(_state.value.email, _state.value.password)
                .onSuccess { _events.send(RegisterEvent.NavigateToFeed) }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    private fun DataError.Network.toMessage(): String = when (this) {
        DataError.Network.CONFLICT -> "Email already registered"
        DataError.Network.NO_INTERNET -> "No internet connection"
        DataError.Network.SERVER_ERROR -> "Server error, please try again"
        else -> "Something went wrong"
    }
}
