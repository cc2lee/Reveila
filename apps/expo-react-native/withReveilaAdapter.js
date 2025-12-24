const {
  withSettingsGradle,
  withMainActivity,
  withDangerousMod,
} = require("@expo/config-plugins");
const {
  addImports,
  insertContents,
} = require("@expo/config-plugins/build/android/codeMod");
const path = require("path");
const fs = require("fs");

module.exports = (config) => {
  // First, apply the settings.gradle modifications
  config = withSettingsGradle(config, (config) => {
    // Ensure the lines aren't already there
    if (!config.modResults.contents.includes("include ':reveila'")) {
      config.modResults.contents += `
include ':reveila'
project(':reveila').projectDir = new File('../../../reveila')
`;
    }
    if (!config.modResults.contents.includes("include ':android'")) {
      config.modResults.contents += `
include ':android'
project(':android').projectDir = new File('../../../adapters/android')
`;
    }
    return config;
  });

  // Then, modify MainActivity.java
  config = withMainActivity(config, (mod) => {
    let contents = mod.modResults.contents;

    // Add import for FontLoaderModule
    contents = addImports(
      contents,
      ["expo.modules.font.FontLoaderModule"],
      "expo.modules.font.FontLoaderModule"
    );

    // Add font loading call
    contents = insertContents(
      contents,
      "super.onCreate(savedInstanceState);",
      `
    // Fonts
    new FontLoaderModule().loadFonts(this.getApplicationContext());
    `
    );

    mod.modResults.contents = contents;
    return mod;
  });

  // Finally, copy the font file
  config = withDangerousMod(config, [
    "android",
    (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const androidRoot = config.modRequest.platformProjectRoot;

      const fontSourceDir = path.join(projectRoot, "assets", "fonts");
      const fontSourceFile = path.join(fontSourceDir, "inter.ttf");

      const fontDestDir = path.join(
        androidRoot,
        "app",
        "src",
        "main",
        "res",
        "font"
      );
      const fontDestFile = path.join(fontDestDir, "inter.ttf");

      // Ensure destination directory exists and copy font if it exists
      if (fs.existsSync(fontSourceFile)) {
        if (!fs.existsSync(fontDestDir)) {
          fs.mkdirSync(fontDestDir, { recursive: true });
        }
        fs.copyFileSync(fontSourceFile, fontDestFile);
      }

      return config;
    },
  ]);

  return config;
};
