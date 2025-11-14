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
    if (!config.modResults.contents.includes("include ':android'")) {
      config.modResults.contents += `
include ':android'
project(':android').projectDir = new File('../../../adapters/android')
`;
    }
    return config;
  });
};
