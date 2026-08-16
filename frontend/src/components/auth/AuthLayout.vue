<template>
  <div class="auth-page">
    <div class="auth-background" aria-hidden="true"><span></span><span></span></div>
    <header class="auth-header">
      <router-link to="/login" class="auth-logo" aria-label="LearnPlatform 登录页">
        <span class="logo-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
            <path
              d="M4 19.5V5.5a1.5 1.5 0 0 1 1.5-1.5H18a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1-1.5 1.5H7l-3 1.5Z"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path d="M8 9h8M8 12.5h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </span>
        <span>LearnPlatform</span>
      </router-link>
      <router-link v-if="alternateTo" :to="alternateTo" class="alternate-link">{{ alternateText }}</router-link>
    </header>
    <main class="auth-main">
      <section class="auth-brand" aria-label="产品介绍">
        <p class="brand-kicker">LEARNPLATFORM</p>
        <slot name="brand">
          <h1>安静、严谨地<br /><span>学好每一门课。</span></h1>
          <p class="brand-description">
            以 408
            数据结构课程为中心的学习环境：知识讲解、互动课件、练习、错题、复习与测评，构成同一条可追踪的学习闭环。
          </p>
        </slot>
        <div class="capability-list">
          <div>
            <el-icon><Reading /></el-icon><span><strong>课程学习</strong>从我的课程继续，始终清楚下一步</span>
          </div>
          <div>
            <el-icon><EditPen /></el-icon><span><strong>真实判分</strong>作答、错题与复习记录来自服务端事实</span>
          </div>
          <div>
            <el-icon><DataAnalysis /></el-icon><span><strong>真题与测评</strong>官方来源可核验，复盘可追溯</span>
          </div>
        </div>
      </section>
      <section class="auth-card" :aria-labelledby="titleId">
        <slot />
      </section>
    </main>
    <footer>© {{ new Date().getFullYear() }} LearnPlatform · 数字教材式学习环境</footer>
  </div>
</template>

<script setup lang="ts">
import { DataAnalysis, EditPen, Reading } from '@element-plus/icons-vue'

withDefaults(defineProps<{ alternateTo?: string; alternateText?: string; titleId?: string }>(), {
  alternateTo: '',
  alternateText: '',
  titleId: 'auth-title',
})
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--lp-bg);
  color: var(--lp-text);
}
.auth-background {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(var(--lp-paper-200) 1px, transparent 1px),
    linear-gradient(90deg, var(--lp-paper-200) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: linear-gradient(to bottom, black, transparent 78%);
}
.auth-background span {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
}
.auth-background span:nth-child(1) {
  width: 30rem;
  height: 30rem;
  background: var(--lp-blue-100);
  left: -12rem;
  top: -14rem;
}
.auth-background span:nth-child(2) {
  width: 26rem;
  height: 26rem;
  background: var(--lp-gold-100);
  right: -14rem;
  bottom: -14rem;
  opacity: 0.7;
}
.auth-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px clamp(20px, 5vw, 72px);
}
.auth-logo {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 18px;
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-tight);
  color: var(--lp-text);
}
.logo-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--lp-radius-md);
  color: var(--lp-paper-0);
  background: var(--lp-primary);
}
.alternate-link {
  padding: 9px 14px;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  background: var(--lp-surface);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}
.alternate-link:hover,
.alternate-link:focus-visible {
  border-color: var(--lp-primary);
  color: var(--lp-primary);
  outline: none;
}
.auth-main {
  position: relative;
  z-index: 1;
  flex: 1;
  width: min(1080px, calc(100% - 40px));
  margin: auto;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 420px);
  gap: clamp(48px, 8vw, 96px);
  align-items: center;
  padding: 36px 0 64px;
}
.brand-kicker {
  margin: 0 0 16px;
  color: var(--lp-primary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: 0.18em;
}
.auth-brand h1 {
  margin: 0;
  font-family: var(--lp-font-display);
  font-size: clamp(34px, 4.6vw, 54px);
  font-weight: var(--lp-weight-bold);
  line-height: 1.14;
  letter-spacing: -0.02em;
  color: var(--lp-text);
}
.auth-brand h1 span {
  color: var(--lp-primary);
}
.brand-description {
  max-width: 520px;
  margin: 20px 0 28px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
}
.capability-list {
  display: grid;
  gap: 10px;
  max-width: 500px;
}
.capability-list > div {
  display: flex;
  gap: 13px;
  align-items: flex-start;
  padding: 13px 15px;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  line-height: 1.55;
}
.capability-list .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--lp-primary);
  font-size: 18px;
}
.capability-list strong {
  display: block;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.auth-card {
  position: relative;
  padding: 34px;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-md);
}
footer {
  position: relative;
  z-index: 1;
  padding: 18px;
  color: var(--lp-text-muted);
  text-align: center;
  font-size: var(--lp-text-xs);
}
@media (max-width: 820px) {
  .auth-main {
    grid-template-columns: 1fr;
    width: min(100% - 32px, 480px);
    padding-top: 18px;
  }
  .auth-brand {
    display: none;
  }
  .auth-card {
    padding: 28px;
  }
  .auth-header {
    padding: 18px 20px;
  }
}
@media (max-width: 430px) {
  .auth-main {
    width: calc(100% - 24px);
  }
  .auth-card {
    padding: 24px 20px;
  }
  .alternate-link {
    padding: 8px 10px;
  }
  .auth-logo {
    font-size: 17px;
  }
}
</style>
