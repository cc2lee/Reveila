import { NativeModules } from 'react-native';

const { ReveilaModule } = NativeModules;

export interface IReveilaModule {
  startService(systemHome?: string): Promise<boolean>;
  invoke(componentName: string, methodName: string, params: any[]): Promise<any>;
  isRunning(): Promise<boolean>;
}

export default ReveilaModule as IReveilaModule;
