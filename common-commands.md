Gradle Wrapper Update:
    ./gradlew wrapper --gradle-version <your-desired-version>

Align React Native Dependency Versions:
    npx @rnx-kit/align-deps --requirements react-native@0.81 --write

How to invoke Reveila REST API:

    Your package.json doesn't include a specific library for HTTP requests like axios, so you can use the fetch API that's built into React Native.

    Here is a TypeScript example of how you can call your invokeComponent endpoint from your app. You can place this in a service file or directly in your component logic.

    // The base URL for your Spring backend when running on the Android emulator
    const API_BASE_URL = 'http://10.0.2.2:8080';

    /**
    * The structure of the data expected by the backend.
    */
    interface MethodPayload {
    methodName: string;
    args: unknown[];
    }

    /**
    * Calls a method on a remote Reveila component.
    *
    * @param componentName The name of the component to invoke.
    * @param payload The method name and arguments to send.
    * @returns The result from the remote method.
    */
    async function invokeRemoteComponent(componentName: string, payload: MethodPayload): Promise<any> {
    const url = `${API_BASE_URL}/api/components/${componentName}/invoke`;

    console.log(`Invoking remote component at: ${url}`);
    console.log('Payload:', payload);

    try {
        const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
        body: JSON.stringify(payload),
        });

        if (!response.ok) {
        // Try to get more detailed error from the response body
        const errorBody = await response.text();
        console.error('Network response was not ok.', {
            status: response.status,
            statusText: response.statusText,
            body: errorBody,
        });
        throw new Error(`HTTP error ${response.status}: ${errorBody}`);
        }

        const result = await response.json();
        console.log('Received result:', result);
        return result;

    } catch (error) {
        console.error('Failed to invoke remote component:', error);
        // Re-throw the error so the calling code can handle it
        throw error;
    }
    }

    // --- Example Usage ---
    async function exampleUsage() {
    const componentToCall = 'someComponent'; // Replace with your component name
    const methodToInvoke: MethodPayload = {
        methodName: 'someMethod', // Replace with your method name
        args: ['hello', 123],      // Replace with your arguments
    };

    try {
        const result = await invokeRemoteComponent(componentToCall, methodToInvoke);
        console.log('Successfully invoked component, result:', result);
        // You can now use the result in your app's state
    } catch (error) {
        // Handle the error, e.g., show a message to the user
    }
    }
    To use this, replace the placeholder values in the exampleUsage function with the actual componentName, methodName, and args you intend to call.
