<script setup lang="ts">
import { onMounted, ref, onUnmounted } from 'vue'
import { ReveilaClient } from '@reveila/core'
import '@reveila/core/ciso-dashboard.js'

const killSwitch = ref<any>(null)
let intervalId: number | undefined

const fetchSessions = async () => {
  try {
    const api = new ReveilaClient()
    const response = await api.invoke('OrchestrationService', 'getActiveSessions', [])
    if (killSwitch.value && Array.isArray(response)) {
      killSwitch.value.agentSessions = response
    }
  } catch (error) {
    console.error('Failed to fetch real-time sessions', error)
  }
}

onMounted(() => {
  fetchSessions()
  intervalId = setInterval(fetchSessions, 5000)
})

onUnmounted(() => {
  if (intervalId) {
    clearInterval(intervalId)
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
