import { NativeModules, Platform } from '../../../apps/expo/Reveila/node_modules/react-native/types';

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
     * Generic command dispatcher
     */
    async sendCommand<T = any>(action: string, params: object = {}): Promise<EngineResponse<T>> {
        if (Platform.OS !== 'android') {
            return { success: false, error: 'Engine only available on Android' };
        }

        try {
            const command = JSON.stringify({ action, ...params });
            const rawResponse = await ReveilaModule.sendCommand(command);
            return JSON.parse(rawResponse);
        } catch (e: any) {
            console.error(`[ReveilaService] Command ${action} failed:`, e);
            return { success: false, error: e.message };
        }
    },

    /**
     * Example: Specialized helper for engine status
     */
    async getStatus() {
        return this.sendCommand('GET_STATUS');
    },

    /**
     * Helper for Biometric Auth (stays native because it's a UI prompt)
     */
    async authenticateBiometric(): Promise<{ success: boolean; error?: string }> {
        return await ReveilaModule.authenticateBiometric();
    }
};