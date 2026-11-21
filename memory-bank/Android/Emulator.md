To start an Android Virtual Device (AVD) from PowerShell, navigate to your Android SDK's `emulator` directory and run the `emulator` executable with the `-avd` flag.

### 1. List your available emulators

Run this to find the exact name of the virtual device you want to launch:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -list-avds

```

### 2. Start the emulator

Replace `<Your_AVD_Name>` with the name returned from the previous command:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <Your_AVD_Name>

```

---

**Useful Options & Tips:**

* **Run in the background (detached):** If you don't want the emulator tied to your current PowerShell session, wrap it with `Start-Process`:
```powershell
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd <Your_AVD_Name>"

```


* **Cold boot (ignore saved snapshot):** Add `-no-snapshot-load` if the emulator is frozen or behaving unexpectedly:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <Your_AVD_Name> -no-snapshot-load

```


* **Custom SDK path:** If you installed the Android SDK in a non-default location, replace `$env:LOCALAPPDATA\Android\Sdk` with your `$env:ANDROID_HOME` path (or add the `emulator` folder to your System `PATH` so you can simply run `emulator -avd <Your_AVD_Name>` from anywhere).