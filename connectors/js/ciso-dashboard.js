/**
 * CISO "Kill Switch" Dashboard - Instant Sovereignty Component
 * 
 * A portable, framework-agnostic dashboard for hardware-level control
 * over autonomous agents. Can be used in Vue, React, or React Native (WebView).
 * 
 * ADR 0006 Realignment: Enforces Docker sandbox isolation and Forensic Sync.
 * 
 * @author CL
 */

const styles = `
  :host {
    --bg-dark: #0f172a;
    --card-bg: #1e293b;
    --text-primary: #f8fafc;
    --text-secondary: #94a3b8;
    --danger: #ef4444;
    --danger-hover: #dc2626;
    --success: #22c55e;
    --accent: #38bdf8;
    font-family: 'Inter', system-ui, sans-serif;
    display: block;
    background: var(--bg-dark);
    color: var(--text-primary);
    padding: 2rem;
    min-height: 400px;
    border-radius: 12px;
  }

  .header {
    margin-bottom: 2rem;
    border-left: 4px solid var(--accent);
    padding-left: 1rem;
  }

  .header h1 {
    margin: 0;
    font-size: 1.5rem;
    letter-spacing: -0.025em;
  }

  .header p {
    color: var(--text-secondary);
    margin: 0.25rem 0 0 0;
    font-size: 0.875rem;
  }

  .agent-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 1.5rem;
  }

  .agent-card {
    background: var(--card-bg);
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 1.25rem;
    transition: all 0.2s;
  }

  .agent-card:hover {
    border-color: var(--accent);
  }

  .agent-info {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 1rem;
  }

  .agent-id {
    font-weight: 600;
    font-family: monospace;
    color: var(--accent);
  }

  .status-badge {
    padding: 0.25rem 0.5rem;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    background: rgba(34, 197, 94, 0.1);
    color: var(--success);
  }

  .usage-metrics {
    font-size: 0.875rem;
    margin-bottom: 1.5rem;
    color: var(--text-secondary);
  }

  .metric-row {
    display: flex;
    justify-content: space-between;
    margin-top: 0.5rem;
  }

  .metric-bar {
    height: 4px;
    background: #334155;
    border-radius: 2px;
    margin-top: 4px;
    overflow: hidden;
  }

  .metric-fill {
    height: 100%;
    background: var(--accent);
  }

  .revoke-btn {
    width: 100%;
    padding: 0.75rem;
    background: var(--danger);
    color: white;
    border: none;
    border-radius: 6px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
  }

  .revoke-btn:hover {
    background: var(--danger-hover);
  }

  .revoke-btn svg {
    width: 16px;
    height: 16px;
  }

  .empty-state {
    text-align: center;
    padding: 4rem;
    color: var(--text-secondary);
  }
`;

class CisoKillSwitch extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this.sessions = [];
  }

  set agentSessions(data) {
    this.sessions = data;
    this.render();
  }

  connectedCallback() {
    this.render();
  }

  handleRevoke(sessionId) {
    const event = new CustomEvent('revoke-perimeter', {
      detail: { sessionId },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
    
    // Optimistic UI update
    this.sessions = this.sessions.filter(s => s.id !== sessionId);
    this.render();
  }

  render() {
    const cards = this.sessions.map(session => `
      <div class="agent-card">
        <div class="agent-info">
          <div>
            <div class="agent-id">${session.id}</div>
            <div style="font-size: 0.75rem; color: var(--text-secondary)">${session.plugin}</div>
          </div>
          <span class="status-badge">Active Sandbox</span>
        </div>
        
        <div class="usage-metrics">
          <div class="metric-row">
            <span>CPU Core Usage</span>
            <span>${session.cpu}%</span>
          </div>
          <div class="metric-bar"><div class="metric-fill" style="width: ${session.cpu}%"></div></div>
          
          <div class="metric-row" style="margin-top: 1rem">
            <span>RAM (Docker Isolation)</span>
            <span>${session.ram}MB / 512MB</span>
          </div>
          <div class="metric-bar"><div class="metric-fill" style="width: ${(session.ram/512)*100}%"></div></div>
        </div>

        <button class="revoke-btn" onclick="this.getRootNode().host.handleRevoke('${session.id}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10" />
          </svg>
          Revoke Perimeter
        </button>
      </div>
    `).join('');

    this.shadowRoot.innerHTML = `
      <style>${styles}</style>
      <div class="header">
        <h1>Instant Sovereignty: The Multi-Agent Kill Switch</h1>
        <p>Agnostic enforcement across OpenAI, Gemini, and Claude</p>
      </div>

      <div class="agent-grid">
        ${this.sessions.length > 0 ? cards : '<div class="empty-state">No active agent sessions detected. Hardware isolation is standby.</div>'}
      </div>
    `;
  }
}

customElements.define('ciso-kill-switch', CisoKillSwitch);
