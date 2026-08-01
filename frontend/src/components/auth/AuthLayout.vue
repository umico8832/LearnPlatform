<template>
  <div class="auth-page">
    <div class="auth-background" aria-hidden="true"><span></span><span></span><span></span></div>
    <header class="auth-header">
      <router-link to="/login" class="auth-logo" aria-label="LearnPlatform 登录页">
        <span class="logo-mark"
          ><el-icon><Reading /></el-icon
        ></span>
        <span>LearnPlatform</span>
      </router-link>
      <router-link v-if="alternateTo" :to="alternateTo" class="alternate-link">{{ alternateText }}</router-link>
    </header>
    <main class="auth-main">
      <section class="auth-brand" aria-label="产品介绍">
        <p class="brand-kicker">AI LEARNING WORKBENCH</p>
        <slot name="brand">
          <h1>把每一次练习，<br /><span>变成下一次进步。</span></h1>
          <p class="brand-description">题库、错题、考试与 AI 学习辅助集中在一个可靠的学习工作台。</p>
        </slot>
        <div class="capability-list">
          <div>
            <el-icon><EditPen /></el-icon><span><strong>真实练习闭环</strong>判分、错题和复习持续衔接</span>
          </div>
          <div>
            <el-icon><DataAnalysis /></el-icon><span><strong>学习诊断</strong>从记录中识别薄弱知识点</span>
          </div>
          <div>
            <el-icon><MagicStick /></el-icon><span><strong>AI 学习辅助</strong>解析、变式与个性化建议</span>
          </div>
        </div>
      </section>
      <section class="auth-card" :aria-labelledby="titleId">
        <div class="card-accent" aria-hidden="true"></div>
        <slot />
      </section>
    </main>
    <footer>© {{ new Date().getFullYear() }} LearnPlatform · 专注真实学习闭环</footer>
  </div>
</template>

<script setup lang="ts">
import { DataAnalysis, EditPen, MagicStick, Reading } from '@element-plus/icons-vue'

withDefaults(defineProps<{ alternateTo?: string; alternateText?: string; titleId?: string }>(), {
  alternateTo: '',
  alternateText: '',
  titleId: 'auth-title',
})
</script>

<style scoped>
.auth-page {
  --auth-bg: #101512;
  --auth-surface: rgba(23, 30, 26, 0.92);
  --auth-surface-soft: rgba(28, 38, 32, 0.68);
  --auth-input: rgba(15, 22, 18, 0.78);
  --auth-text: #f3f7f4;
  --auth-text-strong: #ffffff;
  --auth-muted: #a2afa7;
  --auth-subtle: #77867d;
  --auth-border: rgba(184, 204, 191, 0.16);
  --auth-border-strong: rgba(184, 204, 191, 0.28);
  --auth-primary: #237257;
  --auth-primary-hover: #2c8465;
  --auth-accent: #73d6aa;
  --auth-accent-soft: rgba(115, 214, 170, 0.14);
  --auth-warm: #d4b26c;
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--auth-bg);
  color: var(--auth-text);
}
.auth-background {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(208, 224, 214, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(208, 224, 214, 0.035) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: linear-gradient(to bottom, black, transparent 86%);
}
.auth-background span {
  position: absolute;
  width: 36rem;
  height: 36rem;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.16;
}
.auth-background span:nth-child(1) {
  background: #28745a;
  left: -14rem;
  top: -16rem;
}
.auth-background span:nth-child(2) {
  background: #345a46;
  right: -18rem;
  bottom: -18rem;
}
.auth-background span:nth-child(3) {
  width: 20rem;
  height: 20rem;
  background: var(--auth-warm);
  left: 42%;
  top: 38%;
  opacity: 0.07;
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
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.logo-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(115, 214, 170, 0.3);
  border-radius: 10px;
  color: var(--auth-accent);
  background: var(--auth-accent-soft);
}
.alternate-link {
  padding: 10px 15px;
  border: 1px solid var(--auth-border-strong);
  border-radius: 8px;
  color: #dbe4de;
  font-size: 14px;
  transition: 0.2s ease;
}
.alternate-link:hover,
.alternate-link:focus-visible {
  border-color: var(--auth-accent);
  color: var(--auth-text-strong);
  outline: none;
  box-shadow: 0 0 0 3px rgba(115, 214, 170, 0.14);
}
.auth-main {
  position: relative;
  z-index: 1;
  flex: 1;
  width: min(1120px, calc(100% - 40px));
  margin: auto;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 440px);
  gap: clamp(48px, 8vw, 104px);
  align-items: center;
  padding: 36px 0 64px;
}
.brand-kicker {
  margin: 0 0 16px;
  color: var(--auth-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
}
.auth-brand h1 {
  margin: 0;
  font-size: clamp(38px, 5vw, 62px);
  line-height: 1.08;
  letter-spacing: -0.045em;
}
.auth-brand h1 span {
  color: #8bd9b7;
}
.brand-description {
  max-width: 560px;
  margin: 22px 0 30px;
  color: var(--auth-muted);
  font-size: 16px;
  line-height: 1.75;
}
.capability-list {
  display: grid;
  gap: 12px;
  max-width: 520px;
}
.capability-list > div {
  display: flex;
  gap: 13px;
  align-items: center;
  padding: 13px 15px;
  border: 1px solid var(--auth-border);
  border-radius: 10px;
  background: var(--auth-surface-soft);
  color: var(--auth-muted);
  font-size: 13px;
  line-height: 1.5;
}
.capability-list .el-icon {
  flex: 0 0 auto;
  color: var(--auth-accent);
  font-size: 19px;
}
.capability-list strong {
  display: block;
  color: #e5ece7;
  font-size: 14px;
}
.auth-card {
  position: relative;
  padding: 34px;
  border: 1px solid var(--auth-border);
  border-radius: 14px;
  background: var(--auth-surface);
  box-shadow: 0 28px 70px rgba(0, 0, 0, 0.34);
  backdrop-filter: blur(22px);
}
.card-accent {
  position: absolute;
  inset: 0 28px auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--auth-accent), var(--auth-warm), transparent);
}
footer {
  position: relative;
  z-index: 1;
  padding: 18px;
  color: var(--auth-subtle);
  text-align: center;
  font-size: 12px;
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
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition: none !important;
    animation: none !important;
  }
}
</style>
