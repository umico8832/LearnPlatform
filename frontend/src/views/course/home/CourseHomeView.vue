<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ChevronLeft, RotateCcw } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import QuestionProse from '../../question/QuestionProse.vue'
import CourseQuestionRow from './CourseQuestionRow.vue'
import { useCourseHome } from './useCourseHome'

/**
 * 课程页（ADR-0011 新基线，主线版）：先给今天的下一步，再按课程目录分章列出能逐步讲解的题。
 * 阶段测评、导师课节等旧功能整体留在课程空间（`CourseSpace`），这里只留入口。
 */
defineOptions({ name: 'CourseHomeView' })
useLivingTheme()

const route = useRoute()
const router = useRouter()
const courseId = computed(() => Number(route.params.id))
const { status, overview, chapters, next, total, load } = useCourseHome(courseId)

const redoCount = (questions: { status: string | null }[]) =>
  questions.filter((question) => question.status === 'TO_REDO').length

function openQuestion(id: number) {
  void router.push(`/questions/${id}`)
}

function openSpace() {
  void router.push({ name: 'CourseSpace', params: { id: courseId.value } })
}

onMounted(load)
</script>

<template>
  <main class="page">
    <header class="head">
      <LtIconButton class="back" label="返回我的课程" @click="router.push('/my-courses')">
        <ChevronLeft aria-hidden="true" />
      </LtIconButton>
      <h1 class="title">
        <span v-if="status === 'loading'" class="skeleton" style="width: 220px; height: 32px" />
        <template v-else>{{ overview?.courseName ?? '课程' }}</template>
      </h1>
      <p v-if="status === 'ready' && total" class="lead">
        共 {{ total }} 道题。答错时，会从你选的那个选项讲起，再换个条件让你做一次。
      </p>
    </header>

    <div v-if="status === 'loading'" aria-busy="true">
      <LtCard radius="xl" class="today">
        <div class="next">
          <span class="skeleton" style="width: 120px; height: 14px" />
          <span class="skeleton" style="width: 80%; height: 22px; margin-top: 14px" />
          <span class="skeleton" style="width: 112px; height: 40px; margin-top: 24px; border-radius: 999px" />
        </div>
      </LtCard>
      <span class="sr-only">正在读取课程</span>
    </div>

    <div v-else-if="status === 'error'" class="state">
      <h2 class="state-title">课程没有加载出来</h2>
      <p class="state-text">请检查网络后重试。如果还没加入这门课，可以先去课程库加入。</p>
      <div class="state-actions">
        <LtButton variant="primary" @click="load">
          <template #icon><RotateCcw aria-hidden="true" /></template>
          重试
        </LtButton>
        <LtButton @click="router.push({ name: 'CourseDetail', params: { id: courseId } })">查看课程介绍</LtButton>
      </div>
    </div>

    <template v-else>
      <LtCard as="section" radius="xl" class="today" aria-label="今天">
        <div v-if="next" class="next">
          <p class="next-reason" :data-tone="next.question.status === 'TO_REDO' ? 'orange' : undefined">
            {{ next.reason }}
          </p>
          <h2 class="next-stem"><QuestionProse :content="next.question.stem" inline /></h2>
          <LtButton variant="primary" size="lg" class="next-go" @click="openQuestion(next.question.id)">
            {{ next.question.status === 'TO_REDO' ? '重做这道' : '开始这道' }}
            <template #trailing><ArrowRight aria-hidden="true" /></template>
          </LtButton>
        </div>
        <div v-else class="next">
          <p class="next-reason">{{ total ? '这门课的题都做过了' : '还没有能逐步讲解的题' }}</p>
          <h2 class="next-stem next-stem-quiet">
            {{ total ? '到期的错题会回到这里，也可以去错题页提前巩固。' : '题目审核通过后会出现在这里。' }}
          </h2>
        </div>

        <dl class="todo">
          <div class="todo-item">
            <dt>到期复习</dt>
            <dd>{{ overview?.dueReviewCount ?? 0 }}</dd>
            <button type="button" class="link" @click="router.push({ name: 'Review' })">去复习</button>
          </div>
          <div class="todo-item">
            <dt>未解决错题</dt>
            <dd>{{ overview?.unresolvedWrongCount ?? 0 }}</dd>
            <button
              type="button"
              class="link"
              @click="router.push({ name: 'WrongQuestions', query: { courseId: String(courseId) } })"
            >
              看错题
            </button>
          </div>
        </dl>
      </LtCard>

      <section v-for="chapter in chapters" :key="chapter.id" class="chapter" :aria-labelledby="`chapter-${chapter.id}`">
        <header class="chapter-head">
          <h2 :id="`chapter-${chapter.id}`" class="chapter-title">{{ chapter.name }}</h2>
          <span class="chapter-meta">
            {{ chapter.questions.length }} 道<template v-if="redoCount(chapter.questions)">
              · <span class="chapter-redo">待重做 {{ redoCount(chapter.questions) }}</span></template
            >
          </span>
        </header>
        <LtCard radius="xl" padding="none" class="list">
          <CourseQuestionRow
            v-for="(question, index) in chapter.questions"
            :key="question.id"
            :question="question"
            :index="index + 1"
          />
        </LtCard>
      </section>
    </template>

    <footer v-if="status !== 'loading'" class="more">
      <div>
        <h2 class="more-title">课程空间</h2>
        <p class="more-text">阶段测评、导师课节、课程记忆和学习工具都在这里。</p>
      </div>
      <LtButton @click="openSpace">
        打开课程空间
        <template #trailing><ArrowRight aria-hidden="true" /></template>
      </LtButton>
    </footer>
  </main>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
  padding: 0 var(--lt-space-7) 120px;
}

.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}

.back {
  margin-left: -8px;
}

.title {
  margin: var(--lt-space-4) 0 0;
  font-size: var(--lt-text-title1);
  letter-spacing: var(--lt-tracking-title);
}

.lead {
  max-width: 60ch;
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.6;
}

.today {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--lt-space-7);
  align-items: stretch;
}

.next {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.next-reason {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.next-reason[data-tone='orange'] {
  color: var(--lt-orange-text);
}

.next-stem {
  display: -webkit-box;
  margin: var(--lt-space-2) 0 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  font-size: var(--lt-text-reading);
  font-weight: 500;
  line-height: var(--lt-leading-reading);
}

.next-stem-quiet {
  color: var(--lt-label-2);
  font-weight: 400;
}

.next-go {
  margin-top: var(--lt-space-5);
}

.todo {
  display: flex;
  align-self: center;
  gap: var(--lt-space-6);
  margin: 0;
  padding-left: var(--lt-space-7);
  border-left: 1px solid var(--lt-separator);
}

.todo-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 88px;
}

.todo-item dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.todo-item dd {
  margin: 4px 0 0;
  font: 600 var(--lt-text-title2) / 1.1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.link {
  margin-top: var(--lt-space-3);
  padding: 0;
  border: 0;
  background: none;
  color: var(--lt-accent);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.chapter {
  margin-top: var(--lt-space-8);
}

.chapter-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin: 0 var(--lt-space-2) var(--lt-space-3);
}

.chapter-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.chapter-meta {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.chapter-redo {
  color: var(--lt-orange-text);
}

.list {
  overflow: hidden;
}

.more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-6);
  margin-top: var(--lt-space-9);
  padding-top: var(--lt-space-5);
  border-top: 1px solid var(--lt-separator);
}

.more-title {
  margin: 0;
  font-size: var(--lt-text-headline);
}

.more-text {
  margin: 2px 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-8) 0;
}

.state-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.state-text {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-label-2);
}

.state-actions {
  display: flex;
  gap: var(--lt-space-3);
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
  animation: pending 1.4s ease-in-out infinite alternate;
}

@keyframes pending {
  to {
    opacity: 0.45;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
