import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import vueParser from 'vue-eslint-parser'

const typeScriptRecommendedForVue = tseslint.configs.recommended.map((config) =>
  config.files?.includes('**/*.ts') ? { ...config, files: [...config.files, '**/*.vue'] } : config,
)

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
  ...typeScriptRecommendedForVue,
  ...pluginVue.configs['flat/base'],
  {
    name: 'learnplatform/vue-typescript-setup',
    files: ['*.vue', '**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: {
          js: 'espree',
          jsx: 'espree',
          ts: tseslint.parser,
          tsx: tseslint.parser,
        },
        ecmaVersion: 2024,
        ecmaFeatures: { jsx: false },
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      'vue/block-lang': ['error', { script: { lang: ['ts'], allowNoLang: false } }],
    },
  },
  {
    files: ['src/**/*.{ts,vue}', 'e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'vue/multi-word-component-names': 'off',
    },
  },
]
