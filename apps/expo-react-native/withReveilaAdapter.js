const { withSettingsGradle } = require('@expo/config-plugins');

module.exports = (config) => {
  return withSettingsGradle(config, (config) => {
    // Ensure the lines aren't already there
    if (!config.modResults.contents.includes("include ':reveila'")) {
      config.modResults.contents += `
include ':reveila'
project(':reveila').projectDir = new File('../../../reveila')
`;
    }
    if (!config.modResults.contents.includes("include ':reveila-android-adapter'")) {
      config.modResults.contents += `
include ':reveila-android-adapter'
project(':reveila-android-adapter').projectDir = new File('../../../adapters/reveila-android-adapter')
`;
    }
    return config;
  });
};
