# CISO Kill Switch Dashboard Integration

This generic JavaScript component provides the "Instant Sovereignty" UI for managing isolated agent sessions.

## 📦 Usage

The component is a standard Web Component (`<ciso-kill-switch>`).

### 1. Include the Script
Include the script in your project:
```javascript
import './connectors/js/ciso-dashboard.js';
```

### 2. React / Expo Integration
```jsx
import React, { useEffect, useRef } from 'react';

const Dashboard = () => {
  const dashRef = useRef();

  useEffect(() => {
    // Inject active sessions (Mock or API call)
    dashRef.current.agentSessions = [
      { id: 'TRC-9921', plugin: 'claims-auditor', cpu: 45, ram: 128 },
      { id: 'TRC-4402', plugin: 'ma-summary', cpu: 12, ram: 64 }
    ];

    const handleRevoke = (e) => {
      console.log(`CISO Action: Revoking ${e.detail.sessionId}`);
      // Call your backend API: Reveila.stopComponent(e.detail.sessionId)
    };

    dashRef.current.addEventListener('revoke-perimeter', handleRevoke);
    return () => dashRef.current.removeEventListener('revoke-perimeter', handleRevoke);
  }, []);

  return <ciso-kill-switch ref={dashRef} />;
};
```

### 3. Vue Integration
```html
<template>
  <ciso-kill-switch 
    ref="dashboard" 
    @revoke-perimeter="onRevoke" 
  />
</template>

<script setup>
import { onMounted, ref } from 'vue';

const dashboard = ref(null);

onMounted(() => {
  dashboard.value.agentSessions = [
    { id: 'TRC-9921', plugin: 'claims-auditor', cpu: 45, ram: 128 }
  ];
});

const onRevoke = (event) => {
  const sessionId = event.detail.sessionId;
  // Trigger Forensic Sync via Flight Recorder
};
</script>
```

## 🛡️ Key Features
- **Hardware-Level Isolation**: Visualizes the Docker sandbox resource usage.
- **Instant Revocation**: Sends a `revoke-perimeter` event to trigger the backend kill switch.
- **Agnostic Enforcement**: Works identical for OpenAI, Gemini, or custom worker agents.
