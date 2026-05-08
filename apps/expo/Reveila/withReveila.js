const { 
  withAppBuildGradle, 
  withMainApplication, 
  withAndroidManifest 
} = require('@expo/config-plugins');

/**
 * withReveila - AAR Injection Model
 * Decouples the Engine from the Shell. 
 * Expects a pre-built AAR in: [root]/android/build/outputs/aar/android-release.aar
 */
const withReveila = (config) => {

  // 1. Register Native Module Package
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

  // 3. Inject AAR via Flat Directory (app/build.gradle)
  config = withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // Use a relative path from the generated 'android/app' folder back to the engine build output
    const aarPath = "../../../android/build/outputs/aar";

    const repoBlock = `
repositories {
    flatDir {
        dirs "${aarPath}"
    }
}
`;

    // Add FlatDir Repo if missing
    if (!contents.includes('flatDir')) {
      contents = contents.replace(/android\s?{/, `${repoBlock}\nandroid {`);
    }

    // Add the AAR dependency
    // This looks for 'android-release.aar' in the flatDir specified above
    if (!contents.includes("name: 'android-release'")) {
      contents = contents.replace(/dependencies\s?{/, `dependencies {\n    implementation(name: 'android-release', ext: 'aar')`);
    }

    config.modResults.contents = contents;
    return config;
  });

  return config;
};

module.exports = withReveila;