import { ReveilaClient } from './reveila-core.js';

const styles = `
  :host {
    display: block;
    font-family: 'Inter', system-ui, sans-serif;
    color: #334155;
  }

  .reveila-table-container {
    padding: 1rem;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  }

  .search-bar-group {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
    align-items: flex-end;
  }

  .column-select, .search-input, .ok-button {
    height: 38px;
    box-sizing: border-box;
  }

  .column-select {
    padding: 0 10px;
    border-radius: 4px;
    border: 1px solid #ccc;
    background-color: #f9f9f9;
    cursor: pointer;
  }

  .search-input {
    flex-grow: 1;
    padding: 0 15px;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 0.875rem;
  }

  .ok-button {
    padding: 0 20px;
    background-color: #0f172a;
    color: white;
    border: 1px solid #0f172a;
    border-radius: 4px;
    font-weight: 600;
    cursor: pointer;
    transition: background-color 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .ok-button:hover {
    background-color: #1e293b;
  }

  .ok-button:disabled {
    background-color: #94a3b8;
    cursor: not-allowed;
  }

  .input-wrapper {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .input-label {
    font-size: 0.75rem;
    font-weight: 600;
    color: #64748b;
    text-transform: uppercase;
  }

  .reveila-table {
    width: 100%;
    border-spacing: 0;
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    overflow: hidden;
  }

  .reveila-table th {
    background: #f8fafc;
    padding: 12px;
    text-align: left;
    border-bottom: 2px solid #e2e8f0;
    color: #64748b;
    font-size: 0.875rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .reveila-table td {
    padding: 10px 12px;
    border-bottom: 1px solid #f1f5f9;
    font-size: 0.875rem;
  }

  .id-cell {
    font-family: monospace;
    color: #64748b;
  }

  .loading-state, .no-data {
    text-align: center;
    padding: 2rem;
    color: #94a3b8;
  }
`;

export class ReveilaTable extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.api = new ReveilaClient({ baseURL: '' });
        this.entities = [];
        this.loading = false;
        this.searchQuery = '';
        this.currentSearchField = '';
        this.entityType = '';
        this.pageSize = 10;
        this.headers = [];
        this.pollingInterval = 15000; // 15s default
        this.pollingTimer = null;
    }

    static get observedAttributes() {
        return ['entity-type', 'search-field', 'page-size', 'refresh-interval'];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (oldValue === newValue) return;
        if (name === 'entity-type') this.entityType = newValue;
        if (name === 'search-field') this.currentSearchField = newValue;
        if (name === 'page-size') this.pageSize = parseInt(newValue) || 10;
        if (name === 'refresh-interval') {
            this.pollingInterval = parseInt(newValue) || 15000;
            this.startPolling();
        }
        
        if (this.isConnected) {
            this.fetchData();
        }
    }

    connectedCallback() {
        this.entityType = this.getAttribute('entity-type') || '';
        this.currentSearchField = this.getAttribute('search-field') || 'name';
        this.pageSize = parseInt(this.getAttribute('page-size')) || 10;
        this.pollingInterval = parseInt(this.getAttribute('refresh-interval')) || 15000;
        this.fetchData();
        this.startPolling();
    }

    disconnectedCallback() {
        this.stopPolling();
    }

    startPolling() {
        this.stopPolling();
        if (this.pollingInterval > 0) {
            this.pollingTimer = setInterval(() => this.smartFetch(), this.pollingInterval);
        }
    }

    stopPolling() {
        if (this.pollingTimer) {
            clearInterval(this.pollingTimer);
            this.pollingTimer = null;
        }
    }

    smartFetch() {
        // Only fetch if:
        // 1. Not already loading
        // 2. Search input is not focused (user is not interacting)
        // 3. Current input value matches the last searched query (no pending manual search)
        const input = this.shadowRoot.querySelector('.search-input');
        const isInteracting = input && (this.shadowRoot.activeElement === input || input.value !== this.searchQuery);
        
        if (!this.loading && !isInteracting) {
            this.fetchData(true); // isBackground = true
        }
    }

    async fetchData(isBackground = false) {
        if (!this.entityType) return;
        this.loading = true;
        
        // Only render loading state if not a background fetch or if we have no data yet
        if (!isBackground || this.entities.length === 0) {
            this.render();
        }

        try {
            const searchRequest = {
                entityType: this.entityType,
                filter: {
                    conditions: this.searchQuery ? {
                        [this.currentSearchField]: { value: `%${this.searchQuery}%`, operator: 'LIKE' }
                    } : {},
                    logicalOp: 'AND'
                },
                page: 0,
                size: this.pageSize
            };

            const response = await this.api.search(searchRequest);
            this.entities = response.content || [];
            
            // Auto-detect headers if not set
            if (this.entities.length > 0 && this.headers.length === 0) {
                const excluded = ['password', 'secret_key'];
                this.headers = Object.keys(this.entities[0].attributes || {})
                    .filter(key => !excluded.includes(key));
                
                // If no attributes found but we have entities, try to use keys from the entity itself as fallback
                if (this.headers.length === 0) {
                    this.headers = ['id', 'action', 'timestamp', 'traceId'].filter(h => h in this.entities[0].attributes || h === 'id');
                }
            }
        } catch (err) {
            console.error("Reveila Search Error:", err);
        } finally {
            this.loading = false;
            this.render();
        }
    }

    handleSearchInput(e) {
        this.searchQuery = e.target.value;
    }

    handleKeyDown(e) {
        if (e.key === 'Enter') {
            this.fetchData();
        }
    }

    handleFieldChange(e) {
        this.currentSearchField = e.target.value;
    }

    render() {
        // 1. Initial structure setup if not present
        if (!this.shadowRoot.querySelector('.reveila-table-container')) {
            this.shadowRoot.innerHTML = `
                <style>${styles}</style>
                <div class="reveila-table-container">
                    <div class="search-bar-group">
                        <div class="input-wrapper">
                            <label class="input-label">Search Field</label>
                            <select class="column-select"></select>
                        </div>
                        <div class="input-wrapper" style="flex-grow: 1;">
                            <label class="input-label">Filter Value</label>
                            <input type="text" class="search-input">
                        </div>
                        <button class="ok-button">OK</button>
                    </div>
                    <div class="table-content-area"></div>
                </div>
            `;
            
            // Initial event listeners
            this.shadowRoot.querySelector('.search-input').addEventListener('input', (e) => this.handleSearchInput(e));
            this.shadowRoot.querySelector('.search-input').addEventListener('keydown', (e) => this.handleKeyDown(e));
            this.shadowRoot.querySelector('.column-select').addEventListener('change', (e) => this.handleFieldChange(e));
            this.shadowRoot.querySelector('.ok-button').addEventListener('click', () => this.fetchData());
        }

        // 2. Reference elements
        const container = this.shadowRoot.querySelector('.reveila-table-container');
        const select = container.querySelector('.column-select');
        const input = container.querySelector('.search-input');
        const okButton = container.querySelector('.ok-button');
        const contentArea = container.querySelector('.table-content-area');

        // 3. Update Search Controls State
        // Logic: Disable if loading OR if no results and no query (initial empty state)
        const isDisabled = this.loading || (this.entities.length === 0 && !this.searchQuery);
        
        select.disabled = isDisabled;
        input.disabled = isDisabled;
        okButton.disabled = this.loading; // Button only disabled during loading
        okButton.textContent = this.loading ? '...' : 'OK';
        
        // Only update input value if it's different to avoid cursor jumps
        if (input.value !== this.searchQuery) {
            input.value = this.searchQuery;
        }
        input.placeholder = `Type and press OK/Enter to filter records...`;

        // Update Select options only if they changed
        const currentOptions = Array.from(select.options).map(opt => opt.value).join(',');
        const newOptions = this.headers.join(',');
        if (currentOptions !== newOptions) {
            select.innerHTML = this.headers.map(h => {
                let label = h.charAt(0).toUpperCase() + h.slice(1);
                if (h === 'id') label = 'Record ID';
                if (h === 'traceId') label = 'Trace ID';
                return `<option value="${h}" ${this.currentSearchField === h ? 'selected' : ''}>${label}</option>`;
            }).join('');
        } else if (select.value !== this.currentSearchField) {
            select.value = this.currentSearchField;
        }

        // 4. Update Table Content
        // Only show loading state if we have no data at all
        if (this.loading && this.entities.length === 0) {
            contentArea.innerHTML = `<div class="loading-state">Loading...</div>`;
        } else if (this.entities.length > 0) {
            const headersHtml = this.headers.map(h => {
                let label = h.charAt(0).toUpperCase() + h.slice(1);
                if (h === 'id') label = 'Record ID';
                if (h === 'traceId') label = 'Trace ID';
                return `<th>${label}</th>`;
            }).join('');

            const rowsHtml = this.entities.map(entity => {
                const attributes = entity.attributes || {};
                return `
                <tr>
                    ${this.headers.map(h => {
                        let value = attributes[h];
                        if (h === 'id' && !value) {
                            value = (entity.key && entity.key.id && entity.key.id.value) || (entity.id && entity.id.id) || 'N/A';
                        }
                        return `<td>${h === 'id' ? `<span class="id-cell">${value}</span>` : `<span>${value || ''}</span>`}</td>`;
                    }).join('')}
                </tr>
                `;
            }).join('');

            contentArea.innerHTML = `
                <table class="reveila-table">
                    <thead><tr>${headersHtml}</tr></thead>
                    <tbody>${rowsHtml}</tbody>
                </table>
            `;
        } else {
            contentArea.innerHTML = `<div class="no-data">No results found for "${this.entityType}"</div>`;
        }
    }
}

customElements.define('reveila-table', ReveilaTable);
