# VideoFeed — Design Spec
**Date:** 2026-04-01

> **⚠️ ARCHITECTURE UPDATE (2026-04-02):** The Module Structure section below is outdated. The correct structure uses **3 Gradle modules only** — no `:core:*` or `:feature:*` sub-modules. See the corrected structure below.

## Overview

A proof-of-concept TikTok-style vertical video feed app showcasing senior Kotlin Multiplatform (KMP) development skills. The app runs on Android (Compose) and iOS (SwiftUI), sharing business logic via KMP with SKIE for seamless Swift/coroutine interop. The backend is a Ktor server backed by Cloudflare R2 for video storage.

---

## Goals

- Showcase KMP architecture: shared domain/data/presentation layers consumed by native UIs on both platforms
- Demonstrate SKIE for clean Swift/coroutine interop (StateFlow → AsyncSequence, suspend → async/await)
- Implement a smooth video feed with pre-fetching (Media3 on Android, AVPlayer on iOS)
- Show end-to-end upload flow via Cloudflare R2 presigned URLs
- Keep backend simple but non-hardcoded (env-driven config, PostgreSQL, Exposed ORM)

---

## Architecture

```
┌─────────────────────────────────────────┐
│           KMP shared module             │
│  ┌──────────┐ ┌──────────┐ ┌────────┐  │
│  │  data    │ │  domain  │ │  pres. │  │
│  │ (Ktor    │ │ (models, │ │  (MVI  │  │
│  │  client, │ │  repos)  │ │  VMs + │  │
│  │  R2 API) │ │          │ │  SKIE) │  │
│  └──────────┘ └──────────┘ └────────┘  │
└────────────────────┬────────────────────┘
          ┌──────────┴──────────┐
   ┌──────▼──────┐       ┌──────▼──────┐
   │   Android   │       │     iOS     │
   │  Compose UI │       │  SwiftUI UI │
   │  ExoPlayer  │       │  AVPlayer   │
   │  (Media3)   │       │  + SKIE     │
   └─────────────┘       └─────────────┘
          │                     │
          └──────────┬──────────┘
                ┌────▼────┐
                │  Ktor   │
                │ Backend │
                └────┬────┘
                     │
            ┌────────▼────────┐
            │  Cloudflare R2  │
            │  (video + thumb)│
            └─────────────────┘
```

---

## Module Structure

**3 Gradle modules. One `build.gradle.kts` each. No sub-modules.**

```
:composeApp   — Android Compose UI (Root/Screen composables, koinViewModel())
:server       — Ktor backend (routes, DB, R2 integration)
:shared       — ALL KMP business logic
iOS Xcode project — SwiftUI (consumes KMP ViewModels via SKIE)
```

### `:shared` package layout

```
shared/src/
  commonMain/kotlin/br/gohan/videofeed/
    domain/
      auth/       — AuthRemoteDataSource interface, AuthError
      feed/       — VideoRemoteDataSource interface
      upload/     — UploadRemoteDataSource interface, UploadError
      model/      — Video, User (shared domain models)
      error/      — Result<T,E>, DataError, Error interface
    data/
      auth/       — TokenStorage interface, KtorAuthDataSource
      feed/       — KtorVideoDataSource
      upload/     — KtorUploadDataSource, R2UploadDataSource
      network/    — HttpClientFactory, SafeCallHelpers
    presenter/
      auth/       — LoginViewModel, RegisterViewModel (State/Action/Event)
      feed/       — FeedViewModel (State/Action/Event)
      upload/     — UploadViewModel (State/Action/Event)
  androidMain/    — DataStoreTokenStorage, platform actuals
  iosMain/        — KeychainTokenStorage, platform actuals
```

### Dependency rules

| Layer | May depend on |
|---|---|
| `presenter` | `domain` |
| `data` | `domain` |
| `domain` | nothing else in the project |
| `:composeApp` | `:shared` |

---

## Backend (Ktor + Exposed + PostgreSQL)

### Endpoints

```
POST /auth/register     { email, password }            → JWT
POST /auth/login        { email, password }            → JWT
GET  /feed              ?page=&limit=                  → [VideoDto]
POST /videos/presign    { filename }                   → { uploadUrl, videoKey }
POST /videos            { videoKey, title }               → VideoDto
GET  /videos/{id}                                      → VideoDto
```

### Video upload flow

1. App calls `POST /videos/presign` → backend generates a presigned R2 URL (expires in 15 min)
2. App uploads video **directly to R2** via the presigned URL — video bytes never touch the backend
3. App calls `POST /videos` with `{ videoKey, title }` → backend saves metadata to DB, then asynchronously downloads the video from R2, extracts the first frame via FFmpeg, uploads thumbnail to R2, and updates `thumbnail_url`

### Config (env vars, no hardcoding)

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | DB credentials |
| `JWT_SECRET` | JWT signing key |
| `R2_ACCOUNT_ID` | Cloudflare account |
| `R2_ACCESS_KEY` / `R2_SECRET_KEY` | R2 credentials |
| `R2_BUCKET` | Bucket name |
| `R2_PUBLIC_URL` | CDN base URL for serving videos |

### Auth

- Passwords hashed with bcrypt
- JWT signed with `JWT_SECRET`, validated via Ktor auth middleware
- Feed is public; upload requires a valid JWT

### Database schema

**users:** `id`, `email`, `password_hash`, `created_at`

**videos:** `id`, `title`, `video_key`, `cdn_url`, `thumbnail_url`, `uploader_id`, `created_at`

---

## KMP Shared Layer

### Error handling

Follows the `android-error-handling` skill:

- `Result<T, E>` + `EmptyResult<E>` in `core:domain`
- `DataError.Network` / `DataError.Local` for shared error cases
- Feature-specific errors implement `Error` (e.g. `AuthError`, `UploadError`)
- `safeCall` / `responseToResult` helpers in `core:data`
- Since ViewModels live in KMP shared code, error state is exposed as `String` (dynamic message) rather than Android `R.string.*` resource IDs — platform UI layers handle any additional localization

### Data sources (single-source naming)

- `KtorAuthDataSource` — register/login network calls
- `KtorVideoDataSource` — feed pagination, single video fetch
- `KtorUploadDataSource` — presign + register metadata calls
- `R2UploadDataSource` — PUT bytes to presigned URL, emits `Flow<UploadProgress>`

### Token storage (expect/actual)

- Android: DataStore (`core:data/androidMain`)
- iOS: Keychain (`core:data/iosMain`)
- `HttpClientFactory` installs Ktor `Auth` bearer plugin — reads/writes tokens automatically, handles 401

### ViewModels (MVI)

Each ViewModel exposes `StateFlow<State>` and `Channel<Event>`, processes `Action` via `onAction()`.

**FeedViewModel**
- `FeedState`: `isLoading`, `videos: List<VideoUi>`, `error: String?`
- `FeedAction`: `OnVideoVisible(index)`, `OnRefresh`, `OnUploadClick`, `OnLoginClick`
- `FeedEvent`: `NavigateToUpload`, `NavigateToLogin`
- Pre-fetch trigger: `OnVideoVisible(index)` → pre-fetch `index+1`, `index+2`

**AuthViewModel**
- `LoginState` / `RegisterState`: `email`, `password`, `isLoading`, `error: String?`
- `AuthAction`: `OnEmailChange`, `OnPasswordChange`, `OnSubmit`
- `AuthEvent`: `NavigateToFeed`, `ShowError(UiText)`

**UploadViewModel**
- `UploadState`: `Idle | Presigning | Uploading(progress: Float) | Finalizing | Done | Error(message: String)`
- `UploadAction`: `OnFileSelected(bytes, filename)`, `OnTitleChange`, `OnSubmit`
- `UploadEvent`: `NavigateToFeed`

### DI (Koin)

One module per feature layer, assembled in `:composeApp` (`Application` class). iOS initialises Koin via a shared `initKoin()` function called from the Swift `@main` entry point.

---

## Video Playback & Pre-fetching

### Android (Media3/ExoPlayer)

- `LazyColumn` or `VerticalPager` — one `PlayerView` per visible item
- `SimpleCache` + `CacheDataSource.Factory` for pre-fetching
- `OnVideoVisible(index)` action triggers background cache fill for next 2 items
- Only the currently visible player is playing; others are paused
- Thumbnails loaded with Coil while video buffers

### iOS (AVPlayer + SwiftUI)

- `TabView` with `.tabViewStyle(.page)` for vertical swipe paging
- `AVPlayerViewController` wrapped in `UIViewControllerRepresentable`
- Pre-fetching via `AVAsset.loadValues(forKeys: ["playable", "duration"])` on next 2 items
- `AsyncImage` for thumbnails while video buffers

### Thumbnail generation

- Server-side: FFmpeg extracts the first frame at upload time
- Stored in R2 alongside the video
- CDN URL saved in `videos.thumbnail_url`

---

## Authentication Flow

- Feed is visible without login
- Upload and like actions require auth (redirects to login)
- On successful login/register: JWT saved to `TokenStorage`, navigate to feed
- On 401: `HttpClientFactory` auth plugin clears token and emits `DataError.Network.UNAUTHORIZED` → ViewModel sends `NavigateToLogin` event

---

## Testing Strategy

| Layer | Tool | Scope |
|---|---|---|
| ViewModel (KMP) | JUnit5 + Turbine + AssertK + `UnconfinedTestDispatcher` | State transitions, event emission, fake data sources |
| Data sources | Ktor `MockEngine` | Happy path + each `DataError` case |
| Backend routes | Ktor `testApplication { }` | Auth, feed pagination, presign endpoint |

**Out of scope for PoC:** Compose instrumented tests, iOS XCTest, end-to-end tests.

---

## Key Libraries

| Concern | Library |
|---|---|
| Networking | Ktor Client (KMP) |
| DI | Koin (KMP) |
| Serialization | KotlinX Serialization |
| Token storage | DataStore (Android) / Keychain (iOS) via expect/actual |
| Image loading | Coil (Android) / AsyncImage (iOS) |
| Video (Android) | Media3 / ExoPlayer |
| Video (iOS) | AVPlayer / AVKit |
| Swift interop | SKIE |
| Backend ORM | Exposed + PostgreSQL |
| Backend auth | Ktor JWT + bcrypt (jbcrypt) |
| Thumbnail | FFmpeg (server-side) |
| Logging | Kermit (KMP) |
| Testing | JUnit5, Turbine, AssertK, kotlinx-coroutines-test |
