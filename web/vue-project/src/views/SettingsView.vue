<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const tabs = ref<string[]>([])
const activeTab = ref('')
const settings = ref<Record<string, string>>({})
const plugins = ref<any[]>([])
const tasks = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const message = ref('')

// i18n support
const lang = ref('en')
const uiText = ref<Record<string, string>>({})

// New Plugin Form
const newPlugin = ref({
  pluginId: '',
  version: '1.0.0',
  checksum: '',
  storagePath: '',
  targetClusterRole: 'standard'
})

// Task Editor
const selectedTask = ref<any>(null)

const fetchUiText = async () => {
  try {
    const res = await fetch(`/api/settings/ui/text?lang=${lang.value}`)
    uiText.value = await res.json()
  } catch (e) {
    console.error('Failed to load UI text', e)
  }
}

const fetchTabs = async () => {
  try {
    const res = await fetch('/api/settings')
    tabs.value = await res.json()
    if (!tabs.value.includes('plugins')) tabs.value.push('plugins')
    if (!tabs.value.includes('tasks')) tabs.value.push('tasks')
    if (tabs.value.length > 0 && !activeTab.value) activeTab.value = tabs.value[0]
  } catch (e) {
    tabs.value = ['plugins', 'tasks']
    activeTab.value = 'plugins'
  }
}

const fetchData = async () => {
  if (!activeTab.value) return
  loading.value = true
  try {
    if (activeTab.value === 'plugins') {
      const res = await fetch('/api/settings/plugins')
      plugins.value = await res.json()
    } else if (activeTab.value === 'tasks') {
      const res = await fetch('/api/tasks')
      tasks.value = await res.json()
    } else {
      const res = await fetch(`/api/settings/${activeTab.value}`)
      settings.value = await res.json()
    }
  } catch (e) {} finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  message.value = uiText.value['settings.saving'] || 'Saving...'
  try {
    const res = await fetch(`/api/settings/${activeTab.value}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings.value)
    })
    if (res.ok) {
      message.value = uiText.value['settings.success'] || 'Saved.'
      setTimeout(() => message.value = '', 3000)
    }
  } catch (e) {
    message.value = uiText.value['settings.error'] || 'Error.'
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
      message.value = 'Plugin added successfully.'
      newPlugin.value = { pluginId: '', version: '1.0.0', checksum: '', storagePath: '', targetClusterRole: 'standard' }
      fetchData()
    }
  } catch (e) {} finally {
    saving.value = false
  }
}

const deletePlugin = async (id: string) => {
  if (!confirm('Remove this plugin?')) return
  await fetch(`/api/settings/plugins/${id}`, { method: 'DELETE' })
  fetchData()
}

const editTask = (task: any) => {
  selectedTask.value = { ...task }
}

const saveTask = async () => {
  if (!selectedTask.value) return
  saving.value = true
  try {
    const res = await fetch(`/api/tasks/${selectedTask.value.filename}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: selectedTask.value.content
    })
    if (res.ok) {
      message.value = 'Task saved.'
      selectedTask.value = null
      fetchData()
    }
  } catch (e) {} finally {
    saving.value = false
  }
}

const deleteTask = async (filename: string) => {
  if (!confirm(`Delete task ${filename}?`)) return
  await fetch(`/api/tasks/${filename}`, { method: 'DELETE' })
  fetchData()
}

const createNewTask = () => {
  selectedTask.value = {
    filename: 'new-task.json',
    content: JSON.stringify({
      taskId: 'new-task-id',
      intent: 'general.task',
      prompt: 'Enter instructions here...',
      frequency: 'daily'
    }, null, 2)
  }
}

const t = (key: string) => uiText.value[key] || key

onMounted(async () => {
  await fetchUiText()
  await fetchTabs()
})

watch(activeTab, fetchData)
watch(lang, fetchUiText)
</script>

<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="header-top">
        <router-link to="/" class="back-link">← {{ t('settings.back') }}</router-link>
        <select v-model="lang" class="lang-select">
          <option value="en">English (EN)</option>
        </select>
      </div>
      <h1>{{ t('settings.title') }}</h1>
      <p>{{ t('settings.subtitle') }}</p>
    </header>

    <div class="settings-container">
      <aside class="tabs-sidebar">
        <button 
          v-for="tab in tabs" 
          :key="tab"
          @click="activeTab = tab"
          :class="['tab-btn', { active: activeTab === tab }]"
        >
          {{ tab === 'plugins' ? t('settings.plugins.title') : 
             tab === 'tasks' ? 'Recurring Tasks' :
             (tab.charAt(0).toUpperCase() + tab.slice(1)) }}
        </button>
      </aside>

      <main class="settings-main">
        <div v-if="loading" class="loading">Syncing with AI system...</div>
        
        <div v-else-if="activeTab && activeTab !== 'plugins' && activeTab !== 'tasks'" class="settings-form">
          <h2 class="tab-title">{{ activeTab.toUpperCase() }}</h2>
          <div v-for="(value, key) in settings" :key="key" class="form-group">
            <label :for="key">{{ t(key.toString()) }}</label>
            <input :id="key" type="text" v-model="settings[key]" class="form-input" />
          </div>
          <div class="actions">
            <button @click="saveSettings" :disabled="saving" class="save-btn">Save Changes</button>
            <span v-if="message" class="status-message">{{ message }}</span>
          </div>
        </div>

        <div v-else-if="activeTab === 'plugins'" class="plugin-manager">
          <h2 class="tab-title">{{ t('settings.plugins.title') }}</h2>
          <section class="registration-form">
            <h3>Add New Plugin</h3>
            <div class="grid-form">
              <input v-model="newPlugin.pluginId" placeholder="ID" class="form-input" />
              <input v-model="newPlugin.version" placeholder="Version" class="form-input" />
              <input v-model="newPlugin.checksum" placeholder="Checksum" class="form-input" />
              <input v-model="newPlugin.storagePath" placeholder="Path" class="form-input" />
              <button @click="registerPlugin" :disabled="saving" class="add-btn">Add Plugin</button>
            </div>
          </section>
          <section class="plugin-list">
            <h3>{{ t('settings.plugins.active') }}</h3>
            <table class="data-table">
              <thead><tr><th>ID</th><th>Version</th><th>Status</th><th>Actions</th></tr></thead>
              <tbody>
                <tr v-for="p in plugins" :key="p.pluginId">
                  <td><code class="id-tag">{{ p.pluginId }}</code></td>
                  <td>{{ p.version }}</td>
                  <td><span class="status-pill active">{{ p.status }}</span></td>
                  <td><button @click="deletePlugin(p.pluginId)" class="text-btn danger">Remove</button></td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <div v-else-if="activeTab === 'tasks'" class="task-manager">
          <div class="tab-header-flex">
            <h2 class="tab-title">Recurring AI Tasks</h2>
            <button @click="createNewTask" class="add-btn-small">+ New Task</button>
          </div>
          <div v-if="selectedTask" class="task-editor">
            <div class="editor-header">
              <h3>Editing: {{ selectedTask.filename }}</h3>
              <button @click="selectedTask = null" class="close-btn">✕</button>
            </div>
            <div class="form-group">
              <label>Filename</label>
              <input v-model="selectedTask.filename" class="form-input" />
            </div>
            <div class="form-group">
              <label>JSON Definition</label>
              <textarea v-model="selectedTask.content" class="form-textarea" rows="10"></textarea>
            </div>
            <div class="actions">
              <button @click="saveTask" :disabled="saving" class="save-btn">Save Task</button>
              <button @click="selectedTask = null" class="cancel-btn">Cancel</button>
            </div>
          </div>
          <section class="task-list">
            <table class="data-table">
              <thead><tr><th>Filename</th><th>Actions</th></tr></thead>
              <tbody>
                <tr v-for="task in tasks" :key="task.filename">
                  <td><code class="id-tag">{{ task.filename }}</code></td>
                  <td>
                    <button @click="editTask(task)" class="text-btn">Edit</button>
                    <button @click="deleteTask(task.filename)" class="text-btn danger">Delete</button>
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
.header-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.back-link { color: #3b82f6; text-decoration: none; font-size: 0.875rem; font-weight: 600; }
.lang-select { padding: 4px 8px; border-radius: 6px; border: 1px solid #e2e8f0; font-size: 0.75rem; }
.settings-header h1 { margin: 0; font-size: 1.875rem; font-weight: 800; color: #0f172a; }
.settings-header p { margin: 0.5rem 0 0 0; color: #64748b; }
.settings-container { display: flex; gap: 2rem; background: white; padding: 1.5rem; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); min-height: 500px; }
.tabs-sidebar { width: 200px; display: flex; flex-direction: column; gap: 0.5rem; border-right: 1px solid #e2e8f0; padding-right: 1.5rem; }
.tab-btn { text-align: left; padding: 0.75rem 1rem; border: none; background: none; border-radius: 8px; color: #64748b; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.tab-btn:hover { background: #f1f5f9; color: #0f172a; }
.tab-btn.active { background: #0f172a; color: white; }
.settings-main { flex-grow: 1; }
.tab-title { margin-top: 0; font-size: 1.25rem; border-bottom: 2px solid #f1f5f9; padding-bottom: 1rem; margin-bottom: 1.5rem; color: #0f172a; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; font-size: 0.875rem; font-weight: 700; color: #475569; margin-bottom: 0.5rem; }
.form-input, .form-textarea { width: 100%; padding: 0.625rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.95rem; }
.form-textarea { font-family: monospace; resize: vertical; }
.actions { display: flex; align-items: center; gap: 1rem; margin-top: 2rem; padding-top: 1rem; border-top: 1px solid #f1f5f9; }
.save-btn, .add-btn { padding: 0.75rem 1.5rem; background: #22c55e; color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; }
.cancel-btn { padding: 0.75rem 1.5rem; background: #f1f5f9; color: #475569; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; }
.add-btn { background: #0f172a; }
.add-btn-small { padding: 6px 12px; background: #0f172a; color: white; border: none; border-radius: 6px; font-size: 0.75rem; font-weight: 700; cursor: pointer; }
.status-message { font-size: 0.875rem; color: #059669; font-weight: 600; }
.grid-form { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 2rem; background: #f8fafc; padding: 1.5rem; border-radius: 8px; }
.data-table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
.data-table th { text-align: left; padding: 12px; background: #f1f5f9; color: #475569; font-size: 0.75rem; text-transform: uppercase; }
.data-table td { padding: 12px; border-bottom: 1px solid #f1f5f9; font-size: 0.875rem; }
.id-tag { background: #eff6ff; color: #1e40af; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.status-pill { padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 700; }
.status-pill.active { background: #dcfce7; color: #166534; }
.text-btn { background: none; border: none; color: #3b82f6; cursor: pointer; font-weight: 600; padding: 0; margin-right: 1rem; }
.danger { color: #ef4444; }
.tab-header-flex { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
.task-editor { background: #f8fafc; padding: 1.5rem; border-radius: 8px; margin-bottom: 2rem; border: 1px solid #e2e8f0; }
.editor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
.editor-header h3 { margin: 0; font-size: 1rem; color: #1e293b; }
.close-btn { background: none; border: none; font-size: 1.25rem; color: #94a3b8; cursor: pointer; }
</style>
