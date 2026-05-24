// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\ReveilaBridge.js
import { NativeModules } from 'react-native';

const { ReveilaModule } = NativeModules;

export const ReveilaBridge = {
    /**
     * Starts the long-running Android foreground service container
     */
    startService: async (systemHome = null) => {
        try {
            return await ReveilaModule.startService(systemHome);
        } catch (e) {
            console.error("[Bridge Intent Failure]:", e);
            return false;
        }
    },

    /**
     * Safe static liveness check. Queries class-level properties 
     * instantly to remove the risk of cross-thread network timeouts.
     */
    getEngineStatus: async () => {
        try {
            return await ReveilaModule.getEngineStatus();
        } catch (e) {
            console.error("[Reveila Engine Status Check Failure]:", e);
            return { initialized: false, status: "OFFLINE" };
        }
    },

    /**
     * Primary JSON Gateway Router. Serializes complex payload properties
     * straight down to the thin Java routing kernel.
     */
    invoke: async (component, method, params = []) => {
        const envelope = JSON.stringify({ component, method, params });
        const responseString = await ReveilaModule.sendCommand(envelope);
        return JSON.parse(responseString);
    }
};