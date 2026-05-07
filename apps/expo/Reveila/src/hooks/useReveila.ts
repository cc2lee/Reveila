import { useState, useEffect, useCallback } from 'react';
// @ts-ignore - Ignore if TS complains about the relative path outside root
import { mobileClient } from '../ReveilaClient';

export const useReveila = () => {
    // 1. Fixed: Added 'suspended' to the initial state to match the setter logic
    const [engineStatus, setEngineStatus] = useState({ 
        running: false, 
        suspended: false, 
        error: null as string | null 
    });

    const invoke = useCallback(async (component: string, method: string, args: any[] = []) => {
        try {
            return await mobileClient.invoke(component, method, args);
        } catch (err: any) {
            console.error(`[Reveila] Invoke failed: ${component}.${method}`, err);
            throw err;
        }
    }, []);

    const checkStatus = useCallback(async () => {
        try {
            const running = await invoke('ReveilaModule', 'isRunning');
            
            let suspended = false;
            if (running) {
                // If it's running, we check the suspension state
                suspended = await invoke('ReveilaModule', 'isSuspended').catch(() => false);
            }

            setEngineStatus({
                running,
                suspended,
                error: null
            });
        } catch (err: any) {
            // Keep previous status but update the error
            setEngineStatus(s => ({ ...s, error: err.message }));
        }
    }, [invoke]);

    useEffect(() => {
        checkStatus();
        const heartbeat = setInterval(checkStatus, 5000);
        return () => clearInterval(heartbeat);
    }, [checkStatus]);

    // 2. Fixed: Added checkStatus back to the return object
    return { engineStatus, invoke, checkStatus };
};