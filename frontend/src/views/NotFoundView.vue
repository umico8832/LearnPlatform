<script setup lang="ts">
import { LtLinkButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { isAuthenticated } from '@/utils/auth'

/**
 * 404 on the new baseline (dark only). A short learning path whose last step leads
 * nowhere stands in for the missing page; the actions lead back to real places.
 */
useLivingTheme({ dark: true })

const signedIn = isAuthenticated()
</script>

<template>
  <main class="not-found lt" data-appearance="dark">
    <svg class="path" viewBox="0 0 232 64" aria-hidden="true">
      <line x1="16" y1="40" x2="84" y2="22" class="edge" />
      <line x1="84" y1="22" x2="152" y2="40" class="edge" />
      <line x1="152" y1="40" x2="214" y2="24" class="edge edge-lost" />
      <circle cx="16" cy="40" r="6" class="node" />
      <circle cx="84" cy="22" r="6" class="node" />
      <circle cx="152" cy="40" r="6" class="node" />
      <circle cx="216" cy="24" r="7" class="node-lost" />
    </svg>
    <p class="code">404</p>
    <h1 class="title">这个地址不存在</h1>
    <p class="text">可能是链接写错了，或者内容已经移动。</p>
    <div class="actions">
      <LtLinkButton v-if="signedIn" variant="primary" to="/my-courses">回到我的课程</LtLinkButton>
      <LtLinkButton :variant="signedIn ? 'plain' : 'primary'" to="/">返回首页</LtLinkButton>
      <LtLinkButton v-if="!signedIn" to="/courses">查看课程</LtLinkButton>
    </div>
  </main>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--lt-space-7);
  background: var(--lt-canvas);
  text-align: center;
}

.path {
  width: 232px;
  height: 64px;
  overflow: visible;
}

.edge {
  stroke: var(--lt-fill);
  stroke-width: 2;
  stroke-linecap: round;
}

.edge-lost {
  stroke: var(--lt-label-3);
  stroke-dasharray: 2 6;
  opacity: 0.6;
}

.node {
  fill: var(--lt-label-3);
}

.node-lost {
  fill: none;
  stroke: var(--lt-orange);
  stroke-width: 2;
  stroke-dasharray: 3 4;
}

.code {
  margin: var(--lt-space-7) 0 0;
  color: var(--lt-label-3);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-subhead);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.08em;
}

.title {
  margin: var(--lt-space-2) 0 0;
  font-size: var(--lt-text-title1);
}

.text {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-7);
}
</style>
