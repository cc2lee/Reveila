import { NativeModule, requireNativeModule } from 'expo';

import { ReveilaExpoModuleEvents } from './ReveilaExpoModule.types';

declare class ReveilaExpoModule extends NativeModule<ReveilaExpoModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
  invokeAsync(payload: string): Promise<string>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReveilaExpoModule>('ReveilaExpoModule');
