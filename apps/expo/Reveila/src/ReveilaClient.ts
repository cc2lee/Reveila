import { NativeModules } from 'react-native';
// Path: Go up 4 levels to reach the root ts/js folder
import { ReveilaClient } from '../../../../ts/js/reveila-core';

const { ReveilaModule } = NativeModules;

/**
 * This is the ACTUAL instance used by the mobile app.
 * It injects the Native "sendCommand" transport into your Universal Client.
 */
export const mobileClient = new ReveilaClient({
    transport: async (component, method, args) => {
        const command = JSON.stringify({ 
            action: 'INVOKE', 
            component, 
            method, 
            args 
        });
        const response = await ReveilaModule.sendCommand(command);
        return JSON.parse(response);
    }
});