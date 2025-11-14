import { NativeModule, requireNativeModule } from 'expo';

import { ReveilaModuleEvents } from './Reveila.types';

declare class ReveilaModule extends NativeModule<ReveilaModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReveilaModule>('Reveila');
