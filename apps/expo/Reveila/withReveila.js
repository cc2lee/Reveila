const { withSettingsGradle, withProjectBuildGradle, withAppBuildGradle, withMainApplication, withAndroidManifest } = require('@expo/config-plugins');
const path = require('path');

const withReveila = (config) => {
  // 0. Register Native Module Package (MainApplication.kt)
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

  // 0.1 Manifest declarations (Service, Receiver, Permissions)
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

    return config;
  });

  // 1. Structural & Version Catalog Fixes (settings.gradle)
  config = withSettingsGradle(config, (config) => {
    let contents = config.modResults.contents;
    // Resolve the mono-repo root path
    const rootDir = path.resolve(config.modRequest.projectRoot, "../../../").replace(/\\/g, '/');

    // A. Ensure repositories are set up correctly in pluginManagement
    if (!contents.includes('google()')) {
        contents = contents.replace(
            /pluginManagement\s?{/,
            `pluginManagement {\n  repositories {\n    google()\n    mavenCentral()\n    gradlePluginPortal()\n  }`
        );
    } else if (contents.indexOf('google()') > contents.indexOf('dependencyResolutionManagement')) {
        // If google() only appears after DRM, we still need it in pluginManagement
        contents = contents.replace(
            /pluginManagement\s?{/,
            `pluginManagement {\n  repositories {\n    google()\n    mavenCentral()\n    gradlePluginPortal()\n  }`
        );
    }

    // B. Inject dependencyResolutionManagement and Version Catalog
    const drmBlock = `
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
  versionCatalogs {
    libs {
      from(files("${rootDir}/gradle/libs.versions.toml"))
    }
  }
}
`;
    if (!contents.includes('versionCatalogs {')) {
        // Try to find a good spot: after plugins block or at the end
        if (contents.includes('plugins {')) {
            contents = contents.replace(/plugins\s?{[\s\S]*?}/, (match) => `${match}\n${drmBlock}`);
        } else {
            contents += drmBlock;
        }
    }

    // C. Monorepo Project Inclusions
    const projectInclusions = `
// [Reveila Native Bridge]
includeBuild('${rootDir}/build-logic')
include ':reveila:core'
project(':reveila:core').projectDir = new File('${rootDir}/reveila/core')
include ':android'
project(':android').projectDir = new File('${rootDir}/android')
`;
    if (!contents.includes("include ':reveila:core'")) {
        contents += projectInclusions;
    }

    config.modResults.contents = contents;
    return config;
  });

  // 2. Duplicate Resource Collision Fix (app/build.gradle)
  config = withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // Add Packaging Options to resolve META-INF duplicate collisions
    const packagingOptions = `
    packagingOptions {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/INDEX.LIST"
        }
    }
`;
    if (!contents.includes("META-INF/DEPENDENCIES")) {
        if (contents.includes("packagingOptions {")) {
             contents = contents.replace(/packagingOptions\s?{/, `packagingOptions {\n        resources {\n            excludes += "META-INF/DEPENDENCIES"\n            excludes += "META-INF/LICENSE*"\n            excludes += "META-INF/NOTICE*"\n            excludes += "META-INF/INDEX.LIST"\n        }`);
        } else {
            contents = contents.replace(/android\s?{/, `android {${packagingOptions}`);
        }
    }

    // Add native dependency
    if (!contents.includes("project(':android')")) {
      contents = contents.replace(/dependencies\s?{/, `dependencies {\n    implementation project(':android')`);
    }

    config.modResults.contents = contents;
    return config;
  });

  return config;
};

module.exports = withReveila;
