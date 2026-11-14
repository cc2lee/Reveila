/**
 * Reveila Core: Universal, Protocol-Agnostic Client
 */
export const DEFAULT_REVEILA_VERSION = '1.0.0';

export class ReveilaClient {
    constructor(config = {}) {
        this.baseURL = (config.baseURL || '').replace(/\/+$/, '');
        this.version = config.version || DEFAULT_REVEILA_VERSION;
        this.headers = config.headers || {};
        this.transport = config.transport || null;
    }

    /**
     * The unified component invocation method.
     * Routes all component and callback operations through POST /api/components/{componentName}/invoke
     * with API version verification in MethodDTO.
     */
    async invoke(componentName, methodName, args = []) {
        const normalizedArgs = Array.isArray(args) ? args : [args];
        if (this.transport) {
            return this.transport(componentName, methodName, normalizedArgs, this.version);
        }

        const response = await fetch(`${this.baseURL}/api/components/${encodeURIComponent(componentName)}/invoke`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...this.headers,
            },
            body: JSON.stringify({
                version: this.version,
                methodName,
                args: normalizedArgs,
            }),
        });

        if (!response.ok) {
            let errorDetail = `HTTP ${response.status}`;
            try {
                const errPayload = await response.json();
                errorDetail = errPayload.message || errPayload.error || errorDetail;
            } catch {
                // Fallback to status text
            }
            const err = new Error(`Reveila invoke failed (${componentName}.${methodName}): ${errorDetail}`);
            err.status = response.status;
            throw err;
        }

        const text = await response.text();
        return text ? JSON.parse(text) : null;
    }

    /**
     * Standard search helper delegating to DataService.search via the unified invoke endpoint.
     */
    async search(searchRequest) {
        return this.invoke('DataService', 'search', [searchRequest]);
    }
}

export const reveila = new ReveilaClient();