### How to Publish App to Play Store

#### Option A — Local One-Command Publish
Add your local credentials to `local.properties` (git-ignored):
```properties
aegin.keystore.file=path/to/aegin-release.jks
aegin.keystore.password=YOUR_STORE_PASSWORD
aegin.key.alias=YOUR_KEY_ALIAS
aegin.key.password=YOUR_KEY_PASSWORD
aegin.play.serviceAccountJson=path/to/play-service-account.json
```
Then run:
```powershell
# Build & upload signed AAB to Play Console (internal track, draft)
.\gradlew :apps:Aegin:publishReleaseBundle -PaeginVersionCode=2 -PaeginVersionName=1.0.1

# Or publish directly as completed to a specific track (internal | alpha | beta | production)
.\gradlew :apps:Aegin:publishReleaseBundle -PaeginPlayTrack=internal -PaeginPlayReleaseStatus=COMPLETED
```

#### Option B — Automatic CI Publish (GitHub Actions)
Add these 5 repository secrets under **GitHub → Settings → Secrets and variables → Actions**:
- `AEGIN_KEYSTORE_BASE64` (base64-encoded `.jks` keystore)
- `AEGIN_KEYSTORE_PASSWORD`
- `AEGIN_KEY_ALIAS`
- `AEGIN_KEY_PASSWORD`
- `AEGIN_PLAY_SERVICE_ACCOUNT_JSON` (full JSON content of your Google Play Service Account key)

Then either push a tag (e.g. `git tag aegin-v1.0.1 && git push origin aegin-v1.0.1`) or trigger **Publish Aegin to Google Play** manually from the GitHub Actions tab.

*(Reminder: if `com.reveila.aegin` hasn't been uploaded to Play Console yet, run `.\gradlew :apps:Aegin:bundleRelease` once and upload `apps\Aegin\build\outputs\bundle\release\Aegin-release.aab` manually in Play Console first — after that, all subsequent releases can be fully automated.)*