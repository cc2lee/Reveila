<script setup lang="ts">
import CisoDashboard from '../reveila/CisoDashboard.vue'
import '@reveila/core/ReveilaTable.js'
</script>

<template>
  <div class="landing-page">
    <header class="top-banner">
      <h1 class="banner-heading"><span class="reveila-logo">REVEILA</span> AI Control Center</h1>
      <div style="flex-grow: 1;"></div>
      <router-link to="/settings" class="settings-btn" title="System Settings">
        <svg viewBox="0 0 24 24" width="24" height="24" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
      </router-link>
    </header>
    
    <main class="content-stack">
      <section class="dashboard-section">
        <div class="section-header">
          <h2>Private Guard</h2>
          <p>Real-time protection and hardware-level control</p>
        </div>
        <CisoDashboard />
      </section>

      <section class="table-section">
        <div class="section-header">
          <h2>Activity History</h2>
          <p>Real-time records of all AI system actions</p>
        </div>
        <reveila-table entity-type="auditlog" search-field="action" page-size="15"></reveila-table>
      </section>
    </main>
  </div>
</template>

<style scoped>
.landing-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f1f5f9;
}

.top-banner {
  display: flex;
  align-items: center;
  padding: 1rem 2rem;
  background-color: #0f172a;
  color: white;
  gap: 1.5rem;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  width: 100%;
  box-sizing: border-box;
  z-index: 10;
}

.reveila-logo {
  color: #ff6600; /* Bright Orange */
  margin-right: 0.5rem;
}

.banner-heading {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.025em;
  color: #f8fafc;
}

.settings-btn {
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: 8px;
  transition: all 0.2s;
  text-decoration: none;
}

.settings-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
}

.content-stack {
  display: flex;
  flex-direction: column;
  gap: 3rem;
  padding: 2rem;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.section-header {
  margin-bottom: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0.75rem;
}

.section-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 1.5rem;
  font-weight: 800;
}

.section-header p {
  margin: 0.25rem 0 0 0;
  color: #64748b;
  font-size: 0.875rem;
}

.table-section, .dashboard-section {
  width: 100%;
}

/* Ensure the table component doesn't overflow its section */
reveila-table {
  display: block;
  width: 100%;
}
</style>
