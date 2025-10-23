// Reexport the native module. On web, it will be resolved to ReveilaExpoModule.web.ts
// and on native platforms to ReveilaExpoModule.ts
export { default } from './src/ReveilaExpoModule';
export { default as ReveilaExpoModuleView } from './src/ReveilaExpoModuleView';
export * from  './src/ReveilaExpoModule.types';
