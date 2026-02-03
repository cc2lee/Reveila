How to install?

For the Vue Project:
Run this from the web/vue-project/ directory:

Bash
npm install ../../connectors/js

For the Expo/React Native Project:
Run this from the mobile/ directory:

Bash
npm install ../../connectors/js

How to use?

import { ReveilaClient } from '@reveila/core';
const client = new ReveilaClient({ baseURL: 'https://api.reveila.com' });
