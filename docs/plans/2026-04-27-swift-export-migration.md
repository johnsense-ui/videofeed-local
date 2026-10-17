# Swift Export Migration Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace `kmp-native-coroutines` + `kmp-observableviewmodel` with Kotlin's native Swift Export so that Swift views consume clean enums, async/await, and AsyncSequence without third-party wrapper libraries.

**Architecture:** Enable Swift Export in `shared/build.gradle.kts`; remove the two Rick Clephas libraries from Gradle and from the Xcode SPM package list; strip `@NativeCoroutines`/`@NativeCoroutinesState` from ViewModels and swap their base class to `androidx.lifecycle.ViewModel`; rewrite the four Swift views using `@Observable` host objects that collect `StateFlow` and `Flow` as `AsyncSequence`.

**Tech Stack:** Kotlin 2.3.20, Swift Export (experimental), SwiftUI `@Observable` (iOS 17+), Koin 4.1.0 (factory pattern stays), `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` (already in deps).

**⚠️ Swift Export naming rules (Obj-C → Swift Export)**
- Top-level Kotlin functions: `IOSKoinHelperKt.doInitKoin()` → `initKoin()` (no class prefix, no `do` prefix)
- Top-level factory functions: `IOSKoinHelperKt.loginViewModel()` → `loginViewModel()`
- Sealed interface cases: `LoginActionOnEmailChange(email:)` → `LoginAction.onEmailChange(email:)` (Swift enum with associated values)
- Data object cases: `LoginEventNavigateToFeed` → `LoginEvent.navigateToFeed`
- `StateFlow<T>` and `Flow<T>` → `AnyAsyncSequence<T>` (Swift Export maps Kotlin flows to async sequences)
- `StateFlow.value` stays accessible as a property for reading the current value synchronously

---

## Task 1: Enable Swift Export + remove old plugins from Gradle

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

**Step 1: Remove entries from `libs.versions.toml`**

In the `[versions]` section, delete the line:
```toml
native-coroutines = "1.0.2"
```

In the `[libraries]` section, delete the line:
```toml
kmp-observableviewmodel-core = { module = "com.rickclephas.kmp:kmp-observableviewmodel-core", version.ref = "observable-view-model" }
```
Also delete `observable-view-model` from `[versions]`:
```toml
observable-view-model = "1.0.3"
```

In the `[plugins]` section, delete the line:
```toml
kmpNativeCoroutines = { id = "com.rickclephas.kmp.nativecoroutines", version.ref = "native-coroutines" }
```

**Step 2: Update `shared/build.gradle.kts`**

Remove from `plugins {}` block:
```kotlin
alias(libs.plugins.kmpNativeCoroutines)
```

Remove from `commonMain.dependencies {}`:
```kotlin
api(libs.kmp.observableviewmodel.core)
```

Remove the entire `all { languageSettings { ... } }` block at the bottom (it only existed for NativeCoroutines opt-ins):
```kotlin
all {
    languageSettings {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
    }
}
```

Add Swift Export enablement inside the `kotlin {}` block, after the `sourceSets {}` block:
```kotlin
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalSwiftExportDsl::class)
swiftExport {}
```

Also add `XCFramework` import is no longer needed — remove:
```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
```
(it's only there for the XCFramework DSL which we no longer use for the build phase — the framework still builds, just with Swift interfaces)

Actually keep the XCFramework import as-is since the binaries framework block still references it implicitly. Instead just add the swiftExport block.

**Step 3: Verify the build file compiles**

Run:
```bash
./gradlew :shared:tasks --group=build 2>&1 | head -30
```
Expected: task list without plugin-not-found errors.

**Step 4: Commit**
```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build: enable Swift Export, remove kmp-native-coroutines and kmp-observableviewmodel"
```

---

## Task 2: Update ViewModels — remove annotations, fix base class

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/gohan/videofeed/auth/presenter/LoginViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/br/gohan/videofeed/auth/presenter/RegisterViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/br/gohan/videofeed/feed/presenter/FeedViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/br/gohan/videofeed/upload/presenter/UploadViewModel.kt`

**Replacements to apply to every ViewModel file:**

Remove these imports:
```kotlin
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import com.rickclephas.kmp.observableviewmodel.MutableStateFlow
import com.rickclephas.kmp.observableviewmodel.ViewModel
import com.rickclephas.kmp.observableviewmodel.launch
```

Add these imports:
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
```

For each `_state` declaration, change:
```kotlin
private val _state = MutableStateFlow(viewModelScope, LoginState())
```
to:
```kotlin
private val _state = MutableStateFlow(LoginState())
```
(standard `kotlinx.coroutines.flow.MutableStateFlow` takes only the initial value)

Remove `@NativeCoroutinesState` annotation from `state` property.
Remove `@NativeCoroutines` annotation from `events` property.

The `viewModelScope.launch { }` calls stay the same — `androidx.lifecycle.ViewModel` provides `viewModelScope` on all platforms including iOS.

**Final state of `LoginViewModel.kt` after changes:**
```kotlin
package br.gohan.videofeed.auth.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.videofeed.auth.domain.AuthRemoteDataSource
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.onFailure
import br.gohan.videofeed.core.error.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class LoginViewModel(
    private val authDataSource: AuthRemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

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
                .onSuccess { _events.send(LoginEvent.NavigateToFeed) }
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

Apply the same pattern (remove annotations, fix imports, fix `MutableStateFlow(...)`) to `RegisterViewModel`, `FeedViewModel`, and `UploadViewModel`.

**Step: Build shared Kotlin to verify no compile errors**
```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

**Step: Commit**
```bash
git add shared/src/commonMain/kotlin/br/gohan/videofeed/
git commit -m "refactor: remove NativeCoroutines annotations, switch to androidx lifecycle ViewModel"
```

---

## Task 3: Update IOSKoinHelper imports

**File:**
- Modify: `shared/src/iosMain/kotlin/br/gohan/videofeed/IOSKoinHelper.kt`

Remove the import:
```kotlin
import com.rickclephas.kmp.observableviewmodel.ViewModel
```
(it had this indirectly through the ViewModel classes; verify the file still compiles cleanly)

No logic changes needed — the factory pattern (`ViewModelFactory : KoinComponent`) stays exactly the same.

**Step: Compile**
```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

**Step: Commit**
```bash
git add shared/src/iosMain/
git commit -m "refactor: remove observableviewmodel import from IOSKoinHelper"
```

---

## Task 4: Strip SPM packages from Xcode project

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Modify: `iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`

**Step 1: Remove framework build file entries from `project.pbxproj`**

In the `/* Begin PBXBuildFile section */` area, delete these 6 lines:
```
466E17C52F9BCFC7009E649C /* KMPObservableViewModelCore in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17C42F9BCFC7009E649C /* KMPObservableViewModelCore */; };
466E17C72F9BCFC7009E649C /* KMPObservableViewModelSwiftUI in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17C62F9BCFC7009E649C /* KMPObservableViewModelSwiftUI */; };
466E17E42F9BD210009E649C /* KMPNativeCoroutinesAsync in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17E32F9BD210009E649C /* KMPNativeCoroutinesAsync */; };
466E17E62F9BD250009E649C /* KMPNativeCoroutinesRxSwift in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17E52F9BD250009E649C /* KMPNativeCoroutinesRxSwift */; };
466E17E82F9BD25F009E649C /* KMPNativeCoroutinesCombine in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17E72F9BD25F009E649C /* KMPNativeCoroutinesCombine */; };
466E17EA2F9BD262009E649C /* KMPNativeCoroutinesCore in Frameworks */ = {isa = PBXBuildFile; productRef = 466E17E92F9BD262009E649C /* KMPNativeCoroutinesCore */; };
```

**Step 2: Remove from Frameworks build phase**

In `/* Begin PBXFrameworksBuildPhase section */`, remove these 6 entries from the `files = (...)` array:
```
466E17C52F9BCFC7009E649C /* KMPObservableViewModelCore in Frameworks */,
466E17E82F9BD25F009E649C /* KMPNativeCoroutinesCombine in Frameworks */,
466E17EA2F9BD262009E649C /* KMPNativeCoroutinesCore in Frameworks */,
466E17C72F9BCFC7009E649C /* KMPObservableViewModelSwiftUI in Frameworks */,
466E17E42F9BD210009E649C /* KMPNativeCoroutinesAsync in Frameworks */,
466E17E62F9BD250009E649C /* KMPNativeCoroutinesRxSwift in Frameworks */,
```

**Step 3: Remove product dependencies from target**

In `/* Begin PBXNativeTarget section */`, inside the `packageProductDependencies = (...)` array, remove:
```
466E17C42F9BCFC7009E649C /* KMPObservableViewModelCore */,
466E17C62F9BCFC7009E649C /* KMPObservableViewModelSwiftUI */,
466E17E32F9BD210009E649C /* KMPNativeCoroutinesAsync */,
466E17E52F9BD250009E649C /* KMPNativeCoroutinesRxSwift */,
466E17E72F9BD25F009E649C /* KMPNativeCoroutinesCombine */,
466E17E92F9BD262009E649C /* KMPNativeCoroutinesCore */,
```

**Step 4: Remove package references from project**

In `/* Begin PBXProject section */`, inside `packageReferences = (...)`, remove:
```
466E17C32F9BCFC7009E649C /* XCRemoteSwiftPackageReference "KMP-ObservableViewModel" */,
466E17C82F9BCFED009E649C /* XCRemoteSwiftPackageReference "KMP-NativeCoroutines" */,
```

**Step 5: Remove XCRemoteSwiftPackageReference and XCSwiftPackageProductDependency blocks**

Delete the entire `/* Begin XCRemoteSwiftPackageReference section */` block (the two package definitions):
```
466E17C32F9BCFC7009E649C /* XCRemoteSwiftPackageReference "KMP-ObservableViewModel" */ = { ... };
466E17C82F9BCFED009E649C /* XCRemoteSwiftPackageReference "KMP-NativeCoroutines" */ = { ... };
```

Delete the 6 `XCSwiftPackageProductDependency` blocks:
```
466E17C42F9BCFC7009E649C /* KMPObservableViewModelCore */ = { ... };
466E17C62F9BCFC7009E649C /* KMPObservableViewModelSwiftUI */ = { ... };
466E17E32F9BD210009E649C /* KMPNativeCoroutinesAsync */ = { ... };
466E17E52F9BD250009E649C /* KMPNativeCoroutinesRxSwift */ = { ... };
466E17E72F9BD25F009E649C /* KMPNativeCoroutinesCombine */ = { ... };
466E17E92F9BD262009E649C /* KMPNativeCoroutinesCore */ = { ... };
```

**Step 6: Clean up `Package.resolved`**

Replace `iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` with:
```json
{
  "originHash" : "db79fcc1e89ddda40b63c6b4784113d9a3818a7e51c1cbcb2a074b98d190aa0f",
  "pins" : [],
  "version" : 3
}
```

**Step 7: Delete or empty the KMPObservableViewModel utility file**

`iosApp/iosApp/util/KMPObservableViewModel.swift` currently has:
```swift
import KMPObservableViewModelCore
import Shared
extension Kmp_observableviewmodel_coreViewModel: @retroactive ViewModel { }
```

Replace its content with an empty placeholder:
```swift
// Removed: KMPObservableViewModel — replaced by Swift Export + @Observable
```

**Step 8: Commit**
```bash
git add iosApp/
git commit -m "build: remove KMP-NativeCoroutines and KMP-ObservableViewModel from Xcode project"
```

---

## Task 5: Rewrite Swift views

This is the biggest task. Each view gets a small `@Observable` host class that bridges `StateFlow`/`Flow` to SwiftUI. Sealed interfaces come through as native Swift enums after Swift Export.

**Pattern used in every view:**
```swift
@Observable
final class XxxViewHost {
    var state: XxxState
    private let vm: XxxViewModel

    init() {
        vm = xxxViewModel()          // top-level Swift Export function (no Kt prefix)
        state = vm.state.value       // synchronous initial value
    }

    func onAction(_ action: XxxAction) { vm.onAction(action: action) }

    func observeState() async {
        for await s in vm.state { state = s }
    }

    func events() -> some AsyncSequence { vm.events }
}
```

**File: `iosApp/iosApp/auth/LoginView.swift`**

```swift
import SwiftUI
import Shared

@Observable
final class LoginViewHost {
    var state: LoginState
    private let vm: LoginViewModel

    init() {
        vm = loginViewModel()
        state = vm.state.value
    }

    func onAction(_ action: LoginAction) { vm.onAction(action: action) }
}

struct LoginView: View {
    @State private var host = LoginViewHost()
    let onSuccess: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Sign In").font(.largeTitle).bold()

            TextField("Email", text: Binding(
                get: { host.state.email },
                set: { host.onAction(.onEmailChange(email: $0)) }
            ))
            .keyboardType(.emailAddress)
            .autocapitalization(.none)
            .textFieldStyle(.roundedBorder)

            SecureField("Password", text: Binding(
                get: { host.state.password },
                set: { host.onAction(.onPasswordChange(password: $0)) }
            ))
            .textFieldStyle(.roundedBorder)

            if let error = host.state.error {
                Text(error).foregroundColor(.red).font(.caption)
            }

            Button(action: { host.onAction(.onSubmit) }) {
                if host.state.isLoading {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Sign In").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(host.state.isLoading)
        }
        .padding()
        .task {
            await host.vm.state.collect { @MainActor s in host.state = s }
        }
        .task {
            for await event in host.vm.events {
                switch event {
                case .navigateToFeed: onSuccess()
                default: break
                }
            }
        }
    }
}
```

> **Note on flow collection syntax**: Swift Export maps `StateFlow<T>` and `Flow<T>` to Swift async sequences. The exact iteration API may be `for await x in vm.state { }` (if mapped to `AsyncSequence`) or `await vm.state.collect { x in }` (if a custom collect function is generated). Verify which form compiles after enabling Swift Export and adjust accordingly. If neither works out of the box, wrap the flow in `kotlinx.coroutines.flow.toList()` or expose a suspend helper.

**File: `iosApp/iosApp/auth/RegisterView.swift`**

```swift
import SwiftUI
import Shared

@Observable
final class RegisterViewHost {
    var state: RegisterState
    private let vm: RegisterViewModel

    init() {
        vm = registerViewModel()
        state = vm.state.value
    }

    func onAction(_ action: RegisterAction) { vm.onAction(action: action) }
}

struct RegisterView: View {
    @State private var host = RegisterViewHost()
    let onSuccess: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Create Account").font(.largeTitle).bold()

            TextField("Email", text: Binding(
                get: { host.state.email },
                set: { host.onAction(.onEmailChange(email: $0)) }
            ))
            .keyboardType(.emailAddress)
            .autocapitalization(.none)
            .textFieldStyle(.roundedBorder)

            SecureField("Password", text: Binding(
                get: { host.state.password },
                set: { host.onAction(.onPasswordChange(password: $0)) }
            ))
            .textFieldStyle(.roundedBorder)

            if let error = host.state.error {
                Text(error).foregroundColor(.red).font(.caption)
            }

            Button(action: { host.onAction(.onSubmit) }) {
                if host.state.isLoading {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Create Account").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(host.state.isLoading)
        }
        .padding()
        .task {
            for await s in host.vm.state { host.state = s }
        }
        .task {
            for await event in host.vm.events {
                switch event {
                case .navigateToFeed: onSuccess()
                default: break
                }
            }
        }
    }
}
```

**File: `iosApp/iosApp/feed/FeedView.swift`**

```swift
import SwiftUI
import AVFoundation
import Shared

@Observable
final class FeedViewHost {
    var state: FeedState
    private let vm: FeedViewModel

    init() {
        vm = feedViewModel()
        state = vm.state.value
    }

    func onAction(_ action: FeedAction) { vm.onAction(action: action) }
}

struct FeedView: View {
    @State private var host = FeedViewHost()
    @State private var currentIndex = 0
    @State private var players: [String: AVPlayer] = [:]
    private let preloader = VideoPreloader()

    let onNavigateToLogin: () -> Void
    let onNavigateToUpload: () -> Void

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topTrailing) {
                if host.state.videos.isEmpty && host.state.isLoading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    TabView(selection: $currentIndex) {
                        ForEach(Array(host.state.videos.enumerated()), id: \.element.id) { index, video in
                            VideoItemView(
                                video: video,
                                player: player(for: video),
                                isVisible: index == currentIndex,
                                size: geo.size
                            )
                            .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .ignoresSafeArea()
                    .onChange(of: currentIndex) { _, newIndex in
                        host.onAction(.onVideoVisible(index: Int32(newIndex)))
                        prefetchNext(from: newIndex)
                    }
                }

                Button(action: onNavigateToUpload) {
                    Image(systemName: "plus.circle.fill").font(.title).padding()
                }
                .foregroundColor(.white)
            }
        }
        .task {
            for await s in host.vm.state { host.state = s }
        }
        .task {
            for await event in host.vm.events {
                switch event {
                case .navigateToLogin: onNavigateToLogin()
                case .navigateToUpload: onNavigateToUpload()
                }
            }
        }
    }

    private func player(for video: VideoUi) -> AVPlayer {
        if let existing = players[video.id] { return existing }
        let url = URL(string: video.cdnUrl)!
        let p: AVPlayer
        if let cached = preloader.asset(for: url) {
            p = AVPlayer(playerItem: AVPlayerItem(asset: cached))
        } else {
            p = AVPlayer(url: url)
        }
        players[video.id] = p
        return p
    }

    private func prefetchNext(from index: Int) {
        let urls = (1...2).compactMap { offset -> URL? in
            let next = index + offset
            guard next < host.state.videos.count else { return nil }
            return URL(string: host.state.videos[next].cdnUrl)
        }
        Task { await preloader.preload(urls: urls) }
    }
}
```

**File: `iosApp/iosApp/upload/UploadView.swift`**

```swift
import SwiftUI
import PhotosUI
import Shared

@Observable
final class UploadViewHost {
    var state: UploadState
    private let vm: UploadViewModel

    init() {
        vm = uploadViewModel()
        state = vm.state.value
    }

    func onAction(_ action: UploadAction) { vm.onAction(action: action) }
}

struct UploadView: View {
    @State private var host = UploadViewHost()
    @State private var pickerItem: PhotosPickerItem? = nil
    let onSuccess: () -> Void

    private var isLoading: Bool {
        if case .presigning = host.state.status { return true }
        if case .uploading = host.state.status { return true }
        if case .finalizing = host.state.status { return true }
        return false
    }

    private var canSubmit: Bool {
        !isLoading && host.state.selectedFilename != nil && !host.state.title.isEmpty
    }

    var body: some View {
        VStack(spacing: 24) {
            Text("Upload Video").font(.title).bold()

            PhotosPicker(selection: $pickerItem, matching: .videos, photoLibrary: .shared()) {
                Label(host.state.selectedFilename ?? "Select Video", systemImage: "video.badge.plus")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isLoading)
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                Task { await loadVideo(from: item) }
            }

            TextField("Title", text: Binding(
                get: { host.state.title },
                set: { host.onAction(.onTitleChange(title: $0)) }
            ))
            .textFieldStyle(.roundedBorder)
            .disabled(isLoading)

            Group {
                switch host.state.status {
                case .uploading(let progress):
                    VStack(spacing: 8) {
                        ProgressView(value: Double(progress))
                        Text("Uploading… \(Int(progress * 100))%").font(.caption)
                    }
                case .presigning:
                    HStack { ProgressView(); Text("Preparing upload…") }
                case .finalizing:
                    HStack { ProgressView(); Text("Saving…") }
                case .done:
                    Text("Upload complete!").foregroundColor(.green)
                case .error(let message):
                    Text(message).foregroundColor(.red).font(.caption)
                default:
                    EmptyView()
                }
            }

            Button(action: { host.onAction(.onSubmit) }) {
                Text("Upload").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSubmit)

            Spacer()
        }
        .padding()
        .task {
            for await s in host.vm.state { host.state = s }
        }
        .task {
            for await event in host.vm.events {
                switch event {
                case .navigateToFeed: onSuccess()
                }
            }
        }
    }

    private func loadVideo(from item: PhotosPickerItem) async {
        guard let url = try? await item.loadTransferable(type: URL.self),
              let data = try? Data(contentsOf: url) else { return }
        let bytes = [UInt8](data)
        let kotlinBytes = KotlinByteArray(size: Int32(bytes.count))
        for (i, b) in bytes.enumerated() {
            kotlinBytes.set(index: Int32(i), value: Int8(bitPattern: b))
        }
        host.onAction(.onFileSelected(bytes: kotlinBytes, filename: url.lastPathComponent, mimeType: "video/mp4"))
    }
}
```

**Step: Commit**
```bash
git add iosApp/iosApp/
git commit -m "feat: rewrite Swift views with Swift Export — @Observable hosts, native enum actions"
```

---

## Task 6: Update app entry point

**File:** `iosApp/iosApp/iosApp.swift`

Change:
```swift
IOSKoinHelperKt.doInitKoin(baseUrl: "http://localhost:8081")
```
to:
```swift
initKoin(baseUrl: "http://localhost:8081")
```

(Swift Export exposes top-level Kotlin functions without the `Kt` file class suffix and without the `do` prefix that Obj-C interop adds.)

**Step: Commit**
```bash
git add iosApp/iosApp/iosApp.swift
git commit -m "fix: update Koin init call for Swift Export naming (no Kt prefix)"
```

---

## Task 7: Build framework and verify in Xcode

**Step 1: Build the shared framework with Swift Export**
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL` and a `.framework` directory at `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework`

**Step 2: Check the framework contains Swift interfaces (not just Obj-C headers)**
```bash
ls shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Modules/
```
Expected: a `Shared.swiftmodule` directory instead of (or alongside) the `module.modulemap`.

**Step 3: Open Xcode and build**

Open `iosApp/iosApp.xcodeproj` in Xcode and do Product → Build (⌘B). Fix any compile errors that arise from:
- Flow collection API differences (see note in Task 5)
- Sealed interface case naming (may differ from predicted — use Xcode autocomplete to find actual names)
- Any missing `@MainActor` annotations if state updates happen off main thread

**Step 4: Test on simulator**

Run on iOS Simulator. Verify:
- App launches and Koin initializes without crash
- Feed loads videos
- Login/Register flows work
- Upload flow works

**Step 5: Commit final fixes**
```bash
git add -A
git commit -m "fix: Swift Export compile corrections after Xcode build"
```

---

## Known risks and fallbacks

| Risk | Likelihood | Fallback |
|------|-----------|---------|
| `StateFlow`/`Flow` not mapped to `AsyncSequence` automatically | Medium | Add Kotlin suspend helper: `suspend fun collectState(block: (State) -> Unit)` and call it from Swift as `async` |
| Sealed interface case names differ from predicted | Low | Use Xcode autocomplete to find actual generated names |
| `FeedViewModelWrapper` unresolved (was auto-generated by kmp-observableviewmodel) | Resolved | Replaced by `FeedViewHost` in Task 5 |
| `KotlinByteArray` API changes | Low | Same class, still accessible from Swift |
| `viewModelScope` not available on iOS for `androidx.lifecycle.ViewModel` | Low | Library already in deps, confirmed KMP-compatible |
