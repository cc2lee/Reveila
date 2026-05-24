const { 
  withAppBuildGradle, 
  withMainApplication, 
  withAndroidManifest 
} = require('@expo/config-plugins');

/**
 * withReveila - AAR Injection Model
 * Decouples the Engine from the Shell. 
 * Expects a pre-built AAR in the central suite repository: [root]/build/outputs/android/reveila-android-core.aar
 */
const withReveila = (config) => {

  // 1. Register Native Module Package inside MainApplication
  config = withMainApplication(config, (config) => {
    let contents = config.modResults.contents;
    if (!contents.includes('ReveilaPackage()')) {
      contents = contents.replace(
        /PackageList\(this\)\.packages\.apply\s?{/,
        `PackageList(this).packages.apply {\n          add(com.reveila.android.ReveilaPackage())`
      );
    }
    config.modResults.contents = contents;
    return config;
  });

  // 2. Manifest Declarations (Permissions & Services)
  config = withAndroidManifest(config, (config) => {
    const mainManifest = config.modResults;
    const permissions = [
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
      "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
    ];

    if (!mainManifest.manifest["uses-permission"]) mainManifest.manifest["uses-permission"] = [];
    
    permissions.forEach(permission => {
      if (!mainManifest.manifest["uses-permission"].some(p => p.$["android:name"] === permission)) {
        mainManifest.manifest["uses-permission"].push({ $: { "android:name": permission } });
      }
    });

    const application = mainManifest.manifest.application[0];
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

    return config;
  });

  // 3. Inject AAR via Flat Directory (app/build.gradle Groovy Injection)
  config = withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // Relative path calculation stepping up from: apps/expo/Reveila/android/app/
    // 1: app -> 2: android -> 3: Reveila -> 4: expo -> 5: apps -> (now at Reveila-Suite root) -> down to build/outputs/android
    const aarPath = "../../../../../build/outputs/android";

    const repoBlock = `
repositories {
    flatDir {
        dirs "${aarPath}"
    }
}
`;

    // Add FlatDir Repo block directly above the main android configuration entry if missing
    if (!contents.includes('flatDir')) {
      contents = contents.replace(/^android\s?{/m, `${repoBlock}\nandroid {`);
    }

    // Use native Groovy syntax to bind the re-aligned archive target name
    if (!contents.includes("name: 'reveila-android-core'")) {
      contents = contents.replace(
        /dependencies\s?{/, 
        `dependencies {\n    implementation name: 'reveila-android-core', ext: 'aar'`
      );
    }

    config.modResults.contents = contents;
    return config;
  });

  return config;
};

module.exports = withReveila;