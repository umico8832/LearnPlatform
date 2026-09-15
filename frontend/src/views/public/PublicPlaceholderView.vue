<template>
  <main class="public-placeholder">
    <div class="public-placeholder__content">
      <p class="public-placeholder__status">开发中</p>
      <h1>{{ title }}</h1>
      <p class="public-placeholder__message">本页面正在开发中，更多内容将陆续上线。</p>
      <div class="public-placeholder__actions">
        <RouterLink class="public-placeholder__link" to="/">返回首页</RouterLink>
        <RouterLink class="public-placeholder__link public-placeholder__link--primary" :to="learningDestination">
          开始学习
        </RouterLink>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'

defineProps<{ title: string }>()

const learningDestination = isAuthenticated() ? '/my-courses' : '/register'
</script>

<style scoped>
.public-placeholder {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: var(--lp-space-8);
  background: var(--lp-bg);
  color: var(--lp-text);
  text-align: center;
}

.public-placeholder__content {
  width: min(100%, 42rem);
}

.public-placeholder__status {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-primary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

h1 {
  margin: 0;
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-5xl);
  font-weight: var(--lp-weight-regular);
  line-height: var(--lp-leading-display);
}

.public-placeholder__message {
  margin: var(--lp-space-4) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-body);
}

.public-placeholder__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-8);
}

.public-placeholder__link {
  display: inline-flex;
  min-height: var(--lp-space-12);
  align-items: center;
  justify-content: center;
  padding: 0 var(--lp-space-5);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-full);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  text-decoration: none;
}

.public-placeholder__link:focus-visible {
  outline: 2px solid var(--lp-primary);
  outline-offset: 2px;
}

.public-placeholder__link--primary {
  border-color: var(--lp-primary);
  background: var(--lp-primary);
  color: var(--lp-paper-0);
}
</style>
