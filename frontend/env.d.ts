/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'element-plus/dist/locale/zh-cn.mjs'

declare module '*QuestionVisualMermaid.vue?retry=1' {
  const component: (typeof import('./src/components/question-visual/QuestionVisualMermaid.vue'))['default']
  export default component
}
