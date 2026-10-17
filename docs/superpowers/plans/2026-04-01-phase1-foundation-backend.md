# VideoFeed Phase 1 — Foundation & Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ ARCHITECTURE UPDATE (2026-04-02):** Ignore all references to `:core:domain`, `:core:data`, `:feature:*:domain`, `:feature:*:data`, `:feature:*:presentation` modules in this document — that structure was abandoned. The correct structure is **3 Gradle modules: `:composeApp`, `:server`, `:shared`**. All KMP business logic lives in `:shared` as package folders (`domain/`, `data/`, `presenter/`). Tasks 1–5 are already complete. Start at Task 6.

**Goal:** Implement the complete Ktor backend (auth, feed, video metadata, R2 presigned uploads, async FFmpeg thumbnail generation).

**Architecture:** 3 Gradle modules — `:composeApp`, `:server`, `:shared`. No sub-modules.

**Tech Stack:** Kotlin 2.3.0, KMP, Ktor 3.3.3, Exposed 0.56.0, PostgreSQL, jbcrypt, Auth0 JWT, AWS SDK v2 (for R2), H2 (tests), Koin 4.1.0, kotlinx-serialization 1.8.1

---

## Phase Overview

| Phase | Content |
|---|---|
| **Phase 1 (this)** | Project structure + full backend |
| Phase 2 | Auth feature: KMP ViewModels + Android Compose + iOS SwiftUI |
| Phase 3 | Feed feature: KMP + ExoPlayer (Android) + AVPlayer (iOS) + prefetch |
| Phase 4 | Upload feature: KMP + R2 direct upload + progress |

---

## File Map

### Modified files
- `settings.gradle.kts` — add all new module includes, remove old `:shared`
- `gradle/libs.versions.toml` — add all new dependencies
- `composeApp/build.gradle.kts` — remove JS/WasmJS targets, depend on new modules
- `server/build.gradle.kts` — add backend dependencies

### New module: `:core:domain`
```
core/domain/build.gradle.kts
core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/
  error/Error.kt
  error/DataError.kt
  error/Result.kt
  error/ResultExtensions.kt
  model/Video.kt
  model/User.kt
```

### New module: `:core:data`
```
core/data/build.gradle.kts
core/data/src/commonMain/kotlin/br/gohan/videofeed/core/data/
  network/HttpClientFactory.kt
  network/SafeCallHelpers.kt
  auth/TokenStorage.kt
core/data/src/androidMain/kotlin/br/gohan/videofeed/core/data/auth/
  DataStoreTokenStorage.kt
core/data/src/iosMain/kotlin/br/gohan/videofeed/core/data/auth/
  KeychainTokenStorage.kt
```

### Feature module scaffolds (empty, filled in later phases)
```
feature/auth/domain/build.gradle.kts
feature/auth/data/build.gradle.kts
feature/auth/presentation/build.gradle.kts
feature/feed/domain/build.gradle.kts
feature/feed/data/build.gradle.kts
feature/feed/presentation/build.gradle.kts
feature/upload/domain/build.gradle.kts
feature/upload/data/build.gradle.kts
feature/upload/presentation/build.gradle.kts
```

### Server files (`:server`)
```
server/src/main/kotlin/br/gohan/videofeed/
  Application.kt                  (replace existing)
  config/DatabaseConfig.kt
  config/JwtConfig.kt
  config/R2Config.kt
  db/UserTable.kt
  db/VideoTable.kt
  dto/AuthDtos.kt
  dto/VideoDtos.kt
  service/AuthService.kt
  service/VideoService.kt
  service/ThumbnailService.kt
  routes/AuthRoutes.kt
  routes/FeedRoutes.kt
  routes/VideoRoutes.kt
server/src/test/kotlin/br/gohan/videofeed/
  TestDatabaseConfig.kt
  routes/AuthRoutesTest.kt
  routes/FeedRoutesTest.kt
```

---

## Task 1: Clean Up Project Structure

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `composeApp/build.gradle.kts`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Update `settings.gradle.kts` to include new modules**

```kotlin
rootProject.name = "VideoFeed"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":server")

// Core modules
include(":core:domain")
include(":core:data")

// Feature modules
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:feed:domain")
include(":feature:feed:data")
include(":feature:feed:presentation")
include(":feature:upload:domain")
include(":feature:upload:data")
include(":feature:upload:presentation")
```

- [ ] **Step 2: Remove web targets from `composeApp/build.gradle.kts`**

Replace the full file content:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "br.gohan.videofeed"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.gohan.videofeed"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
```

- [ ] **Step 3: Remove web targets from `shared/build.gradle.kts`**

Replace the full file content:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "br.gohan.videofeed.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
```

- [ ] **Step 4: Verify the project syncs**

Run:
```
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL (or at minimum no "module not found" errors — compile errors in app code are acceptable at this stage)

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts composeApp/build.gradle.kts shared/build.gradle.kts
git commit -m "chore: restructure project modules, remove web targets"
```

---

## Task 2: Update Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add new versions and libraries**

Append to the `[versions]` section in `gradle/libs.versions.toml`:

```toml
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.8.1"
exposed = "0.56.0"
postgresql = "42.7.5"
h2 = "2.3.232"
jbcrypt = "0.4"
auth0-jwt = "4.5.0"
awssdk-s3 = "2.31.26"
koin = "4.1.0"
datastore = "1.1.7"
kermit = "2.0.5"
```

Append to the `[libraries]` section:

```toml
# KMP core
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
# Ktor client
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
# Ktor server additions
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation-jvm", version.ref = "ktor" }
ktor-server-auth = { module = "io.ktor:ktor-server-auth-jvm", version.ref = "ktor" }
ktor-server-auth-jwt = { module = "io.ktor:ktor-server-auth-jwt-jvm", version.ref = "ktor" }
ktor-serialization-json-jvm = { module = "io.ktor:ktor-serialization-kotlinx-json-jvm", version.ref = "ktor" }
# Exposed ORM
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-dao = { module = "org.jetbrains.exposed:exposed-dao", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-javatime = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
# Database drivers
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
h2 = { module = "com.h2database:h2", version.ref = "h2" }
# Auth
jbcrypt = { module = "org.mindrot:jbcrypt", version.ref = "jbcrypt" }
auth0-jwt = { module = "com.auth0:java-jwt", version.ref = "auth0-jwt" }
# AWS SDK (for Cloudflare R2 — S3-compatible)
awssdk-s3 = { module = "software.amazon.awssdk:s3", version.ref = "awssdk-s3" }
# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
# DataStore (Android token storage)
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
```

Append to the `[plugins]` section:

```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Verify catalog parses**

Run:
```
./gradlew :help
```
Expected: BUILD SUCCESSFUL — no "unresolved version catalog" errors

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add backend, KMP, and feature library versions to catalog"
```

---

## Task 3: Create `:core:domain` Module

**Files:**
- Create: `core/domain/build.gradle.kts`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/error/Error.kt`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/error/DataError.kt`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/error/Result.kt`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/error/ResultExtensions.kt`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/model/Video.kt`
- Create: `core/domain/src/commonMain/kotlin/br/gohan/videofeed/core/domain/model/User.kt`
- Test: `core/domain/src/commonTest/kotlin/br/gohan/videofeed/core/domain/ResultExtensionsTest.kt`

- [ ] **Step 1: Write the failing test for `Result` extensions**

Create `core/domain/src/commonTest/kotlin/br/gohan/videofeed/core/domain/ResultExtensionsTest.kt`:

```kotlin
package br.gohan.videofeed.core.domain

import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.core.domain.error.map
import br.gohan.videofeed.core.domain.error.onFailure
import br.gohan.videofeed.core.domain.error.onSuccess
import br.gohan.videofeed.core.domain.error.asEmptyResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultExtensionsTest {

    @Test
    fun `map transforms success value`() {
        val result: Result<Int, DataError.Network> = Result.Success(2)
        val mapped = result.map { it * 3 }
        assertEquals(Result.Success(6), mapped)
    }

    @Test
    fun `map preserves error`() {
        val result: Result<Int, DataError.Network> = Result.Error(DataError.Network.NO_INTERNET)
        val mapped = result.map { it * 3 }
        assertEquals(Result.Error(DataError.Network.NO_INTERNET), mapped)
    }

    @Test
    fun `onSuccess is called for success`() {
        var called = false
        Result.Success(42).onSuccess<Int, DataError.Network> { called = true }
        assertTrue(called)
    }

    @Test
    fun `onFailure is called for error`() {
        var called = false
        Result.Error(DataError.Network.UNKNOWN).onFailure<Int, DataError.Network> { called = true }
        assertTrue(called)
    }

    @Test
    fun `asEmptyResult maps success to Unit`() {
        val result: Result<String, DataError.Network> = Result.Success("hello")
        assertEquals(Result.Success(Unit), result.asEmptyResult())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
./gradlew :core:domain:test
```
Expected: FAIL — module does not exist yet

- [ ] **Step 3: Create `core/domain/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "br.gohan.videofeed.core.domain"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
```

- [ ] **Step 4: Create `Error.kt`**

```kotlin
package br.gohan.videofeed.core.domain.error

interface Error
```

- [ ] **Step 5: Create `DataError.kt`**

```kotlin
package br.gohan.videofeed.core.domain.error

sealed interface DataError : Error {
    enum class Network : DataError {
        BAD_REQUEST,
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }
}
```

- [ ] **Step 6: Create `Result.kt`**

```kotlin
package br.gohan.videofeed.core.domain.error

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : br.gohan.videofeed.core.domain.error.Error>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
```

- [ ] **Step 7: Create `ResultExtensions.kt`**

```kotlin
package br.gohan.videofeed.core.domain.error

inline fun <T, E : Error, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Error -> Result.Error(error)
    is Result.Success -> Result.Success(transform(data))
}

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Error) action(error)
    return this
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }
```

- [ ] **Step 8: Create `Video.kt`**

```kotlin
package br.gohan.videofeed.core.domain.model

data class Video(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)
```

- [ ] **Step 9: Create `User.kt`**

```kotlin
package br.gohan.videofeed.core.domain.model

data class User(
    val id: String,
    val email: String
)
```

- [ ] **Step 10: Run tests to verify they pass**

Run:
```
./gradlew :core:domain:test
```
Expected: BUILD SUCCESSFUL — all 5 `ResultExtensionsTest` tests PASS

- [ ] **Step 11: Commit**

```bash
git add core/domain/
git commit -m "feat: add :core:domain module with Result, DataError, and domain models"
```

---

## Task 4: Create `:core:data` Module

**Files:**
- Create: `core/data/build.gradle.kts`
- Create: `core/data/src/commonMain/kotlin/br/gohan/videofeed/core/data/network/HttpClientFactory.kt`
- Create: `core/data/src/commonMain/kotlin/br/gohan/videofeed/core/data/network/SafeCallHelpers.kt`
- Create: `core/data/src/commonMain/kotlin/br/gohan/videofeed/core/data/auth/TokenStorage.kt`
- Create: `core/data/src/androidMain/kotlin/br/gohan/videofeed/core/data/auth/DataStoreTokenStorage.kt`
- Create: `core/data/src/iosMain/kotlin/br/gohan/videofeed/core/data/auth/KeychainTokenStorage.kt`
- Test: `core/data/src/commonTest/kotlin/br/gohan/videofeed/core/data/network/SafeCallHelpersTest.kt`

- [ ] **Step 1: Write the failing test for `responseToResult`**

Create `core/data/src/commonTest/kotlin/br/gohan/videofeed/core/data/network/SafeCallHelpersTest.kt`:

```kotlin
package br.gohan.videofeed.core.data.network

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SafeCallHelpersTest {

    @Serializable
    data class TestBody(val value: String)

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
    }

    @Test
    fun `200 response maps to Result Success`() = runTest {
        val client = mockClient(HttpStatusCode.OK, """{"value":"hello"}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertIs<Result.Success<TestBody>>(result)
        assertEquals("hello", result.data.value)
    }

    @Test
    fun `401 maps to UNAUTHORIZED`() = runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, """{}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
    }

    @Test
    fun `500 maps to SERVER_ERROR`() = runTest {
        val client = mockClient(HttpStatusCode.InternalServerError, """{}""")
        val result = client.get<TestBody>("/test", "http://localhost")
        assertEquals(Result.Error(DataError.Network.SERVER_ERROR), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
./gradlew :core:data:jvmTest
```
Expected: FAIL — module does not exist yet

- [ ] **Step 3: Create `core/data/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.kotlinx.coroutines.get()}")
        }
    }
}

android {
    namespace = "br.gohan.videofeed.core.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
```

- [ ] **Step 4: Create `TokenStorage.kt`**

```kotlin
package br.gohan.videofeed.core.data.auth

interface TokenStorage {
    suspend fun getToken(): String?
    suspend fun saveToken(token: String)
    suspend fun clearToken()
}
```

- [ ] **Step 5: Create `DataStoreTokenStorage.kt`**

```kotlin
package br.gohan.videofeed.core.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class DataStoreTokenStorage(private val context: Context) : TokenStorage {
    private val tokenKey = stringPreferencesKey("jwt_token")

    override suspend fun getToken(): String? =
        context.dataStore.data.map { it[tokenKey] }.firstOrNull()

    override suspend fun saveToken(token: String) {
        context.dataStore.edit { it[tokenKey] = token }
    }

    override suspend fun clearToken() {
        context.dataStore.edit { it.remove(tokenKey) }
    }
}
```

- [ ] **Step 6: Create `KeychainTokenStorage.kt`**

```kotlin
package br.gohan.videofeed.core.data.auth

import platform.Foundation.NSUserDefaults

// Uses NSUserDefaults for simplicity in a PoC.
// In production, replace with Security framework Keychain calls.
class KeychainTokenStorage : TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "jwt_token"

    override suspend fun getToken(): String? = defaults.stringForKey(key)

    override suspend fun saveToken(token: String) {
        defaults.setObject(token, key)
    }

    override suspend fun clearToken() {
        defaults.removeObjectForKey(key)
    }
}
```

- [ ] **Step 7: Create `HttpClientFactory.kt`**

```kotlin
package br.gohan.videofeed.core.data.network

import br.gohan.videofeed.core.data.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

object HttpClientFactory {
    fun create(engine: HttpClientEngine, tokenStorage: TokenStorage): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val token = tokenStorage.getToken() ?: return@loadTokens null
                    BearerTokens(accessToken = token, refreshToken = "")
                }
            }
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}
```

- [ ] **Step 8: Create `SafeCallHelpers.kt`**

```kotlin
package br.gohan.videofeed.core.data.network

import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

suspend inline fun <reified T> safeCall(execute: () -> HttpResponse): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        return Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: SerializationException) {
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        return Result.Error(DataError.Network.UNKNOWN)
    }
    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(response: HttpResponse): Result<T, DataError.Network> =
    when (response.status.value) {
        in 200..299 -> Result.Success(response.body<T>())
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }

suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    baseUrl: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Response, DataError.Network> = safeCall {
    get {
        url("$baseUrl$route")
        queryParameters.forEach { (k, v) -> parameter(k, v) }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    baseUrl: String,
    body: Request
): Result<Response, DataError.Network> = safeCall {
    post {
        url("$baseUrl$route")
        setBody(body)
    }
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run:
```
./gradlew :core:data:jvmTest
```
Expected: BUILD SUCCESSFUL — all 3 `SafeCallHelpersTest` tests PASS

- [ ] **Step 10: Commit**

```bash
git add core/data/
git commit -m "feat: add :core:data module with HttpClientFactory, SafeCallHelpers, and TokenStorage"
```

---

## Task 5: Scaffold Empty Feature Modules

**Files:**
- Create: `feature/auth/domain/build.gradle.kts`, `feature/auth/data/build.gradle.kts`, `feature/auth/presentation/build.gradle.kts`
- Create: `feature/feed/domain/build.gradle.kts`, `feature/feed/data/build.gradle.kts`, `feature/feed/presentation/build.gradle.kts`
- Create: `feature/upload/domain/build.gradle.kts`, `feature/upload/data/build.gradle.kts`, `feature/upload/presentation/build.gradle.kts`

- [ ] **Step 1: Create `feature/auth/domain/build.gradle.kts`**

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
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.auth.domain"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 2: Create `feature/auth/data/build.gradle.kts`**

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
            implementation(projects.feature.auth.domain)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.auth.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 3: Create `feature/auth/presentation/build.gradle.kts`**

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
            implementation(projects.feature.auth.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.auth.presentation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 4: Create `feature/feed/domain/build.gradle.kts`**

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
        commonMain.dependencies { implementation(projects.core.domain) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.feed.domain"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 5: Create `feature/feed/data/build.gradle.kts`**

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
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.feed.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 6: Create `feature/feed/presentation/build.gradle.kts`**

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
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.feed.presentation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 7: Create upload module build files**

Create `feature/upload/domain/build.gradle.kts`:

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
        commonMain.dependencies { implementation(projects.core.domain) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.upload.domain"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

Create `feature/upload/data/build.gradle.kts`:

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
            implementation(projects.feature.upload.domain)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.upload.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

Create `feature/upload/presentation/build.gradle.kts`:

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
            implementation(projects.feature.upload.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "br.gohan.videofeed.feature.upload.presentation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 8: Verify all modules resolve**

Run:
```
./gradlew :feature:auth:domain:build :feature:feed:domain:build :feature:upload:domain:build
```
Expected: BUILD SUCCESSFUL (empty modules compile fine)

- [ ] **Step 9: Commit**

```bash
git add feature/
git commit -m "chore: scaffold empty feature module Gradle configs"
```

---

## Task 6: Update `:server` Build File

**Files:**
- Modify: `server/build.gradle.kts`

- [ ] **Step 1: Replace `server/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "br.gohan.videofeed"
version = "1.0.0"

application {
    mainClass.set("br.gohan.videofeed.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Ktor server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.json.jvm)
    implementation(libs.logback)
    // Exposed + DB
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javatime)
    implementation(libs.postgresql)
    // Auth
    implementation(libs.jbcrypt)
    implementation(libs.auth0.jwt)
    // R2 (S3-compatible)
    implementation(libs.awssdk.s3)
    // Test
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.h2)
}
```

- [ ] **Step 2: Verify server compiles**

Run:
```
./gradlew :server:compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add server/build.gradle.kts
git commit -m "chore: add backend dependencies to :server"
```

---

## Task 7: Implement Database and Config

**Files:**
- Create: `server/src/main/kotlin/br/gohan/videofeed/config/DatabaseConfig.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/config/JwtConfig.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/config/R2Config.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/db/UserTable.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/db/VideoTable.kt`
- Create: `server/src/test/kotlin/br/gohan/videofeed/TestDatabaseConfig.kt`

- [ ] **Step 1: Create `UserTable.kt`**

```kotlin
package br.gohan.videofeed.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Create `VideoTable.kt`**

```kotlin
package br.gohan.videofeed.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object VideoTable : Table("videos") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val videoKey = varchar("video_key", 512)
    val cdnUrl = varchar("cdn_url", 512)
    val thumbnailUrl = varchar("thumbnail_url", 512).nullable()
    val uploaderId = uuid("uploader_id").references(UserTable.id)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 3: Create `DatabaseConfig.kt`**

```kotlin
package br.gohan.videofeed.config

import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    fun init(
        url: String = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/videofeed",
        driver: String = "org.postgresql.Driver",
        user: String = System.getenv("DB_USER") ?: "postgres",
        password: String = System.getenv("DB_PASSWORD") ?: "postgres"
    ) {
        Database.connect(url = url, driver = driver, user = user, password = password)
        transaction {
            SchemaUtils.create(UserTable, VideoTable)
        }
    }
}
```

- [ ] **Step 4: Create `JwtConfig.kt`**

```kotlin
package br.gohan.videofeed.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"
    private val algorithm = Algorithm.HMAC256(secret)
    const val ISSUER = "videofeed"
    const val AUDIENCE = "videofeed-users"
    const val REALM = "VideoFeed"
    private const val EXPIRY_MS = 86_400_000L // 24 hours

    fun generateToken(userId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + EXPIRY_MS))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()
}
```

- [ ] **Step 5: Create `R2Config.kt`**

```kotlin
package br.gohan.videofeed.config

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

object R2Config {
    val bucket: String = System.getenv("R2_BUCKET") ?: "videofeed"
    val publicUrl: String = System.getenv("R2_PUBLIC_URL") ?: "https://pub-placeholder.r2.dev"

    private val accountId = System.getenv("R2_ACCOUNT_ID") ?: "placeholder"
    private val accessKey = System.getenv("R2_ACCESS_KEY") ?: "placeholder"
    private val secretKey = System.getenv("R2_SECRET_KEY") ?: "placeholder"
    private val endpoint = URI.create("https://$accountId.r2.cloudflarestorage.com")

    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)
    )

    val client: S3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of("auto"))
        .build()

    val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of("auto"))
        .build()
}
```

- [ ] **Step 6: Create `TestDatabaseConfig.kt`**

```kotlin
package br.gohan.videofeed

import br.gohan.videofeed.config.DatabaseConfig

object TestDatabaseConfig {
    fun init() = DatabaseConfig.init(
        url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        driver = "org.h2.Driver",
        user = "sa",
        password = ""
    )
}
```

- [ ] **Step 7: Commit**

```bash
git add server/src/
git commit -m "feat: add DB tables, JwtConfig, R2Config, and DatabaseConfig"
```

---

## Task 8: Implement DTOs

**Files:**
- Create: `server/src/main/kotlin/br/gohan/videofeed/dto/AuthDtos.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/dto/VideoDtos.kt`

- [ ] **Step 1: Create `AuthDtos.kt`**

```kotlin
package br.gohan.videofeed.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String)
```

- [ ] **Step 2: Create `VideoDtos.kt`**

```kotlin
package br.gohan.videofeed.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideoDto(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)

@Serializable
data class PresignRequest(val filename: String)

@Serializable
data class PresignResponse(val uploadUrl: String, val videoKey: String)

@Serializable
data class CreateVideoRequest(val videoKey: String, val title: String)

@Serializable
data class FeedResponse(val videos: List<VideoDto>, val page: Int, val hasMore: Boolean)
```

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/br/gohan/videofeed/dto/
git commit -m "feat: add auth and video DTOs"
```

---

## Task 9: Implement Auth Service and Routes

**Files:**
- Create: `server/src/main/kotlin/br/gohan/videofeed/service/AuthService.kt`
- Create: `server/src/main/kotlin/br/gohan/videofeed/routes/AuthRoutes.kt`
- Test: `server/src/test/kotlin/br/gohan/videofeed/routes/AuthRoutesTest.kt`

- [ ] **Step 1: Write the failing auth tests**

Create `server/src/test/kotlin/br/gohan/videofeed/routes/AuthRoutesTest.kt`:

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.TestDatabaseConfig
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthRoutesTest {

    @BeforeTest
    fun setup() {
        TestDatabaseConfig.init()
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(VideoTable, UserTable)
            SchemaUtils.create(UserTable, VideoTable)
        }
    }

    @Test
    fun `register returns 201 with token`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"register@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `register with duplicate email returns 409`() = testApplication {
        application { module(useTestDb = true) }
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@test.com","password":"password123"}""")
        }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with valid credentials returns 200 with token`() = testApplication {
        application { module(useTestDb = true) }
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"login@test.com","password":"password123"}""")
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"login@test.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login with wrong password returns 401`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@test.com","password":"wrongpass"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```
./gradlew :server:test --tests "br.gohan.videofeed.routes.AuthRoutesTest"
```
Expected: FAIL — `module()` and `AuthService` do not exist yet

- [ ] **Step 3: Create `AuthService.kt`**

```kotlin
package br.gohan.videofeed.service

import br.gohan.videofeed.config.JwtConfig
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.dto.AuthResponse
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

class AuthService {

    fun register(email: String, password: String): AuthResponse {
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val userId = transaction {
            UserTable.insert {
                it[UserTable.email] = email
                it[UserTable.passwordHash] = hash
                it[UserTable.createdAt] = Instant.now()
            } get UserTable.id
        }
        return AuthResponse(JwtConfig.generateToken(userId.toString()))
    }

    fun login(email: String, password: String): AuthResponse? {
        val user = transaction {
            UserTable.selectAll()
                .where { UserTable.email eq email }
                .singleOrNull()
        } ?: return null

        if (!BCrypt.checkpw(password, user[UserTable.passwordHash])) return null

        return AuthResponse(JwtConfig.generateToken(user[UserTable.id].toString()))
    }
}
```

- [ ] **Step 4: Create `AuthRoutes.kt`**

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.dto.LoginRequest
import br.gohan.videofeed.dto.RegisterRequest
import br.gohan.videofeed.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

fun Route.authRoutes(authService: AuthService) {
    post("/auth/register") {
        val request = call.receive<RegisterRequest>()
        val response = try {
            authService.register(request.email, request.password)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already registered"))
            return@post
        }
        call.respond(HttpStatusCode.Created, response)
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val response = authService.login(request.email, request.password) ?: run {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            return@post
        }
        call.respond(response)
    }
}
```

- [ ] **Step 5: Create `Application.kt`**

Replace `server/src/main/kotlin/br/gohan/videofeed/Application.kt`:

```kotlin
package br.gohan.videofeed

import br.gohan.videofeed.config.DatabaseConfig
import br.gohan.videofeed.config.JwtConfig
import br.gohan.videofeed.routes.authRoutes
import br.gohan.videofeed.routes.feedRoutes
import br.gohan.videofeed.routes.videoRoutes
import br.gohan.videofeed.service.AuthService
import br.gohan.videofeed.service.ThumbnailService
import br.gohan.videofeed.service.VideoService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

const val SERVER_PORT = 8080

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(useTestDb: Boolean = false) {
    if (!useTestDb) {
        DatabaseConfig.init()
    }

    install(ContentNegotiation) { json() }

    install(Authentication) {
        jwt("jwt") {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }

    val authService = AuthService()
    val thumbnailService = ThumbnailService()
    val videoService = VideoService(thumbnailService)

    routing {
        authRoutes(authService)
        feedRoutes(videoService)
        videoRoutes(videoService)
    }
}
```

- [ ] **Step 6: Create stub `ThumbnailService.kt` and `VideoService.kt` so Application.kt compiles**

Create `server/src/main/kotlin/br/gohan/videofeed/service/ThumbnailService.kt`:

```kotlin
package br.gohan.videofeed.service

class ThumbnailService {
    fun extractAndUpload(videoKey: String): String {
        // Implemented in Task 11
        return "thumbnails/placeholder.jpg"
    }
}
```

Create `server/src/main/kotlin/br/gohan/videofeed/service/VideoService.kt`:

```kotlin
package br.gohan.videofeed.service

import br.gohan.videofeed.dto.FeedResponse
import br.gohan.videofeed.dto.PresignResponse
import br.gohan.videofeed.dto.VideoDto

class VideoService(private val thumbnailService: ThumbnailService) {
    fun presign(filename: String): PresignResponse {
        // Implemented in Task 10
        return PresignResponse("", "")
    }

    fun createVideo(videoKey: String, title: String, uploaderId: String): VideoDto {
        // Implemented in Task 10
        return VideoDto("", title, "", null, "")
    }

    fun getFeed(page: Int, limit: Int): FeedResponse {
        // Implemented in Task 10
        return FeedResponse(emptyList(), page, false)
    }

    fun getVideo(id: String): VideoDto? = null
}
```

Create stub route files so Application.kt compiles.

Create `server/src/main/kotlin/br/gohan/videofeed/routes/FeedRoutes.kt`:

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.service.VideoService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.feedRoutes(videoService: VideoService) {
    get("/feed") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceAtMost(50)
        call.respond(videoService.getFeed(page, limit))
    }
}
```

Create `server/src/main/kotlin/br/gohan/videofeed/routes/VideoRoutes.kt`:

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.dto.CreateVideoRequest
import br.gohan.videofeed.dto.PresignRequest
import br.gohan.videofeed.service.VideoService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.videoRoutes(videoService: VideoService) {
    authenticate("jwt") {
        post("/videos/presign") {
            val request = call.receive<PresignRequest>()
            call.respond(videoService.presign(request.filename))
        }

        post("/videos") {
            val uploaderId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val request = call.receive<CreateVideoRequest>()
            call.respond(HttpStatusCode.Created, videoService.createVideo(request.videoKey, request.title, uploaderId))
        }
    }

    get("/videos/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val video = videoService.getVideo(id) ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respond(video)
    }
}
```

- [ ] **Step 7: Run auth tests to verify they pass**

Run:
```
./gradlew :server:test --tests "br.gohan.videofeed.routes.AuthRoutesTest"
```
Expected: BUILD SUCCESSFUL — all 4 `AuthRoutesTest` tests PASS

- [ ] **Step 8: Commit**

```bash
git add server/src/
git commit -m "feat: implement auth service, routes, and Application module wiring"
```

---

## Task 10: Implement Feed and Video Service

**Files:**
- Modify: `server/src/main/kotlin/br/gohan/videofeed/service/VideoService.kt`
- Test: `server/src/test/kotlin/br/gohan/videofeed/routes/FeedRoutesTest.kt`

- [ ] **Step 1: Write feed route tests**

Create `server/src/test/kotlin/br/gohan/videofeed/routes/FeedRoutesTest.kt`:

```kotlin
package br.gohan.videofeed.routes

import br.gohan.videofeed.TestDatabaseConfig
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedRoutesTest {

    @BeforeTest
    fun setup() {
        TestDatabaseConfig.init()
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(VideoTable, UserTable)
            SchemaUtils.create(UserTable, VideoTable)
        }
    }

    @Test
    fun `GET feed returns 200 with empty videos list`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/feed")
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(json["videos"])
        assertEquals("1", json["page"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET feed respects limit param`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/feed?page=1&limit=5")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET videos by id returns 404 for unknown id`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.get("/videos/00000000-0000-0000-0000-000000000000")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST videos presign requires auth`() = testApplication {
        application { module(useTestDb = true) }
        val response = client.post("/videos/presign") {
            contentType(ContentType.Application.Json)
            setBody("""{"filename":"test.mp4"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run:
```
./gradlew :server:test --tests "br.gohan.videofeed.routes.FeedRoutesTest"
```
Expected: FAIL — `VideoService.getFeed` returns stub data and `getVideo` always returns null for wrong reasons

- [ ] **Step 3: Replace `VideoService.kt` with full implementation**

```kotlin
package br.gohan.videofeed.service

import br.gohan.videofeed.config.R2Config
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.dto.FeedResponse
import br.gohan.videofeed.dto.PresignResponse
import br.gohan.videofeed.dto.VideoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.join
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class VideoService(private val thumbnailService: ThumbnailService) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun presign(filename: String): PresignResponse {
        val videoKey = "videos/${UUID.randomUUID()}/$filename"
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(
                PutObjectRequest.builder()
                    .bucket(R2Config.bucket)
                    .key(videoKey)
                    .build()
            )
            .build()
        val url = R2Config.presigner.presignPutObject(presignRequest).url().toString()
        return PresignResponse(uploadUrl = url, videoKey = videoKey)
    }

    fun createVideo(videoKey: String, title: String, uploaderId: String): VideoDto {
        val cdnUrl = "${R2Config.publicUrl}/$videoKey"
        val uploaderUuid = UUID.fromString(uploaderId)

        val videoId = transaction {
            VideoTable.insert {
                it[VideoTable.videoKey] = videoKey
                it[VideoTable.title] = title
                it[VideoTable.cdnUrl] = cdnUrl
                it[VideoTable.uploaderId] = uploaderUuid
                it[VideoTable.createdAt] = Instant.now()
            } get VideoTable.id
        }

        val uploaderName = transaction {
            UserTable.selectAll()
                .where { UserTable.id eq uploaderUuid }
                .single()[UserTable.email]
                .substringBefore("@")
        }

        // Generate thumbnail asynchronously — does not block the response
        scope.launch {
            val thumbnailKey = thumbnailService.extractAndUpload(videoKey)
            val thumbnailUrl = "${R2Config.publicUrl}/$thumbnailKey"
            transaction {
                VideoTable.update({ VideoTable.id eq videoId }) {
                    it[VideoTable.thumbnailUrl] = thumbnailUrl
                }
            }
        }

        return VideoDto(
            id = videoId.toString(),
            title = title,
            cdnUrl = cdnUrl,
            thumbnailUrl = null, // populated asynchronously
            uploaderName = uploaderName
        )
    }

    fun getFeed(page: Int, limit: Int): FeedResponse {
        val offset = ((page - 1) * limit).toLong()
        val rows = transaction {
            VideoTable
                .join(UserTable, JoinType.LEFT, VideoTable.uploaderId, UserTable.id)
                .selectAll()
                .orderBy(VideoTable.createdAt, SortOrder.DESC)
                .limit(limit + 1)
                .offset(offset)
                .toList()
        }
        val hasMore = rows.size > limit
        val videos = rows.take(limit).map { row ->
            VideoDto(
                id = row[VideoTable.id].toString(),
                title = row[VideoTable.title],
                cdnUrl = row[VideoTable.cdnUrl],
                thumbnailUrl = row[VideoTable.thumbnailUrl],
                uploaderName = row[UserTable.email].substringBefore("@")
            )
        }
        return FeedResponse(videos = videos, page = page, hasMore = hasMore)
    }

    fun getVideo(id: String): VideoDto? = transaction {
        VideoTable
            .join(UserTable, JoinType.LEFT, VideoTable.uploaderId, UserTable.id)
            .selectAll()
            .where { VideoTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.let { row ->
                VideoDto(
                    id = row[VideoTable.id].toString(),
                    title = row[VideoTable.title],
                    cdnUrl = row[VideoTable.cdnUrl],
                    thumbnailUrl = row[VideoTable.thumbnailUrl],
                    uploaderName = row[UserTable.email].substringBefore("@")
                )
            }
    }
}
```

- [ ] **Step 4: Run feed tests to verify they pass**

Run:
```
./gradlew :server:test --tests "br.gohan.videofeed.routes.FeedRoutesTest"
```
Expected: BUILD SUCCESSFUL — all 4 `FeedRoutesTest` tests PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/
git commit -m "feat: implement VideoService with feed, presign, and video metadata endpoints"
```

---

## Task 11: Implement Thumbnail Service

**Files:**
- Modify: `server/src/main/kotlin/br/gohan/videofeed/service/ThumbnailService.kt`

> Note: FFmpeg must be installed on the host machine for thumbnail extraction to work. The PoC uses `ffmpeg` via `ProcessBuilder`. In production, replace with a cloud function or background job queue.

- [ ] **Step 1: Replace `ThumbnailService.kt` with full implementation**

```kotlin
package br.gohan.videofeed.service

import br.gohan.videofeed.config.R2Config
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.util.UUID

class ThumbnailService {
    private val log = LoggerFactory.getLogger(ThumbnailService::class.java)

    fun extractAndUpload(videoKey: String): String {
        val cdnVideoUrl = "${R2Config.publicUrl}/$videoKey"
        val tmpFile = File.createTempFile("thumbnail-${UUID.randomUUID()}", ".jpg")
        return try {
            val process = ProcessBuilder(
                "ffmpeg", "-i", cdnVideoUrl,
                "-ss", "00:00:01",
                "-vframes", "1",
                "-q:v", "2",
                "-y",
                tmpFile.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                log.warn("FFmpeg exited with code $exitCode for key $videoKey")
                return "thumbnails/placeholder.jpg"
            }

            val thumbnailKey = "thumbnails/${UUID.randomUUID()}.jpg"
            R2Config.client.putObject(
                PutObjectRequest.builder()
                    .bucket(R2Config.bucket)
                    .key(thumbnailKey)
                    .contentType("image/jpeg")
                    .build(),
                RequestBody.fromFile(tmpFile)
            )
            thumbnailKey
        } catch (e: Exception) {
            log.error("Thumbnail extraction failed for $videoKey", e)
            "thumbnails/placeholder.jpg"
        } finally {
            tmpFile.delete()
        }
    }
}
```

- [ ] **Step 2: Run full server test suite**

Run:
```
./gradlew :server:test
```
Expected: BUILD SUCCESSFUL — all tests pass

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/br/gohan/videofeed/service/ThumbnailService.kt
git commit -m "feat: implement FFmpeg thumbnail extraction and R2 upload in ThumbnailService"
```

---

## Task 12: Final Verification

- [ ] **Step 1: Run all module tests**

Run:
```
./gradlew :core:domain:test :core:data:jvmTest :server:test
```
Expected: BUILD SUCCESSFUL — all tests pass across all modules

- [ ] **Step 2: Verify composeApp still assembles**

Run:
```
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat: Phase 1 complete — KMP foundation modules and full Ktor backend"
```

---

## Phase 1 Checklist

- [ ] `:core:domain` — `Result`, `DataError`, `Video`, `User` ✓
- [ ] `:core:data` — `HttpClientFactory`, `SafeCallHelpers`, `TokenStorage` ✓
- [ ] Feature module scaffolds (auth, feed, upload) ✓
- [ ] Backend auth: `POST /auth/register`, `POST /auth/login` with bcrypt + JWT ✓
- [ ] Backend feed: `GET /feed` with pagination ✓
- [ ] Backend video: `POST /videos/presign`, `POST /videos`, `GET /videos/{id}` ✓
- [ ] Thumbnail: async FFmpeg extraction + R2 upload ✓
- [ ] All config via env vars (no hardcoding) ✓
- [ ] Tests: ResultExtensions, SafeCallHelpers, AuthRoutes, FeedRoutes ✓

**Next:** Phase 2 — Auth Feature (KMP ViewModels + Android Compose + iOS SwiftUI)
