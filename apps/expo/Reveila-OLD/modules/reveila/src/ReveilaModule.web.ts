import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './Reveila.types';

type ReveilaModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ReveilaModule extends NativeModule<ReveilaModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ReveilaModule, 'ReveilaModule');
