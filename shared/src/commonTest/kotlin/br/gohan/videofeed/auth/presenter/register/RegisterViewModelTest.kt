package br.gohan.videofeed.auth.presenter.register

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.auth.presenter.FakeAuthDataSource
import br.gohan.videofeed.auth.presenter.RegisterAction
import br.gohan.videofeed.auth.presenter.RegisterEvent
import br.gohan.videofeed.auth.presenter.RegisterViewModel
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
class RegisterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeDataSource: FakeAuthDataSource
    private lateinit var viewModel: RegisterViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataSource = FakeAuthDataSource()
        viewModel = RegisterViewModel(fakeDataSource)
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
    fun `OnSubmit with success emits NavigateToFeed`() = runTest {
        fakeDataSource.registerResult = Result.Success("token-abc")
        viewModel.onAction(RegisterAction.OnEmailChange("new@test.com"))
        viewModel.onAction(RegisterAction.OnPasswordChange("password123"))

        viewModel.events.test {
            viewModel.onAction(RegisterAction.OnSubmit)
            assertThat(awaitItem()).isEqualTo(RegisterEvent.NavigateToFeed)
        }
    }

    @Test
    fun `OnSubmit with conflict sets error in state`() = runTest {
        fakeDataSource.registerResult = Result.Error(DataError.Network.CONFLICT)
        viewModel.onAction(RegisterAction.OnEmailChange("existing@test.com"))
        viewModel.onAction(RegisterAction.OnPasswordChange("password123"))

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(RegisterAction.OnSubmit)
            awaitItem() // loading
            val errorState = awaitItem()
            assertThat(errorState.error).isNotNull()
            assertThat(errorState.isLoading).isFalse()
        }
    }
}
