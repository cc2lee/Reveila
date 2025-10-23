import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './ReveilaExpoModule.types';

type ReveilaExpoModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ReveilaExpoModule extends NativeModule<ReveilaExpoModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ReveilaExpoModule, 'ReveilaExpoModule');
