# VideoFeed Phase 6 — Optimistic Metrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ ARCHITECTURE UPDATE (2026-04-02):** Ignore any `:feature:feed:*` module references. There are **3 Gradle modules only: `:composeApp`, `:server`, `:shared`**. Feed metrics additions go in `:shared` under the existing `domain/feed/`, `data/feed/`, `presenter/feed/` packages.

**Goal:** Add simple optimistic like and view count interactions to the video feed. Tapping Like immediately flips `isLiked` and increments `likeCount` in the MVI state, fires a fire-and-forget request to the backend, and reverts the state if the request fails.

**Architecture:** No new modules. Metrics are additions to the existing `feature:feed` layers. Backend gets two new endpoints: `POST /videos/{id}/like` and `POST /videos/{id}/view`. The KMP `FeedViewModel` handles optimistic state updates. Android and iOS UIs add a like button and view/like count overlays to the existing feed item composable/view.

**Tech Stack:** Same stack as Phase 3 (Ktor Client, KMP, Koin, Media3, AVPlayer). No new dependencies.

**Scope — simple version only:**
- Optimistic UI: immediate state flip, revert on failure
- Fire-and-forget: no retry, no offline queue, no local persistence
- No WebSocket real-time sync across devices
- View count: increment once when video becomes visible (not on every revisit in the same session)

---

## File Map

### Backend (`:server`)
```
server/src/main/kotlin/br/gohan/videofeed/
  routes/MetricsRoutes.kt     — POST /videos/{id}/like, POST /videos/{id}/view
  service/MetricsService.kt   — like/unlike toggle + view count increment (DB)
  db/Tables.kt                — updated: add likes table, view_count to videos
```

### `:feature:feed:domain`
```
feature/feed/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/domain/
  VideoRemoteDataSource.kt    — updated: add likeVideo(), recordView()
  Video.kt (or FeedResult.kt) — updated: Video domain model gains isLiked, likeCount, viewCount
```

### `:feature:feed:data`
```
feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/
  dto/FeedDtos.kt             — updated: VideoDto gains isLiked, likeCount, viewCount
  KtorVideoDataSource.kt      — updated: implement likeVideo() and recordView()
feature/feed/data/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/data/
  KtorVideoDataSourceTest.kt  — updated: add tests for likeVideo and recordView
```

### `:feature:feed:presentation`
```
feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/
  FeedViewModel.kt            — updated: OnLikeClick action, OnVideoVisible triggers view record
  VideoUi.kt                  — updated: VideoUi gains isLiked, likeCount, viewCount fields
feature/feed/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/presentation/
  FeedViewModelTest.kt        — updated: tests for optimistic like and revert
```

### `:composeApp`
```
composeApp/src/androidMain/kotlin/br/gohan/videofeed/
  feed/VideoInfoOverlay.kt    — updated: show likeCount, viewCount, like button
```

### `iosApp`
```
iosApp/iosApp/feed/FeedView.swift   — updated: like button + counts in VideoItemView
```

---

## Task 1: Backend — Database Schema Updates

**Files:**
- Modify: `server/src/main/kotlin/br/gohan/videofeed/db/Tables.kt`

- [ ] **Step 1: Add `viewCount` to `Videos` table and create `Likes` table**

```kotlin
// In Tables.kt, update the Videos table object:
object Videos : LongIdTable("videos") {
    val title = varchar("title", 255)
    val videoKey = varchar("video_key", 255)
    val cdnUrl = varchar("cdn_url", 512)
    val thumbnailUrl = varchar("thumbnail_url", 512).nullable()
    val uploaderId = reference("uploader_id", Users)
    val viewCount = long("view_count").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

// New table for likes (many-to-many: user ↔ video)
object Likes : Table("likes") {
    val userId = reference("user_id", Users)
    val videoId = reference("video_id", Videos)
    override val primaryKey = PrimaryKey(userId, videoId)
}
```

- [ ] **Step 2: Apply schema migration in `configureDatabase()`**

In `Application.kt` (or wherever `SchemaUtils.create` is called), add the new table and column:

```kotlin
SchemaUtils.createMissingTablesAndColumns(Users, Videos, Likes)
```

> `createMissingTablesAndColumns` adds the `view_count` column to existing `videos` rows with value 0 and creates the `likes` table if absent. Safe to run against an existing database.

- [ ] **Step 3: Verify migration compiles**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/br/gohan/videofeed/db/Tables.kt
git commit -m "feat(metrics): add view_count column and likes table"
```

---

## Task 2: Backend — MetricsService + MetricsRoutes

**Files:**
- Create: `server/src/main/kotlin/br/gohan/videofeed/service/MetricsService.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/routes/MetricsRoutes.kt`
- Modify: `server/src/main/kotlin/br/gohan/videofeed/Application.kt` — register `metricsRoutes()`

- [ ] **Step 1: Write the failing backend test**

```kotlin
// server/src/test/kotlin/br/gohan/videofeed/MetricsRoutesTest.kt
class MetricsRoutesTest {

    @Test
    fun `POST like toggles like on and returns 200`() = testApplication {
        application { module(useTestDb = true) }
        val token = loginAndGetToken(client)   // helper shared from AuthRoutesTest
        val videoId = createTestVideo(client, token)

        val response = client.post("/videos/$videoId/like") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST like requires authentication and returns 401 without token`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/videos/1/like")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST view increments view count and returns 200`() = testApplication {
        application { module(useTestDb = true) }
        val token = loginAndGetToken(client)
        val videoId = createTestVideo(client, token)

        val response = client.post("/videos/$videoId/view")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `./gradlew :server:test --tests "*.MetricsRoutesTest"`
Expected: FAIL — routes do not exist yet

- [ ] **Step 3: Create `MetricsService.kt`**

```kotlin
package br.gohan.videofeed.service

import br.gohan.videofeed.db.Likes
import br.gohan.videofeed.db.Videos
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MetricsService {

    fun toggleLike(videoId: Long, userId: Long) {
        transaction {
            val existing = Likes.select {
                (Likes.videoId eq videoId) and (Likes.userId eq userId)
            }.firstOrNull()

            if (existing == null) {
                Likes.insert {
                    it[Likes.videoId] = videoId
                    it[Likes.userId] = userId
                }
            } else {
                Likes.deleteWhere {
                    (Likes.videoId eq videoId) and (Likes.userId eq userId)
                }
            }
        }
    }

    fun recordView(videoId: Long) {
        transaction {
            Videos.update({ Videos.id eq videoId }) {
                with(SqlExpressionBuilder) {
                    it.update(Videos.viewCount, Videos.viewCount + 1)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create `MetricsRoutes.kt`**

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.service.MetricsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.metricsRoutes(metricsService: MetricsService) {
    routing {
        // View count — public (no auth required)
        post("/videos/{id}/view") {
            val videoId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            metricsService.recordView(videoId)
            call.respond(HttpStatusCode.OK)
        }

        // Like — requires auth
        authenticate("jwt") {
            post("/videos/{id}/like") {
                val videoId = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asLong()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                metricsService.toggleLike(videoId, userId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
```

- [ ] **Step 5: Register in `Application.kt`**

```kotlin
// In Application.kt, alongside other route registrations:
val metricsService = MetricsService()
metricsRoutes(metricsService)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "*.MetricsRoutesTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/gohan/videofeed/service/MetricsService.kt server/src/main/kotlin/br/gohan/videofeed/routes/MetricsRoutes.kt server/src/main/kotlin/br/gohan/videofeed/Application.kt server/src/test/kotlin/br/gohan/videofeed/MetricsRoutesTest.kt
git commit -m "feat(metrics): add like toggle and view count backend endpoints"
```

---

## Task 3: Update Feed DTOs and Domain Models

**Files:**
- Modify: `feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/dto/FeedDtos.kt`
- Modify: `feature/feed/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/domain/VideoRemoteDataSource.kt`
- Modify: relevant domain `Video` model (wherever it was defined in Phase 3)

- [ ] **Step 1: Update `VideoDto` in `FeedDtos.kt`**

Add the new fields to the response DTO. Use `@SerialName` if the JSON key differs:

```kotlin
@Serializable
data class VideoDto(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val isLiked: Boolean = false,
    val likeCount: Long = 0,
    val viewCount: Long = 0
)
```

- [ ] **Step 2: Update feed `/feed` endpoint response on the backend**

In `VideoService.kt`, update the `toDto()` mapping to include `isLiked`, `likeCount`, and `viewCount`. The feed endpoint receives an optional `userId` from the JWT (if authenticated):

```kotlin
// In VideoService.kt, update getFeed to accept optional userId:
fun getFeed(page: Int, limit: Int, userId: Long?): List<VideoDto> = transaction {
    Videos.select { Videos.id.isNotNull() }
        .orderBy(Videos.createdAt, SortOrder.DESC)
        .limit(limit).offset(((page - 1) * limit).toLong())
        .map { row ->
            val videoId = row[Videos.id].value
            val likeCount = Likes.select { Likes.videoId eq videoId }.count()
            val isLiked = userId != null && Likes.select {
                (Likes.videoId eq videoId) and (Likes.userId eq userId)
            }.count() > 0
            VideoDto(
                id = videoId.toString(),
                title = row[Videos.title],
                cdnUrl = row[Videos.cdnUrl],
                thumbnailUrl = row[Videos.thumbnailUrl],
                uploaderName = "user",  // join with Users table if needed
                isLiked = isLiked,
                likeCount = likeCount,
                viewCount = row[Videos.viewCount]
            )
        }
}
```

Update `FeedRoutes.kt` to pass `userId` (null for unauthenticated requests):
```kotlin
get("/feed") {
    val userId = call.principal<JWTPrincipal>()
        ?.payload?.getClaim("userId")?.asLong()
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
    call.respond(videoService.getFeed(page, limit, userId))
}
```

Wrap `/feed` in an optional-auth block so it works both with and without a token:
```kotlin
// In FeedRoutes.kt — replace the existing route registration:
authenticate("jwt", optional = true) {
    get("/feed") { ... }
}
```

- [ ] **Step 3: Update `VideoRemoteDataSource` interface**

```kotlin
interface VideoRemoteDataSource {
    suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network>
    suspend fun likeVideo(videoId: String): EmptyResult<DataError.Network>
    suspend fun recordView(videoId: String): EmptyResult<DataError.Network>
}
```

- [ ] **Step 4: Update `VideoUi` model in the presentation module**

```kotlin
data class VideoUi(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val isLiked: Boolean,
    val likeCount: Long,
    val viewCount: Long
)
```

Update `Video.toVideoUi()` mapper to populate the new fields.

- [ ] **Step 5: Compile check**

Run: `./gradlew :feature:feed:domain:compileCommonMainKotlinMetadata :feature:feed:data:compileCommonMainKotlinMetadata :feature:feed:presentation:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL (compilation errors will surface any missed usages)

- [ ] **Step 6: Commit**

```bash
git add feature/feed/ server/src/main/kotlin/
git commit -m "feat(metrics): add isLiked, likeCount, viewCount to feed models and backend response"
```

---

## Task 4: `KtorVideoDataSource` — implement `likeVideo` and `recordView`

**Files:**
- Modify: `feature/feed/data/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/data/KtorVideoDataSource.kt`
- Modify: `feature/feed/data/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/data/KtorVideoDataSourceTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `KtorVideoDataSourceTest.kt`:

```kotlin
@Test
fun `likeVideo returns success on 200`() = runTest {
    val engine = MockEngine { _ ->
        respond(content = "", status = HttpStatusCode.OK)
    }
    val result = buildDataSource(engine).likeVideo("42")
    assertIs<Result.Success<*>>(result)
}

@Test
fun `likeVideo returns DataError_Network_UNAUTHORIZED on 401`() = runTest {
    val engine = MockEngine { _ ->
        respond(content = "", status = HttpStatusCode.Unauthorized)
    }
    val result = buildDataSource(engine).likeVideo("42")
    assertIs<Result.Error<*>>(result)
    assertEquals(DataError.Network.UNAUTHORIZED, result.error)
}

@Test
fun `recordView returns success on 200`() = runTest {
    val engine = MockEngine { _ ->
        respond(content = "", status = HttpStatusCode.OK)
    }
    val result = buildDataSource(engine).recordView("42")
    assertIs<Result.Success<*>>(result)
}
```

- [ ] **Step 2: Run the failing tests**

Run: `./gradlew :feature:feed:data:testDebugUnitTest --tests "*.KtorVideoDataSourceTest"`
Expected: FAIL — `likeVideo` and `recordView` not implemented

- [ ] **Step 3: Implement in `KtorVideoDataSource.kt`**

Add to the class:

```kotlin
override suspend fun likeVideo(videoId: String): EmptyResult<DataError.Network> {
    return safeCall {
        httpClient.post("$baseUrl/videos/$videoId/like")
    }.map { }
}

override suspend fun recordView(videoId: String): EmptyResult<DataError.Network> {
    return safeCall {
        httpClient.post("$baseUrl/videos/$videoId/view")
    }.map { }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:feed:data:testDebugUnitTest --tests "*.KtorVideoDataSourceTest"`
Expected: PASS (all tests including the 3 new ones)

- [ ] **Step 5: Commit**

```bash
git add feature/feed/data/src/
git commit -m "feat(metrics): implement likeVideo and recordView in KtorVideoDataSource"
```

---

## Task 5: `FeedViewModel` — Optimistic Like + View Recording

**Files:**
- Modify: `feature/feed/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/feed/presentation/FeedViewModel.kt`
- Modify: `feature/feed/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/feed/presentation/FeedViewModelTest.kt`

- [ ] **Step 1: Add `OnLikeClick` to `FeedAction`**

In `FeedViewModel.kt`, update the `FeedAction` sealed interface:

```kotlin
sealed interface FeedAction {
    data class OnVideoVisible(val index: Int) : FeedAction
    data object OnRefresh : FeedAction
    data object OnUploadClick : FeedAction
    data object OnLoginClick : FeedAction
    data class OnLikeClick(val videoId: String) : FeedAction  // NEW
}
```

- [ ] **Step 2: Write the failing tests**

Add to `FeedViewModelTest.kt`:

```kotlin
@Test
fun `OnLikeClick optimistically flips isLiked and increments likeCount`() = runTest(dispatcher) {
    val vm = buildViewModel()
    // Load initial videos first
    vm.onAction(FeedAction.OnRefresh)
    val initialVideo = vm.state.value.videos.first()
    assertThat(initialVideo.isLiked).isFalse()
    assertThat(initialVideo.likeCount).isEqualTo(0L)

    vm.onAction(FeedAction.OnLikeClick(initialVideo.id))

    val updatedVideo = vm.state.value.videos.first { it.id == initialVideo.id }
    assertThat(updatedVideo.isLiked).isTrue()
    assertThat(updatedVideo.likeCount).isEqualTo(1L)
}

@Test
fun `OnLikeClick reverts state when backend call fails`() = runTest(dispatcher) {
    val vm = buildViewModel(
        videoDataSource = FakeVideoDataSource(likeResult = Result.Error(DataError.Network.SERVER_ERROR))
    )
    vm.onAction(FeedAction.OnRefresh)
    val initialVideo = vm.state.value.videos.first()

    vm.onAction(FeedAction.OnLikeClick(initialVideo.id))

    val revertedVideo = vm.state.value.videos.first { it.id == initialVideo.id }
    assertThat(revertedVideo.isLiked).isFalse()
    assertThat(revertedVideo.likeCount).isEqualTo(0L)
}

@Test
fun `OnVideoVisible records a view once per video`() = runTest(dispatcher) {
    val fakeDataSource = FakeVideoDataSource()
    val vm = buildViewModel(videoDataSource = fakeDataSource)
    vm.onAction(FeedAction.OnRefresh)
    val videoId = vm.state.value.videos.first().id

    vm.onAction(FeedAction.OnVideoVisible(0))
    vm.onAction(FeedAction.OnVideoVisible(0)) // second call — should not record again

    assertThat(fakeDataSource.recordViewCallCount).isEqualTo(1)
}
```

Update `FakeVideoDataSource` in the test file to support `likeResult` parameter and track `recordViewCallCount`:

```kotlin
private class FakeVideoDataSource(
    private val likeResult: EmptyResult<DataError.Network> = Result.Success(Unit)
) : VideoRemoteDataSource {
    var recordViewCallCount = 0

    override suspend fun getFeed(page: Int, limit: Int): Result<FeedResult, DataError.Network> =
        Result.Success(FeedResult(
            videos = listOf(
                Video(id = "1", title = "Test", cdnUrl = "https://cdn/v.mp4",
                    thumbnailUrl = null, uploaderName = "user",
                    isLiked = false, likeCount = 0L, viewCount = 0L)
            ),
            page = page,
            hasMore = false
        ))

    override suspend fun likeVideo(videoId: String): EmptyResult<DataError.Network> = likeResult

    override suspend fun recordView(videoId: String): EmptyResult<DataError.Network> {
        recordViewCallCount++
        return Result.Success(Unit)
    }
}
```

- [ ] **Step 3: Run the failing tests**

Run: `./gradlew :feature:feed:presentation:testDebugUnitTest --tests "*.FeedViewModelTest"`
Expected: FAIL — `OnLikeClick` not handled, `recordView` not called

- [ ] **Step 4: Implement optimistic like in `FeedViewModel.kt`**

Add a `viewedVideoIds` set to track which videos have had their view recorded this session. Handle `OnLikeClick` and update `OnVideoVisible` to record views:

```kotlin
class FeedViewModel(
    private val videoDataSource: VideoRemoteDataSource,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _events = Channel<FeedEvent>()
    val events = _events.receiveAsFlow()

    private val viewedVideoIds = mutableSetOf<String>()

    fun onAction(action: FeedAction) {
        when (action) {
            is FeedAction.OnVideoVisible -> {
                triggerPaginationIfNeeded(action.index)
                recordViewIfNew(action.index)
            }
            FeedAction.OnRefresh -> loadFeed(page = 1)
            FeedAction.OnUploadClick -> coroutineScope.launch { _events.send(FeedEvent.NavigateToUpload) }
            FeedAction.OnLoginClick -> coroutineScope.launch { _events.send(FeedEvent.NavigateToLogin) }
            is FeedAction.OnLikeClick -> toggleLikeOptimistic(action.videoId)
        }
    }

    private fun toggleLikeOptimistic(videoId: String) {
        val currentVideos = _state.value.videos
        val target = currentVideos.find { it.id == videoId } ?: return

        // Optimistic update
        val optimisticVideos = currentVideos.map { video ->
            if (video.id == videoId) {
                video.copy(
                    isLiked = !video.isLiked,
                    likeCount = if (video.isLiked) video.likeCount - 1 else video.likeCount + 1
                )
            } else video
        }
        _state.update { it.copy(videos = optimisticVideos) }

        // Fire-and-forget — revert on failure
        coroutineScope.launch {
            val result = videoDataSource.likeVideo(videoId)
            if (result is Result.Error) {
                // Revert: restore the original video state
                _state.update { state ->
                    state.copy(
                        videos = state.videos.map { video ->
                            if (video.id == videoId) target else video
                        }
                    )
                }
            }
        }
    }

    private fun recordViewIfNew(index: Int) {
        val videos = _state.value.videos
        if (index >= videos.size) return
        val videoId = videos[index].id
        if (viewedVideoIds.contains(videoId)) return
        viewedVideoIds.add(videoId)
        coroutineScope.launch {
            videoDataSource.recordView(videoId) // result ignored — fire-and-forget
        }
    }

    // ... existing loadFeed and triggerPaginationIfNeeded unchanged
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :feature:feed:presentation:testDebugUnitTest --tests "*.FeedViewModelTest"`
Expected: PASS (all tests)

- [ ] **Step 6: Commit**

```bash
git add feature/feed/presentation/src/
git commit -m "feat(metrics): add optimistic like toggle and view recording to FeedViewModel"
```

---

## Task 6: Android — Like Button + Counts in Feed UI

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoInfoOverlay.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/FeedScreen.kt`

- [ ] **Step 1: Update `VideoInfoOverlay.kt` to show like button and counts**

```kotlin
@Composable
fun VideoInfoOverlay(
    video: VideoUi,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // Left: title + uploader
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = video.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Right: like button + counts
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onLikeClick) {
                    Icon(
                        imageVector = if (video.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (video.isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = video.likeCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = "Views",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = video.viewCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
```

- [ ] **Step 2: Update `FeedScreen.kt` to pass `onLikeClick` to overlay**

In the `VideoPage` composable (or wherever `VideoInfoOverlay` is called), wire in the action:

```kotlin
VideoInfoOverlay(
    video = video,
    onLikeClick = { onAction(FeedAction.OnLikeClick(video.id)) },
    modifier = Modifier.align(Alignment.BottomCenter)
)
```

- [ ] **Step 3: Compile and run on emulator**

Run: `./gradlew :composeApp:assembleDebug && adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`
Expected: BUILD SUCCESSFUL. Feed shows like button and counts. Tapping Like immediately flips the icon to red and increments the count. If the backend is running and the request fails (e.g. not logged in), the icon reverts.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/VideoInfoOverlay.kt composeApp/src/androidMain/kotlin/br/gohan/videofeed/feed/FeedScreen.kt
git commit -m "feat(metrics): add like button and view/like counts to Android feed screen"
```

---

## Task 7: iOS — Like Button + Counts in FeedView

**Files:**
- Modify: `iosApp/iosApp/feed/FeedView.swift`

- [ ] **Step 1: Update `VideoItemView` in `FeedView.swift`**

Add `onLikeClick` callback and render the like button + counts in the right-side column:

```swift
struct VideoItemView: View {
    let video: VideoUi
    let player: AVPlayer
    let isVisible: Bool
    let size: CGSize
    let onLikeClick: () -> Void  // NEW

    var body: some View {
        ZStack(alignment: .bottom) {
            VideoPlayerView(player: player)
                .frame(width: size.width, height: size.height)
                .onAppear { if isVisible { player.play() } }
                .onDisappear { player.pause() }
                .onChange(of: isVisible) { _, visible in
                    if visible { player.play() } else { player.pause() }
                }

            HStack(alignment: .bottom) {
                // Left: title + uploader
                VStack(alignment: .leading, spacing: 4) {
                    Text(video.title)
                        .font(.headline)
                        .foregroundColor(.white)
                    Text(video.uploaderName)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right: like + view counts
                VStack(spacing: 20) {
                    VStack(spacing: 4) {
                        Button(action: onLikeClick) {
                            Image(systemName: video.isLiked ? "heart.fill" : "heart")
                                .font(.title)
                                .foregroundColor(video.isLiked ? .red : .white)
                        }
                        Text("\(video.likeCount)")
                            .foregroundColor(.white)
                            .font(.caption)
                    }

                    VStack(spacing: 4) {
                        Image(systemName: "eye")
                            .font(.title2)
                            .foregroundColor(.white)
                        Text("\(video.viewCount)")
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                }
            }
            .padding()
            .padding(.bottom, 48)
        }
    }
}
```

- [ ] **Step 2: Update `FeedView` to pass `onLikeClick` to `VideoItemView`**

In the `ForEach` loop inside `FeedView.body`, update the `VideoItemView` call:

```swift
VideoItemView(
    video: video,
    player: player(for: video),
    isVisible: index == currentIndex,
    size: geo.size,
    onLikeClick: {
        vm.onAction(action: FeedAction.OnLikeClick(videoId: video.id))
    }
)
```

- [ ] **Step 3: Build in Xcode and run on simulator**

Expected: Feed shows a heart icon and view count for each video. Tapping the heart immediately turns red and increments the count. If the backend request fails, the icon reverts.

- [ ] **Step 4: Commit**

```bash
git add iosApp/iosApp/feed/FeedView.swift
git commit -m "feat(metrics): add like button and view/like counts to iOS feed screen"
```

---

## Self-Review

**Spec coverage:**
- `OnLikeClick(videoId)` action → immediately flip `isLiked` + increment/decrement `likeCount` ✓
- Fire-and-forget request to backend ✓
- Revert state on failure ✓
- No offline queuing ✓
- No WebSocket real-time sync ✓
- No local persistence of liked state ✓
- View count incremented once per video per session ✓
- Backend `POST /videos/{id}/like` (toggle) + `POST /videos/{id}/view` ✓
- Android like button + counts UI ✓
- iOS like button + counts UI ✓

**Placeholder scan:** No TBDs. All code blocks are complete.

**Type consistency:**
- `FeedAction.OnLikeClick(videoId: String)` defined in `FeedViewModel.kt` and used in `FeedScreen.kt` and `FeedView.swift` ✓
- `VideoUi.isLiked`, `VideoUi.likeCount`, `VideoUi.viewCount` added in Task 3 and consumed in Tasks 6 + 7 ✓
- `VideoRemoteDataSource.likeVideo()` and `recordView()` defined in Task 3, implemented in Task 4, called in Task 5 ✓
- `FakeVideoDataSource` in tests updated with `likeResult` parameter and `recordViewCallCount` to match the tests ✓
