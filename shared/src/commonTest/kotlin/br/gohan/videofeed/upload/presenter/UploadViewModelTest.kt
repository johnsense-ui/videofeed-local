package br.gohan.videofeed.upload.presenter

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
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
class UploadViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        remoteDataSource: FakeUploadRemoteDataSource = FakeUploadRemoteDataSource(),
        r2DataSource: FakeR2UploadDataSource = FakeR2UploadDataSource()
    ) = UploadViewModel(remoteDataSource, r2DataSource)

    @Test
    fun `initial state is Idle with empty form`() = runTest {
        val vm = buildViewModel()
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Idle::class)
        assertThat(vm.state.value.title).isEqualTo("")
        assertThat(vm.state.value.selectedFilename).isEqualTo(null)
    }

    @Test
    fun `OnTitleChange updates title in state`() = runTest {
        val vm = buildViewModel()
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        assertThat(vm.state.value.title).isEqualTo("My Video")
    }

    @Test
    fun `OnFileSelected stores filename in state`() = runTest {
        val vm = buildViewModel()
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        assertThat(vm.state.value.selectedFilename).isEqualTo("clip.mp4")
    }

    @Test
    fun `OnSubmit progresses through full upload flow and emits NavigateToFeed`() = runTest {
        val vm = buildViewModel()
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        vm.onAction(UploadAction.OnTitleChange("My Video"))

        vm.events.test {
            vm.onAction(UploadAction.OnSubmit)
            assertThat(awaitItem()).isInstanceOf(UploadEvent.NavigateToFeed::class)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Done::class)
    }

    @Test
    fun `OnSubmit sets Error state when presign fails`() = runTest {
        val vm = buildViewModel(
            remoteDataSource = FakeUploadRemoteDataSource(presignResult = Result.Error(UploadError.Presign))
        )
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        vm.onAction(UploadAction.OnSubmit)
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Error::class)
    }

    @Test
    fun `OnSubmit sets Error state when R2 upload fails`() = runTest {
        val vm = buildViewModel(
            r2DataSource = FakeR2UploadDataSource(listOf(Result.Error(UploadError.Upload)))
        )
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        vm.onAction(UploadAction.OnSubmit)
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Error::class)
    }
}
