<script setup lang="ts">
import CisoDashboard from '../reveila/CisoDashboard.vue'
import '@reveila/core/ReveilaTable.js'
</script>

<template>
  <div class="landing-page">
    <header class="top-banner">
      <img src="/images/brother.png" alt="Reveila Logo" class="banner-logo" />
      <h1 class="banner-heading">Reveila AI Runtime Fabric Control Center</h1>
    </header>
    
    <main class="content-stack">
      <section class="dashboard-section">
        <div class="section-header">
          <h2>Agent Sovereignty</h2>
          <p>Instant perimeter enforcement and hardware-level control</p>
        </div>
        <CisoDashboard />
      </section>

      <section class="table-section">
        <div class="section-header">
          <h2>Flight Recorder</h2>
          <p>Real-time audit logs of all fabric invocations and agent actions</p>
        </div>
        <reveila-table entity-type="AuditLog" search-field="action" page-size="15"></reveila-table>
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

.banner-logo {
  height: 40px;
  width: auto;
}

.banner-heading {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.025em;
  color: #f8fafc;
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

/* Ensure the table component doesn't overflow */
reveila-table {
  width: 100%;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}
</style>
