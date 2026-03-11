# Configuring the remote API endpoint for the Android app

There are two primary ways to configure the remote API endpoint for the Android app, depending on whether you want a static or dynamic configuration:

### 1. Static Asset Configuration (Recommended for Development)
You can configure the endpoint directly in the component manifest within the app's assets. 
*   **File Location**: `apps/expo/Reveila/assets/reveila/system/configs/components/system-components.json`
*   **Configuration**: Add or update a component that uses `com.reveila.service.ReveilaRemote` (or a similar remote service class) and set the `BaseURL` argument.
    *   *Example snippet*:
        ```json
        {
          "component": {
            "name": "remote.reveila",
            "class": "com.reveila.service.ReveilaRemote",
            "arguments": [
              {
                "name": "BaseURL",
                "type": "java.lang.String",
                "value": "http://YOUR_SERVER_IP:8080"
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

### Note on Connectivity
When using a local development server with the Android Simulator, remember to use:
*   `http://10.0.2.2:8080` (Standard Android alias for the host machine's localhost).
*   Or your machine's specific local IP (e.g., `192.168.x.x`) if testing on a physical device over the same Wi-Fi.