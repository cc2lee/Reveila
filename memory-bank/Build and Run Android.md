# 📱 Build and Run (Mobile/Android)

This guide outlines the daily Android development workflow.

## Frequently Used Commands Quick Reference

```bash

# cd C:\IDE\Projects\Reveila-Suite>
./gradlew.bat clean bootJar

# cd C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila>
npm start

# cd C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila>
npm run android
npm run android -- --no-packager
npm run android -- --port 8081
cd android && ./gradlew installDebug

# Set up native debugging
adb shell am set-debug-app -w --persistent com.reveila.android
adb forward tcp:5005 "jdwp:$(adb shell pidof com.reveila.android)"
adb logcat | findstr reveila

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

## Test Local LLM on Android Device

The following script handles the environment setup, clears out old test data on the phone to make room, pushes the new files, and initiates the inference. It also included a **Port Forwarding** command at the end for testing the `llama-server` REST API.

**mobile-llm-test.bat**

```batch
@echo off
SETLOCAL EnableDelayedExpansion

:: --- CONFIGURATION ---
SET "BIN_PATH=C:\IDE\Projects\Reveila-Suite\deploy-binaries\llama-cli"
SET "MODEL_PATH=C:\dev\ai models\gemma-2b.gguf"
SET "PHONE_DIR=/data/local/tmp/reveila"
SET "PROMPT=Explain the role of an Enterprise Architect in 2 sentences."

echo ===========================================================
echo   REVEILA-SUITE: Sovereign Node Deployment
echo ===========================================================

:: 1. Check for Device
echo [*] Checking for Android device...
adb get-state >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [!] ERROR: No device detected via ADB. Connect your phone.
    pause
    exit /b
)

:: 2. Space Management (The "Clean" Phase)
echo [*] Clearing space in %PHONE_DIR%...
adb shell "rm -rf %PHONE_DIR%/*"

:: 3. Provisioning
echo [*] Pushing Sovereign Engine (llama-cli)...
adb push "%BIN_PATH%" %PHONE_DIR%/

echo [*] Pushing Sovereign Weights (gemma-2b.gguf)...
echo     (Note: This may take a minute depending on USB speed)
adb push "%MODEL_PATH%" %PHONE_DIR%/

echo [*] Setting executable permissions...
adb shell "chmod +x %PHONE_DIR%/llama-cli"

:: 4. Execution
echo ===========================================================
echo   STARTING LOCAL INFERENCE (Sovereign Pulse)
echo ===========================================================
echo Running: %PROMPT%
echo.

adb shell "%PHONE_DIR%/llama-cli -m %PHONE_DIR%/gemma-2b.gguf -p '%PROMPT%' -n 128"

echo.
echo ===========================================================
echo   POST-FLIGHT: Setting up REST Forwarding for tomorrow
echo ===========================================================
adb forward tcp:8080 tcp:8080
echo [*] ADB Port Forwarding active: localhost:8080 -> Phone:8080
echo [*] Deployment Successful.

pause
```

### **Architectural Notes for your Test Tomorrow:**

1.  **Storage Tip:** Since your phone is low on space, remember that the **Gemma 2B** model takes about **1.7GB**. If the `adb push` fails tomorrow, check if you have large 4K videos in your DCIM folder that can be moved to your Montgomery local server.
2.  **The "Static" Advantage:** Because we compiled this as a **Static Binary**, you don't need to worry about pushing `.so` libraries. This single file contains everything it needs to talk to the Linux kernel on the phone.
3.  **ADB Forwarding:** I included `adb forward` at the end. Tomorrow, when you switch from `llama-cli` to `llama-server`, your Windows browser will be able to hit `http://localhost:8080` and reach the phone's LLM immediately.

### **How to use it:**
1.  Save the code above as `reveila-deploy-test.bat`.
2.  Update the `BIN_PATH` and `MODEL_PATH` lines if your folders are named differently.
3.  Connect your phone and run the script.


To run a local LLM like **Gemma 2B** on an Android Virtual Device (AVD), you need to move beyond standard "app testing" specs. Since the emulator adds overhead, your settings must compensate for both the guest OS and the intensive LLM inference.

Here are the upgraded AVD settings for a "Sovereign Node" environment in 2026:

### ## 1. Memory & Storage (The "Critical" Upgrades)
The standard 2GB of RAM will cause immediate "Killed" errors. You need enough space for the **GGUF weights** plus the **KV Cache**.

* **RAM:** Set to **8GB (8192 MB)**.
    * *Why:* Android OS takes ~3GB. A 4-bit quantized 2B model takes ~1.6GB. The extra is for the context window and system stability.
* **Internal Storage:** Increase to **16GB (16384 MB)**.
    * *Why:* AVDs default to small partitions. You need space for the OS, your binary, and multiple model iterations (GGUF files).
* **Heap:** Increase to **512 MB** or higher.

### ## 2. Emulated Performance (The "Speed" Upgrades)
To prevent the inference from feeling like a slideshow, you must bridge the hardware gap.

* **Graphics:** Select **Hardware - GLES 2.0**.
    * *Why:* This offloads rendering to your PC’s GPU, freeing up the emulated CPU to focus entirely on the LLM's matrix multiplications.
* **CPU Cores:** Set to **4 or 6 Cores**.
    * *Why:* LLM inference is highly parallel. `llama.cpp` defaults to 4 threads; giving the AVD 6 cores allows 4 for the LLM and 2 for the background OS tasks.

---

### ## 3. Manual `config.ini` Hacks
Some settings aren't available in the standard Android Studio UI. You can find your AVD's `config.ini` (usually in `C:\Users\Charles Lee\.android\avd\Your_Device.avd/`) and add/edit these lines:

```ini
# Force a larger data partition if the UI limits you
data.dataPartition.size=16g

# Enable fast storage virtio (speeds up model loading)
disk.dataPartition.path=userdata-qemu.img

# Ensure the NPU/GPU features are exposed
hw.gpu.enabled=yes
hw.gpu.mode=host
```

### ## 4. The "Architect's" Optimization: ARM on x86
If you are running on a Windows PC (x86_64), running an ARM64 AVD will be **extremely slow** due to binary translation.

* **Recommendation:** For development and REST API testing, use an **x86_64 system image** (e.g., UpsideDownCake x86_64).
* **Impact:** You will need to compile a separate `llama-cli` for x86_64 to test in the emulator. Save the ARM64 build for your real physical device test tomorrow.

### ## Summary Checklist for your Demo
| Setting | Standard | LLM Optimized |
| :--- | :--- | :--- |
| **RAM** | 2 GB | **8 GB** |
| **Storage** | 2 GB | **16 GB** |
| **Cores** | 2 | **4-6** |
| **Graphics** | Automatic | **Hardware** |


