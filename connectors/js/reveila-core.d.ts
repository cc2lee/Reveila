// reveila-core.d.ts
export class ReveilaClient {
    constructor(config?: { baseURL?: string, headers?: Record<string, string> });
    invoke(componentName: string, methodName: string, args?: any[]): Promise<any>;
    search(searchRequest: any): Promise<any>;
}
export const reveila: ReveilaClient;