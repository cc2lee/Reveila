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
    setVaultFolder: (folderPath: string) => Promise<{ folder: string; filesScanned: number; entitiesFound: number; status: string }>;
    getVaultFolder: () => Promise<{ folder: string; focusKeywords: string }>;
    scanVault: () => Promise<{ folder: string; filesScanned: number; entitiesFound: number; status: string }>;
    getVaultStats: () => Promise<{ documentCount: number; chunkCount: number; folder: string }>;
}

export declare const ReveilaBridge: ReveilaBridgeType;
export default ReveilaBridge;
