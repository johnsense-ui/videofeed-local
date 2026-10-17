# VideoFeed Phase 3 — Feed Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ ARCHITECTURE UPDATE (2026-04-02):** Ignore all `:feature:feed:*` module references. There are **3 Gradle modules only: `:composeApp`, `:server`, `:shared`**. Feed code lives in `:shared` as package folders: `domain/feed/`, `data/feed/`, `presenter/feed/`. File paths like `feature/feed/domain/src/.../Foo.kt` map to `shared/src/commonMain/kotlin/br/gohan/videofeed/domain/feed/Foo.kt`.

**Goal:** Implement the video feed — KMP `FeedViewModel` with pagination, and the Android feed screen using `VerticalPager` + a single shared `ExoPlayer` instance + `SimpleCache` pre-fetching for the next 2 videos.

**Architecture:** All feed KMP code lives in `:shared` under `domain/feed/`, `data/feed/`, `presenter/feed/`. Android composables in `:composeApp` own the `ExoPlayer` lifecycle, `VideoPreloader`, and `VerticalPager`.

**Tech Stack:** KMP, Ktor Client, Koin, Media3 ExoPlayer + SimpleCache, Coil 3, Compose Foundation VerticalPager

---

## File Map

### Version catalog additions
- `gradle/libs.versions.toml` — Media3, Coil 3

### `:feature:feed:domain`
```
feature/feed/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/domain/
  VideoRemoteDataSource.kt
  FeedResult.kt
```

### `:feature:feed:data`
```
feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/
  dto/FeedDtos.kt
  KtorVideoDataSource.kt
  feedDataModule.kt
feature/feed/data/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/data/
  KtorVideoDataSourceTest.kt
```

### `:feature:feed:presentation`
```
feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/
  FeedViewModel.kt          (State, Action, Event + ViewModel)
  VideoUi.kt                (UI model + mapper)
  feedPresentationModule.kt
feature/feed/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/presentation/
  FeedViewModelTest.kt
```

### `:composeApp`
```
composeApp/src/androidMain/kotlin/br/gohan/videofeed/
  feed/FeedScreen.kt            (Root + Screen composables)
  feed/VideoPlayerView.kt       (AndroidView wrapping PlayerView)
  feed/VideoInfoOverlay.kt      (title + uploader overlay)
  feed/VideoPreloader.kt        (pre-fetches next 2 videos into SimpleCache)
  navigation/FeedNavGraph.kt
  di/AppModules.kt              (updated — add feed + ExoPlayer modules)
  navigation/AppNavHost.kt      (updated — add feedGraph)
```

---

## Task 1: Add Phase 3 Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/feed/data/build.gradle.kts`
- Modify: `feature/feed/presentation/build.gradle.kts`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to `gradle/libs.versions.toml`**

Append to `[versions]`:
```toml
media3 = "1.6.1"
coil = "3.1.0"
```

Append to `[libraries]`:
```toml
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-datasource-cache = { module = "androidx.media3:media3-datasource-cache", version.ref = "media3" }
media3-datasource = { module = "androidx.media3:media3-datasource", version.ref = "media3" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
```

- [ ] **Step 2: Update `feature/feed/data/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    iosX64(); iosArm64(); iosSimulatorArm64(); jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.feature.feed.domain)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
        }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.feed.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 3: Update `feature/feed/presentation/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    iosX64(); iosArm64(); iosSimulatorArm64(); jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.feature.feed.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.assertk)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.feed.presentation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 4: Add Media3 and Coil to `composeApp/build.gradle.kts`**

In the `androidMain.dependencies` block add:
```kotlin
implementation(libs.media3.exoplayer)
implementation(libs.media3.ui)
implementation(libs.media3.datasource.cache)
implementation(libs.media3.datasource)
implementation(libs.coil.compose)
implementation(libs.coil.network.ktor)
// Feed feature modules
implementation(projects.feature.feed.domain)
implementation(projects.feature.feed.data)
implementation(projects.feature.feed.presentation)
```

- [ ] **Step 5: Verify sync**

Run:
```
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml feature/feed/data/build.gradle.kts feature/feed/presentation/build.gradle.kts composeApp/build.gradle.kts
git commit -m "chore: add Phase 3 dependencies — Media3, Coil, feed module configs"
```

---

## Task 2: Implement `:feature:feed:domain`

**Files:**
- Create: `feature/feed/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/domain/FeedResult.kt`
- Create: `feature/feed/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/domain/VideoRemoteDataSource.kt`

- [ ] **Step 1: Create `FeedResult.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.domain

import br.gohan.videofeed.core.domain.model.Video

data class FeedResult(
    val videos: List<Video>,
    val page: Int,
    val hasMore: Boolean
)
```

- [ ] **Step 2: Create `VideoRemoteDataSource.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.domain

import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result

interface VideoRemoteDataSource {
    suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network>
}
```

- [ ] **Step 3: Commit**

```bash
git add feature/feed/domain/src/
git commit -m "feat: add VideoRemoteDataSource interface and FeedResult domain model"
```

---

## Task 3: Implement `:feature:feed:data`

**Files:**
- Create: `feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/dto/FeedDtos.kt`
- Create: `feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/KtorVideoDataSource.kt`
- Create: `feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/feedDataModule.kt`
- Test: `feature/feed/data/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/data/KtorVideoDataSourceTest.kt`

- [ ] **Step 1: Write failing tests**

Create `feature/feed/data/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/data/KtorVideoDataSourceTest.kt`:

```kotlin
package br.gohan.videofeed.feature.feed.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertIs

class KtorVideoDataSourceTest {

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `getFeed returns mapped videos on 200`() = kotlinx.coroutines.test.runTest {
        val body = """
            {
                "videos": [
                    {"id":"1","title":"Video 1","cdnUrl":"https://cdn/v1.mp4","thumbnailUrl":"https://cdn/t1.jpg","uploaderName":"alice"},
                    {"id":"2","title":"Video 2","cdnUrl":"https://cdn/v2.mp4","thumbnailUrl":null,"uploaderName":"bob"}
                ],
                "page": 1,
                "hasMore": true
            }
        """.trimIndent()
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertIs<Result.Success<*>>(result)
        assertThat(result.data.videos).hasSize(2)
        assertThat(result.data.videos[0].title).isEqualTo("Video 1")
        assertThat(result.data.hasMore).isTrue()
    }

    @Test
    fun `getFeed returns empty list on 200 with no videos`() = kotlinx.coroutines.test.runTest {
        val body = """{"videos":[],"page":1,"hasMore":false}"""
        val client = mockClient(HttpStatusCode.OK, body)
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertIs<Result.Success<*>>(result)
        assertThat(result.data.videos).hasSize(0)
        assertThat(result.data.hasMore).isFalse()
    }

    @Test
    fun `getFeed returns SERVER_ERROR on 500`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, """{}""")
        val dataSource = KtorVideoDataSource(client, "http://localhost")
        val result = dataSource.getFeed(page = 1, limit = 10)
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run:
```
./gradlew :feature:feed:data:jvmTest
```
Expected: FAIL — `KtorVideoDataSource` does not exist yet

- [ ] **Step 3: Create `FeedDtos.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.data.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class VideoDtoData(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)

@Serializable
internal data class FeedResponseDto(
    val videos: List<VideoDtoData>,
    val page: Int,
    val hasMore: Boolean
)
```

- [ ] **Step 4: Create `KtorVideoDataSource.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.data

import br.gohan.videofeed.core.data.network.get
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.core.domain.error.map
import br.gohan.videofeed.core.domain.model.Video
import br.gohan.videofeed.feature.feed.data.dto.FeedResponseDto
import br.gohan.videofeed.feature.feed.data.dto.VideoDtoData
import br.gohan.videofeed.feature.feed.domain.FeedResult
import br.gohan.videofeed.feature.feed.domain.VideoRemoteDataSource
import io.ktor.client.HttpClient

class KtorVideoDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : VideoRemoteDataSource {

    override suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network> =
        httpClient.get<FeedResponseDto>(
            route = "/feed",
            baseUrl = baseUrl,
            queryParameters = mapOf("page" to page, "limit" to limit)
        ).map { it.toDomain() }

    private fun FeedResponseDto.toDomain() = FeedResult(
        videos = videos.map { it.toDomain() },
        page = page,
        hasMore = hasMore
    )

    private fun VideoDtoData.toDomain() = Video(
        id = id,
        title = title,
        cdnUrl = cdnUrl,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName
    )
}
```

- [ ] **Step 5: Create `feedDataModule.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.data

import br.gohan.videofeed.feature.feed.domain.VideoRemoteDataSource
import org.koin.dsl.bind
import org.koin.dsl.module

val feedDataModule = module {
    single { KtorVideoDataSource(get(), getProperty("baseUrl")) } bind VideoRemoteDataSource::class
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:
```
./gradlew :feature:feed:data:jvmTest
```
Expected: BUILD SUCCESSFUL — all 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add feature/feed/data/src/
git commit -m "feat: implement KtorVideoDataSource with mapping and tests"
```

---

## Task 4: Implement `FeedViewModel`

**Files:**
- Create: `feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/VideoUi.kt`
- Create: `feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/FeedViewModel.kt`
- Create: `feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/feedPresentationModule.kt`
- Test: `feature/feed/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/presentation/FeedViewModelTest.kt`

- [ ] **Step 1: Create `VideoUi.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.presentation

import br.gohan.videofeed.core.domain.model.Video

data class VideoUi(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)

fun Video.toVideoUi() = VideoUi(
    id = id,
    title = title,
    cdnUrl = cdnUrl,
    thumbnailUrl = thumbnailUrl,
    uploaderName = uploaderName
)
```

- [ ] **Step 2: Write failing tests**

Create `feature/feed/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/presentation/FeedViewModelTest.kt`:

```kotlin
package br.gohan.videofeed.feature.feed.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.core.domain.model.Video
import br.gohan.videofeed.feature.feed.domain.FeedResult
import br.gohan.videofeed.feature.feed.domain.VideoRemoteDataSource
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
        fakeDataSource.hasMore = true
        val secondPageVideos = listOf(makeVideo("3"), makeVideo("4"))
        fakeDataSource.secondPageVideos = secondPageVideos

        viewModel.state.test {
            awaitItem() // initial load with 2 videos
            viewModel.onAction(FeedAction.OnVideoVisible(1)) // near end (size - 1)
            val state = awaitItem()
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
    id = id,
    title = "Video $id",
    cdnUrl = "https://cdn/video$id.mp4",
    thumbnailUrl = "https://cdn/thumb$id.jpg",
    uploaderName = "user$id"
)

class FakeVideoDataSource : VideoRemoteDataSource {
    var shouldReturnError = false
    var hasMore = false
    var secondPageVideos: List<Video> = emptyList()
    private var callCount = 0

    override suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network> {
        if (shouldReturnError) return Result.Error(DataError.Network.SERVER_ERROR)
        callCount++
        val videos = if (callCount == 1) listOf(makeVideo("1"), makeVideo("2"))
                     else secondPageVideos
        return Result.Success(FeedResult(videos = videos, page = page, hasMore = hasMore && callCount == 1))
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run:
```
./gradlew :feature:feed:presentation:jvmTest
```
Expected: FAIL — `FeedViewModel` does not exist yet

- [ ] **Step 4: Create `FeedViewModel.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.core.domain.error.onFailure
import br.gohan.videofeed.core.domain.error.onSuccess
import br.gohan.videofeed.feature.feed.domain.VideoRemoteDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class FeedViewModel(
    private val videoDataSource: VideoRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()

    private val _events = Channel<FeedEvent>()
    val events = _events.receiveAsFlow()

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
                    _state.update { it.copy(
                        videos = result.videos.map { it.toVideoUi() },
                        isLoading = false
                    )}
                    isLastPage = !result.hasMore
                    currentPage = 2
                }
                .onFailure { error ->
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Failed to load feed"
                    )}
                }
        }
    }

    private fun loadNextPage() {
        isLoadingNextPage = true
        viewModelScope.launch {
            videoDataSource.getFeed(page = currentPage, limit = 10)
                .onSuccess { result ->
                    _state.update { it.copy(
                        videos = it.videos + result.videos.map { it.toVideoUi() }
                    )}
                    isLastPage = !result.hasMore
                    currentPage++
                }
                .onFailure { /* silently ignore pagination errors */ }
            isLoadingNextPage = false
        }
    }

    private fun refresh() {
        currentPage = 1
        isLastPage = false
        isLoadingNextPage = false
        loadFeed()
    }
}
```

- [ ] **Step 5: Create `feedPresentationModule.kt`**

```kotlin
package br.gohan.videofeed.feature.feed.presentation

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val feedPresentationModule = module {
    viewModelOf(::FeedViewModel)
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:
```
./gradlew :feature:feed:presentation:jvmTest
```
Expected: BUILD SUCCESSFUL — all 5 tests PASS

- [ ] **Step 7: Commit**

```bash
git add feature/feed/presentation/src/
git commit -m "feat: implement FeedViewModel with pagination, prefetch trigger, and tests"
```

---

## Task 5: Android ExoPlayer Infrastructure

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoPreloader.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoPlayerView.kt`

- [ ] **Step 1: Create `VideoPreloader.kt`**

```kotlin
package br.gohan.videofeed.feed

import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@UnstableApi
class VideoPreloader(
    private val cache: SimpleCache,
    private val dataSourceFactory: CacheDataSource.Factory
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pre-caches the first 2 MB of the video — enough for a smooth start
    fun prefetch(url: String) {
        scope.launch {
            try {
                val dataSpec = DataSpec(Uri.parse(url), 0, 2 * 1024 * 1024)
                CacheWriter(
                    dataSourceFactory.createDataSource(),
                    dataSpec,
                    null,
                    null
                ).cache()
            } catch (_: Exception) {
                // Prefetch failure is non-fatal — video will stream normally
            }
        }
    }
}
```

- [ ] **Step 2: Create `VideoPlayerView.kt`**

```kotlin
package br.gohan.videofeed.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoPreloader.kt \
        composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoPlayerView.kt
git commit -m "feat: add VideoPreloader with SimpleCache and VideoPlayerView composable"
```

---

## Task 6: Build the Feed Screen

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoInfoOverlay.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/FeedScreen.kt`

- [ ] **Step 1: Create `VideoInfoOverlay.kt`**

```kotlin
package br.gohan.videofeed.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.gohan.videofeed.feature.feed.presentation.VideoUi

@Composable
fun VideoInfoOverlay(
    video: VideoUi,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 16.dp, bottom = 80.dp, end = 80.dp)
    ) {
        Text(
            text = "@${video.uploaderName}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
```

- [ ] **Step 2: Create `FeedScreen.kt`**

```kotlin
package br.gohan.videofeed.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import br.gohan.videofeed.auth.ObserveAsEvents
import br.gohan.videofeed.feature.feed.presentation.FeedAction
import br.gohan.videofeed.feature.feed.presentation.FeedEvent
import br.gohan.videofeed.feature.feed.presentation.FeedState
import br.gohan.videofeed.feature.feed.presentation.FeedViewModel
import br.gohan.videofeed.feature.feed.presentation.VideoUi
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@UnstableApi
@Composable
fun FeedRoot(
    onNavigateToUpload: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val simpleCache = remember {
        SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L),
            StandaloneDatabaseProvider(context)
        )
    }

    val cacheDataSourceFactory = remember {
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }

    val preloader = remember { VideoPreloader(simpleCache, cacheDataSourceFactory) }

    // Prefetch next 2 videos whenever the current index changes
    LaunchedEffect(state.currentIndex, state.videos.size) {
        state.videos
            .drop(state.currentIndex + 1)
            .take(2)
            .forEach { preloader.prefetch(it.cdnUrl) }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            simpleCache.release()
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            FeedEvent.NavigateToUpload -> onNavigateToUpload()
            FeedEvent.NavigateToLogin -> onNavigateToLogin()
        }
    }

    FeedScreen(
        state = state,
        exoPlayer = exoPlayer,
        onAction = viewModel::onAction
    )
}

@UnstableApi
@Composable
fun FeedScreen(
    state: FeedState,
    exoPlayer: ExoPlayer,
    onAction: (FeedAction) -> Unit
) {
    if (state.videos.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { state.videos.size })

    // Update player and notify ViewModel when page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.videos.isNotEmpty()) {
                val video = state.videos[page]
                exoPlayer.setMediaItem(MediaItem.fromUri(video.cdnUrl))
                exoPlayer.prepare()
                exoPlayer.play()
                onAction(FeedAction.OnVideoVisible(page))
            }
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        VideoPageItem(
            video = state.videos[page],
            isCurrentPage = pagerState.currentPage == page,
            exoPlayer = exoPlayer
        )
    }
}

@UnstableApi
@Composable
private fun VideoPageItem(
    video: VideoUi,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isCurrentPage) {
            VideoPlayerView(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        VideoInfoOverlay(
            video = video,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/
git commit -m "feat: implement FeedScreen with VerticalPager, ExoPlayer, and prefetch"
```

---

## Task 7: Wire Navigation and DI

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/FeedNavGraph.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AppNavHost.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/AppModules.kt`

- [ ] **Step 1: Create `FeedNavGraph.kt`**

```kotlin
package br.gohan.videofeed.navigation

import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.gohan.videofeed.feed.FeedRoot

@UnstableApi
fun NavGraphBuilder.feedGraph(
    onNavigateToUpload: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    composable<FeedRoute> {
        FeedRoot(
            onNavigateToUpload = onNavigateToUpload,
            onNavigateToLogin = onNavigateToLogin
        )
    }
}
```

- [ ] **Step 2: Update `AppNavHost.kt`**

```kotlin
package br.gohan.videofeed.navigation

import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

@UnstableApi
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        authGraph(
            navController = navController,
            onNavigateToFeed = {
                navController.navigate(FeedRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                }
            }
        )
        feedGraph(
            onNavigateToUpload = { navController.navigate(UploadRoute) },
            onNavigateToLogin = {
                navController.navigate(LoginRoute) {
                    popUpTo(FeedRoute) { inclusive = true }
                }
            }
        )
        // uploadGraph() added in Phase 4
    }
}
```

- [ ] **Step 3: Add `UploadRoute` placeholder to `AppRoutes.kt`**

```kotlin
@Serializable data object UploadRoute   // placeholder for Phase 4
```

- [ ] **Step 4: Update `AppModules.kt`**

```kotlin
package br.gohan.videofeed.di

import androidx.media3.common.util.UnstableApi
import br.gohan.videofeed.core.data.auth.DataStoreTokenStorage
import br.gohan.videofeed.core.data.auth.TokenStorage
import br.gohan.videofeed.core.data.network.HttpClientFactory
import br.gohan.videofeed.feature.auth.data.authDataModule
import br.gohan.videofeed.feature.auth.presentation.authPresentationModule
import br.gohan.videofeed.feature.feed.data.feedDataModule
import br.gohan.videofeed.feature.feed.presentation.feedPresentationModule
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreAndroidModule = module {
    single<TokenStorage> { DataStoreTokenStorage(androidContext()) }
    single { HttpClientFactory.create(Android.create(), get()) }
}

@UnstableApi
val appModules = listOf(
    coreAndroidModule,
    authDataModule,
    authPresentationModule,
    feedDataModule,
    feedPresentationModule
)
```

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/ \
        composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/
git commit -m "feat: wire feed nav graph and Koin modules"
```

---

## Task 8: Final Verification

- [ ] **Step 1: Run all Phase 3 tests**

Run:
```
./gradlew :feature:feed:data:jvmTest :feature:feed:presentation:jvmTest
```
Expected: BUILD SUCCESSFUL — all tests pass

- [ ] **Step 2: Build the Android app**

Run:
```
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat: Phase 3 complete — feed feature with ExoPlayer, SimpleCache prefetch, and pagination"
```

---

## Phase 3 Checklist

- [ ] `VideoRemoteDataSource` interface + `FeedResult` in `:feature:feed:domain` ✓
- [ ] `KtorVideoDataSource` with mapper + tests ✓
- [ ] `FeedViewModel` with pagination, prefetch trigger, events — tested with Turbine ✓
- [ ] `VideoPreloader` using Media3 `CacheWriter` (2 MB prefetch per video) ✓
- [ ] `VideoPlayerView` wrapping `PlayerView` via `AndroidView` ✓
- [ ] `FeedScreen` with `VerticalPager` + single shared `ExoPlayer` ✓
- [ ] Thumbnail shown for non-visible pages via Coil `AsyncImage` ✓
- [ ] Feed nav graph + `AppNavHost` updated ✓
- [ ] App builds and scrollable video feed works ✓

**Next:** Phase 4 — Upload feature (KMP + R2 direct upload + progress bar)
