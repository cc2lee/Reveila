const { withSettingsGradle, withProjectBuildGradle, withAppBuildGradle, withMainApplication, withAndroidManifest, withGradleProperties } = require('@expo/config-plugins');
const path = require('path');

const withReveila = (config) => {
  // -1. Force Kotlin Version for compatibility with Room 2.6.1
  config = withGradleProperties(config, (config) => {
    config.modResults = config.modResults.map(prop => {
      if (prop.key === 'expo.kotlinVersion') {
        return { ...prop, value: '2.1.20' };
      }
      return prop;
    });
    // Add if not present
    if (!config.modResults.some(prop => prop.key === 'expo.kotlinVersion')) {
      config.modResults.push({ type: 'property', key: 'expo.kotlinVersion', value: '2.1.20' });
    }
    return config;
  });

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

  // 1. Structural & Version Catalog Fixes (settings.gradle)
  config = withSettingsGradle(config, (config) => {
    let contents = config.modResults.contents;
    const rootDir = path.resolve(config.modRequest.projectRoot, "../../../").replace(/\\/g, '/');

    // A. Define the Sovereign Plugin Management block with explicit Repositories
    const pluginManagementBlock = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.android") version "2.1.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
        id("com.google.devtools.ksp") version "2.1.20-2.0.1"
    }
`;

    // Wholesale replacement of the pluginManagement block
    if (contents.includes('pluginManagement')) {
      // This regex captures the existing pluginManagement block start to its first major closing brace
      contents = contents.replace(/pluginManagement\s?{[\s\S]*?}/, (match) => {
        // If the match looks like it's missing our plugins, replace it entirely
        return pluginManagementBlock;
      });
    } else {
      contents = pluginManagementBlock + "\n}\n" + contents;
    }

    // B. Inject DRM after the pluginManagement block
    const drmBlock = `
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  versionCatalogs {
    libs {
      from(files("${rootDir}/gradle/libs.versions.toml"))
    }
  }
}
`;
    if (!contents.includes('versionCatalogs {')) {
      // Anchor it before the rootProject.name
      contents = contents.replace(/rootProject.name/, (match) => `${drmBlock}\n${match}`);
    }

  // C. Monorepo Project Inclusions
  const projectInclusions = `
// [Reveila Native Bridge]
includeBuild('${rootDir}/build-logic')
include ':reveila:core'
project(':reveila:core').projectDir = new File(rootProject.projectDir, '../../../../reveila/core')
include ':android'
project(':android').projectDir = new File(rootProject.projectDir, '../../../../android')
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
            pickFirsts += "reveila/**"
        }
    }
`;
  if (!contents.includes("META-INF/DEPENDENCIES")) {
    if (contents.includes("packagingOptions {")) {
      contents = contents.replace(/packagingOptions\s?{/, `packagingOptions {\n        resources {\n            excludes += "META-INF/DEPENDENCIES"\n            excludes += "META-INF/LICENSE*"\n            excludes += "META-INF/NOTICE*"\n            excludes += "META-INF/INDEX.LIST"\n            pickFirsts += "reveila/**"\n        }`);
    } else {
      contents = contents.replace(/android\s?{/, `android {${packagingOptions}`);
    }
  }

  // REMOVE THE UNSUPPORTED PROPERTY
  if (contents.includes("enableBundleCompression")) {
    // Comment out the property to prevent the "Unknown property" error
    contents = contents.replace(
      /enableBundleCompression\s?=\s?.*?\n/,
      "// enableBundleCompression is handled via extraPackagerArgs or deprecated\n"
    );
  }

  // Add native dependency
  if (!contents.includes("project(':android')")) {
    contents = contents.replace(/dependencies\s?{/, `dependencies {\n    implementation project(':android')`);
  }

  config.modResults.contents = contents;
  return config;
});

// 2.1 Build Fixes (root build.gradle)
config = withProjectBuildGradle(config, (config) => {
  let contents = config.modResults.contents;

  // Force Kotlin version at the buildscript level
  if (contents.includes('kotlinVersion =')) {
    // Logic Flipped: Update the existing version
    contents = contents.replace(/kotlinVersion\s?=\s?['"].*?['"]/, "kotlinVersion = '2.1.20'");

    // Flip the inner check for minSdkVersion
    if (contents.includes('minSdkVersion =')) {
      contents = contents.replace(/minSdkVersion\s?=\s?\d+/, "minSdkVersion = 26");
    } else {
      // If missing, append it after the kotlinVersion line
      contents = contents.replace(/ext\.kotlinVersion\s?=\s?.*?\n/, (match) => `${match}    ext.minSdkVersion = 26\n`);
    }
  } else {
    // If kotlinVersion is missing entirely, inject the full block
    contents = contents.replace(
      /buildscript\s?{/,
      "buildscript {\n    ext {\n        kotlinVersion = '2.1.20'\n        minSdkVersion = 26\n    }"
    );
  }

  // Ensure gradlePluginPortal() is in the buildscript repositories
  if (!contents.includes('gradlePluginPortal()')) {
    contents = contents.replace(
      /repositories\s?{/,
      `repositories {\n        gradlePluginPortal()`
    );
  }

  config.modResults.contents = contents;
  return config;
});

// 3. Shorten Build Path on Windows (app/build.gradle)
config = withAppBuildGradle(config, (config) => {
  let contents = config.modResults.contents;

  const buildDirShortener = `
    // [Reveila Path Fix] Shorten build directory on Windows to avoid MAX_PATH issues (CMake)
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
        def shortBuildDir = "C:/IDE/Projects/Reveila-Suite/build/expo"
        try {
            new File(shortBuildDir).mkdirs()
            if (new File(shortBuildDir).exists() && new File(shortBuildDir).canWrite()) {
                buildDir = "\${shortBuildDir}/\${project.name}"
                // Also redirect the CMake intermediate files (.cxx)
                externalNativeBuild {
                    cmake {
                        buildStagingDirectory = "\${shortBuildDir}/cmake/\${project.name}"
                    }
                }
                println "[Reveila] Using short build directory: \${buildDir}"
            }
        } catch (Exception e) {
            println "[Reveila] Warning: Could not use short build directory \${shortBuildDir}: \${e.message}"
        }
    }
`;

  if (!contents.includes("[Reveila Path Fix]")) {
    // Inject after the android block starts
    contents = contents.replace(/android\s?{/, `android {${buildDirShortener}`);
  }

  config.modResults.contents = contents;
  return config;
});

return config;
};

module.exports = withReveila;
