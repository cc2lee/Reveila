# [ADR-0002]: Dynamic Plugin Strategy using DexClassLoader

* **Status:** Accepted
* **Deciders:** Charles Lee
* **Date:** 2026-01-17
* **Tags:** #android #plugins #security #classloader

## 1. Context and Problem Statement
To maintain a modular "Reveila-Suite," the Android application must support runtime loading of feature modules (plugins). Standard Java `URLClassLoader` is incompatible with the Android ART runtime. Additionally, starting with Android 14 (API 34), the system enforces strict "Safer Dynamic Code Loading" rules, requiring all loaded files to be immutable (read-only) at the time of loading.

## 2. Decision Drivers
* **Dependency Isolation:** Plugins must run their own library versions without conflicting with the host app's classpath.
* **Security Compliance:** Must meet Android 14+ requirements to avoid `SecurityException`.
* **Zero-Touch Deployment:** Ability to update plugins without re-releasing the main APK.

## 3. Considered Options
* **Option 1: Google Play Dynamic Delivery:** Official Android App Bundle feature.
    * *Cons:* Tied to Play Store; high friction for enterprise "sideloading."
* **Option 2: Custom DexClassLoader (Child-First):** Loading `.dex` or `.jar` files from internal storage.
    * *Pros:* Total control over delegation; supports independent versioning.

## 4. Decision Outcome
Chosen option: **Option 2: Custom DexClassLoader (Child-First)**. This allows the suite to remain flexible and supports a "plugin marketplace" model where features are loaded dynamically from the app's internal private storage.

### 4.1 Positive Consequences
* **Version Safety:** Our child-first logic prevents `NoSuchMethodError` when the host and plugin use different versions of the same library.
* **Fast Iteration:** Plugins can be compiled and deployed via `adb` or download without a full build cycle.

### 4.2 Negative Consequences
* **Build Overhead:** Requires a custom Gradle task to run the `d8` tool to convert `.jar` to `.dex`.
* **Android 14 Constraint:** We must implement a specific file-handling ceremony (Write -> setReadOnly -> Load).

## 5. Implementation Notes



* **Security:** Plugins must be stored in `context.getFilesDir()` (internal storage). 
* **Android 14 Fix:** Before calling the `DexClassLoader` constructor, we must call `file.setReadOnly()`.
* **Delegation:** We override `loadClass` to search the plugin's `findClass()` before calling `super.loadClass()`, except for `java.*` and `android.*` packages.

## 6. Pros and Cons of the Options

### Option 2: DexClassLoader
* **Pros:** Highly modular; mimics enterprise OSGi-style isolation.
* **Cons:** Requires manual management of the plugin lifecycle and security permissions.

## 7. Build Automation (Update)
- A custom Gradle task `deployPluginToHost` is used to automate the `d8` conversion.
- It targets `src/main/assets/plugins` in the host app to ensure the plugin is bundled with the APK for easy testing during development.