# 📱 Build and Run (Mobile/Android)

This guide outlines the daily Android development workflow.

## Most used commands quick reference

```bash

npm run android
adb forward tcp:5005 "jdwp:$(adb shell pidof com.reveila.android)"

```

---

## 📋 Prerequisites
- **Android Studio:** Installed with a configured Virtual Device (Emulator) or a physical device with **USB Debugging** enabled.
- **Node.js & npm:** Required for the Expo/React Native frontend.
- **ADB (Android Debug Bridge):** Should be in your system PATH.

---

## ☀️ Setup (New Session)

Whenever you start a new emulator or connect a physical device, you must bridge the ports so the Android app can talk to your development environment.

```bash

# For mobile app to download JavaScript from Metro
adb reverse tcp:8081 tcp:8081

# For mobile app to call backend on the local host
adb reverse tcp:8080 tcp:8080

# Start Metro Bundler
npm start

```

---

## Development

```bash

# Build native library. In /Reveila-Suite/, run
./gradlew.bat clean bootJar

# Synchronize Native Code. Required whenever you change `app.json` or native Android files.
# In /Reveila-Suite/apps/expo/Reveila/, run
npm run prebuild

# Create and install the mobile app. /Reveila-Suite/apps/expo/Reveila/, run
npm run android

# Connect to Logcat
adb logcat | findstr Reveila
adb logcat *:E
adb logcat *:E | findstr Reveila
adb logcat *:E | grep Reveila
adb logcat -d | findstr /C:"at com.reveila.system.Reveila.invoke" /C:"Exception" /C:"Error"

```

---

## 🪲 Native Debug

```bash

# Set the app to wait for debugger connection
adb shell am set-debug-app -w --persistent com.reveila.android

# Launch Android app on your phone (it will hang on "Waiting for Debugger").
# Run this command every time the app is installed / reinstalled.
adb forward tcp:5005 "jdwp:$(adb shell pidof com.reveila.android)"

# If not working, break up the command
$APP_PID = adb shell pidof com.reveila.android
adb forward tcp:5005 jdwp:$APP_PID

# In VS Code Debug, launch "Attach to Android".

```

**Verification:**

* `adb forward --list` You should see `tcp:5005 jdwp:<your_pid>`. If the PID column is empty or says 0, the forward failed.
* `adb jdwp` This lists all PIDs currently eligible for debugging.

**The "Waiting For Debugger" Catch-22**

Sometimes the `am set-debug-app -w` flag holds the process so tightly that `adb forward` fails to "hook" in time. Try this sequence:
1.  **Kill current session:** `adb shell am force-stop com.reveila.android`
2.  **Clear debug app:** `adb shell am clear-debug-app` (just to reset)
3.  **Set debug app again:** `adb shell am set-debug-app -w com.reveila.android`
4.  **Launch app** manually on the phone.
5.  **Run the forward command** immediately while the dialog is visible.
6.  **Click "Attach" in VS Code.**

---

## 🪲 Troubleshooting

### "Cannot connect to Metro"

If the app opens but shows a red error screen saying it cannot connect:
1. Ensure your terminal is running the bundler (or run `npx expo start`).
2. Re-run the bridge command: `adb reverse tcp:8081 tcp:8081`.

### Build Failures or Inconsistant Code Observed

Try a deep clean:

```bash

# In both apps/expo/Reveila/android and /android (native lib)
./gradlew clean

# Run Expo prebuild
npm run prebuild

```

### Device Not Found

If `adb devices` shows your device as `unauthorized`:
1. Check your phone/emulator screen.
2. Accept the "Allow USB Debugging?" prompt.
3. Kill ADB: `adb kill-server` followed by `adb start-server`.
4. Clear Port 5005: Run `Stop-Process -Id (Get-NetTCPConnection -LocalPort 5005).OwningProcess -Force` in PowerShell (if it returns an error, the port is already clear).
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
