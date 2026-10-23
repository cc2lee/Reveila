// TypeScript typings for ReveilaBridge
export interface EngineStatus {
    initialized: boolean;
    status: 'ONLINE' | 'INITIALIZING_CORE' | 'OFFLINE' | string;
}

export interface ReveilaBridgeType {
    startService: (systemHome?: string | null) => Promise<string | boolean>;
    getEngineStatus: () => Promise<EngineStatus>;
    waitForEngine: (timeoutMs?: number, pollIntervalMs?: number) => Promise<boolean>;
    invoke: <T = any>(component: string, method: string, params?: any[]) => Promise<T>;
    createProxy: <T = any>(componentName: string) => T;
    authenticateBiometric: () => Promise<boolean>;
    saveSensitiveData: (data: string) => void;
    triggerEmergencyStop: () => Promise<boolean>;
}

export declare const ReveilaBridge: ReveilaBridgeType;
export default ReveilaBridge;
