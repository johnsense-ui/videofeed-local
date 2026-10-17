package br.gohan.videofeed.auth.presenter.login

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.auth.presenter.FakeAuthDataSource
import br.gohan.videofeed.auth.presenter.LoginAction
import br.gohan.videofeed.auth.presenter.LoginEvent
import br.gohan.videofeed.auth.presenter.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeDataSource: FakeAuthDataSource
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataSource = FakeAuthDataSource()
        viewModel = LoginViewModel(fakeDataSource)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty fields and no error`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.email).isEqualTo("")
            assertThat(state.password).isEqualTo("")
            assertThat(state.isLoading).isFalse()
            assertThat(state.error).isNull()
        }
    }

    @Test
    fun `OnEmailChange updates email in state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onAction(LoginAction.OnEmailChange("test@example.com"))
            assertThat(awaitItem().email).isEqualTo("test@example.com")
        }
    }

    @Test
    fun `OnPasswordChange updates password in state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onAction(LoginAction.OnPasswordChange("secret"))
            assertThat(awaitItem().password).isEqualTo("secret")
        }
    }

    @Test
    fun `OnSubmit with valid credentials emits NavigateToFeed event`() = runTest {
        fakeDataSource.loginResult = Result.Success("token-123")
        viewModel.onAction(LoginAction.OnEmailChange("user@test.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("password123"))

        viewModel.events.test {
            viewModel.onAction(LoginAction.OnSubmit)
            assertThat(awaitItem()).isEqualTo(LoginEvent.NavigateToFeed)
        }
    }

    @Test
    fun `OnSubmit with invalid credentials sets error in state`() = runTest {
        fakeDataSource.loginResult = Result.Error(DataError.Network.UNAUTHORIZED)
        viewModel.onAction(LoginAction.OnEmailChange("user@test.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("wrong"))

        viewModel.state.test {
            awaitItem() // current state
            viewModel.onAction(LoginAction.OnSubmit)
            awaitItem() // isLoading = true
            val errorState = awaitItem()
            assertThat(errorState.error).isNotNull()
            assertThat(errorState.isLoading).isFalse()
        }
    }
}
