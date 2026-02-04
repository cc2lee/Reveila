<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { ReveilaClient } from '@reveila/core';

const props = defineProps({
    entityType: { type: String, required: true },
    searchField: { type: String, default: 'name' }, // Default prop
    pageSize: { type: Number, default: 10 }
});

const api = new ReveilaClient({ baseURL: '' });
const loading = ref(true);
const searchQuery = ref('');

// 3. User-selectable search field, initialized by prop
const currentSearchField = ref(props.searchField);

/** @type {import('vue').Ref<Array<{id: {id: string}, attributes: Record<string, any>}>>} */
const entities = ref([]);

const headers = computed(() => {
    if (entities.value.length === 0) return [];
    const excluded = ['password', 'secret_key'];
    return Object.keys(entities.value[0].attributes)
        .filter(key => !excluded.includes(key));
});

// 4. Watch for column changes to trigger a re-search if text exists
watch(currentSearchField, () => {
    if (searchQuery.value) fetchData();
});

let debounceTimer;
const debouncedSearch = () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => fetchData(), 300);
};

const fetchData = async () => {
    loading.value = true;
    try {
        const searchRequest = {
            entityType: props.entityType,
            filter: {
                conditions: searchQuery.value ? {
                    // DYNAMIC KEY: Uses the value from the dropdown
                    [currentSearchField.value]: { value: `%${searchQuery.value}%`, operator: 'LIKE' }
                } : {},
                logicalOp: 'AND'
            },
            page: 0,
            size: props.pageSize
        };

        const response = await api.search(searchRequest);
        entities.value = response.content || [];
    } catch (err) {
        console.error("Reveila Search Error:", err);
    } finally {
        loading.value = false;
    }
};

onMounted(fetchData);
</script>

<template>
    <div class="reveila-table-container">
        <div class="search-bar-group">
            <select v-model="currentSearchField" class="column-select">
                <option v-for="h in headers" :key="h" :value="h">
                    Search by {{ h.charAt(0).toUpperCase() + h.slice(1) }}
                </option>
            </select>

            <input v-model="searchQuery" @input="debouncedSearch" :placeholder="`Filter ${entityType}...`"
                class="search-input" />
        </div>

        <table v-if="entities.length > 0" class="reveila-table">
            <thead>
                <tr>
                    <th v-for="h in headers" :key="h">
                        {{ h.charAt(0).toUpperCase() + h.slice(1) }}
                    </th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="entity in entities" :key="entity.id.id">
                    <td v-for="h in headers" :key="h">
                        <span v-if="h === 'id'" class="id-cell">{{ entity.id.id }}</span>
                        <span v-else>{{ entity.attributes[h] }}</span>
                    </td>
                </tr>
            </tbody>
        </table>
        <div v-else-if="!loading" class="no-data">
            No results found for "{{ entityType }}"
        </div>
        <div v-else class="loading-state">
            Loading...
        </div>
    </div>
</template>

<style scoped>
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
}

.reveila-table {
    width: 100%;
    border-spacing: 0;
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    overflow: hidden;
}

.reveila-table th {
    background: #f5f5f5;
    padding: 12px;
    text-align: left;
    border-bottom: 2px solid #ddd;
}

.reveila-table td {
    padding: 10px 12px;
    border-bottom: 1px solid #eee;
}

.id-cell {
    font-family: monospace;
    color: #666;
}
</style>