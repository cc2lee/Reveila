const { withSettingsGradle, withProjectBuildGradle, withAppBuildGradle, withMainApplication, withAndroidManifest, withGradleProperties } = require('@expo/config-plugins');
const path = require('path');

const withReveila = (config) => {
  // -1. Force Kotlin Version for compatibility with Room 2.6.1
  config = withGradleProperties(config, (config) => {
    config.modResults = config.modResults.map(prop => {
        if (prop.key === 'expo.kotlinVersion') {
            return { ...prop, value: '2.0.21' };
        }
        return prop;
    });
    // Add if not present
    if (!config.modResults.some(prop => prop.key === 'expo.kotlinVersion')) {
        config.modResults.push({ type: 'property', key: 'expo.kotlinVersion', value: '2.0.21' });
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
    if (!contents.includes('kotlinVersion =')) {
        contents = contents.replace(/buildscript\s?{/, "buildscript {\n    ext.kotlinVersion = '2.0.21'");
    } else {
        contents = contents.replace(/kotlinVersion\s?=\s?['"].*?['"]/, "kotlinVersion = '2.0.21'");
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
