# Enable Compatibility mode for web targets

You can enable compatibility mode for your web application to ensure it works on all browsers out of the box. In this mode, modern browsers use the Wasm version, while older ones fall back to the JS version. This mode is achieved through cross-compilation for both the js and wasmJs targets.

To enable compatibility mode for your web application, run the following command in your terminal at the root of your project:

./gradlew composeCompatibilityBrowserDistribution