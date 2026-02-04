<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ReveilaClient } from '@reveila/core';

const users = ref([]);
const loading = ref(false);

const fetchUsers = async () => {
    loading.value = true;
    try {
        const searchRequest = {
            entityType: 'user',
            filter: {
                conditions: {
                    'active': { value: true, operator: 'EQUAL' }
                },
                logicalOp: 'AND'
            },
            sort: { field: 'username', ascending: true },
            fetches: ['org'], // Eagerly load the organization info
            page: 0,
            size: 20,
            includeCount: true
        };

        const response = await ReveilaClient.search(searchRequest);
        users.value = response.content; // 'content' comes from the Page<Entity> object
    } finally {
        loading.value = false;
    }
};

onMounted(fetchUsers);
</script>

<template>
    <div v-if="loading">Loading...</div>
    <table v-else>
        <tr v-for="user in users" :key="user.id.id">
            <td>{{ user.attributes.username }}</td>
            <td>{{ user.attributes.org?.attributes?.name }}</td>
        </tr>
    </table>
</template>