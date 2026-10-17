# VideoFeed Phase 2 — Auth Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ ARCHITECTURE UPDATE (2026-04-02):** Ignore all `:feature:auth:domain`, `:feature:auth:data`, `:feature:auth:presentation` module references. There are **3 Gradle modules only: `:composeApp`, `:server`, `:shared`**. Auth code lives in `:shared` as package folders: `domain/auth/`, `data/auth/`, `presenter/auth/`. File paths like `feature/auth/domain/src/.../Foo.kt` map to `shared/src/commonMain/kotlin/br/gohan/videofeed/domain/auth/Foo.kt` (and so on for data/presenter).

**Goal:** Implement the full auth feature — KMP data sources, MVI ViewModels, and Android Compose login/register screens wired with Koin and type-safe navigation.

**Architecture:** All auth KMP code lives in `:shared` under `domain/auth/`, `data/auth/`, `presenter/auth/`. Android composables live in `:composeApp` and consume ViewModels via `koinViewModel()`.

**Tech Stack:** KMP, Ktor Client, Koin 4.1.0, Compose Navigation 2.8.x, Turbine, AssertK, kotlin.test, androidx lifecycle-viewmodel (KMP)

---

## File Map

### Version catalog additions
- `gradle/libs.versions.toml` — Turbine, AssertK, Navigation, lifecycle-viewmodel KMP

### `:feature:auth:domain`
```
feature/auth/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/domain/
  AuthRemoteDataSource.kt
  AuthError.kt
```

### `:feature:auth:data`
```
feature/auth/data/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/data/
  dto/AuthDtos.kt
  KtorAuthDataSource.kt
  authDataModule.kt
feature/auth/data/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/data/
  KtorAuthDataSourceTest.kt
```

### `:feature:auth:presentation`
```
feature/auth/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/presentation/
  login/LoginViewModel.kt        (State, Action, Event + ViewModel)
  register/RegisterViewModel.kt  (State, Action, Event + ViewModel)
  authPresentationModule.kt
feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/
  login/LoginViewModelTest.kt
  register/RegisterViewModelTest.kt
```

### `:composeApp`
```
composeApp/src/androidMain/kotlin/br/gohan/videofeed/
  VideoFeedApplication.kt
  di/AppModules.kt
  navigation/AppNavHost.kt
  navigation/AuthNavGraph.kt
  auth/LoginScreen.kt
  auth/RegisterScreen.kt
  MainActivity.kt              (updated)
```

---

## Task 1: Add Phase 2 Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/auth/presentation/build.gradle.kts`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to `gradle/libs.versions.toml`**

Append to `[versions]`:
```toml
turbine = "1.2.0"
assertk = "0.28.1"
navigation = "2.8.9"
```

Append to `[libraries]`:
```toml
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
assertk = { module = "com.willowtreeapps.assertk:assertk", version.ref = "assertk" }
androidx-lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "androidx-lifecycle" }
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

- [ ] **Step 2: Update `feature/auth/presentation/build.gradle.kts`**

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
            implementation(projects.feature.auth.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
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
    namespace = "br.gohan.videofeed.feature.auth.presentation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
```

- [ ] **Step 3: Update `composeApp/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.navigation.compose)
            // Feature modules
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.feature.auth.domain)
            implementation(projects.feature.auth.data)
            implementation(projects.feature.auth.presentation)
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
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
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

- [ ] **Step 4: Verify sync**

Run:
```
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml feature/auth/presentation/build.gradle.kts composeApp/build.gradle.kts
git commit -m "chore: add Phase 2 dependencies — Turbine, AssertK, Navigation, lifecycle-viewmodel"
```

---

## Task 2: Implement `:feature:auth:domain`

**Files:**
- Create: `feature/auth/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/domain/AuthRemoteDataSource.kt`
- Create: `feature/auth/domain/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/domain/AuthError.kt`

- [ ] **Step 1: Create `AuthError.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.domain

import br.gohan.videofeed.core.domain.error.Error

enum class AuthError : Error {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_EXISTS,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT
}
```

- [ ] **Step 2: Create `AuthRemoteDataSource.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.domain

import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result

interface AuthRemoteDataSource {
    suspend fun login(email: String, password: String): Result<String, DataError.Network>
    suspend fun register(email: String, password: String): Result<String, DataError.Network>
}
```

- [ ] **Step 3: Commit**

```bash
git add feature/auth/domain/src/
git commit -m "feat: add AuthRemoteDataSource interface and AuthError"
```

---

## Task 3: Implement `:feature:auth:data`

**Files:**
- Create: `feature/auth/data/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/data/dto/AuthDtos.kt`
- Create: `feature/auth/data/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/data/KtorAuthDataSource.kt`
- Create: `feature/auth/data/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/data/authDataModule.kt`
- Test: `feature/auth/data/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/data/KtorAuthDataSourceTest.kt`

- [ ] **Step 1: Write failing tests for `KtorAuthDataSource`**

Create `feature/auth/data/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/data/KtorAuthDataSourceTest.kt`:

```kotlin
package br.gohan.videofeed.feature.auth.data

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
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorAuthDataSourceTest {

    private val fakeTokenStorage = FakeTokenStorage()

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `login returns token on 200 and saves it`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.OK, """{"token":"jwt-token-123"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.login("user@test.com", "password123")
        assertIs<Result.Success<String>>(result)
        assertEquals("jwt-token-123", result.data)
        assertEquals("jwt-token-123", fakeTokenStorage.getToken())
    }

    @Test
    fun `login returns UNAUTHORIZED on 401`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Unauthorized, """{"error":"Invalid credentials"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.login("user@test.com", "wrong")
        assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
    }

    @Test
    fun `register returns token on 201 and saves it`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Created, """{"token":"new-jwt-token"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.register("new@test.com", "password123")
        assertIs<Result.Success<String>>(result)
        assertEquals("new-jwt-token", result.data)
        assertEquals("new-jwt-token", fakeTokenStorage.getToken())
    }

    @Test
    fun `register returns CONFLICT on 409`() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.Conflict, """{"error":"Email already registered"}""")
        val dataSource = KtorAuthDataSource(client, "http://localhost", fakeTokenStorage)
        val result = dataSource.register("existing@test.com", "password123")
        assertEquals(Result.Error(DataError.Network.CONFLICT), result)
    }
}

class FakeTokenStorage : br.gohan.videofeed.core.data.auth.TokenStorage {
    private var token: String? = null
    override suspend fun getToken() = token
    override suspend fun saveToken(token: String) { this.token = token }
    override suspend fun clearToken() { token = null }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```
./gradlew :feature:auth:data:jvmTest
```
Expected: FAIL — `KtorAuthDataSource` does not exist yet

- [ ] **Step 3: Create `AuthDtos.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequestDto(val email: String, val password: String)

@Serializable
internal data class RegisterRequestDto(val email: String, val password: String)

@Serializable
internal data class AuthResponseDto(val token: String)
```

- [ ] **Step 4: Create `KtorAuthDataSource.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.data

import br.gohan.videofeed.core.data.auth.TokenStorage
import br.gohan.videofeed.core.data.network.post
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.core.domain.error.map
import br.gohan.videofeed.feature.auth.data.dto.AuthResponseDto
import br.gohan.videofeed.feature.auth.data.dto.LoginRequestDto
import br.gohan.videofeed.feature.auth.data.dto.RegisterRequestDto
import br.gohan.videofeed.feature.auth.domain.AuthRemoteDataSource
import io.ktor.client.HttpClient

class KtorAuthDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenStorage: TokenStorage
) : AuthRemoteDataSource {

    override suspend fun login(email: String, password: String): Result<String, DataError.Network> {
        val result = httpClient.post<LoginRequestDto, AuthResponseDto>(
            route = "/auth/login",
            baseUrl = baseUrl,
            body = LoginRequestDto(email, password)
        ).map { it.token }
        if (result is Result.Success) tokenStorage.saveToken(result.data)
        return result
    }

    override suspend fun register(email: String, password: String): Result<String, DataError.Network> {
        val result = httpClient.post<RegisterRequestDto, AuthResponseDto>(
            route = "/auth/register",
            baseUrl = baseUrl,
            body = RegisterRequestDto(email, password)
        ).map { it.token }
        if (result is Result.Success) tokenStorage.saveToken(result.data)
        return result
    }
}
```

- [ ] **Step 5: Create `authDataModule.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.data

import br.gohan.videofeed.feature.auth.domain.AuthRemoteDataSource
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    single { KtorAuthDataSource(get(), getProperty("baseUrl"), get()) } bind AuthRemoteDataSource::class
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:
```
./gradlew :feature:auth:data:jvmTest
```
Expected: BUILD SUCCESSFUL — all 4 `KtorAuthDataSourceTest` tests PASS

- [ ] **Step 7: Commit**

```bash
git add feature/auth/data/src/
git commit -m "feat: implement KtorAuthDataSource with tests"
```

---

## Task 4: Implement `LoginViewModel`

**Files:**
- Create: `feature/auth/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/presentation/login/LoginViewModel.kt`
- Test: `feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/login/LoginViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/login/LoginViewModelTest.kt`:

```kotlin
package br.gohan.videofeed.feature.auth.presentation.login

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.feature.auth.domain.AuthRemoteDataSource
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

class FakeAuthDataSource : AuthRemoteDataSource {
    var loginResult: Result<String, DataError.Network> = Result.Success("fake-token")
    var registerResult: Result<String, DataError.Network> = Result.Success("fake-token")

    override suspend fun login(email: String, password: String) = loginResult
    override suspend fun register(email: String, password: String) = registerResult
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```
./gradlew :feature:auth:presentation:jvmTest --tests "*.LoginViewModelTest"
```
Expected: FAIL — `LoginViewModel` does not exist yet

- [ ] **Step 3: Create `LoginViewModel.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.onFailure
import br.gohan.videofeed.core.domain.error.onSuccess
import br.gohan.videofeed.feature.auth.domain.AuthRemoteDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface LoginAction {
    data class OnEmailChange(val email: String) : LoginAction
    data class OnPasswordChange(val password: String) : LoginAction
    data object OnSubmit : LoginAction
    data object OnNavigateToRegister : LoginAction
}

sealed interface LoginEvent {
    data object NavigateToFeed : LoginEvent
    data object NavigateToRegister : LoginEvent
}

class LoginViewModel(
    private val authDataSource: AuthRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange -> _state.update { it.copy(email = action.email) }
            is LoginAction.OnPasswordChange -> _state.update { it.copy(password = action.password) }
            is LoginAction.OnSubmit -> login()
            is LoginAction.OnNavigateToRegister -> {
                viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
            }
        }
    }

    private fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authDataSource.login(_state.value.email, _state.value.password)
                .onSuccess {
                    _events.send(LoginEvent.NavigateToFeed)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    private fun DataError.Network.toMessage(): String = when (this) {
        DataError.Network.UNAUTHORIZED -> "Invalid email or password"
        DataError.Network.NO_INTERNET -> "No internet connection"
        DataError.Network.SERVER_ERROR -> "Server error, please try again"
        else -> "Something went wrong"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```
./gradlew :feature:auth:presentation:jvmTest --tests "*.LoginViewModelTest"
```
Expected: BUILD SUCCESSFUL — all 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add feature/auth/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/presentation/login/ \
        feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/login/
git commit -m "feat: implement LoginViewModel with MVI pattern and tests"
```

---

## Task 5: Implement `RegisterViewModel`

**Files:**
- Create: `feature/auth/presentation/src/commonMain/kotlin/br/gohan/videofeed/feature/auth/presentation/register/RegisterViewModel.kt`
- Test: `feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/register/RegisterViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `feature/auth/presentation/src/commonTest/kotlin/br/gohan/videofeed/feature/auth/presentation/register/RegisterViewModelTest.kt`:

```kotlin
package br.gohan.videofeed.feature.auth.presentation.register

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.Result
import br.gohan.videofeed.feature.auth.presentation.login.FakeAuthDataSource
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
```

- [ ] **Step 2: Run to verify they fail**

Run:
```
./gradlew :feature:auth:presentation:jvmTest --tests "*.RegisterViewModelTest"
```
Expected: FAIL

- [ ] **Step 3: Create `RegisterViewModel.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.core.domain.error.DataError
import br.gohan.videofeed.core.domain.error.onFailure
import br.gohan.videofeed.core.domain.error.onSuccess
import br.gohan.videofeed.feature.auth.domain.AuthRemoteDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface RegisterAction {
    data class OnEmailChange(val email: String) : RegisterAction
    data class OnPasswordChange(val password: String) : RegisterAction
    data object OnSubmit : RegisterAction
    data object OnNavigateToLogin : RegisterAction
}

sealed interface RegisterEvent {
    data object NavigateToFeed : RegisterEvent
    data object NavigateToLogin : RegisterEvent
}

class RegisterViewModel(
    private val authDataSource: AuthRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state = _state.asStateFlow()

    private val _events = Channel<RegisterEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.OnEmailChange -> _state.update { it.copy(email = action.email) }
            is RegisterAction.OnPasswordChange -> _state.update { it.copy(password = action.password) }
            is RegisterAction.OnSubmit -> register()
            is RegisterAction.OnNavigateToLogin -> {
                viewModelScope.launch { _events.send(RegisterEvent.NavigateToLogin) }
            }
        }
    }

    private fun register() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authDataSource.register(_state.value.email, _state.value.password)
                .onSuccess {
                    _events.send(RegisterEvent.NavigateToFeed)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    private fun DataError.Network.toMessage(): String = when (this) {
        DataError.Network.CONFLICT -> "Email already registered"
        DataError.Network.NO_INTERNET -> "No internet connection"
        DataError.Network.SERVER_ERROR -> "Server error, please try again"
        else -> "Something went wrong"
    }
}
```

- [ ] **Step 4: Create `authPresentationModule.kt`**

```kotlin
package br.gohan.videofeed.feature.auth.presentation

import br.gohan.videofeed.feature.auth.presentation.login.LoginViewModel
import br.gohan.videofeed.feature.auth.presentation.register.RegisterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
}
```

- [ ] **Step 5: Run all auth presentation tests**

Run:
```
./gradlew :feature:auth:presentation:jvmTest
```
Expected: BUILD SUCCESSFUL — all 8 tests PASS

- [ ] **Step 6: Commit**

```bash
git add feature/auth/presentation/src/
git commit -m "feat: implement RegisterViewModel and Koin presentation module"
```

---

## Task 6: Set Up Koin in `:composeApp`

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/VideoFeedApplication.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/AppModules.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/MainActivity.kt`

- [ ] **Step 1: Create `AppModules.kt`**

```kotlin
package br.gohan.videofeed.di

import br.gohan.videofeed.core.data.auth.DataStoreTokenStorage
import br.gohan.videofeed.core.data.auth.TokenStorage
import br.gohan.videofeed.core.data.network.HttpClientFactory
import br.gohan.videofeed.feature.auth.data.authDataModule
import br.gohan.videofeed.feature.auth.presentation.authPresentationModule
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreAndroidModule = module {
    single<TokenStorage> { DataStoreTokenStorage(androidContext()) }
    single { HttpClientFactory.create(Android.create(), get()) }
}

val appModules = listOf(
    coreAndroidModule,
    authDataModule,
    authPresentationModule
)
```

- [ ] **Step 2: Create `VideoFeedApplication.kt`**

```kotlin
package br.gohan.videofeed

import android.app.Application
import br.gohan.videofeed.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class VideoFeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@VideoFeedApplication)
            properties(mapOf("baseUrl" to BuildConfig.BASE_URL))
            modules(appModules)
        }
    }
}
```

- [ ] **Step 3: Register Application class in AndroidManifest**

Open `composeApp/src/androidMain/AndroidManifest.xml` and add `android:name=".VideoFeedApplication"` to the `<application>` tag:

```xml
<application
    android:name=".VideoFeedApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.VideoFeed">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

- [ ] **Step 4: Update `MainActivity.kt`**

```kotlin
package br.gohan.videofeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import br.gohan.videofeed.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppNavHost()
            }
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/VideoFeedApplication.kt \
        composeApp/src/androidMain/kotlin/br/gohan/videofeed/di/ \
        composeApp/src/androidMain/kotlin/br/gohan/videofeed/MainActivity.kt \
        composeApp/src/androidMain/AndroidManifest.xml
git commit -m "feat: set up Koin in Android app with auth modules"
```

---

## Task 7: Add Auth Screens and Navigation

**Files:**
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AppNavHost.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AuthNavGraph.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/auth/LoginScreen.kt`
- Create: `composeApp/src/androidMain/kotlin/br/gohan/videofeed/auth/RegisterScreen.kt`

- [ ] **Step 1: Create `LoginScreen.kt`**

```kotlin
package br.gohan.videofeed.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.gohan.videofeed.feature.auth.presentation.login.LoginAction
import br.gohan.videofeed.feature.auth.presentation.login.LoginEvent
import br.gohan.videofeed.feature.auth.presentation.login.LoginState
import br.gohan.videofeed.feature.auth.presentation.login.LoginViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun LoginRoot(
    onNavigateToFeed: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            LoginEvent.NavigateToFeed -> onNavigateToFeed()
            LoginEvent.NavigateToRegister -> onNavigateToRegister()
        }
    }

    LoginScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun LoginScreen(
    state: LoginState,
    onAction: (LoginAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VideoFeed", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onAction(LoginAction.OnSubmit) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log In")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { onAction(LoginAction.OnNavigateToRegister) }) {
            Text("Don't have an account? Register")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(state = LoginState(), onAction = {})
    }
}
```

- [ ] **Step 2: Create `RegisterScreen.kt`**

```kotlin
package br.gohan.videofeed.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.gohan.videofeed.feature.auth.presentation.register.RegisterAction
import br.gohan.videofeed.feature.auth.presentation.register.RegisterEvent
import br.gohan.videofeed.feature.auth.presentation.register.RegisterState
import br.gohan.videofeed.feature.auth.presentation.register.RegisterViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterRoot(
    onNavigateToFeed: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RegisterEvent.NavigateToFeed -> onNavigateToFeed()
            RegisterEvent.NavigateToLogin -> onNavigateToLogin()
        }
    }

    RegisterScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun RegisterScreen(
    state: RegisterState,
    onAction: (RegisterAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create Account", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = { onAction(RegisterAction.OnEmailChange(it)) },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = { onAction(RegisterAction.OnPasswordChange(it)) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onAction(RegisterAction.OnSubmit) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Account")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { onAction(RegisterAction.OnNavigateToLogin) }) {
            Text("Already have an account? Log In")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    MaterialTheme {
        RegisterScreen(state = RegisterState(), onAction = {})
    }
}
```

- [ ] **Step 3: Create `ObserveAsEvents.kt` utility**

```kotlin
package br.gohan.videofeed.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    onEvent: (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(onEvent)
        }
    }
}
```

- [ ] **Step 4: Create route objects**

Create `composeApp/src/androidMain/kotlin/br/gohan/videofeed/navigation/AppRoutes.kt`:

```kotlin
package br.gohan.videofeed.navigation

import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object FeedRoute      // placeholder for Phase 3
```

- [ ] **Step 5: Create `AuthNavGraph.kt`**

```kotlin
package br.gohan.videofeed.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.gohan.videofeed.auth.LoginRoot
import br.gohan.videofeed.auth.RegisterRoot

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onNavigateToFeed: () -> Unit
) {
    composable<LoginRoute> {
        LoginRoot(
            onNavigateToFeed = onNavigateToFeed,
            onNavigateToRegister = { navController.navigate(RegisterRoute) }
        )
    }
    composable<RegisterRoute> {
        RegisterRoot(
            onNavigateToFeed = onNavigateToFeed,
            onNavigateToLogin = { navController.popBackStack() }
        )
    }
}
```

- [ ] **Step 6: Create `AppNavHost.kt`**

```kotlin
package br.gohan.videofeed.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

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
        // feedGraph() added in Phase 3
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/gohan/videofeed/
git commit -m "feat: add Login/Register screens, ObserveAsEvents utility, and auth nav graph"
```

---

## Task 8: Final Verification

- [ ] **Step 1: Run all Phase 2 tests**

Run:
```
./gradlew :feature:auth:data:jvmTest :feature:auth:presentation:jvmTest
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
git commit -m "feat: Phase 2 complete — auth feature with KMP ViewModels and Android Compose screens"
```

---

## Phase 2 Checklist

- [ ] `AuthRemoteDataSource` interface + `AuthError` in `:feature:auth:domain` ✓
- [ ] `KtorAuthDataSource` with MockEngine tests ✓
- [ ] `LoginViewModel` (State/Action/Event) with Turbine + AssertK tests ✓
- [ ] `RegisterViewModel` (State/Action/Event) with Turbine + AssertK tests ✓
- [ ] Koin modules: `authDataModule`, `authPresentationModule`, `coreAndroidModule` ✓
- [ ] `VideoFeedApplication` with Koin setup ✓
- [ ] `LoginScreen` + `RegisterScreen` (Root/Screen split, previews) ✓
- [ ] Type-safe navigation with `authGraph` ✓
- [ ] App builds and login/register screens display ✓

**Next:** Phase 3 — Feed feature (KMP FeedViewModel + Android ExoPlayer + prefetch)
