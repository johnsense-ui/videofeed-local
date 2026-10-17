# VideoFeed

A TikTok-style short video feed app built as a KMP portfolio project to showcase senior-level Android and Kotlin Multiplatform skills.

## Features

- **Auth** — JWT-based login and registration
- **Feed** — vertical swipe video feed with ExoPlayer, prefetch, and thumbnail loading
- **Upload** — direct-to-R2 video upload with presigned URLs and real-time progress
- **Backend** — Ktor REST API with PostgreSQL, Cloudflare R2 storage, and FFmpeg thumbnail generation

## Tech Stack

| Layer | Technology |
|---|---|
| Android UI | Compose + Material 3 |
| Video Playback | Media3 / ExoPlayer |
| KMP Shared | MVI ViewModels (Kotlin coroutines + StateFlow) |
| iOS UI | SwiftUI + `@Observable` |
| Swift Interop | Kotlin/Native ObjC interop (Swift Export ready) |
| DI | Koin |
| Networking | Ktor HttpClient |
| Token Storage | DataStore (Android) |
| Backend | Ktor + Exposed + PostgreSQL |
| Object Storage | Cloudflare R2 (presigned URLs) |
| Auth | JWT + bcrypt |
| Thumbnails | FFmpeg (server-side, async) |
| Error Handling | `Result<T, E>` + `DataError` |

## Module Structure

```
VideoFeed/
├── composeApp/   # Android app — Compose UI, navigation, Koin Android
├── iosApp/       # iOS app — SwiftUI views consuming shared ViewModels
├── shared/       # KMP shared — domain, data, presenter (as packages)
└── server/       # Ktor backend
```

Layers (`domain`, `data`, `presenter`) are **packages inside `:shared`**, not separate Gradle modules.

## Architecture

Each feature follows a strict MVI pattern:

```
shared/src/commonMain/.../
└── <feature>/
    ├── domain/       # interfaces + error types
    ├── data/         # Ktor data sources + DTOs
    └── presenter/    # ViewModel + Contract (Action/Event) + UI models
```

Android-specific UI lives in `composeApp/src/androidMain/`.

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Xcode 15+ (for iOS)
- JDK 17+
- [ngrok](https://ngrok.com) (for device testing over the internet)

### 1. Start the backend

Run the `:server` configuration from Android Studio, or:

```shell
./gradlew :server:run
```

The server binds to `0.0.0.0:8081`.

### 2. Expose it via ngrok (for physical devices)

```shell
ngrok http 8081
```

Copy the HTTPS URL (e.g. `https://abc123.ngrok-free.app`) and update:

- **Android**: `composeApp/build.gradle.kts` → `buildConfigField("String", "BASE_URL", "\"https://abc123.ngrok-free.app\"")`
- **iOS**: `iosApp/iosApp/iosApp.swift` → `IOSKoinHelperKt.doInitKoin(baseUrl: "https://abc123.ngrok-free.app")`

> For emulator-only Android development use `http://10.0.2.2:8081` instead.

### 3. Run Android

Select the `composeApp` run configuration in Android Studio and run on device or emulator.

### 4. Run iOS

Open `iosApp/iosApp.xcodeproj` in Xcode, select your simulator or device, and press **⌘R**.

The Xcode build phase runs `embedAndSignAppleFrameworkForXcode` which builds the shared KMP framework automatically.
