<template>
  <main class="not-found">
    <h1 class="not-found-code">404</h1>
    <p class="not-found-message">页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-4);
  padding: var(--lp-space-6);
  text-align: center;
  background: var(--lp-bg);
}

.not-found-code {
  margin: 0;
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-5xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-display);
  letter-spacing: var(--lp-tracking-tight);
  color: var(--lp-text);
}

.not-found-message {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-body);
}
</style>
