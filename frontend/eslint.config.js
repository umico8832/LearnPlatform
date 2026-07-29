import pluginVue from 'eslint-plugin-vue'
import vueTsConfigs from '@vue/eslint-config-typescript'

export default [
  {
    ignores: [
      'coverage/**',
      'dist/**',
      'node_modules/**',
      'playwright-report/**',
      'test-results/**',
      'src/auto-imports.d.ts',
      'src/components.d.ts',
    ],
  },
  ...pluginVue.configs['flat/essential'],
  ...vueTsConfigs(),
  {
    files: ['src/**/*.{ts,vue}', 'e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'vue/multi-word-component-names': 'off',
    },
  },
]
