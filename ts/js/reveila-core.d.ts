// reveila-core.d.ts
export const DEFAULT_REVEILA_VERSION: string;

export interface ReveilaClientConfig {
    baseURL?: string;
    version?: string;
    headers?: Record<string, string>;
    transport?: (componentName: string, methodName: string, args: any[], version: string) => Promise<any>;
}

export class ReveilaClient {
    baseURL: string;
    version: string;
    headers: Record<string, string>;
    constructor(config?: ReveilaClientConfig);
    invoke(componentName: string, methodName: string, args?: any[]): Promise<any>;
    search(searchRequest: any): Promise<any>;
}

export interface Entity {
    id: { id: string };
    attributes: Record<string, any>;
}

export const reveila: ReveilaClient;