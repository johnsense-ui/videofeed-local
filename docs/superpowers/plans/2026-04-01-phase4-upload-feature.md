# VideoFeed Phase 4 — Upload Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the video upload feature — KMP ViewModels coordinating a three-step flow (presign → direct R2 upload with live progress → register metadata), plus the Android upload screen with a file picker and progress bar.

**Architecture:** All upload KMP code lives in `:shared` under `upload/domain/`, `upload/data/`, `upload/presenter/`. The Android upload screen in `:composeApp` owns the file picker and feeds raw bytes into the ViewModel.

**Tech Stack:** KMP, Ktor Client, Koin, Compose, `ActivityResultContracts.GetContent` for file picking

---

## File Map

### `:shared` commonMain
```
shared/src/commonMain/kotlin/br/gohan/videofeed/
  upload/domain/
    UploadError.kt
    PresignResult.kt
    UploadRemoteDataSource.kt
    R2UploadDataSource.kt
  upload/data/
    dto/UploadDtos.kt
    KtorUploadDataSource.kt
    KtorR2UploadDataSource.kt
    uploadDataModule.kt
  upload/presenter/
    UploadContract.kt   ← UploadState, UploadStatus, UploadAction, UploadEvent
    UploadViewModel.kt
```

### `:shared` commonTest
```
shared/src/commonTest/kotlin/br/gohan/videofeed/
  upload/data/
    KtorUploadDataSourceTest.kt
    KtorR2UploadDataSourceTest.kt
  upload/presenter/
    FakeUploadDataSources.kt
    UploadViewModelTest.kt
```

### `:composeApp`
```
composeApp/src/androidMain/kotlin/br/gohan/videofeed/
  upload/UriHelpers.kt
  upload/UploadScreen.kt
  navigation/UploadNavGraph.kt
  navigation/AppNavHost.kt        — updated: uncomment uploadGraph
  di/AppModules.kt                — updated: add uploadDataModule + uploadViewModelOf
```

---

## Task 1: Upload Domain Layer

**Files:**
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/domain/UploadError.kt`
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/domain/PresignResult.kt`
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/domain/UploadRemoteDataSource.kt`
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/domain/R2UploadDataSource.kt`

- [ ] **Step 1: Create `UploadError.kt`**

```kotlin
package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.Error

sealed interface UploadError : Error {
    data object Presign : UploadError
    data object Upload : UploadError
    data object Register : UploadError
}
```

- [ ] **Step 2: Create `PresignResult.kt`**

```kotlin
package br.gohan.videofeed.upload.domain

data class PresignResult(
    val uploadUrl: String,
    val videoKey: String
)
```

- [ ] **Step 3: Create `UploadRemoteDataSource.kt`**

```kotlin
package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result

interface UploadRemoteDataSource {
    suspend fun presign(filename: String): Result<PresignResult, UploadError>
    suspend fun registerVideo(videoKey: String, title: String): EmptyResult<UploadError>
}
```

- [ ] **Step 4: Create `R2UploadDataSource.kt`**

```kotlin
package br.gohan.videofeed.upload.domain

import br.gohan.videofeed.core.error.Result
import kotlinx.coroutines.flow.Flow

interface R2UploadDataSource {
    fun upload(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String
    ): Flow<Result<Float, UploadError>>
}
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/upload/domain/
git commit -m "feat(upload): add upload domain interfaces and error types"
```

---

## Task 2: Upload DTOs and `KtorUploadDataSource`

**Files:**
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/dto/UploadDtos.kt`
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/KtorUploadDataSource.kt`
- Create: `shared/src/commonTest/kotlin/br/gohan/videofeed/upload/data/KtorUploadDataSourceTest.kt`

- [ ] **Step 1: Create `UploadDtos.kt`**

```kotlin
package br.gohan.videofeed.upload.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PresignRequestDto(val filename: String)

@Serializable
data class PresignResponseDto(val uploadUrl: String, val videoKey: String)

@Serializable
data class RegisterVideoRequestDto(val videoKey: String, val title: String)

@Serializable
data class RegisterVideoResponseDto(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?
)
```

- [ ] **Step 2: Create `KtorUploadDataSource.kt`**

```kotlin
package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.core.network.post
import br.gohan.videofeed.upload.data.dto.PresignRequestDto
import br.gohan.videofeed.upload.data.dto.PresignResponseDto
import br.gohan.videofeed.upload.data.dto.RegisterVideoRequestDto
import br.gohan.videofeed.upload.data.dto.RegisterVideoResponseDto
import br.gohan.videofeed.upload.domain.PresignResult
import br.gohan.videofeed.upload.domain.UploadError
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import io.ktor.client.HttpClient

class KtorUploadDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : UploadRemoteDataSource {

    override suspend fun presign(filename: String): Result<PresignResult, UploadError> {
        return when (val result = httpClient.post<PresignRequestDto, PresignResponseDto>(
            route = "/videos/presign",
            baseUrl = baseUrl,
            body = PresignRequestDto(filename)
        )) {
            is Result.Success -> Result.Success(PresignResult(result.data.uploadUrl, result.data.videoKey))
            is Result.Error -> Result.Error(UploadError.Presign)
        }
    }

    override suspend fun registerVideo(videoKey: String, title: String): EmptyResult<UploadError> {
        return when (val result = httpClient.post<RegisterVideoRequestDto, RegisterVideoResponseDto>(
            route = "/videos",
            baseUrl = baseUrl,
            body = RegisterVideoRequestDto(videoKey, title)
        )) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> Result.Error(UploadError.Register)
        }
    }
}
```

- [ ] **Step 3: Write the tests**

```kotlin
package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class KtorUploadDataSourceTest {

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `presign returns PresignResult on 200`() = runTest {
        val client = mockClient(
            HttpStatusCode.OK,
            """{"uploadUrl":"https://r2.example.com/upload","videoKey":"videos/abc.mp4"}"""
        )
        val result = KtorUploadDataSource(client, "http://localhost").presign("test.mp4")
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `presign returns UploadError_Presign on 500`() = runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, "")
        val result = KtorUploadDataSource(client, "http://localhost").presign("test.mp4")
        assertIs<Result.Error<*>>(result)
        assertIs<UploadError.Presign>(result.error)
    }

    @Test
    fun `registerVideo returns success on 201`() = runTest {
        val client = mockClient(
            HttpStatusCode.Created,
            """{"id":"1","title":"My Video","cdnUrl":"https://cdn/v.mp4","thumbnailUrl":null}"""
        )
        val result = KtorUploadDataSource(client, "http://localhost").registerVideo("videos/abc.mp4", "My Video")
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `registerVideo returns UploadError_Register on 401`() = runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, "")
        val result = KtorUploadDataSource(client, "http://localhost").registerVideo("videos/abc.mp4", "My Video")
        assertIs<Result.Error<*>>(result)
        assertIs<UploadError.Register>(result.error)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "*.KtorUploadDataSourceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/ shared/src/commonTest/kotlin/br/gohan/videofeed/upload/data/KtorUploadDataSourceTest.kt
git commit -m "feat(upload): add KtorUploadDataSource with presign and register"
```

---

## Task 3: `KtorR2UploadDataSource`

**Files:**
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/KtorR2UploadDataSource.kt`
- Create: `shared/src/commonTest/kotlin/br/gohan/videofeed/upload/data/KtorR2UploadDataSourceTest.kt`

R2 upload uses a **separate plain `HttpClient`** with no bearer auth headers — the presigned URL contains all the auth the request needs.

- [ ] **Step 1: Write the tests**

```kotlin
package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorR2UploadDataSourceTest {

    @Test
    fun `upload emits 1_0 success on 200`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val results = KtorR2UploadDataSource(HttpClient(engine))
            .upload("https://r2.example.com/upload", ByteArray(100), "video/mp4")
            .toList()
        val last = results.last()
        assertIs<Result.Success<Float>>(last)
        assertTrue(last.data == 1.0f)
    }

    @Test
    fun `upload emits UploadError_Upload on non-2xx`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Forbidden) }
        val results = KtorR2UploadDataSource(HttpClient(engine))
            .upload("https://r2.example.com/upload", ByteArray(100), "video/mp4")
            .toList()
        val last = results.last()
        assertIs<Result.Error<UploadError>>(last)
        assertIs<UploadError.Upload>(last.error)
    }
}
```

- [ ] **Step 2: Implement `KtorR2UploadDataSource.kt`**

```kotlin
package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class KtorR2UploadDataSource(
    private val httpClient: HttpClient
) : R2UploadDataSource {

    override fun upload(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String
    ): Flow<Result<Float, UploadError>> = callbackFlow {
        try {
            val response = httpClient.put(uploadUrl) {
                contentType(ContentType.parse(mimeType))
                setBody(bytes)
                onUpload { bytesSentTotal, contentLength ->
                    if (contentLength > 0L) {
                        trySendBlocking(
                            Result.Success(bytesSentTotal.toFloat() / contentLength.toFloat())
                        )
                    }
                }
            }
            if (response.status.isSuccess()) {
                trySendBlocking(Result.Success(1.0f))
            } else {
                trySendBlocking(Result.Error(UploadError.Upload))
            }
        } catch (e: Exception) {
            trySendBlocking(Result.Error(UploadError.Upload))
        }
        close()
        awaitClose()
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "*.KtorR2UploadDataSourceTest"`
Expected: PASS (2 tests)

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/KtorR2UploadDataSource.kt shared/src/commonTest/kotlin/br/gohan/videofeed/upload/data/KtorR2UploadDataSourceTest.kt
git commit -m "feat(upload): add KtorR2UploadDataSource with progress flow"
```

---

## Task 4: Koin Module for Upload Data

**Files:**
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/uploadDataModule.kt`

The R2 data source gets a **plain** `HttpClient` (no bearer plugin) identified by Koin qualifier `"plain"`.

- [ ] **Step 1: Create `uploadDataModule.kt`**

```kotlin
package br.gohan.videofeed.upload.data

import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val uploadDataModule = module {
    single(named("plain")) {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single {
        KtorUploadDataSource(
            httpClient = get(),
            baseUrl = getProperty("baseUrl")
        )
    } bind UploadRemoteDataSource::class

    single {
        KtorR2UploadDataSource(httpClient = get(named("plain")))
    } bind R2UploadDataSource::class
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/upload/data/uploadDataModule.kt
git commit -m "feat(upload): add Koin upload data module"
```

---

## Task 5: `UploadViewModel`

**Files:**
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/presenter/UploadContract.kt`
- Create: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/presenter/UploadViewModel.kt`
- Create: `shared/src/commonTest/kotlin/br/gohan/videofeed/upload/presenter/FakeUploadDataSources.kt`
- Create: `shared/src/commonTest/kotlin/br/gohan/videofeed/upload/presenter/UploadViewModelTest.kt`

- [ ] **Step 1: Create `UploadContract.kt`**

```kotlin
package br.gohan.videofeed.upload.presenter

data class UploadState(
    val title: String = "",
    val selectedFilename: String? = null,
    val status: UploadStatus = UploadStatus.Idle
)

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Presigning : UploadStatus
    data class Uploading(val progress: Float) : UploadStatus
    data object Finalizing : UploadStatus
    data object Done : UploadStatus
    data class Error(val message: String) : UploadStatus
}

sealed interface UploadAction {
    data class OnFileSelected(
        val bytes: ByteArray,
        val filename: String,
        val mimeType: String
    ) : UploadAction
    data class OnTitleChange(val title: String) : UploadAction
    data object OnSubmit : UploadAction
}

sealed interface UploadEvent {
    data object NavigateToFeed : UploadEvent
}
```

- [ ] **Step 2: Write fakes**

```kotlin
package br.gohan.videofeed.upload.presenter

import br.gohan.videofeed.core.error.EmptyResult
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.PresignResult
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadError
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeUploadRemoteDataSource(
    private val presignResult: Result<PresignResult, UploadError> = Result.Success(
        PresignResult("https://r2.example.com/upload", "videos/abc.mp4")
    ),
    private val registerResult: EmptyResult<UploadError> = Result.Success(Unit)
) : UploadRemoteDataSource {
    override suspend fun presign(filename: String) = presignResult
    override suspend fun registerVideo(videoKey: String, title: String) = registerResult
}

class FakeR2UploadDataSource(
    private val emissions: List<Result<Float, UploadError>> = listOf(
        Result.Success(0.5f),
        Result.Success(1.0f)
    )
) : R2UploadDataSource {
    override fun upload(uploadUrl: String, bytes: ByteArray, mimeType: String): Flow<Result<Float, UploadError>> =
        flowOf(*emissions.toTypedArray())
}
```

- [ ] **Step 3: Write tests**

```kotlin
package br.gohan.videofeed.upload.presenter

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
    ) = UploadViewModel(remoteDataSource, r2DataSource, dispatcher)

    @Test
    fun `initial state is Idle with empty form`() = runTest(dispatcher) {
        val vm = buildViewModel()
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Idle::class)
        assertThat(vm.state.value.title).isEqualTo("")
        assertThat(vm.state.value.selectedFilename).isEqualTo(null)
    }

    @Test
    fun `OnTitleChange updates title in state`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        assertThat(vm.state.value.title).isEqualTo("My Video")
    }

    @Test
    fun `OnFileSelected stores filename in state`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        assertThat(vm.state.value.selectedFilename).isEqualTo("clip.mp4")
    }

    @Test
    fun `OnSubmit progresses through full upload flow and emits NavigateToFeed`() = runTest(dispatcher) {
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
    fun `OnSubmit sets Error state when presign fails`() = runTest(dispatcher) {
        val vm = buildViewModel(
            remoteDataSource = FakeUploadRemoteDataSource(presignResult = Result.Error(UploadError.Presign))
        )
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        vm.onAction(UploadAction.OnSubmit)
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Error::class)
    }

    @Test
    fun `OnSubmit sets Error state when R2 upload fails`() = runTest(dispatcher) {
        val vm = buildViewModel(
            r2DataSource = FakeR2UploadDataSource(listOf(Result.Error(UploadError.Upload)))
        )
        vm.onAction(UploadAction.OnFileSelected(ByteArray(10), "clip.mp4", "video/mp4"))
        vm.onAction(UploadAction.OnTitleChange("My Video"))
        vm.onAction(UploadAction.OnSubmit)
        assertThat(vm.state.value.status).isInstanceOf(UploadStatus.Error::class)
    }
}
```

- [ ] **Step 4: Implement `UploadViewModel.kt`**

```kotlin
package br.gohan.videofeed.upload.presenter

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UploadViewModel(
    private val remoteDataSource: UploadRemoteDataSource,
    private val r2DataSource: R2UploadDataSource,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    private val _events = Channel<UploadEvent>()
    val events = _events.receiveAsFlow()

    private var selectedBytes: ByteArray? = null
    private var selectedMimeType: String = "video/mp4"

    fun onAction(action: UploadAction) {
        when (action) {
            is UploadAction.OnFileSelected -> {
                selectedBytes = action.bytes
                selectedMimeType = action.mimeType
                _state.update { it.copy(selectedFilename = action.filename) }
            }
            is UploadAction.OnTitleChange -> _state.update { it.copy(title = action.title) }
            is UploadAction.OnSubmit -> upload()
        }
    }

    private fun upload() {
        val bytes = selectedBytes ?: return
        val filename = _state.value.selectedFilename ?: return
        val title = _state.value.title.trim()
        if (title.isBlank()) return

        coroutineScope.launch {
            _state.update { it.copy(status = UploadStatus.Presigning) }
            val presignResult = remoteDataSource.presign(filename)
            if (presignResult is Result.Error) {
                _state.update { it.copy(status = UploadStatus.Error("Failed to get upload URL")) }
                return@launch
            }
            val presign = (presignResult as Result.Success).data

            r2DataSource.upload(presign.uploadUrl, bytes, selectedMimeType).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(status = UploadStatus.Uploading(result.data)) }
                    is Result.Error -> {
                        _state.update { it.copy(status = UploadStatus.Error("Upload failed")) }
                        return@collect
                    }
                }
            }
            if (_state.value.status is UploadStatus.Error) return@launch

            _state.update { it.copy(status = UploadStatus.Finalizing) }
            val registerResult = remoteDataSource.registerVideo(presign.videoKey, title)
            if (registerResult is Result.Error) {
                _state.update { it.copy(status = UploadStatus.Error("Failed to save video")) }
                return@launch
            }

            _state.update { it.copy(status = UploadStatus.Done) }
            _events.send(UploadEvent.NavigateToFeed)
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "*.UploadViewModelTest"`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/upload/presenter/ shared/src/commonTest/kotlin/br/gohan/videofeed/upload/presenter/
git commit -m "feat(upload): add UploadViewModel with three-step upload flow"
```

---

## Task 6: Koin Wiring in AppModules

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/AppModules.kt`

`viewModelOf` is Android-specific so `UploadViewModel` registration lives here, not in shared.

- [ ] **Step 1: Update `AppModules.kt`**

```kotlin
package br.gohan.videofeed.di

import androidx.media3.common.util.UnstableApi
import br.gohan.videofeed.auth.data.DataStoreTokenStorage
import br.gohan.videofeed.auth.data.TokenStorage
import br.gohan.videofeed.auth.data.authDataModule
import br.gohan.videofeed.auth.presenter.LoginViewModel
import br.gohan.videofeed.auth.presenter.RegisterViewModel
import br.gohan.videofeed.core.network.HttpClientFactory
import br.gohan.videofeed.feed.data.feedDataModule
import br.gohan.videofeed.feed.presenter.FeedViewModel
import br.gohan.videofeed.upload.data.uploadDataModule
import br.gohan.videofeed.upload.presenter.UploadViewModel
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val coreAndroidModule = module {
    single<TokenStorage> { DataStoreTokenStorage(androidContext()) }
    single { HttpClientFactory.create(Android.create(), get()) }
}

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
}

val feedPresentationAndroidModule = module {
    viewModelOf(::FeedViewModel)
}

val uploadPresentationAndroidModule = module {
    viewModelOf(::UploadViewModel)
}

@UnstableApi
val appModules = listOf(
    coreAndroidModule,
    authDataModule,
    authPresentationModule,
    feedDataModule,
    feedPresentationAndroidModule,
    uploadDataModule,
    uploadPresentationAndroidModule
)
```

- [ ] **Step 2: Verify the app compiles**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/AppModules.kt
git commit -m "feat(upload): wire upload modules into Koin"
```

---

## Task 7: Android URI Helpers

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/upload/UriHelpers.kt`

- [ ] **Step 1: Create `UriHelpers.kt`**

```kotlin
package br.gohan.videofeed.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun readBytesFromUri(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()

fun getFilenameFromUri(context: Context, uri: Uri): String {
    var name = "video.mp4"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

fun getMimeTypeFromUri(context: Context, uri: Uri): String =
    context.contentResolver.getType(uri) ?: "video/mp4"
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/upload/UriHelpers.kt
git commit -m "feat(upload): add URI helpers for file reading on Android"
```

---

## Task 8: Android Upload Screen

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/upload/UploadScreen.kt`

- [ ] **Step 1: Create `UploadScreen.kt`**

```kotlin
package br.gohan.videofeed.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.gohan.videofeed.auth.ObserveAsEvents
import br.gohan.videofeed.upload.presenter.UploadAction
import br.gohan.videofeed.upload.presenter.UploadEvent
import br.gohan.videofeed.upload.presenter.UploadState
import br.gohan.videofeed.upload.presenter.UploadStatus
import br.gohan.videofeed.upload.presenter.UploadViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun UploadRoot(
    onNavigateToFeed: () -> Unit,
    viewModel: UploadViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UploadEvent.NavigateToFeed -> onNavigateToFeed()
        }
    }

    UploadScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun UploadScreen(
    state: UploadState,
    onAction: (UploadAction) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = readBytesFromUri(context, uri)
            val filename = getFilenameFromUri(context, uri)
            val mimeType = getMimeTypeFromUri(context, uri)
            onAction(UploadAction.OnFileSelected(bytes, filename, mimeType))
        }
    }

    val isLoading = state.status is UploadStatus.Presigning
        || state.status is UploadStatus.Uploading
        || state.status is UploadStatus.Finalizing
    val canSubmit = !isLoading && state.selectedFilename != null && state.title.isNotBlank()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { launcher.launch("video/*") },
                enabled = !isLoading
            ) {
                Text(if (state.selectedFilename == null) "Select Video" else "Change Video")
            }

            if (state.selectedFilename != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = state.selectedFilename, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = { onAction(UploadAction.OnTitleChange(it)) },
                label = { Text("Title") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            when (val status = state.status) {
                is UploadStatus.Uploading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Uploading… ${(status.progress * 100).toInt()}%")
                }
                is UploadStatus.Presigning -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Preparing upload…")
                }
                is UploadStatus.Finalizing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Saving…")
                }
                is UploadStatus.Error -> Text(
                    text = status.message,
                    color = MaterialTheme.colorScheme.error
                )
                is UploadStatus.Done -> Text("Upload complete!")
                else -> Unit
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onAction(UploadAction.OnSubmit) },
                enabled = canSubmit
            ) {
                Text("Upload")
            }
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/upload/UploadScreen.kt
git commit -m "feat(upload): add Android upload screen with file picker and progress"
```

---

## Task 9: Navigation — Upload Graph

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/UploadNavGraph.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AppNavHost.kt`

`UploadRoute` is already declared in `AppRoutes.kt`. `AppNavHost.kt` already calls `navController.navigate(UploadRoute)` from the feed graph and has a `// uploadGraph() added in Phase 4` comment — just uncomment/add the call.

- [ ] **Step 1: Create `UploadNavGraph.kt`**

```kotlin
package br.gohan.videofeed.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import br.gohan.videofeed.upload.UploadRoot

fun NavGraphBuilder.uploadGraph(navController: NavHostController) {
    composable<UploadRoute> {
        UploadRoot(
            onNavigateToFeed = {
                navController.navigate(FeedRoute) {
                    popUpTo(UploadRoute) { inclusive = true }
                }
            }
        )
    }
}
```

- [ ] **Step 2: Update `AppNavHost.kt`**

Replace the `// uploadGraph() added in Phase 4` comment with:

```kotlin
uploadGraph(navController)
```

- [ ] **Step 3: Compile and launch on emulator**

Run: `./gradlew :composeApp:assembleDebug && adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`
Expected: BUILD SUCCESSFUL. App launches, feed screen shows an Upload button, tapping it opens the upload screen, selecting a video + entering a title enables the Upload button.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/UploadNavGraph.kt composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AppNavHost.kt
git commit -m "feat(upload): wire upload screen into navigation graph"
```

---

## Self-Review

**Spec coverage:**
- `POST /videos/presign` → `KtorUploadDataSource.presign()` ✓
- Direct R2 PUT with presigned URL → `KtorR2UploadDataSource.upload()` ✓
- `POST /videos { videoKey, title }` → `KtorUploadDataSource.registerVideo()` ✓
- `UploadState: Idle | Presigning | Uploading(progress) | Finalizing | Done | Error` → `UploadStatus` sealed interface ✓
- `UploadAction: OnFileSelected, OnTitleChange, OnSubmit` ✓
- `UploadEvent: NavigateToFeed` ✓
- Android file picker + progress bar ✓
- JWT required for upload (handled by `HttpClientFactory` bearer plugin from Phase 1) ✓
- Plain `HttpClient` for R2 (no bearer headers on presigned URL) ✓

**Path corrections applied:**
- All `feature/upload/{domain,data,presentation}/src/commonMain/...` → `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/{domain,data,presenter}/`
- Package names: `br.gohan.videofeed.feature.upload.*` → `br.gohan.videofeed.upload.*`
- `presenter` (not `presentation`) to match existing convention
- Core imports: `core.domain.*` → `core.error.*`, `core.network.*`
- `safeCall` wrapper replaced by direct `httpClient.post()` with `when` on Result
- `uploadPresentationModule` moved into `AppModules.kt` (Android-only, `viewModelOf`)
- Gradle tasks: `:feature:upload:*:testDebugUnitTest` → `:shared:testDebugUnitTest`
- `UploadRoute` already exists in `AppRoutes.kt` — no change needed
- `ObserveAsEvents` import: `br.gohan.videofeed.auth.ObserveAsEvents`
- `AppNavHost.kt` already has `UploadRoute` nav call — Task 9 just adds `uploadGraph(navController)`
