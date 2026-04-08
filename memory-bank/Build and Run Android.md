# 📱 Build and Run (Mobile/Android)

This guide covers the installation and synchronization of the **Reveila Mobile** app onto Android emulators or physical devices.

---

## 📋 Prerequisites
- **Android Studio:** Installed with a configured Virtual Device (Emulator) or a physical device with **USB Debugging** enabled.
- **Node.js & npm:** Required for the Expo/React Native frontend.
- **ADB (Android Debug Bridge):** Should be in your system PATH.

---

## ☀️ New Device Setup (One-Time for each session)

Whenever you start a new emulator or connect a physical device, you must bridge the ports so the app can talk to your development environment.

1. **Start the Bridge:**
   ```bash
   # Metro Bundler (JavaScript)
   adb reverse tcp:8081 tcp:8081

   # Backend API (Optional - if running Spring Boot locally)
   adb reverse tcp:8080 tcp:8080
   ```

---

## 🚀 Daily Development Workflow

### 1. Build native library
In /Reveila-Suite/, run:
```bash
./gradlew.bat clean bootJar
```

### 2. Synchronize Native Code
*Required whenever you change `app.json` or native Android files.*
In /Reveila-Suite/apps/expo/Reveila/, run:
```bash
npm run prebuild
```

### 3. Launch the mobile app
In /Reveila-Suite/apps/expo/Reveila/, run:
```bash
npm run android
```
*This command will build the native app, install it on your device, and start the Metro bundler.*

### 4. Monitoring Logcat
To see native Android logs, run one of these:
```bash
adb logcat | findstr Reveila
adb logcat *:E
adb logcat *:E | findstr Reveila
adb logcat *:E | grep Reveila
adb logcat -d | findstr /C:"at com.reveila.system.Reveila.invoke" /C:"Exception" /C:"Error"
```

### 5. Debug native code
1. Set the app to wait for debugger connection:
   ```bash
   adb shell am set-debug-app -w --persistent com.reveila.android
   ```
   Open the app on your phone (it will hang on "Waiting for Debugger").

2. Forward the debugging port:
   ```bash
   adb forward tcp:5005 "jdwp:$(adb shell pidof com.reveila.android)"
   ```

3. In VS Code, launch the "Attach to Android (Java/Kotlin)" debug configuration.
   **Success:** The "Waiting" dialog disappears, and your breakpoints will now be hit!

---

## 🪲 Troubleshooting

### "Cannot connect to Metro"
If the app opens but shows a red error screen saying it cannot connect:
1. Ensure your terminal is running the bundler (or run `npx expo start`).
2. Re-run the bridge command: `adb reverse tcp:8081 tcp:8081`.

### Build Failures (Native)
If the build fails after a major update, try a deep clean:
```bash
cd apps/expo/Reveila/android
./gradlew clean
cd ..
npm run prebuild
```

### Device Not Found
If `adb devices` shows your device as `unauthorized`:
1. Check your phone/emulator screen.
2. Accept the "Allow USB Debugging?" prompt.
3. Run `adb kill-server && adb start-server`.

---

## 🛠️ VS Code Native Debugging Setup

### 📋 Prerequisites:
- "Extension Pack for Java"

In VS Code, create a debug configuration for "Android" and attach to the running process.
```json
  {
    "type": "java",
    "name": "Attach to Android (Java/Kotlin)",
    "request": "attach",
    "hostName": "localhost",
    "port": 5005
  }
```
This allows you to set breakpoints in the Java/Kotlin code.

## Setting Up Local LLM with Roo Code
- Install Ollama: https://ollama.com/download
- `ollama pull qwen2.5-coder:1.5b`
- `ollama list` to verify the model is in the local library
- Optimize for Roo Code: num_ctx 16384 / 8192 minimum
  ```bash
  ollama run qwen2.5-coder:7b
  >>> /set parameter num_ctx 16384
  >>> /save Quen2.5-coder-7b-ctx.16384
  >>> /exit
  ```
- `ollama rm <model-name>`
