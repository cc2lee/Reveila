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
  }

  .column-select {
    padding: 10px;
    border-radius: 4px;
    border: 1px solid #ccc;
    background-color: #f9f9f9;
    cursor: pointer;
  }

  .search-input {
    flex-grow: 1;
    padding: 10px 15px;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 0.875rem;
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
        this.debounceTimer = null;
    }

    static get observedAttributes() {
        return ['entity-type', 'search-field', 'page-size'];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (oldValue === newValue) return;
        if (name === 'entity-type') this.entityType = newValue;
        if (name === 'search-field') this.currentSearchField = newValue;
        if (name === 'page-size') this.pageSize = parseInt(newValue) || 10;
        
        if (this.isConnected) {
            this.fetchData();
        }
    }

    connectedCallback() {
        this.entityType = this.getAttribute('entity-type') || '';
        this.currentSearchField = this.getAttribute('search-field') || 'name';
        this.pageSize = parseInt(this.getAttribute('page-size')) || 10;
        this.fetchData();
    }

    async fetchData() {
        if (!this.entityType) return;
        this.loading = true;
        this.render();

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
            
            if (this.entities.length > 0 && this.headers.length === 0) {
                const excluded = ['password', 'secret_key'];
                this.headers = Object.keys(this.entities[0].attributes)
                    .filter(key => !excluded.includes(key));
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
        if (this.debounceTimer) clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => this.fetchData(), 300);
    }

    handleFieldChange(e) {
        this.currentSearchField = e.target.value;
        if (this.searchQuery) {
            this.fetchData();
        } else {
            this.render();
        }
    }

    render() {
        const headersHtml = this.headers.map(h => `
            <th>${h.charAt(0).toUpperCase() + h.slice(1)}</th>
        `).join('');

        const rowsHtml = this.entities.map(entity => `
            <tr>
                ${this.headers.map(h => `
                    <td>
                        ${h === 'id' ? `<span class="id-cell">${entity.id.id}</span>` : `<span>${entity.attributes[h] || ''}</span>`}
                    </td>
                `).join('')}
            </tr>
        `).join('');

        const optionsHtml = this.headers.map(h => `
            <option value="${h}" ${this.currentSearchField === h ? 'selected' : ''}>
                ${h.charAt(0).toUpperCase() + h.slice(1)}
            </option>
        `).join('');

        this.shadowRoot.innerHTML = `
            <style>${styles}</style>
            <div class="reveila-table-container">
                <div class="search-bar-group">
                    <div class="input-wrapper">
                        <label class="input-label">Search Field</label>
                        <select class="column-select">
                            ${optionsHtml}
                        </select>
                    </div>

                    <div class="input-wrapper" style="flex-grow: 1;">
                        <label class="input-label">Filter Value</label>
                        <input type="text" class="search-input" 
                            value="${this.searchQuery}"
                            placeholder="Type to filter ${this.entityType} records...">
                    </div>
                </div>

                ${this.loading ? `
                    <div class="loading-state">Loading...</div>
                ` : this.entities.length > 0 ? `
                    <table class="reveila-table">
                        <thead>
                            <tr>${headersHtml}</tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                ` : `
                    <div class="no-data">No results found for "${this.entityType}"</div>
                `}
            </div>
        `;

        this.shadowRoot.querySelector('.search-input').addEventListener('input', (e) => this.handleSearchInput(e));
        this.shadowRoot.querySelector('.column-select').addEventListener('change', (e) => this.handleFieldChange(e));
    }
}

customElements.define('reveila-table', ReveilaTable);
