/**
 * Reveila Core: Universal, Protocol-Agnostic Client
 */
export class ReveilaClient {
    constructor(config = {}) {
        this.baseURL = config.baseURL || '';
        this.transport = config.transport || null;
    }

    /**
     * The only method the core needs. 
     * It handles the routing regardless of the environment.
     */
    async invoke(componentName, methodName, args = []) {
        if (this.transport) {
            return this.transport(componentName, methodName, args);
        }

        const response = await fetch(`${this.baseURL}/api/components/${componentName}/invoke`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ methodName, args }),
        });
        return response.json();
    }
}