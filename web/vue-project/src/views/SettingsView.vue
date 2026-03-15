<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const tabs = ref<string[]>([])
const activeTab = ref('')
const settings = ref<Record<string, string>>({})
const plugins = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const message = ref('')

// New Plugin Form
const newPlugin = ref({
  pluginId: '',
  version: '1.0.0',
  checksum: '',
  storagePath: '',
  targetClusterRole: 'standard'
})

const fetchTabs = async () => {
  const res = await fetch('/api/settings')
  tabs.value = await res.json()
  // Add virtual "plugins" tab
  if (!tabs.value.includes('plugins')) {
    tabs.value.push('plugins')
  }
  if (tabs.value.length > 0) {
    activeTab.value = tabs.value[0]
  }
}

const fetchData = async () => {
  if (!activeTab.value) return
  loading.value = true
  
  if (activeTab.value === 'plugins') {
    const res = await fetch('/api/settings/plugins')
    plugins.value = await res.json()
  } else {
    const res = await fetch(`/api/settings/${activeTab.value}`)
    settings.value = await res.json()
  }
  
  loading.value = false
}

const saveSettings = async () => {
  saving.value = true
  message.value = 'Saving and refreshing runtime...'
  try {
    const res = await fetch(`/api/settings/${activeTab.value}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings.value)
    })
    if (res.ok) {
      message.value = 'Settings saved successfully. Engine refreshed.'
      setTimeout(() => message.value = '', 3000)
    }
  } catch (e) {
    message.value = 'Error saving settings.'
  } finally {
    saving.value = false
  }
}

const registerPlugin = async () => {
  saving.value = true
  try {
    const res = await fetch('/api/settings/plugins', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newPlugin.value)
    })
    if (res.ok) {
      message.value = 'Plugin registered successfully.'
      newPlugin.value = { pluginId: '', version: '1.0.0', checksum: '', storagePath: '', targetClusterRole: 'standard' }
      fetchData()
    }
  } catch (e) {
    message.value = 'Error registering plugin.'
  } finally {
    saving.value = false
  }
}

const deletePlugin = async (id: string) => {
  if (!confirm('Delete this plugin registration?')) return
  await fetch(`/api/settings/plugins/${id}`, { method: 'DELETE' })
  fetchData()
}

onMounted(fetchTabs)
watch(activeTab, fetchData)
</script>

<template>
  <div class="settings-page">
    <header class="settings-header">
      <router-link to="/" class="back-link">← Back to Dashboard</router-link>
      <h1>System Settings</h1>
      <p>Configure the Reveila AI Fabric runtime and infrastructure.</p>
    </header>

    <div class="settings-container">
      <aside class="tabs-sidebar">
        <button 
          v-for="tab in tabs" 
          :key="tab"
          @click="activeTab = tab"
          :class="['tab-btn', { active: activeTab === tab }]"
        >
          {{ tab.charAt(0).toUpperCase() + tab.slice(1) }}
        </button>
      </aside>

      <main class="settings-main">
        <div v-if="loading" class="loading">Syncing with Sovereign Registry...</div>
        
        <!-- Standard Property Editor -->
        <div v-else-if="activeTab && activeTab !== 'plugins'" class="settings-form">
          <h2 class="tab-title">{{ activeTab.toUpperCase() }} Configuration</h2>
          <div v-for="(value, key) in settings" :key="key" class="form-group">
            <label :for="key">{{ key }}</label>
            <input :id="key" type="text" v-model="settings[key]" class="form-input" />
          </div>
          <div class="actions">
            <button @click="saveSettings" :disabled="saving" class="save-btn">Save Changes</button>
            <span v-if="message" class="status-message">{{ message }}</span>
          </div>
        </div>

        <!-- Specialized Plugin Manager -->
        <div v-else-if="activeTab === 'plugins'" class="plugin-manager">
          <h2 class="tab-title">Sovereign Plugin Registry</h2>
          
          <section class="registration-form">
            <h3>Register New Plugin</h3>
            <div class="grid-form">
              <input v-model="newPlugin.pluginId" placeholder="Plugin ID (e.g. weather-agent)" class="form-input" />
              <input v-model="newPlugin.version" placeholder="Version" class="form-input" />
              <input v-model="newPlugin.checksum" placeholder="SHA-256 Checksum" class="form-input" />
              <input v-model="newPlugin.storagePath" placeholder="Storage Path (S3/Local)" class="form-input" />
              <button @click="registerPlugin" :disabled="saving" class="add-btn">Register Plugin</button>
            </div>
          </section>

          <section class="plugin-list">
            <h3>Active Registrations</h3>
            <table class="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Version</th>
                  <th>Status</th>
                  <th>Role</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in plugins" :key="p.pluginId">
                  <td><code class="id-tag">{{ p.pluginId }}</code></td>
                  <td>{{ p.version }}</td>
                  <td><span :class="['status-pill', p.status.toLowerCase()]">{{ p.status }}</span></td>
                  <td>{{ p.targetClusterRole }}</td>
                  <td>
                    <button @click="deletePlugin(p.pluginId)" class="text-btn danger">Revoke</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-page { padding: 2rem; max-width: 1200px; margin: 0 auto; font-family: 'Inter', sans-serif; }
.settings-header { margin-bottom: 2rem; }
.back-link { color: #3b82f6; text-decoration: none; font-size: 0.875rem; font-weight: 600; display: block; margin-bottom: 1rem; }
.settings-header h1 { margin: 0; font-size: 1.875rem; font-weight: 800; color: #0f172a; }
.settings-header p { margin: 0.5rem 0 0 0; color: #64748b; }
.settings-container { display: flex; gap: 2rem; background: white; padding: 1.5rem; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); min-height: 500px; }
.tabs-sidebar { width: 200px; display: flex; flex-direction: column; gap: 0.5rem; border-right: 1px solid #e2e8f0; padding-right: 1.5rem; }
.tab-btn { text-align: left; padding: 0.75rem 1rem; border: none; background: none; border-radius: 8px; color: #64748b; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.tab-btn:hover { background: #f1f5f9; color: #0f172a; }
.tab-btn.active { background: #0f172a; color: white; }
.settings-main { flex-grow: 1; }
.tab-title { margin-top: 0; font-size: 1.25rem; border-bottom: 2px solid #f1f5f9; padding-bottom: 1rem; margin-bottom: 1.5rem; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; font-size: 0.875rem; font-weight: 700; color: #475569; margin-bottom: 0.5rem; }
.form-input { width: 100%; padding: 0.625rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.95rem; margin-bottom: 0.5rem; }
.actions { display: flex; align-items: center; gap: 1rem; margin-top: 2rem; padding-top: 1rem; border-top: 1px solid #f1f5f9; }
.save-btn, .add-btn { padding: 0.75rem 1.5rem; background: #22c55e; color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; }
.add-btn { background: #0f172a; }
.status-message { font-size: 0.875rem; color: #059669; font-weight: 600; }

.grid-form { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 2rem; background: #f8fafc; padding: 1.5rem; border-radius: 8px; }
.data-table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
.data-table th { text-align: left; padding: 12px; background: #f1f5f9; color: #475569; font-size: 0.75rem; text-transform: uppercase; }
.data-table td { padding: 12px; border-bottom: 1px solid #f1f5f9; font-size: 0.875rem; }
.id-tag { background: #eff6ff; color: #1e40af; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.status-pill { padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 700; }
.status-pill.active { background: #dcfce7; color: #166534; }
.danger { color: #ef4444; background: none; border: none; cursor: pointer; font-weight: 600; }
</style>
