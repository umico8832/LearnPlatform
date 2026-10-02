/**
 * LearnPlatform UI 基础组件注册表。
 * 在 main.ts 中全局注册，页面可直接使用 Lp* 组件。
 */
import type { App, Component } from 'vue'
import LpKicker from './LpKicker.vue'
import LpPageHeader from './LpPageHeader.vue'
import LpSectionHeading from './LpSectionHeading.vue'
import LpStat from './LpStat.vue'
import LpEmptyState from './LpEmptyState.vue'
import LpDivider from './LpDivider.vue'
import LpSignal from './LpSignal.vue'
import LpProgress from './LpProgress.vue'
import LpProgressRing from './LpProgressRing.vue'
import LpSkeleton from './LpSkeleton.vue'
import LpCard from './LpCard.vue'
import LpListItem from './LpListItem.vue'
import LpStatePanel from './LpStatePanel.vue'

const components: Record<string, Component> = {
  LpKicker,
  LpPageHeader,
  LpSectionHeading,
  LpStat,
  LpEmptyState,
  LpDivider,
  LpSignal,
  LpProgress,
  LpProgressRing,
  LpSkeleton,
  LpCard,
  LpListItem,
  LpStatePanel,
}

export function registerUiComponents(app: App) {
  for (const [name, component] of Object.entries(components)) {
    app.component(name, component)
  }
}

export {
  LpKicker,
  LpPageHeader,
  LpSectionHeading,
  LpStat,
  LpEmptyState,
  LpDivider,
  LpSignal,
  LpProgress,
  LpProgressRing,
  LpSkeleton,
  LpCard,
  LpListItem,
  LpStatePanel,
}
