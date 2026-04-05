import { NativeModules } from 'react-native';

const { ReveilaModule } = NativeModules;

export interface IReveilaModule {
  startService(systemHome?: string): Promise<boolean>;
  invoke(componentName: string, methodName: string, params: any[]): Promise<any>;
  isRunning(): Promise<boolean>;
  isSetupComplete(): Promise<boolean>;
  startSovereignSetup(): Promise<boolean>;
  unlockWithMasterPassword(password: string): Promise<boolean>;
  changeMasterPassword(oldPassword: string, newPassword: string): Promise<boolean>;
  isSessionValid(): Promise<boolean>;
  resetApplication(): Promise<boolean>;
  triggerVaultScan(): Promise<boolean>;
  triggerEmergencyStop(): Promise<boolean>;
}

export default ReveilaModule as IReveilaModule;
