// Reexport the native module. On web, it will be resolved to ReveilaModule.web.ts
// and on native platforms to ReveilaModule.ts
export { default } from './src/ReveilaModule';
export { default as ReveilaView } from './src/ReveilaView';
export * from  './src/Reveila.types';
