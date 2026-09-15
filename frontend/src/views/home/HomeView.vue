<template>
  <div class="home-page">
    <header class="site-header">
      <div class="site-header__inner">
        <RouterLink class="brand" to="/" aria-label="LearnPlatform 首页">
          <span class="brand__mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none">
              <path d="M7 8.5c3.7 0 6.7 1.2 9 3.5v14c-2.3-2.3-5.3-3.5-9-3.5v-14Z" />
              <path d="M25 8.5c-3.7 0-6.7 1.2-9 3.5v14c2.3-2.3 5.3-3.5 9-3.5v-14Z" />
            </svg>
          </span>
          <span>LearnPlatform</span>
        </RouterLink>

        <nav class="site-nav" aria-label="首页导航">
          <RouterLink to="/product">产品</RouterLink>
          <RouterLink to="/learning">学习</RouterLink>
          <RouterLink to="/courses">课程</RouterLink>
          <RouterLink to="/resources">资源</RouterLink>
          <RouterLink to="/roadmap">路线图</RouterLink>
        </nav>

        <div class="site-actions">
          <RouterLink v-if="!loggedIn" class="text-link" to="/login">登录</RouterLink>
          <RouterLink class="button button--compact" :to="primaryDestination">
            {{ loggedIn ? '回到课程' : '开始学习' }}
          </RouterLink>
        </div>
      </div>
    </header>

    <main>
      <section class="hero-wrap" aria-labelledby="home-title">
        <div class="hero-stage">
          <div class="hero-stage__texture" aria-hidden="true"></div>
          <img
            class="hero-landscape"
            :src="learningLandscape"
            width="1600"
            height="800"
            alt=""
            aria-hidden="true"
            decoding="async"
            fetchpriority="high"
          />

          <div class="hero-copy">
            <p class="hero-kicker">AI 相伴的学习空间</p>
            <h1 id="home-title">把难懂的知识，<br />真正学会。</h1>
            <p>它会根据你卡住的地方改变讲法，并把讲解、作答、错题与复习连接成一段连续的学习过程。</p>
            <div class="hero-actions">
              <RouterLink class="button button--hero" :to="primaryDestination">
                {{ loggedIn ? '继续我的学习' : '开始学习' }}
              </RouterLink>
              <a class="button button--ghost" href="#teaching-demo">看看它怎么教</a>
            </div>
          </div>

          <div id="teaching-demo" class="lesson-window" aria-label="自适应教学互动演示">
            <div class="lesson-window__bar">
              <span class="lesson-window__brand"><i></i> Tutor 正在调整讲法</span>
              <span>集合与概率</span>
            </div>

            <div class="lesson-window__body">
              <div class="lesson-dialogue">
                <div class="learner-message">
                  <span>你</span>
                  <p>公式我记住了，但我还是不明白为什么要减去重复的人。</p>
                </div>

                <div class="teaching-options" aria-label="选择当前的困难">
                  <button
                    v-for="option in teachingOptions"
                    :key="option.id"
                    type="button"
                    :class="{ 'is-active': teachingMode === option.id }"
                    :aria-pressed="teachingMode === option.id"
                    @click="teachingMode = option.id"
                  >
                    {{ option.label }}
                  </button>
                </div>

                <div class="tutor-response" aria-live="polite">
                  <span class="tutor-response__mark" aria-hidden="true">A</span>
                  <div>
                    <small>{{ activeTeaching.eyebrow }}</small>
                    <p data-testid="teaching-response">{{ activeTeaching.response }}</p>
                  </div>
                </div>
              </div>

              <div class="venn-demo">
                <div class="venn-stage" :style="vennStyle" aria-hidden="true">
                  <div class="venn-circle venn-circle--left"><strong>18</strong><span>A 组</span></div>
                  <div class="venn-circle venn-circle--right"><strong>14</strong><span>B 组</span></div>
                  <span v-if="overlap > 0" class="overlap-badge">重复 {{ overlap }}</span>
                </div>
                <label class="overlap-control">
                  <span>拖动蓝色圆，观察合并人数</span>
                  <input
                    v-model.number="overlap"
                    data-testid="overlap-input"
                    type="range"
                    min="0"
                    max="8"
                    step="1"
                    :aria-valuetext="`重复 ${overlap} 人，合并后 ${unionTotal} 人`"
                  />
                </label>
                <div class="formula-card">
                  <small>合并后的人数</small>
                  <strong data-testid="union-formula">18 + 14 − {{ overlap }} = {{ unionTotal }}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="one-space section-shell" aria-labelledby="one-space-title">
        <div class="section-heading section-heading--center">
          <p class="section-kicker">不只是一次回答</p>
          <h2 id="one-space-title">学习发生在同一个空间里。</h2>
          <p>从第一次讲解到下一次复习，每个动作都为真正重要的下一步留下线索。</p>
        </div>

        <ul class="capability-line" aria-label="连续学习能力">
          <li v-for="capability in capabilities" :key="capability.label">
            <span aria-hidden="true">
              <svg viewBox="0 0 28 28" fill="none">
                <path v-for="path in capability.paths" :key="path" :d="path" />
              </svg>
            </span>
            <strong>{{ capability.label }}</strong>
          </li>
        </ul>
      </section>

      <section class="people-section" aria-labelledby="people-title">
        <div class="section-shell">
          <div class="section-heading section-heading--center">
            <h2 id="people-title">从你正在面对的问题开始。</h2>
          </div>
          <div class="scenario-track">
            <article v-for="scenario in scenarios" :key="scenario.title" :class="`scenario-card--${scenario.tone}`">
              <span class="scenario-card__number" aria-hidden="true">{{ scenario.number }}</span>
              <div>
                <p>{{ scenario.meta }}</p>
                <h3>{{ scenario.title }}</h3>
                <span>{{ scenario.detail }}</span>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="story-section section-shell" aria-labelledby="adaptive-title">
        <div class="story-panel story-panel--blue">
          <div class="story-copy">
            <p class="section-kicker">教学会转弯</p>
            <h2 id="adaptive-title">不是把原答案，再说一遍。</h2>
            <p>当公式没有帮助时，就补上前置、换一个例子，或者把看不见的过程画出来。</p>
            <RouterLink class="story-link" to="/learning">了解学习方式 <span>→</span></RouterLink>
          </div>
          <div class="route-visual" aria-label="教学路径根据困难变化">
            <div class="route-visual__question">“我知道公式，但还是不知道为什么。”</div>
            <svg viewBox="0 0 620 320" fill="none" aria-hidden="true">
              <path d="M310 34v56c0 48-196 24-196 112" />
              <path d="M310 90v112" />
              <path d="M310 90c0 48 196 24 196 112" />
              <circle cx="310" cy="90" r="6" />
            </svg>
            <div class="route-visual__steps"><span>补上前置</span><span>换个例子</span><span>画出过程</span></div>
            <div class="route-visual__check">然后只确认一件事：你能说出关键那一步吗？</div>
          </div>
        </div>
      </section>

      <section class="story-section section-shell" aria-labelledby="continuity-title">
        <div class="story-panel story-panel--green">
          <div class="story-copy">
            <p class="section-kicker">每一步都有下文</p>
            <h2 id="continuity-title">这次学过的，会成为下次的起点。</h2>
            <p>真实作答留下学习证据，错题进入复盘，到期内容回到下一次学习。</p>
          </div>
          <ol class="continuity-path">
            <li v-for="(step, index) in continuitySteps" :key="step.title">
              <span>0{{ index + 1 }}</span>
              <div>
                <strong>{{ step.title }}</strong
                ><small>{{ step.detail }}</small>
              </div>
            </li>
          </ol>
        </div>
      </section>

      <section class="story-section section-shell" aria-labelledby="exam-title">
        <div class="story-panel story-panel--yellow">
          <div class="story-copy">
            <p class="section-kicker">一张真题，三段旅程</p>
            <h2 id="exam-title">先理解，再作答，然后回来复盘。</h2>
            <p>同一份试卷保留原题结构，同时支持逐题学习、限时考试和提交后的可信复盘。</p>
            <RouterLink class="story-link" :to="primaryDestination">查看当前课程 <span>→</span></RouterLink>
          </div>
          <div class="paper-modes">
            <article>
              <small>01</small><strong>学习模式</strong>
              <p>逐题作答，必要时获得针对性讲解。</p>
            </article>
            <article>
              <small>02</small><strong>考试模式</strong>
              <p>保留时限、试卷结构和服务端判分。</p>
            </article>
            <article>
              <small>03</small><strong>复盘模式</strong>
              <p>围绕错题、用时和后续练习继续。</p>
            </article>
          </div>
        </div>
      </section>

      <section class="metrics-section section-shell" aria-labelledby="metrics-title">
        <div class="metrics-heading">
          <p class="section-kicker">Placeholder metrics</p>
          <h2 id="metrics-title">学习正在发生。</h2>
          <span>以下为开发阶段占位数据</span>
        </div>
        <dl class="metrics-grid">
          <div v-for="metric in placeholderMetrics" :key="metric.label">
            <dt>{{ metric.label }}</dt>
            <dd>{{ metric.value }}</dd>
          </div>
        </dl>
      </section>

      <section class="course-section" aria-labelledby="course-title">
        <div class="course-section__ambient" aria-hidden="true"></div>
        <div class="course-section__inner section-shell">
          <div class="course-mark" aria-hidden="true">
            <span>408</span>
            <svg viewBox="0 0 280 190" fill="none">
              <path d="M24 146c58-92 116 24 166-54 22-34 42-48 66-57" />
              <path d="m238 27 20 8-12 18" />
            </svg>
          </div>
          <div class="course-copy">
            <p class="section-kicker">当前可体验</p>
            <h2 id="course-title">先把一门课的学习过程，真正连起来。</h2>
            <p>408 数据结构课程已经连接 AI 教学、互动课件、练习、错题、间隔复习、阶段测评与真题考试。</p>
            <RouterLink class="button" :to="primaryDestination">
              {{ loggedIn ? '进入我的课程' : '从一个问题开始' }}
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="closing section-shell" aria-labelledby="closing-title">
        <div class="closing__inner">
          <div>
            <p class="section-kicker">LearnPlatform</p>
            <h2 id="closing-title">一个安静、专注，并会随着你的理解而改变的学习空间。</h2>
          </div>
          <RouterLink class="button button--closing" :to="primaryDestination">
            {{ loggedIn ? '继续学习' : '开始学习' }}
          </RouterLink>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <div class="site-footer__inner section-shell">
        <RouterLink class="brand brand--footer" to="/">
          <span class="brand__mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none">
              <path d="M7 8.5c3.7 0 6.7 1.2 9 3.5v14c-2.3-2.3-5.3-3.5-9-3.5v-14Z" />
              <path d="M25 8.5c-3.7 0-6.7 1.2-9 3.5v14c2.3-2.3 5.3-3.5 9-3.5v-14Z" />
            </svg>
          </span>
          <span>LearnPlatform</span>
        </RouterLink>
        <nav aria-label="页脚导航">
          <RouterLink to="/product">产品</RouterLink>
          <RouterLink to="/learning">学习</RouterLink>
          <RouterLink to="/resources">资源</RouterLink>
          <RouterLink to="/roadmap">路线图</RouterLink>
          <RouterLink to="/about">关于我们</RouterLink>
        </nav>
        <p>© 2026 LearnPlatform</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import learningLandscape from '@/assets/images/home/learning-landscape.webp'
import { isAuthenticated } from '@/utils/auth'

type TeachingMode = 'formula' | 'process' | 'example'

const teachingMode = ref<TeachingMode>('process')
const overlap = ref(6)
const loggedIn = isAuthenticated()
const primaryDestination = computed(() => (loggedIn ? '/my-courses' : '/register'))
const unionTotal = computed(() => 18 + 14 - overlap.value)
const vennStyle = computed(() => ({ '--venn-shift': `${164 - overlap.value * 11}px` }))

const teachingOptions: Array<{ id: TeachingMode; label: string }> = [
  { id: 'formula', label: '我看不懂公式' },
  { id: 'process', label: '我想看见过程' },
  { id: 'example', label: '给我一个例子' },
]

const teachingContent: Record<TeachingMode, { eyebrow: string; response: string }> = {
  formula: {
    eyebrow: '先补上前置',
    response: '先不算数字。两份名单里如果有同一个人，直接相加时，他是不是被数了两次？',
  },
  process: {
    eyebrow: '把过程画出来',
    response: '拖动右边的圆。两个圆重叠得越多，被重复计算的人就越多，所以要减回一次。',
  },
  example: {
    eyebrow: '换一个熟悉的例子',
    response: '把它想成两个社团的报名表。小林同时报名两个社团，但学校总人数里只能算一个小林。',
  },
}

const activeTeaching = computed(() => teachingContent[teachingMode.value])

const capabilities = [
  {
    label: 'AI 教学',
    paths: ['M5 7.5h18v13H10l-5 4v-17Z', 'M9 12h10M9 16h6'],
  },
  {
    label: '真题学习',
    paths: ['M7 4h11l4 4v16H7V4Z', 'M18 4v5h4M11 14h7M11 18h7'],
  },
  {
    label: '练习',
    paths: ['M14 4a10 10 0 1 1 0 20 10 10 0 0 1 0-20Z', 'm10 14 3 3 6-7'],
  },
  {
    label: '错题',
    paths: ['M6 5h16v18H6V5Z', 'm10 10 8 8M18 10l-8 8'],
  },
  {
    label: '复习',
    paths: ['M6 10a9 9 0 1 1 0 8', 'M6 5v5h5'],
  },
]

const scenarios = [
  {
    number: '01',
    meta: '系统学习',
    title: '把一门课程从头学懂',
    detail: '课程目录、教学位置与复习任务保持连续',
    tone: 'blue',
  },
  {
    number: '02',
    meta: '真题备考',
    title: '在原题里发现真正的卡点',
    detail: '学习、考试与复盘使用同一份可信题目',
    tone: 'yellow',
  },
  {
    number: '03',
    meta: '资料学习',
    title: '把自己的资料变成学习入口',
    detail: '导入、确认，再进入可练习的私有内容',
    tone: 'green',
  },
]

const continuitySteps = [
  { title: '讲解', detail: '围绕当前卡点，只展开必要内容' },
  { title: '作答', detail: '用一次真实回答确认理解' },
  { title: '错题', detail: '保留误解，而不是覆盖历史' },
  { title: '复习', detail: '在合适的时候重新遇见' },
]

const placeholderMetrics = [
  { value: '00,000+', label: '学习片段' },
  { value: '000', label: '课程空间' },
  { value: '00%', label: '持续学习率' },
]
</script>

<style scoped src="./HomeView.css"></style>
