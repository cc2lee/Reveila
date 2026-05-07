import { ReveilaClient } from './reveila-core.js';

const styles = `
  :host {
    display: block;
    font-family: 'Inter', system-ui, sans-serif;
    color: #334155;
    width: 100%;
  }

  .reveila-table-container {
    padding: 1.5rem;
    background: #ffffff;
    border-radius: 12px;
    box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
    max-width: 100%;
    box-sizing: border-box;
    overflow: hidden;
  }

  .search-bar-group {
    display: flex;
    gap: 12px;
    margin-bottom: 1.5rem;
    align-items: flex-end;
    flex-wrap: wrap;
  }

  .column-select, .search-input, .ok-button {
    height: 42px;
    box-sizing: border-box;
    border: 1px solid #e2e8f0;
    font-size: 0.95rem;
  }

  .column-select {
    padding: 0 12px;
    border-radius: 8px;
    background-color: #f8fafc;
    cursor: pointer;
    min-width: 160px;
  }

  .search-input {
    flex-grow: 1;
    min-width: 200px;
    padding: 0 16px;
    border-radius: 8px;
    transition: border-color 0.2s, box-shadow 0.2s;
  }

  .search-input:focus {
    outline: none;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }

  .ok-button {
    padding: 0 24px;
    background-color: #0f172a;
    color: white;
    border: none;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
    transition: background-color 0.2s, transform 0.1s;
  }

  .ok-button:hover {
    background-color: #1e293b;
  }

  .ok-button:active {
    transform: translateY(1px);
  }

  .ok-button:disabled {
    background-color: #94a3b8;
    cursor: not-allowed;
  }

  .input-wrapper {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .input-label {
    font-size: 0.75rem;
    font-weight: 700;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 0.025em;
  }

  .table-scroll-area {
    width: 100%;
    overflow-x: auto;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    background: #fcfcfd;
  }

  .reveila-table {
    width: 100%;
    border-spacing: 0;
    table-layout: auto;
    min-width: 800px;
  }

  .reveila-table th {
    background: #f8fafc;
    padding: 14px 16px;
    text-align: left;
    border-bottom: 2px solid #e2e8f0;
    color: #475569;
    font-size: 0.8rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    white-space: nowrap;
  }

  .reveila-table td {
    padding: 12px 16px;
    border-bottom: 1px solid #f1f5f9;
    font-size: 0.9rem;
    line-height: 1.5;
    color: #334155;
    vertical-align: top;
  }

  .reveila-table tr:last-child td {
    border-bottom: none;
  }

  .reveila-table tr:hover td {
    background-color: #f1f5f9;
  }

  .id-cell {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    color: #64748b;
    font-size: 0.8rem;
    background: #f8fafc;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid #e2e8f0;
  }

  .risk-badge {
    display: inline-block;
    padding: 4px 10px;
    border-radius: 6px;
    font-weight: 700;
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.025em;
  }

  .risk-high { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
  .risk-medium { background: #fef3c7; color: #92400e; border: 1px solid #fde68a; }
  .risk-low { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }

  .pagination-group {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 1.5rem;
    padding-top: 1rem;
    border-top: 1px solid #f1f5f9;
  }

  .pagination-info {
    font-size: 0.875rem;
    color: #64748b;
  }

  .pagination-controls {
    display: flex;
    gap: 8px;
  }

  .page-btn {
    padding: 6px 12px;
    background: white;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    color: #475569;
    font-size: 0.875rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
  }

  .page-btn:hover:not(:disabled) {
    border-color: #3b82f6;
    color: #3b82f6;
    background-color: #eff6ff;
  }

  .page-btn:disabled {
    color: #cbd5e1;
    cursor: not-allowed;
    background-color: #f8fafc;
  }

  .loading-state, .no-data {
    text-align: center;
    padding: 3rem 2rem;
    color: #94a3b8;
    font-style: italic;
  }

  /* Custom Scrollbar */
  .table-scroll-area::-webkit-scrollbar {
    height: 8px;
  }
  .table-scroll-area::-webkit-scrollbar-track {
    background: #f1f5f9;
  }
  .table-scroll-area::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 4px;
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
        this.currentPage = 0;
        this.hasNext = false;
        this.totalElements = 0;
        this.headers = [];
        this.pollingInterval = 15000;
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
            this.currentPage = 0;
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
        const input = this.shadowRoot.querySelector('.search-input');
        const isInteracting = input && (this.shadowRoot.activeElement === input || input.value !== this.searchQuery);
        
        if (!this.loading && !isInteracting) {
            this.fetchData(this.currentPage, true);
        }
    }

    async fetchData(page = 0, isBackground = false) {
        if (!this.entityType) return;
        this.loading = true;
        this.currentPage = page;

        // Fetch UI configuration
        let displayPriority = [];
        let excludedCols = ['password', 'secret_key'];
        try {
            const configResponse = await fetch(`/api/config/ui?tableId=${this.entityType}`);
            if (configResponse.ok) {
                const config = await configResponse.json();
                if (config && config.table) {
                    if (config.table.displayColumns) displayPriority = config.table.displayColumns;
                    if (config.table.excludeColumns) excludedCols = config.table.excludeColumns;
                }
            }
        } catch (e) {
            console.warn("Could not fetch UI config, using defaults.");
        }
        
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
                page: this.currentPage,
                size: this.pageSize,
                includeCount: true
            };

            const response = await this.api.search(searchRequest);
            this.entities = response.content || [];
            this.hasNext = response.hasNext || false;
            this.totalElements = response.totalElements || 0;
            
            if (this.entities.length > 0 && this.headers.length === 0) {
                const attributes = Object.keys(this.entities[0].attributes || {});
                
                if (displayPriority && displayPriority.length > 0) {
                    // Strict mode: Only show what is in displayColumns
                    this.headers = displayPriority;
                } else {
                    // Fallback: Show all except excluded
                    this.headers = attributes.filter(key => !excludedCols.includes(key));
                }

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

    handleSearch() {
        this.currentPage = 0;
        this.fetchData();
    }

    changePage(delta) {
        const next = this.currentPage + delta;
        if (next >= 0 && (delta < 0 || this.hasNext)) {
            this.fetchData(next);
        }
    }

    formatHeader(key) {
        if (key === 'id') return 'Record ID';
        if (key === 'traceId') return 'Trace ID';
        if (key === 'sessionId') return 'Session ID';
        
        // Convert camelCase to Title Case with Spaces
        const result = key.replace(/([A-Z])/g, " $1");
        return result.charAt(0).toUpperCase() + result.slice(1);
    }

    getRiskBadge(score) {
        if (score === null || score === undefined) return '';
        const num = parseFloat(score);
        let cls = 'risk-low';
        let label = 'Low';
        
        if (num >= 0.8) {
            cls = 'risk-high';
            label = 'High';
        } else if (num >= 0.4) {
            cls = 'risk-medium';
            label = 'Medium';
        }
        
        return `<span class="risk-badge ${cls}">${label} (${num.toFixed(2)})</span>`;
    }

    render() {
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
                    <div class="table-scroll-area"></div>
                    <div class="pagination-area"></div>
                </div>
            `;
            
            this.shadowRoot.querySelector('.search-input').addEventListener('input', (e) => this.searchQuery = e.target.value);
            this.shadowRoot.querySelector('.search-input').addEventListener('keydown', (e) => e.key === 'Enter' && this.handleSearch());
            this.shadowRoot.querySelector('.column-select').addEventListener('change', (e) => this.currentSearchField = e.target.value);
            this.shadowRoot.querySelector('.ok-button').addEventListener('click', () => this.handleSearch());
        }

        const container = this.shadowRoot.querySelector('.reveila-table-container');
        const select = container.querySelector('.column-select');
        const input = container.querySelector('.search-input');
        const okButton = container.querySelector('.ok-button');
        const scrollArea = container.querySelector('.table-scroll-area');
        const paginationArea = container.querySelector('.pagination-area');

        const isDisabled = this.loading || (this.entities.length === 0 && !this.searchQuery);
        
        select.disabled = isDisabled;
        input.disabled = isDisabled;
        okButton.disabled = this.loading;
        okButton.textContent = this.loading ? '...' : 'OK';
        
        if (input.value !== this.searchQuery) {
            input.value = this.searchQuery;
        }

        const currentOptions = Array.from(select.options).map(opt => opt.value).join(',');
        const newOptions = this.headers.join(',');
        if (currentOptions !== newOptions) {
            select.innerHTML = this.headers.map(h => {
                return `<option value="${h}" ${this.currentSearchField === h ? 'selected' : ''}>${this.formatHeader(h)}</option>`;
            }).join('');
        }

        if (this.loading && this.entities.length === 0) {
            scrollArea.innerHTML = `<div class="loading-state">Syncing with Flight Recorder...</div>`;
        } else if (this.entities.length > 0) {
            const headersHtml = this.headers.map(h => `<th>${this.formatHeader(h)}</th>`).join('');

            const rowsHtml = this.entities.map(entity => {
                const attributes = entity.attributes || {};
                return `
                <tr>
                    ${this.headers.map(h => {
                        let value = attributes[h];
                        if (h === 'id' && !value) {
                            value = (entity.key && entity.key.id && entity.key.id.value) || (entity.id && entity.id.id) || 'N/A';
                        }
                        
                        if (h === 'riskScore') {
                            return `<td>${this.getRiskBadge(value)}</td>`;
                        }

                        if (h === 'timestamp' && value) {
                            try {
                                const date = new Date(value);
                                if (!isNaN(date.getTime())) {
                                    value = date.toLocaleString('en-US');
                                }
                            } catch (e) {}
                        }
                        
                        return `<td>${h === 'id' ? `<span class="id-cell">${value}</span>` : `<span>${value || ''}</span>`}</td>`;
                    }).join('')}
                </tr>
                `;
            }).join('');

            scrollArea.innerHTML = `<table class="reveila-table"><thead><tr>${headersHtml}</tr></thead><tbody>${rowsHtml}</tbody></table>`;
            
            const start = (this.currentPage * this.pageSize) + 1;
            const end = start + this.entities.length - 1;
            const total = this.totalElements;

            paginationArea.innerHTML = `
                <div class="pagination-group">
                    <div class="pagination-info">Showing <b>${start}-${end}</b> of <b>${total}</b> records</div>
                    <div class="pagination-controls">
                        <button class="page-btn prev-btn" ${this.currentPage === 0 ? 'disabled' : ''}>Previous</button>
                        <button class="page-btn next-btn" ${!this.hasNext ? 'disabled' : ''}>Next</button>
                    </div>
                </div>
            `;

            paginationArea.querySelector('.prev-btn').addEventListener('click', () => this.changePage(-1));
            paginationArea.querySelector('.next-btn').addEventListener('click', () => this.changePage(1));
        } else {
            scrollArea.innerHTML = `<div class="no-data">No records found for "${this.entityType}"</div>`;
            paginationArea.innerHTML = '';
        }
    }
}

customElements.define('reveila-table', ReveilaTable);
