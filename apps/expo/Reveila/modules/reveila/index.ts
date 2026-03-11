import { NativeModules, Platform, DeviceEventEmitter } from 'react-native';

const LINKING_ERROR =
  `The package 'reveila-native-bridge' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go (this requires a Development Build)';

// Access the module we defined in the :android Java code
const ReveilaModule = NativeModules.ReveilaModule
  ? NativeModules.ReveilaModule
  : new Proxy(
      {},
      {
        get() {
          if (Platform.OS === 'web') {
            return () => {
              console.warn('ReveilaModule is not available on Web.');
              return Promise.resolve(false);
            };
          }
          throw new Error(LINKING_ERROR);
        },
      }
    );

// Global listener for safety commands (Biometric-gated)
if (Platform.OS !== 'web') {
  DeviceEventEmitter.addListener('onSafetyCommand', (command) => {
    console.warn('⚠️ SAFETY COMMAND AUTHORIZED:', command);
    // Here we would typically broadcast this to the remote server
    // For this local engine, we can invoke the internal Safety Service
    ReveilaModule.invoke('SafetyListener', 'onSafetyCommand', [command])
      .catch((err: any) => console.error('Failed to propagate safety command:', err));
  });
}

export interface ReveilaStatus {
  isRunning: boolean;
  version: string;
  platform: string;
}

/**
 * Sovereign AI Engine Interface
 */
export const Reveila = {
  /**
   * Initializes and starts the Reveila Agentic Fabric
   */
  start: (): Promise<string> => {
    return ReveilaModule.startService();
  },

  /**
   * Gracefully shuts down the engine
   */
  stop: (): Promise<boolean> => {
    return ReveilaModule.stop();
  },

  /**
   * Returns current engine health and metadata
   */
  getStatus: (): Promise<ReveilaStatus> => {
    return ReveilaModule.getStatus();
  }
};

export default ReveilaModule;
