// C:\IDE\Projects\Reveila-Suite\ts\packages\services\ReveilaService.ts
import { NativeModules, Platform } from 'react-native';

// Safely pull the correct registered identifier string verified by Java's getName()
const { ReveilaModule } = NativeModules;

// Type definition for standard Engine responses
export interface EngineResponse<T = any> {
    success: boolean;
    data?: T;
    error?: string;
}

/**
 * Sovereign Gateway Service
 * Encapsulates all communication with the Headless Java Engine.
 */
export const ReveilaService = {
    /**
     * Generic command dispatcher mapping frontend actions to enterprise JSON envelopes
     */
    async sendCommand<T = any>(action: string, params: any[] = []): Promise<EngineResponse<T>> {
        if (Platform.OS !== 'android') {
            return { success: false, error: 'Engine only available on Android' };
        }

        // Defensive guard: Handle dynamic startup/recovery delays gracefully
        if (!ReveilaModule) {
            return { 
                success: true, 
                data: { initialized: false, status: "INITIALIZING_BRIDGE" } as any 
            };
        }

        try {
            // Re-map the payload structure to align perfectly with Jackson mapper bindings inside ReveilaModule.java
            const commandEnvelope = JSON.stringify({
                component: "core", // Standard component targeting
                method: action,    // Maps cleanly to your engine command strings (e.g., 'GET_STATUS')
                params: params     // Sent explicitly as an array list to bind cleanly to Object[] params
            });

            const rawResponse = await ReveilaModule.sendCommand(commandEnvelope);
            return JSON.parse(rawResponse);
        } catch (e: any) {
            console.error(`[ReveilaService] Command ${action} failed:`, e);
            return { success: false, error: e.message || 'Unknown execution bridge exception' };
        }
    },

    /**
     * Polled helper tracking runtime liveness metrics
     */
    async getStatus(): Promise<EngineResponse> {
        return this.sendCommand('GET_STATUS');
    },

    /**
     * Helper handling Biometric Authentication transitions (UI Thread Main Looper operations)
     */
    async authenticateBiometric(): Promise<boolean> {
        if (Platform.OS !== 'android') {
            throw new Error('Biometric operations require an active Android hardware context');
        }

        if (!ReveilaModule) {
            console.warn("[ReveilaService] Biometric sequence deferred: Native module bridge is offline.");
            return false;
        }

        return await ReveilaModule.authenticateBiometric();
    }
};