# Configuring the remote API endpoint for the Android app

There are two primary ways to configure the remote API endpoint for the Android app, depending on whether you want a static or dynamic configuration:

### 1. Static Asset Configuration (Recommended for Development)
You can configure the endpoint directly in the component manifest within the app's assets. 
*   **File Location**: `system-home\android\configs\components\android.json`
*   **Configuration**: Add or update a component that uses `com.reveila.service.RemoteService` and set the `RemoteURLs` argument.
    * *Example snippet*:
      ```json
      {
        "component": {
          "name": "remote.reveila",
          "class": "com.reveila.service.RemoteService",
          "version": "1",
          "//comment1": "To set up a cluster of Reveila instances, add the instance's base URL to the value array of the RemoteURLs argument.",
          "//comment2": "The URL is followed by a comma and priority as an integer (lower value means higher priority).",
          "arguments": [
            {
              "name": "RemoteURLs",
              "type": "java.lang.String",
              "value": [
                "http://10.0.2.2:8080, 1"
              ]
            }
          ]
        }
      }
      ```

### 2. Build-Time Dynamic Overwrite (Recommended for Production/CI)
You can inject a remote properties URL during the build process.
*   **File Location**: `android/build.gradle.kts`
*   **Configuration**: Update the `REVEILA_PROPERTIES_URL` build-config field.
    *   *Example*: `buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"https://api.yourdomain.com/configs/mobile.properties\"")`
*   **Behavior**: On startup, `ReveilaService.java` will attempt to fetch this remote properties file. If successful, it will overwrite any local properties (like `system.name` or custom component settings) with the values from the remote server.

### 3. Google OAuth Setup (For Execution Tracking)
The Android app requires Google authentication to provide a `Subject` for component invocations.
*   **Registration**: You must register the app in the [Google Cloud Console](https://console.cloud.google.com/).
*   **Credentials**:
    *   Create an **Android Client ID** using package name `com.reveila.android` and your certificate SHA-1.
    *   Create a **Web Client ID** and copy it into `apps/expo/Reveila/app/(tabs)/index.tsx`.
*   **File**: Download `google-services.json` and place it in `apps/expo/Reveila/`.
*   **Native Bridge**: The `ReveilaModule.java` uses the **Android Credential Manager API** to handle the sign-in and synchronize the identity with the core engine.

### Note on Connectivity
When using a local development server with the Android Simulator, remember to use:
*   `http://10.0.2.2:8080` (Standard Android alias for the host machine's localhost).
*   Or your machine's specific local IP (e.g., `192.168.x.x`) if testing on a physical device over the same Wi-Fi.