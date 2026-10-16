<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowDown, ArrowRight } from '@lucide/vue'
import { LtCard, LtLinkButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { isAuthenticated } from '@/utils/auth'
import { VizPlayer } from '@/viz'
import ChoiceList from '../question/ChoiceList.vue'
import FlowStep from '../question/FlowStep.vue'
import OptionChip from '../question/OptionChip.vue'
import QuestionProse from '../question/QuestionProse.vue'
import SourceBadge from '../question/SourceBadge.vue'
import {
  exampleChosen,
  exampleMisconception,
  exampleOptions,
  exampleSource,
  exampleStem,
  exampleVariantCondition,
} from './homeExample'

/**
 * 公开首页（ADR-0011 新基线）：不讲口号，直接把一道已审核真题的题目理解页摊开——
 * 选了 B 之后，它讲 B 错在哪、用本题的树放动画并先让人预测，正解照样要等做对或主动揭示。
 */
defineOptions({ name: 'HomeView' })
useLivingTheme()

const loggedIn = isAuthenticated()
const primary = computed(() =>
  loggedIn ? { to: '/my-courses', text: '回到课程' } : { to: '/register', text: '开始学习' },
)
const chosen = ref<string | null>(exampleChosen)
const chosenOption = exampleOptions.find((option) => option.label === exampleChosen)

const nav = [
  { to: '/product', text: '产品' },
  { to: '/learning', text: '学习方式' },
  { to: '/resources', text: '资源' },
  { to: '/roadmap', text: '路线图' },
  { to: '/about', text: '关于我们' },
]
</script>

<template>
  <div class="home">
    <header class="bar">
      <div class="bar-inner">
        <RouterLink class="brand" to="/">LearnPlatform</RouterLink>
        <nav class="nav" aria-label="网站导航">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to">{{ item.text }}</RouterLink>
        </nav>
        <div class="bar-actions">
          <RouterLink v-if="!loggedIn" class="bar-login" to="/login">登录</RouterLink>
          <LtLinkButton variant="primary" size="sm" :to="primary.to">{{ primary.text }}</LtLinkButton>
        </div>
      </div>
    </header>

    <main>
      <section class="hero" aria-labelledby="home-title">
        <div class="hero-copy">
          <h1 id="home-title" class="hero-title">选错的那一项，<br />就是它开讲的地方。</h1>
          <p class="hero-lead">
            答错一道题，LearnPlatform
            不把标准答案再念一遍：它讲你选的那个错项混淆了什么，再用这道题自己的数据把过程放一遍。现在开放的课程是 408
            数据结构。
          </p>
          <div class="hero-actions">
            <LtLinkButton variant="primary" size="lg" :to="primary.to">
              {{ primary.text }}<ArrowRight aria-hidden="true" />
            </LtLinkButton>
            <LtLinkButton size="lg" href="#walkthrough">
              看它怎么讲这道题<ArrowDown aria-hidden="true" />
            </LtLinkButton>
          </div>
        </div>

        <LtCard as="figure" radius="xl" padding="none" class="question" aria-labelledby="example-stem">
          <div class="question-body">
            <SourceBadge :source="exampleSource" />
            <p id="example-stem" class="question-stem"><QuestionProse :content="exampleStem" inline /></p>
            <ChoiceList
              v-model="chosen"
              :options="exampleOptions"
              name="home-example"
              label="2026 年 408 真题第 3 题的选项"
              locked
              :chosen="exampleChosen"
              :active="false"
            />
          </div>
          <figcaption class="question-caption">有人在这道题上选了 {{ exampleChosen }}。下面是它接着做的事。</figcaption>
        </LtCard>
      </section>

      <section id="walkthrough" class="walk" aria-labelledby="walk-title">
        <div class="walk-head">
          <h2 id="walk-title" class="section-title">选了 {{ exampleChosen }} 之后</h2>
          <p class="section-lead">下面就是题目理解页上的内容，原样摊开放在这里。动画可以直接播放。</p>
        </div>

        <div class="walk-flow">
          <FlowStep label="误区" tone="orange">
            <OptionChip tone="orange" label="你的选择" :option-key="exampleChosen" :content="chosenOption?.content" />
            <h3 class="step-title">{{ exampleMisconception.title }}</h3>
            <QuestionProse class="reading" :content="exampleMisconception.explanation" />
            <p class="note">讲的是你选的这一项。选 A 或 D 的人，看到的是另外两段讲解。</p>
          </FlowStep>

          <FlowStep label="演示" tone="purple">
            <h3 class="step-title">用这道题自己的树走一遍</h3>
            <p class="reading">播放到第一个输出的结点会停下来，先猜是谁，再看结果。</p>
            <VizPlayer :spec="exampleMisconception.viz" :title="exampleMisconception.vizTitle" class="step-viz" />
          </FlowStep>

          <FlowStep label="提示" tone="accent">
            <h3 class="step-title">还没想通，就一级一级要提示</h3>
            <p class="reading">
              先给一个方向，再指出关键一步，最后才是完整思路。用过提示的作答照样记录，但不计入复习评分。
            </p>
          </FlowStep>

          <FlowStep label="正解" tone="muted">
            <h3 class="step-title">正解要等你做对，或者你主动要看</h3>
            <p class="reading">首页也一样，所以这里没有写出答案。</p>
          </FlowStep>

          <FlowStep label="变式" tone="accent">
            <h3 class="step-title">换一个条件，不看提示再做一次</h3>
            <p class="reading">{{ exampleVariantCondition }}</p>
          </FlowStep>

          <FlowStep label="复习" tone="green">
            <h3 class="step-title">按你独立作答的结果，用 FSRS 排下一次复习</h3>
            <p class="reading">到期那天它会回到复习页，并写明为什么回来：上次答错、上次觉得难，还是按排期到了。</p>
          </FlowStep>
        </div>
      </section>

      <section class="course" aria-labelledby="course-title">
        <div class="course-copy">
          <h2 id="course-title" class="section-title">第一门课：408 数据结构</h2>
          <p class="section-lead">
            线性表、栈和队列的每道题都配了三级提示、逐个错项的误区讲解、正解和不看提示的变式题，内容由 AI
            起草、逐题审核后上线；上面这道 2026 年 408 真题的来源也逐题核对过。
          </p>
          <LtLinkButton variant="primary" size="lg" class="course-action" :to="primary.to">
            {{ loggedIn ? '回到课程' : '从第一道题开始' }}<ArrowRight aria-hidden="true" />
          </LtLinkButton>
        </div>
        <dl class="facts">
          <div>
            <dt>线性表、栈和队列</dt>
            <dd>25<span>道题</span></dd>
          </div>
          <div>
            <dt>每道题的提示</dt>
            <dd>3<span>级</span></dd>
          </div>
          <div>
            <dt>能播放的数据结构</dt>
            <dd>3<span>种：链表、栈、二叉树</span></dd>
          </div>
        </dl>
      </section>
    </main>

    <footer class="foot">
      <div class="foot-inner">
        <span class="foot-brand">LearnPlatform</span>
        <nav class="foot-nav" aria-label="页脚导航">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to">{{ item.text }}</RouterLink>
        </nav>
        <p class="foot-copy">© 2026 LearnPlatform</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home {
  min-height: 100dvh;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.bar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--lt-glass);
  backdrop-filter: blur(20px) saturate(180%);
  box-shadow: 0 0.5px 0 var(--lt-separator);
}

.bar-inner,
.hero,
.walk,
.course,
.foot-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding-inline: var(--lt-space-7);
}

.bar-inner {
  display: flex;
  align-items: center;
  gap: var(--lt-space-8);
  height: 60px;
}

.brand,
.foot-brand {
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
  font-weight: 600;
  letter-spacing: var(--lt-tracking-title);
  text-decoration: none;
}

.nav {
  display: flex;
  gap: var(--lt-space-6);
}

.nav a,
.foot-nav a,
.bar-login {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  text-decoration: none;
}

.nav a:hover,
.foot-nav a:hover,
.bar-login:hover {
  color: var(--lt-label);
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: var(--lt-space-5);
  margin-left: auto;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 520px;
  align-items: center;
  gap: var(--lt-space-10);
  padding-top: 88px;
  padding-bottom: 104px;
}

.hero-title {
  margin: 0;
  font-size: var(--lt-text-display);
  line-height: 1.15;
  letter-spacing: var(--lt-tracking-title);
}

.hero-lead {
  max-width: 34em;
  margin: var(--lt-space-6) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-8);
}

.question {
  margin: 0;
  overflow: hidden;
}

.question-body {
  padding: var(--lt-space-6) var(--lt-space-6) var(--lt-space-5);
}

.question-stem {
  margin: var(--lt-space-4) 0 var(--lt-space-5);
  font-size: var(--lt-text-reading);
  font-weight: 500;
  line-height: var(--lt-leading-reading);
}

.question-caption {
  padding: var(--lt-space-4) var(--lt-space-6);
  border-top: 1px solid var(--lt-separator);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.walk {
  padding-top: var(--lt-space-10);
  padding-bottom: 112px;
}

.walk-head {
  margin-bottom: var(--lt-space-9);
}

.section-title {
  margin: 0;
  font-size: var(--lt-text-title1);
  letter-spacing: var(--lt-tracking-title);
}

.section-lead {
  max-width: 36em;
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.walk-flow {
  max-width: 960px;
}

.step-title {
  margin: var(--lt-space-3) 0 0;
  font-size: var(--lt-text-title3);
}

.step-title:first-child {
  margin-top: 0;
}

.reading {
  max-width: 40em;
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.note {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.step-viz {
  margin-top: var(--lt-space-5);
}

.course {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--lt-space-10);
  padding-top: var(--lt-space-10);
  padding-bottom: 120px;
  border-top: 1px solid var(--lt-separator);
}

.course-action {
  margin-top: var(--lt-space-7);
}

.facts {
  display: grid;
  gap: var(--lt-space-6);
  margin: 0;
}

.facts dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.facts dd {
  margin: 2px 0 0;
  font: 600 var(--lt-text-title1) / 1.1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.facts dd span {
  margin-left: 6px;
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font);
}

.foot {
  box-shadow: 0 -0.5px 0 var(--lt-separator);
}

.foot-inner {
  display: flex;
  align-items: center;
  gap: var(--lt-space-8);
  height: 88px;
}

.foot-brand {
  font-size: var(--lt-text-body);
}

.foot-nav {
  display: flex;
  gap: var(--lt-space-6);
}

.foot-copy {
  margin: 0 0 0 auto;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
</style>
