import { NativeModule, requireNativeModule } from 'expo';

import { ReveilaModuleEvents } from './Reveila.types';

declare class ReveilaModule extends NativeModule<ReveilaModuleEvents> {
  startService(): Promise<void>;
  invoke(payload: string): Promise<string>;
  isRunning(): boolean;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReveilaModule>('Reveila');
