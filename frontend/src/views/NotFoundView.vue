<template>
  <main class="not-found">
    <h1 class="not-found-code">404</h1>
    <p class="not-found-message">这个地址不存在，或内容已移动。</p>
    <div class="not-found-actions">
      <router-link v-slot="{ href, navigate }" to="/" custom>
        <el-button tag="a" type="primary" :href="href" @click="navigate">返回首页</el-button>
      </router-link>
      <router-link v-slot="{ href, navigate }" to="/courses" custom>
        <el-button tag="a" :href="href" @click="navigate">查看课程</el-button>
      </router-link>
    </div>
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

.not-found-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lp-space-3);
}

.not-found-message {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-body);
}
</style>
