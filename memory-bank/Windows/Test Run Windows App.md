From [build.gradle.kts](file:///c:/IDE/Projects/Reveila-Suite/apps/Aegin/build.gradle.kts#L224-L307), you have three ways to test-run the Windows desktop app:

1. **Quickest dev run (directly via Gradle):**
   ```powershell
   .\gradlew :apps:Aegin:run
   ```
   This launches `com.reveila.aegin.MainKt` with `-Dsystem.home` automatically pointed at the slim `system-home`.

2. **Portable slim Windows build (`Aegin.bat`):**
   ```powershell
   .\gradlew :apps:Aegin:packageSlimWindowsDist
   ```
   Then run `apps\Aegin\build\distributions\aegin-windows-slim\Aegin.bat`.

3. **Native Windows `.exe` / `.msi` package:**
   ```powershell
   .\gradlew :apps:Aegin:packageExe
   ```
   (Output is generated under `apps\Aegin\build\compose\binaries\main\exe\`.)
