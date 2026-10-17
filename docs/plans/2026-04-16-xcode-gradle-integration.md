# Xcode Gradle Auto-Build Integration Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the manually committed VideoFeed.xcframework with an Xcode Run Script build phase that calls `embedAndSignAppleFrameworkForXcode` automatically on every build.

**Architecture:** Add a `PBXShellScriptBuildPhase` to the Xcode project that invokes Gradle before compilation. Remove the static xcframework from git tracking. Update framework search paths so Xcode can link against the dynamically built output.

**Tech Stack:** Kotlin Multiplatform, Xcode 26, Gradle, Swift

---

### Task 1: Add Run Script build phase to project.pbxproj

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Step 1: Add a new PBXShellScriptBuildPhase entry**

In the `/* Begin PBXShellScriptBuildPhase section */` (create it if missing), add this block after the `/* End PBXCopyFilesBuildPhase section */` line:

```
/* Begin PBXShellScriptBuildPhase section */
		4640BFF02F910000004890AB /* Gradle embedAndSign */ = {
			isa = PBXShellScriptBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			inputFileListPaths = (
			);
			inputPaths = (
			);
			name = "Gradle embedAndSign";
			outputFileListPaths = (
			);
			outputPaths = (
			);
			runOnlyForDeploymentPostprocessing = 0;
			shellPath = /bin/sh;
			shellScript = "cd \"$SRCROOT/../..\"\n./gradlew :shared:embedAndSignAppleFrameworkForXcode\n";
		};
/* End PBXShellScriptBuildPhase section */
```

**Step 2: Add the script phase to the target's buildPhases list**

Find the `buildPhases` array for the `iosApp` target (around line 90) and add the new phase ID as the FIRST item:

```
buildPhases = (
    4640BFF02F910000004890AB /* Gradle embedAndSign */,
    4640BFA32F905D0F0048903B /* Sources */,
    4640BFA42F905D0F0048903B /* Frameworks */,
    4640BFA52F905D0F0048903B /* Resources */,
    4640BFE52F90632B0048903B /* Embed Frameworks */,
);
```

**Step 3: Verify the pbxproj is valid**

Open Xcode and confirm the project loads without errors. You should see a "Gradle embedAndSign" Run Script phase in the target's Build Phases.

---

### Task 2: Update FRAMEWORK_SEARCH_PATHS so Xcode can link against the built framework

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Context:** `embedAndSignAppleFrameworkForXcode` places the built framework at `$(BUILT_PRODUCTS_DIR)/PackageFrameworks`. Xcode needs this in `FRAMEWORK_SEARCH_PATHS` to link against it at build time.

**Step 1: Find both Debug and Release XCBuildConfiguration blocks for the iosApp target**

Look for the two `XCBuildConfiguration` blocks that contain `PRODUCT_NAME = iosApp` (not the project-level ones).

**Step 2: Add FRAMEWORK_SEARCH_PATHS to each**

In each target build configuration block, add:
```
FRAMEWORK_SEARCH_PATHS = (
    "$(inherited)",
    "$(BUILT_PRODUCTS_DIR)/PackageFrameworks",
);
```

---

### Task 3: Remove VideoFeed.xcframework from git tracking

**Step 1: Untrack the xcframework directory**

```bash
git rm -r --cached iosApp/VideoFeed.xcframework
```

Expected: lines like `rm 'iosApp/VideoFeed.xcframework/...'`

**Step 2: Add to .gitignore**

Open `.gitignore` (root of repo) and add:
```
# KMP XCFramework — built automatically by Xcode via Gradle
iosApp/VideoFeed.xcframework/
```

**Step 3: Commit**

```bash
git add .gitignore iosApp/iosApp.xcodeproj/project.pbxproj
git commit -m "feat: replace static xcframework with Xcode-Gradle build phase"
```

---

### Task 4: Verify the integration works

**Step 1: Open iosApp in Xcode**

```bash
open iosApp/iosApp.xcodeproj
```

**Step 2: Build for simulator**

Press Cmd+B. In the build log you should see the "Gradle embedAndSign" phase run and output Gradle download/build progress.

**Step 3: Confirm no missing framework errors**

The build should succeed. If you see "framework not found VideoFeed", check that `FRAMEWORK_SEARCH_PATHS` includes `$(BUILT_PRODUCTS_DIR)/PackageFrameworks`.
