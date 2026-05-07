// packages/reveila-bridge/hooks/useReveilaEngine.ts
import { useState, useEffect } from 'react';
import { ReveilaService } from '../services/ReveilaService';

export const useReveilaEngine = () => {
    const [status, setStatus] = useState({ running: false, suspended: false });
    const [loading, setLoading] = useState(true);

    const refreshStatus = async () => {
        const response = await ReveilaService.sendCommand('GET_STATUS');
        if (response.success) {
            setStatus(response.data);
        }
        setLoading(false);
    };

    useEffect(() => {
        refreshStatus();
        // Poll every 5 seconds to stay in sync with the Java Service
        const interval = setInterval(refreshStatus, 5000);
        return () => clearInterval(interval);
    }, []);

    return { ...status, loading, refreshStatus };
};