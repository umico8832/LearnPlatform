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
          <a href="#how-it-teaches">它如何教</a>
          <a href="#learning-space">学习空间</a>
          <a href="#current-course">当前可体验</a>
        </nav>

        <div class="site-actions">
          <RouterLink v-if="!loggedIn" class="text-link" to="/login">登录</RouterLink>
          <RouterLink class="button button--compact" :to="primaryDestination">
            {{ loggedIn ? '回到我的课程' : '开始学习' }}
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M3.5 8h9M9 4.5 12.5 8 9 11.5" />
            </svg>
          </RouterLink>
        </div>
      </div>
    </header>

    <main>
      <section class="hero section-shell" aria-labelledby="home-title">
        <div class="hero__copy">
          <p class="eyebrow">AI 相伴的学习空间</p>
          <h1 id="home-title">难懂的知识，<br /><em>也可以轻松学会。</em></h1>
          <p class="hero__lead">
            和教学 Agent 一起学。它会听你卡在哪里，换一种讲法、画出过程， 再用一个小问题确认你真的理解。
          </p>
          <div class="hero__actions">
            <RouterLink class="button button--hero" :to="primaryDestination">
              {{ loggedIn ? '继续我的学习' : '创建学习空间' }}
              <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3.5 8h9M9 4.5 12.5 8 9 11.5" />
              </svg>
            </RouterLink>
            <a class="secondary-link" href="#how-it-teaches">看看它怎么讲</a>
          </div>
          <p class="hero__note">
            <svg viewBox="0 0 18 18" fill="none" aria-hidden="true">
              <path d="m3 9 3.4 3.4L15 4.8" />
            </svg>
            不是更长的答案，是更适合你的讲法
          </p>
        </div>

        <div class="hero-demo" aria-label="并集计数互动讲解">
          <div class="demo-window">
            <div class="demo-window__bar">
              <span class="demo-window__status"><i></i> Agent 正在换一种讲法</span>
              <span>集合与概率</span>
            </div>

            <div class="agent-prompt">
              <span class="agent-prompt__mark" aria-hidden="true">A</span>
              <p>先别记公式。拖动蓝色圆，看看两份名单出现重复时，总人数会怎么变。</p>
            </div>

            <div class="venn-stage" :style="vennStyle" aria-hidden="true">
              <div class="venn-circle venn-circle--left"><strong>18</strong><span>A 组</span></div>
              <div class="venn-circle venn-circle--right"><strong>14</strong><span>B 组</span></div>
              <span v-if="overlap > 0" class="overlap-badge">重复 {{ overlap }}</span>
            </div>

            <label class="overlap-control">
              <span>拖动，让两组出现重复</span>
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

            <div class="formula-card" aria-live="polite">
              <div>
                <small>合并后的人数</small>
                <strong data-testid="union-formula">18 + 14 − {{ overlap }} = {{ unionTotal }}</strong>
              </div>
              <p>{{ overlapExplanation }}</p>
            </div>
          </div>
          <span class="hero-demo__scribble" aria-hidden="true">
            <svg viewBox="0 0 120 72" fill="none">
              <path d="M111 7C82 13 52 30 25 58" />
              <path d="m34 42-12 19 21-5" />
            </svg>
            动手一下，直觉就来了
          </span>
        </div>
      </section>

      <section id="how-it-teaches" class="teaching-section" aria-labelledby="teaching-title">
        <div class="teaching-section__inner section-shell">
          <div class="section-copy section-copy--light">
            <p class="eyebrow eyebrow--light">不懂不是你的问题</p>
            <h2 id="teaching-title">同一个知识，<br />总有另一种讲法。</h2>
            <p>当你说“我还是没懂”，Agent 不会把原答案再说一遍。</p>
          </div>

          <div class="teaching-path" aria-label="Agent 根据理解情况调整讲解路径">
            <div class="learner-question">
              <span>你</span>
              <p>“我知道公式，但还是不知道为什么。”</p>
            </div>

            <svg class="path-lines" viewBox="0 0 640 420" fill="none" aria-hidden="true">
              <path d="M320 18v68c0 38-114 34-114 84v38" />
              <path d="M320 86c0 38 0 34 0 84v38" />
              <path d="M320 86c0 38 114 34 114 84v38" />
              <circle cx="320" cy="86" r="6" />
            </svg>

            <div class="path-options">
              <article>
                <span>01</span>
                <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
                  <circle cx="18" cy="32" r="9" />
                  <circle cx="46" cy="32" r="9" />
                  <path d="M27 32h10" />
                </svg>
                <h3>补上前置</h3>
                <p>先找到断掉的那一步</p>
              </article>
              <article>
                <span>02</span>
                <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
                  <path d="M10 46 26 18l12 20 7-10 9 18H10Z" />
                  <circle cx="45" cy="16" r="5" />
                </svg>
                <h3>换个例子</h3>
                <p>把抽象概念放进熟悉场景</p>
              </article>
              <article>
                <span>03</span>
                <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
                  <path d="M10 48c8-24 20-30 42-32" />
                  <path d="m43 10 10 6-7 10" />
                  <circle cx="21" cy="31" r="4" />
                </svg>
                <h3>画出过程</h3>
                <p>让看不见的变化真正发生</p>
              </article>
            </div>

            <div class="understanding-check">
              <span class="understanding-check__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none"><path d="m5 12 4 4L19 7" /></svg>
              </span>
              <div><small>然后再确认</small><strong>你能不能用自己的话，说出关键那一步？</strong></div>
            </div>
          </div>
        </div>
      </section>

      <section id="learning-space" class="learning-space section-shell" aria-labelledby="space-title">
        <div class="section-copy">
          <p class="eyebrow">不只是一次对话</p>
          <h2 id="space-title">学过的每一步，<br />都在同一个空间里继续。</h2>
          <p>讲解、作答、错题和复习不再是四个断开的工具，而是一段连续的学习过程。</p>
        </div>

        <div class="learning-track">
          <svg class="learning-track__line" viewBox="0 0 1100 170" fill="none" aria-hidden="true">
            <path d="M42 95C180 8 283 155 420 76s247-3 342 38 188 7 296-61" />
          </svg>
          <ol>
            <li><span>01</span><strong>问出卡住的地方</strong><small>Agent 先定位问题</small></li>
            <li><span>02</span><strong>跟着讲解动手</strong><small>把抽象过程看见</small></li>
            <li><span>03</span><strong>用一次作答确认</strong><small>知道是继续还是回头</small></li>
            <li><span>04</span><strong>在该复习时再遇见</strong><small>让理解慢慢留下来</small></li>
          </ol>
        </div>
      </section>

      <section id="current-course" class="current-course" aria-labelledby="course-title">
        <div class="current-course__inner section-shell">
          <div class="course-visual" aria-hidden="true">
            <span class="course-visual__orbit course-visual__orbit--one"></span>
            <span class="course-visual__orbit course-visual__orbit--two"></span>
            <span class="course-visual__node course-visual__node--one">A</span>
            <span class="course-visual__node course-visual__node--two">B</span>
            <span class="course-visual__node course-visual__node--three">C</span>
            <svg viewBox="0 0 440 320" fill="none">
              <path d="M82 207c62-105 146 38 208-70 28-49 55-66 80-72" />
              <path d="m349 55 23 9-14 21" />
            </svg>
            <strong>学会，<br />不止做完。</strong>
          </div>

          <div class="course-copy">
            <p class="eyebrow">当前可体验</p>
            <h2 id="course-title">先把一门课的学习过程，真正连起来。</h2>
            <p>
              当前可体验的完整课程是 408 数据结构，已连接分步讲解、互动课件、练习、错题、
              间隔复习、阶段测评与真题考试。它是第一个课程案例，不是平台的知识边界。
            </p>
            <RouterLink class="button" :to="primaryDestination">
              {{ loggedIn ? '进入我的课程' : '从一个问题开始' }}
              <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3.5 8h9M9 4.5 12.5 8 9 11.5" />
              </svg>
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="closing" aria-labelledby="closing-title">
        <div class="closing__inner section-shell">
          <p class="eyebrow eyebrow--light">LearnPlatform</p>
          <h2 id="closing-title">把“不明白”说出来。<br /><em>剩下的，一起慢慢弄懂。</em></h2>
          <RouterLink class="button button--light" :to="primaryDestination">
            {{ loggedIn ? '继续学习' : '开始轻松学习' }}
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M3.5 8h9M9 4.5 12.5 8 9 11.5" />
            </svg>
          </RouterLink>
        </div>
      </section>
    </main>

    <footer class="site-footer section-shell">
      <RouterLink class="brand" to="/">
        <span class="brand__mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none">
            <path d="M7 8.5c3.7 0 6.7 1.2 9 3.5v14c-2.3-2.3-5.3-3.5-9-3.5v-14Z" />
            <path d="M25 8.5c-3.7 0-6.7 1.2-9 3.5v14c2.3-2.3 5.3-3.5 9-3.5v-14Z" />
          </svg>
        </span>
        <span>LearnPlatform</span>
      </RouterLink>
      <p>一个 AI 相伴的学习空间。</p>
      <div><RouterLink to="/login">登录</RouterLink><a href="#home-title">回到顶部</a></div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'

const overlap = ref(6)
const unionTotal = computed(() => 18 + 14 - overlap.value)
const overlapExplanation = computed(() =>
  overlap.value === 0 ? '没有人重复，两组可以直接相加。' : `重叠的 ${overlap.value} 人被数了两次，所以要减回一次。`,
)
const vennStyle = computed(() => ({ '--venn-shift': `${176 - overlap.value * 12}px` }))

const loggedIn = isAuthenticated()
const primaryDestination = computed(() => (loggedIn ? '/my-courses' : '/register'))
</script>

<style scoped src="./HomeView.css"></style>
