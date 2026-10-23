// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\ReveilaBridge.js
import { NativeModules, Platform } from 'react-native';

const { ReveilaModule } = NativeModules;

/**
 * ReveilaBridge: The Universal Runtime Interface between JavaScript / Expo applications
 * and the sovereign Reveila Core Engine (running as an Android Foreground Service).
 *
 * Universal Invocation Signature:
 *   invoke: async (component, method, params = []) => { ... }
 */
export const ReveilaBridge = {
    /**
     * Starts the long-running Android foreground service container.
     * Offloads engine initialization from the main thread.
     *
     * @param {string|null} systemHome Optional custom system.home path override
     * @returns {Promise<string|boolean>} The initialized home directory or false
     */
    startService: async (systemHome = null) => {
        if (Platform.OS !== 'android' || !ReveilaModule) {
            console.warn('[ReveilaBridge] startService is only supported on Android with native ReveilaModule.');
            return false;
        }
        try {
            return await ReveilaModule.startService(systemHome);
        } catch (e) {
            console.error('[ReveilaBridge Intent Failure]:', e);
            return false;
        }
    },

    /**
     * Safe static liveness check. Queries class-level properties 
     * instantly to remove the risk of cross-thread network timeouts.
     *
     * @returns {Promise<{ initialized: boolean, status: string }>}
     */
    getEngineStatus: async () => {
        if (!ReveilaModule || !ReveilaModule.getEngineStatus) {
            return { initialized: false, status: 'OFFLINE' };
        }
        try {
            return await ReveilaModule.getEngineStatus();
        } catch (e) {
            console.error('[Reveila Engine Status Check Failure]:', e);
            return { initialized: false, status: 'OFFLINE' };
        }
    },

    /**
     * Awaits engine startup readiness before triggering dependent service calls.
     *
     * @param {number} timeoutMs Maximum wait time in milliseconds (default: 30000)
     * @param {number} pollIntervalMs Polling frequency in milliseconds (default: 500)
     * @returns {Promise<boolean>} True if online, false if timed out
     */
    waitForEngine: async (timeoutMs = 30000, pollIntervalMs = 500) => {
        const startTime = Date.now();
        while (Date.now() - startTime < timeoutMs) {
            const status = await ReveilaBridge.getEngineStatus();
            if (status && status.initialized) {
                return true;
            }
            await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
        }
        return false;
    },

    /**
     * Primary JSON Gateway Router. Serializes complex payload properties
     * straight down to the thin Java routing kernel.
     *
     * Universal signature:
     *   invoke: async (component, method, params = []) => { ... }
     *
     * @param {string} component The target Reveila component or plugin name
     * @param {string} method The method to execute on the component
     * @param {any[]} params Method arguments array (default: [])
     * @returns {Promise<any>} The deserialized JSON result from the engine
     */
    invoke: async (component, method, params = []) => {
        if (!ReveilaModule || !ReveilaModule.sendCommand) {
            throw new Error(`[ReveilaBridge] Native ReveilaModule is unavailable on this platform (${Platform.OS}). Ensure the Android ReveilaService is running.`);
        }
        const normalizedParams = Array.isArray(params) ? params : [params];
        const envelope = JSON.stringify({ component, method, params: normalizedParams });
        const responseString = await ReveilaModule.sendCommand(envelope);
        try {
            return typeof responseString === 'string' ? JSON.parse(responseString) : responseString;
        } catch {
            return responseString;
        }
    },

    /**
     * Dynamic component proxy generator.
     * Enables AI code agents and UI views to invoke any service dynamically like native JS:
     *   const agentic = ReveilaBridge.createProxy('AgenticFabric');
     *   const answer = await agentic.askAgent(prompt, sessionId);
     *
     * @param {string} componentName The component or plugin name
     * @returns {object} Dynamic proxy delegating method calls to invoke(...)
     */
    createProxy: (componentName) => {
        return new Proxy({}, {
            get: (_, methodName) => {
                return async (...args) => {
                    return ReveilaBridge.invoke(componentName, methodName, args);
                };
            }
        });
    },

    /**
     * Native biometric authentication capability.
     *
     * @returns {Promise<boolean>}
     */
    authenticateBiometric: async () => {
        if (!ReveilaModule || !ReveilaModule.authenticateBiometric) {
            return false;
        }
        return await ReveilaModule.authenticateBiometric();
    },

    /**
     * Hardware keystore encrypted data persistence.
     *
     * @param {string} data Secret data to persist
     */
    saveSensitiveData: (data) => {
        if (ReveilaModule && ReveilaModule.saveSensitiveData) {
            ReveilaModule.saveSensitiveData(data);
        }
    },

    /**
     * Emergency killswitch triggering.
     *
     * @returns {Promise<boolean>}
     */
    triggerEmergencyStop: async () => {
        if (!ReveilaModule || !ReveilaModule.triggerEmergencyStop) {
            return false;
        }
        return await ReveilaModule.triggerEmergencyStop();
    }
};

export default ReveilaBridge;