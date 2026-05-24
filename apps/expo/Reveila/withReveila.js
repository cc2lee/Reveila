// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\withReveila.js
const { withMainApplication, withAppBuildGradle, withSettingsGradle } = require('@expo/config-plugins');

function withReveilaNative(config) {

    // 1. Intercept Expo's settings layout to map convention engines, repositories, and the Version Catalog
    config = withSettingsGradle(config, (config) => {
        let contents = config.modResults.contents;

        // A. Inject the composite build-logic hook directly inside the pre-existing top-level pluginManagement block
        const buildLogicLine = `
    includeBuild('C:/IDE/Projects/Reveila-Suite/build-logic')
    repositories {
        google()
        mavenCentral()
    }
`;
        if (!contents.includes('build-logic')) {
            contents = contents.replace(
                /pluginManagement\s*\{/,
                `pluginManagement {${buildLogicLine}`
            );
        }

        // B. Inject the Version Catalog configuration block immediately AFTER the closed pluginManagement block
        const versionCatalogInjection = `
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("C:/IDE/Projects/Reveila-Suite/gradle/libs.versions.toml"))
        }
    }
}
`;
        // We locate the start of the file structure to place dependency resolution safely below initialization blocks
        if (!contents.includes('libs.versions.toml')) {
            // Find the first occurrence of the root project assignment or an include statement to splice in safely
            if (contents.includes("rootProject.name")) {
                contents = contents.replace("rootProject.name", `${versionCatalogInjection}\nrootProject.name`);
            } else {
                // Fallback: Append it to the very end if structural positions are unmappable
                contents += versionCatalogInjection;
            }
        }

        // C. Define explicit absolute file paths for your business logic and native layer
        const projectLinkageString = `
include ':reveila-core'
project(':reveila-core').projectDir = new File('C:/IDE/Projects/Reveila-Suite/reveila/core')

include ':reveila-android'
project(':reveila-android').projectDir = new File('C:/IDE/Projects/Reveila-Suite/android')
`;

        // Append project links to the end of the file if not present
        if (!contents.includes("':reveila-android'")) {
            contents += projectLinkageString;
        }

        config.modResults.contents = contents;
        return config;
    });

    // 2. Inject the compiled native library into the master app/build.gradle
    config = withAppBuildGradle(config, (config) => {
        const dependencyTarget = "implementation project(':reveila-android')";
        if (!config.modResults.contents.includes(dependencyTarget)) {
            config.modResults.contents = config.modResults.contents.replace(
                /dependencies\s*\{/,
                `dependencies {\n    ${dependencyTarget}`
            );
        }
        return config;
    });

    // 3. Import and map the package constructor inside MainApplication.kt
    config = withMainApplication(config, (config) => {
        let contents = config.modResults.contents;
        
        // A. Inject the exact package location header match
        if (!contents.includes('import com.reveila.android.ReveilaPackage')) {
            contents = contents.replace(
                /package com\.reveila\.app/,
                `package com.reveila.app\n\nimport com.reveila.android.ReveilaPackage`
            );
        }
        
        // B. FIXED: Safely target the apply-closure matching your actual MainApplication.kt format
        // This targets "PackageList(this).packages.apply {" and adds your instantiation right below it
        const packageApplyBlock = /PackageList\(this\)\.packages\.apply\s*\{/;
        if (contents.match(packageApplyBlock) && !contents.includes('add(ReveilaPackage())')) {
            contents = contents.replace(
                packageApplyBlock,
                `PackageList(this).packages.apply {\n              add(ReveilaPackage())`
            );
        }
        
        config.modResults.contents = contents;
        return config;
    });

    return config;
}

module.exports = withReveilaNative;