<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>

<style>
/* Global resets for the Reveila Fabric look */
body {
  margin: 0;
  padding: 0;
  background-color: #f1f5f9;
}

#app {
  max-width: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
}
</style>
