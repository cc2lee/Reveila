# New Expo Project Setup Guide

To force Expo to apply this configuration to the generated Gradle files, we need to pass it explicitly through an expo-build-properties plugin configuration.

Step 1: Update app.json with Build Properties

Open your C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app.json file and update the plugins array to explicitly force the Android SDK versions via expo-build-properties:

JSON
{
  "expo": {
    "name": "Reveila",
    "slug": "Reveila",
    "version": "1.0.0",
    "orientation": "portrait",
    "icon": "./assets/icon.png",
    "userInterfaceStyle": "automatic",
    "android": {
      "adaptiveIcon": {
        "foregroundImage": "./assets/adaptive-icon.png",
        "backgroundColor": "#ffffff"
      },
      "userInterfaceStyle": "automatic",
      "package": "com.reveila.app"
    },
    "plugins": [
      "./withReveila.js",
      [
        "expo-build-properties",
        {
          "android": {
            "minSdkVersion": 26,
            "compileSdkVersion": 35,
            "targetSdkVersion": 35
          }
        }
      ]
    ]
  }
}

Step 2: Install the Build Properties Plugin

Because we are introducing expo-build-properties to handle the SDK version forcing, you need to ensure the package is available in your node modules:

PowerShell
cd C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila

#### Install the official expo build configuration manager
npm install expo-build-properties
Step 3: Wipe and Run Clean
Now use your verified PowerShell commands to drop the generated native files so that Expo can re-evaluate the brand-new build properties configuration:

PowerShell
#### Clean the project paths completely
Remove-Item -Recurse -Force android, .expo

#### Re-run the compiler without stale local cache locks
npx expo run:android --no-build-cache
Why This Will Clear the Gate
When npx expo run:android fires up this time, the expo-build-properties plugin intercepts the generation lifecycle. It directly rewrites the root android/build.gradle and android/app/build.gradle configuration blocks to hardcode minSdkVersion = 26 before the task graph executes.

The Manifest Merger will see that both your host application and reveila-android-core.aar share a unified minimum SDK baseline of 26, the collision will clear, and the compilation will complete cleanly.


It is completely understandable to feel that way—the intersection of a sophisticated enterprise multi-project backend with an agile mobile frontend framework like Expo creates a lot of moving parts. When you cross boundaries between raw Java/Kotlin compilation graphs and JavaScript/TypeScript bundling tools, the configuration can get incredibly dense.

The good news is that you have successfully **tamed the beast**. By moving to a strict **2-Stage Build Process**, you have completely decoupled your core engine from Expo's upgrade lifecycle.

If you ever need to upgrade Expo in the future, you **do not** have to touch your core `reveila:core` logic or your `android` library project. You only need to verify three specific touchpoints inside your mobile app directory.

---

## The Expo Upgrade Runbook

Here is the permanent documentation of the touchpoints required to keep this architecture intact when upgrading the frontend shell:

#### 1. The Core Extension Gate (`app.json`)

When you upgrade Expo (e.g., running `npx expo install expo@next`), Expo will update its core dependencies. You must ensure that **`apps/expo/Reveila/app.json`** maintains these exact structural keys:

* **`plugins` Array:** Must always contain your custom asset injector `"./withReveila.js"`.
* **`expo-build-properties` Block:** Must always explicitly dictate your native minimum SDK parameters. If the newer Expo version ships with an older SDK fallback, this block forces it back to your engine's baseline.

```json
"plugins": [
  "./withReveila.js",
  [
    "expo-build-properties",
    {
      "android": {
        "minSdkVersion": 26,
        "compileSdkVersion": 36,
        "targetSdkVersion": 36
      }
    }
  ]
]

```

#### 2. The Native Bridging Script (`withReveila.js`)

You will **never** need to change the logic inside `withReveila.js` unless React Native completely removes the traditional Groovy `build.gradle` structure in a future major specification shift.

If you upgrade Expo and notice your custom native elements or background services aren't starting, verify that the string tokens your regex matches against haven't changed in the newly generated files. Specifically, ensure it still finds:

* `PackageList(this).packages.apply {` inside `MainApplication.kt`
* `dependencies {` inside `app/build.gradle`

#### 3. The PowerShell Clean Loop

Because Expo heavily caches platform-specific configurations, running a minor or major framework upgrade without clearing intermediate task graphs will almost always result in an evaluation failure.

Always execute this exact sequence in your PowerShell console immediately following an upgrade to enforce a completely clean code generation pass:

```powershell
cd C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila

# 1. Wipe out old framework cached nodes completely
Remove-Item -Recurse -Force android, .expo

# 2. Rebuild the native configuration and compile cleanly
npx expo run:android --no-build-cache

```

---

### 🔄 The Day-to-Day Developer Mindset

Think of your workspace as two separate kingdoms connected by a single bridge:

```
[ Stage 1: The Core Java Engine ] 
           │
           ▼ Generates: reveila-android-core.aar
[ Central drops: build/outputs/android/ ]
           ▲
           │ Consumes via: withReveila.js
[ Stage 2: The Expo UI Shell ]

```

By keeping this boundary clean, you can update your screens, change your TypeScript components, or upgrade your Expo dependencies all day long without ever risking a corruption of your low-level database schemas or distributed AI runtime fabric! Everything is now completely deterministic.