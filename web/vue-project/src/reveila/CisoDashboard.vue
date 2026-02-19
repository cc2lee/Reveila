<script setup lang="ts">
import { onMounted, ref } from 'vue'
import '@reveila/core/ciso-dashboard.js'

const killSwitch = ref<any>(null)

const mockSessions = [
  { id: 'agent-77', plugin: 'healthcare-audit-worker', cpu: 12, ram: 156 },
  { id: 'agent-42', plugin: 'finance-analyzer', cpu: 85, ram: 412 }
]

onMounted(() => {
  if (killSwitch.value) {
    killSwitch.value.agentSessions = mockSessions
  }
})
</script>

<template>
  <div class="dashboard-container">
    <ciso-kill-switch ref="killSwitch"></ciso-kill-switch>
  </div>
</template>

<style scoped>
.dashboard-container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
</style>
