/**
 * Reveila Core: Universal client for Vue and React/Expo
 */
export class ReveilaClient {
    constructor(config = {}) {
        this.baseURL = config.baseURL || '';
        this.headers = config.headers || { 'Content-Type': 'application/json' };
    }

    /**
     * Universal fetch wrapper
     */
    async request(path, body) {
        const response = await fetch(`${this.baseURL}${path}`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify(body),
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || `HTTP error! status: ${response.status}`);
        }

        return response.json();
    }

    /**
     * The core Reveila bridge method
     */
    async invoke(componentName, methodName, args = []) {
        return this.request(`/api/components/${componentName}/invoke`, {
            methodName,
            args
        });
    }

    /**
     * Tenant-aware search helper
     */
    async search(searchRequest) {
        return this.invoke('DataService', 'search', [searchRequest]);
    }
}

// Export a singleton for easy use
export const reveila = new ReveilaClient();