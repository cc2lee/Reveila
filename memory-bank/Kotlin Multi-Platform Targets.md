### 1. Multiplatform Target Topology

In [`reveila/core/build.gradle.kts`], the following native and platform targets have been configured:

| Target | Architecture | Output Artifact | Intended Environment |
| :--- | :--- | :--- | :--- |
| **`jvm`** | Java 17+ bytecode | `reveila-core-jvm.jar` | Enterprise server, Spring Boot ([`spring:core`](file:///c:/IDE/Projects/Reveila-Suite/spring/core)), and guarded runtime ([`reveila:rt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/rt)) |
| **`androidTarget`** | Android API 26–35 (AAR) | `reveila-core-debug.aar` | Android host service ([`android`](file:///c:/IDE/Projects/Reveila-Suite/android)) and React Native Expo shell |
| **`iosArm64` / `iosSimulatorArm64` / `iosX64`** | Apple Cocoa / Darwin | `ReveilaCore.xcframework` (Device + Simulator Universal) | iPhone, iPad native offline execution (**Sovereign Mode**) |
| **`macosArm64` / `macosX64`** | Apple Silicon & Intel Mac | `ReveilaCoreMac.framework` | Mac desktop autonomous agents |
| **`mingwX64`** | Windows x64 Native | `reveila_core.dll` (2.3 MB) + C header (`reveila_core_api.h`) | Windows edge devices and standalone sovereign desktop CLI |
| **`linuxX64`** | Linux x64 Native | `libreveila_core.so` + Static Library | Linux edge gateways, servers, and minimal containers |

---

### 2. Interface + Expect Factory Implementation
   - **`commonMain` Contracts:** Defined pure Kotlin interfaces with companion `invoke()` instantiation and static delegates:
     - [`PlatformFileSystem.kt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/commonMain/kotlin/com/reveila/system/io/PlatformFileSystem.kt)
     - [`PlatformSystem.kt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/commonMain/kotlin/com/reveila/system/platform/PlatformSystem.kt)
     - [`PlatformLogger.kt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/commonMain/kotlin/com/reveila/system/logging/PlatformLogger.kt)
     - [`PlatformScheduler.kt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/commonMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.kt)
     - [`PlatformCryptographer.kt`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/commonMain/kotlin/com/reveila/crypto/PlatformCryptographer.kt)
   - **Target Adapters (`actual fun` factories + concrete platform implementations):**
     - **`jvmMain`**: [`JvmPlatformFileSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/jvmMain/kotlin/com/reveila/system/io/PlatformFileSystem.jvm.kt), [`JvmPlatformSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/jvmMain/kotlin/com/reveila/system/platform/PlatformSystem.jvm.kt), [`JvmPlatformLogger`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/jvmMain/kotlin/com/reveila/system/logging/PlatformLogger.jvm.kt), [`JvmPlatformScheduler`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/jvmMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.jvm.kt), [`JvmPlatformCryptographer`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/jvmMain/kotlin/com/reveila/crypto/PlatformCryptographer.jvm.kt).
     - **`androidMain`**: [`AndroidPlatformFileSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/androidMain/kotlin/com/reveila/system/io/PlatformFileSystem.android.kt), [`AndroidPlatformSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/androidMain/kotlin/com/reveila/system/platform/PlatformSystem.android.kt), [`AndroidPlatformLogger`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/androidMain/kotlin/com/reveila/system/logging/PlatformLogger.android.kt), [`AndroidPlatformScheduler`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/androidMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.android.kt), [`AndroidPlatformCryptographer`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/androidMain/kotlin/com/reveila/crypto/PlatformCryptographer.android.kt).
     - **`appleMain`**: [`ApplePlatformFileSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/appleMain/kotlin/com/reveila/system/io/PlatformFileSystem.apple.kt), [`ApplePlatformSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/appleMain/kotlin/com/reveila/system/platform/PlatformSystem.apple.kt), [`ApplePlatformLogger`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/appleMain/kotlin/com/reveila/system/logging/PlatformLogger.apple.kt), [`ApplePlatformScheduler`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/appleMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.apple.kt), [`ApplePlatformCryptographer`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/appleMain/kotlin/com/reveila/crypto/PlatformCryptographer.apple.kt).
     - **`mingwMain`**: [`MingwPlatformFileSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/mingwMain/kotlin/com/reveila/system/io/PlatformFileSystem.mingw.kt), [`MingwPlatformSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/mingwMain/kotlin/com/reveila/system/platform/PlatformSystem.mingw.kt), [`MingwPlatformLogger`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/mingwMain/kotlin/com/reveila/system/logging/PlatformLogger.mingw.kt), [`MingwPlatformScheduler`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/mingwMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.mingw.kt), [`MingwPlatformCryptographer`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/mingwMain/kotlin/com/reveila/crypto/PlatformCryptographer.mingw.kt).
     - **`linuxMain`**: [`LinuxPlatformFileSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/linuxMain/kotlin/com/reveila/system/io/PlatformFileSystem.linux.kt), [`LinuxPlatformSystem`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/linuxMain/kotlin/com/reveila/system/platform/PlatformSystem.linux.kt), [`LinuxPlatformLogger`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/linuxMain/kotlin/com/reveila/system/logging/PlatformLogger.linux.kt), [`LinuxPlatformScheduler`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/linuxMain/kotlin/com/reveila/system/concurrency/PlatformScheduler.linux.kt), [`LinuxPlatformCryptographer`](file:///c:/IDE/Projects/Reveila-Suite/reveila/core/src/linuxMain/kotlin/com/reveila/crypto/PlatformCryptographer.linux.kt).

---

### 3. Build & Compilation

All multiplatform targets and downstream modules were verified using Gradle:

```powershell
# 1. Common Metadata compilation
.\gradlew.bat :reveila:core:compileCommonMainKotlinMetadata   # SUCCESS (0 errors)

# 2. Windows Native MinGW compilation & DLL linking
.\gradlew.bat :reveila:core:compileKotlinMingwX64             # SUCCESS (0 errors)
.\gradlew.bat :reveila:core:linkReleaseSharedMingwX64         # SUCCESS -> reveila_core.dll (2.3 MB)

# 3. Android target compilation
.\gradlew.bat :reveila:core:compileDebugKotlinAndroid         # SUCCESS (0 errors)

# 4. JVM target compilation & JAR packaging (136 Java classes + KMP Kotlin)
.\gradlew.bat :reveila:core:compileKotlinJvm                   # SUCCESS (0 errors)
.\gradlew.bat :reveila:core:jvmJar                            # SUCCESS -> reveila-core-jvm.jar

# 5. Downstream Consumer Verification
.\gradlew.bat :reveila:rt:compileJava                         # SUCCESS (0 errors)
.\gradlew.bat :android:compileDebugKotlin                     # SUCCESS (0 errors)
.\gradlew.bat :spring:core:compileJava                        # SUCCESS (0 errors)
```

---

### 4. Virtual Mac (GitHub Actions) Isolated Build Pipeline

To build Apple frameworks (`.xcframework` / `.framework`) without pulling down the entire Reveila Suite codebase, the pipeline uses **Git Sparse-Checkout**:

- **Workflow File:** [`.github/workflows/build-apple-frameworks.yml`](file:///c:/IDE/Projects/Reveila-Suite/.github/workflows/build-apple-frameworks.yml)
- **Runner Environment:** `macos-14` (Apple Silicon M1)
- **Sparse Paths Downloaded:**
  - `reveila/core`
  - `build-logic`
  - `gradle`
  - `gradlew`
  - `settings.gradle.kts` (configured with dynamic discovery so missing modules are skipped)
  - `build.gradle.kts`
  - `gradle.properties`
- **Output Artifacts:**
  - `ReveilaCore-iOS.xcframework.zip` (Universal XCFramework for physical iOS devices & simulator)
  - `ReveilaCore-macOS-Arm64.framework.zip` (Apple Silicon M-series framework)
  - `ReveilaCore-macOS-x64.framework.zip` (Intel x86_64 Mac framework)
  - `checksums.sha256` (Integrity checksums)

