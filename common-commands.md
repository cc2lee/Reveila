C:\IDE\Android\SDK/emulator/emulator @Medium_Phone

Gradle Wrapper Update:
    ./gradlew wrapper --gradle-version <your-desired-version>

Align React Native Dependency Versions:
    npx @rnx-kit/align-deps --requirements react-native@0.81 --write


Syntax to use in the Android app's UI text box to invoke a remote Reveila Service method is:
    ReveilaRemote invoke "EchoService" "echo" "Hello"
    Or, ReveilaRemote invoke "ServiceName" "methodName" "Arg1" "Arg2", if the remote method takes multiple arguments.

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


Here are a few clients you can use to test the URL, along with an example for the most common command-line tool, cURL:

1. cURL (Command-Line)
This is a versatile command-line tool available on most systems (Windows, macOS, Linux). It's great for quick tests without needing a graphical interface.

Example cURL Command:

To test the EchoService's echo method with the message "Hello", you can use the following command in your terminal:

curl -X POST -H "Content-Type: application/json" -d '{"methodName": "echo", "args": ["Hello"]}' http://127.0.0.1:8080/api/components/EchoService/invoke
2. Postman
Postman is a very popular and user-friendly graphical application specifically designed for API testing. It allows you to save your requests, organize them into collections, and easily inspect the responses. You can download it from the Postman website.

3. Insomnia
Insomnia is another excellent graphical API client, similar to Postman. It's known for its clean, modern interface and is also a very powerful tool for testing and debugging APIs. You can find it on the Insomnia website.

For your immediate needs, the cURL command is likely the fastest way to test your endpoint.