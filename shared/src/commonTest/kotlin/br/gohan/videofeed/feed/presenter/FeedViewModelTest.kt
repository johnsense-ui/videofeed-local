package br.gohan.videofeed.feed.presenter

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.feed.domain.FeedResult
import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import br.gohan.videofeed.core.model.Video
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
class FeedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeDataSource: FakeVideoDataSource
    private lateinit var viewModel: FeedViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataSource = FakeVideoDataSource()
        viewModel = FeedViewModel(fakeDataSource)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads first page of feed`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.videos).hasSize(2)
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun `OnVideoVisible near end loads next page`() = runTest {
        val paginatedFake = FakeVideoDataSource().also {
            it.hasMore = true
            it.secondPageVideos = listOf(makeVideo("3"), makeVideo("4"))
        }
        val vm = FeedViewModel(paginatedFake)

        vm.state.test {
            awaitItem() // initial load with 2 videos
            vm.onAction(FeedAction.OnVideoVisible(1)) // near end (size - 1)
            awaitItem() // currentIndex update
            val state = awaitItem() // videos updated with next page
            assertThat(state.videos).hasSize(4)
        }
    }

    @Test
    fun `OnVideoVisible updates currentIndex`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onAction(FeedAction.OnVideoVisible(1))
            assertThat(awaitItem().currentIndex).isEqualTo(1)
        }
    }

    @Test
    fun `error from data source sets error in state`() = runTest {
        fakeDataSource.shouldReturnError = true
        val vm = FeedViewModel(fakeDataSource)
        vm.state.test {
            val state = awaitItem()
            assertThat(state.error).isNotNull()
            assertThat(state.videos).isEmpty()
        }
    }

    @Test
    fun `OnUploadClick emits NavigateToUpload event`() = runTest {
        viewModel.events.test {
            viewModel.onAction(FeedAction.OnUploadClick)
            assertThat(awaitItem()).isEqualTo(FeedEvent.NavigateToUpload)
        }
    }
}

fun makeVideo(id: String) = Video(
    id = id, title = "Video $id", cdnUrl = "https://cdn/video$id.mp4",
    thumbnailUrl = "https://cdn/thumb$id.jpg", uploaderName = "user$id"
)
