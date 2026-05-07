const { 
  withSettingsGradle, 
  withAppBuildGradle, 
  withMainApplication, 
  withAndroidManifest 
} = require('@expo/config-plugins');
const path = require('node:path');

/**
 * withReveila - Headless Engine Integration
 * This version treats the Java backend as a "Sidecar" and removes all 
 * aggressive Kotlin/Gradle customizations to ensure portability.
 */
const withReveila = (config) => {

  // 1. Register Native Module Package in MainApplication.kt
  config = withMainApplication(config, (config) => {
    let contents = config.modResults.contents;
    if (!contents.includes('ReveilaPackage()')) {
      contents = contents.replace(
        /PackageList\(this\)\.packages\.apply\s?{/,
        `PackageList(this).packages.apply {\n              add(com.reveila.android.ReveilaPackage())`
      );
    }
    config.modResults.contents = contents;
    return config;
  });

  // 2. Manifest declarations (Service, Receiver, Permissions)
  config = withAndroidManifest(config, (config) => {
    const mainManifest = config.modResults;
    const permissions = [
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
      "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
    ];

    if (!mainManifest.manifest["uses-permission"]) {
      mainManifest.manifest["uses-permission"] = [];
    }

    permissions.forEach(permission => {
      if (!mainManifest.manifest["uses-permission"].some(p => p.$["android:name"] === permission)) {
        mainManifest.manifest["uses-permission"].push({ $: { "android:name": permission } });
      }
    });

    const application = mainManifest.manifest.application[0];
    
    // Background Service
    if (!application.service) application.service = [];
    if (!application.service.some(s => s.$["android:name"] === "com.reveila.android.ReveilaService")) {
      application.service.push({
        $: {
          "android:name": "com.reveila.android.ReveilaService",
          "android:exported": "false",
          "android:foregroundServiceType": "specialUse|dataSync"
        }
      });
    }

    // Restart Receiver (Sovereign Persistence)
    if (!application.receiver) application.receiver = [];
    if (!application.receiver.some(r => r.$["android:name"] === "com.reveila.android.RestartReceiver")) {
      application.receiver.push({
        $: {
          "android:name": "com.reveila.android.RestartReceiver",
          "android:exported": "false"
        },
        "intent-filter": [{
          action: [{ $: { "android:name": "reveila.action.RESTART_SERVICE" } }]
        }]
      });
    }

    // Native Setup Activity
    if (!application.activity) application.activity = [];
    if (!application.activity.some(a => a.$["android:name"] === "com.reveila.android.SovereignSetupActivity")) {
      application.activity.push({
        $: {
          "android:name": "com.reveila.android.SovereignSetupActivity",
          "android:exported": "true",
          "android:theme": "@style/Theme.AppCompat.Light.NoActionBar"
        }
      });
    }

    return config;
  });

  // 3. Project Structural Mapping (settings.gradle)
  config = withSettingsGradle(config, (config) => {
    let contents = config.modResults.contents;
    // Resolve the mono-repo root path (standardized for Windows)
    const rootDir = path.resolve(config.modRequest.projectRoot, "../../../").replaceAll('\\', '/');

    const projectInclusions = `
// [Reveila Native Bridge - Sovereign Sidecar]
include ':android'
project(':android').projectDir = new File('${rootDir}/android')
`;

    if (!contents.includes("include ':android'")) {
      contents += projectInclusions;
    }

    config.modResults.contents = contents;
    return config;
  });

  // 4. Link Implementation Dependency (app/build.gradle)
  config = withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // Clean up specific SDK conflicts
    if (contents.includes("enableBundleCompression")) {
      contents = contents.replace(/enableBundleCompression\s?=\s?.*?\n/, "// Property removed for SDK 54 stability\n");
    }

    // Standard Implementation Link
    if (!contents.includes("project(':android')")) {
      contents = contents.replace(/dependencies\s?{/, `dependencies {\n    implementation project(':android')`);
    }

    config.modResults.contents = contents;
    return config;
  });

  return config;
};

module.exports = withReveila;