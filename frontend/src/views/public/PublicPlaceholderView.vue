<script setup lang="ts">
import { LtLinkButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { isAuthenticated } from '@/utils/auth'

/** 首页导航上尚未写好的公开页面（ADR-0011 新基线）：如实说明还在整理，并给出两条去路。 */
defineProps<{ title: string }>()
useLivingTheme()

const learningDestination = isAuthenticated() ? '/my-courses' : '/register'
</script>

<template>
  <main class="placeholder">
    <div class="content">
      <p class="status">正在整理</p>
      <h1 class="title">{{ title }}</h1>
      <p class="message">这一页的介绍还在写。可以先回首页看一道题是怎么讲的，或者直接开始学习。</p>
      <div class="actions">
        <LtLinkButton size="lg" to="/">返回首页</LtLinkButton>
        <LtLinkButton variant="primary" size="lg" :to="learningDestination">开始学习</LtLinkButton>
      </div>
    </div>
  </main>
</template>

<style scoped>
.placeholder {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  padding: var(--lt-space-8);
  background: var(--lt-canvas);
  color: var(--lt-label);
  text-align: center;
}

.content {
  max-width: 34em;
}

.status {
  margin: 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.title {
  margin: var(--lt-space-3) 0 0;
  font-size: var(--lt-text-display);
  letter-spacing: var(--lt-tracking-title);
}

.message {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.actions {
  display: flex;
  justify-content: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-8);
}
</style>
