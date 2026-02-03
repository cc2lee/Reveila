import { globalIgnores } from 'eslint/config'
import { defineConfigWithVueTs, vueTsConfigs, configureVueProject } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import pluginVitest from '@vitest/eslint-plugin'
import pluginPlaywright from 'eslint-plugin-playwright'
import pluginOxlint from 'eslint-plugin-oxlint'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

/**
 * This tells the TypeScript plugin that it's okay to find plain JavaScript 
 * inside the .vue files.
 */
configureVueProject({ scriptLangs: ['ts', 'tsx', 'js', 'jsx'] })

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    // ADDED 'js' and 'mjs' to the patterns so your service files are included
    files: ['**/*.{js,mjs,ts,mts,tsx,vue}'],
  },

  globalIgnores(['**/dist/**', '**/dist-ssr/**', '**/coverage/**']),

  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  
  {
    ...pluginVitest.configs.recommended,
    files: ['src/**/__tests__/*'],
  },
  
  {
    ...pluginPlaywright.configs['flat/recommended'],
    files: ['e2e/**/*.{test,spec}.{js,ts,jsx,tsx}'],
  },
  
  // Custom Rule Overrides
  {
    rules: {
      // Allows <script setup> without an explicit lang="ts"
      'vue/block-lang': ['error', {
        'script': {
          'lang': ['ts', 'js'],
          'allowNoLang': true
        }
      }],
      // Relaxes strict TS checking for your JS-heavy hybrid files
      '@typescript-eslint/no-explicit-any': 'off'
    }
  },

  ...pluginOxlint.configs['flat/recommended'],
  skipFormatting,
)