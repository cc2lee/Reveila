// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\index.js
import { registerRootComponent } from 'expo';
import { ExpoRoot } from 'expo-router';
import { useEffect } from 'react';
import { Platform } from 'react-native';
import { ReveilaBridge } from './ReveilaBridge';

// 1. Initialize Expo Router and handle the background engine lifecycle
export function App() {
    useEffect(() => {
        // Safeguard against web/iOS compilation targets
        if (Platform.OS !== 'android') return;

        console.log("[Reveila App] Requesting native background engine activation via ReveilaBridge...");
        
        // Fired non-blocking as a traditional promise chain instead of top-level async await.
        // This lets the main Expo thread drop straight through to render the routing layout context.
        ReveilaBridge.startService()
            .then((homePathHandshake) => {
                console.log(`[Reveila App] Foreground service active. Home: ${homePathHandshake}`);
            })
            .catch((error) => {
                console.error("[Reveila App] Failed to initialize background service context:", error);
            });
            
    }, []);

    // Tell Expo Router to scan your "/app" folder dynamically
    const ctx = require.context('./app');
    return <ExpoRoot context={ctx} />;
}

// 2. Register this layout engine as the native entry point
registerRootComponent(App);