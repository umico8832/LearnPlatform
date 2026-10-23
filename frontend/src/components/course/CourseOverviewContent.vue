<template>
  <div class="lt course-page course-overview" data-appearance="dark">
    <CoursePageState v-if="loading" state="loading" title="正在读取课程空间" />
    <CoursePageState
      v-else-if="failed || !overview"
      state="error"
      title="暂时无法读取课程空间"
      description="请刷新重试；如果课程尚未加入课程库，请先从课程库加入。"
      retry-label="重新加载"
      @retry="emit('retry')"
    />
    <template v-else>
      <LtPageHeader class="overview-hero" :title="overview.courseName">
        <template #lead>
          <LtBackLink :to="{ name: 'CourseOverview', params: { id: courseId } }" label="返回课程" />
        </template>
        <template #actions>
          <div class="course-actions">
            <LtButton
              v-if="!hasRecommendedTarget"
              variant="primary"
              :disabled="starting"
              :aria-label="primaryActionLabel"
              @click="emit('primaryAction')"
              >{{ starting ? '正在准备…' : primaryActionLabel }}</LtButton
            >
            <LtButton @click="emit('openContent')">查看教学目录</LtButton
            ><LtButton @click="moreOpen = true">更多</LtButton>
          </div>
        </template>
        <p v-if="primaryActionError" class="course-error" role="alert">{{ primaryActionError }}</p>
      </LtPageHeader>
      <LtCard v-if="overview.recommendedTargets.length" as="section" radius="xl" class="continue-section">
        <div>
          <p class="course-muted">下一步</p>
          <h2>{{ recommendedTargetTitle }}</h2>
          <p class="course-muted">{{ overview.recommendedTargets[0].reason }}</p>
        </div>
        <LtButton variant="primary" @click="emit('openTarget', overview.recommendedTargets[0])">{{
          recommendedActionLabel
        }}</LtButton>
      </LtCard>
      <div class="overview-grid" :class="{ 'overview-grid--single': !hasLearningActivity }">
        <div class="overview-main">
          <LtCard as="section" radius="xl" class="panel" aria-labelledby="course-outline-heading">
            <div class="panel-heading">
              <h2 id="course-outline-heading">教学内容</h2>
              <LtButton variant="quiet" @click="emit('openContent')">课程目录</LtButton>
            </div>
            <p v-if="overview.tutorProgress.length" class="course-muted">
              共 {{ overview.tutorProgress.length }} 节教学内容。
            </p>
            <div v-if="overview.tutorProgress.length" id="course-outline-list" class="outline-list">
              <div v-for="item in visibleTutorProgress" :key="item.knowledgePointId" class="outline-item">
                <span class="outline-status" :data-status="item.status" aria-hidden="true" />
                <div class="outline-copy">
                  <strong>{{ item.title }}</strong
                  ><small>{{ tutorStatusLabel(item.status) }}</small>
                </div>
                <LtButton size="sm" @click="emit('openTutor', item.knowledgePointId)">{{
                  tutorActionLabel(item.status)
                }}</LtButton>
              </div>
            </div>
            <LtButton
              v-if="overview.tutorProgress.length > initialOutlineCount"
              variant="quiet"
              class="outline-toggle"
              :aria-expanded="showAllTutorProgress"
              aria-controls="course-outline-list"
              @click="showAllTutorProgress = !showAllTutorProgress"
              >{{
                showAllTutorProgress ? '收起目录' : `展开其余 ${overview.tutorProgress.length - initialOutlineCount} 节`
              }}</LtButton
            >
            <div v-else-if="!overview.tutorProgress.length" class="course-muted">
              <h3>暂无教学内容</h3>
              <p>可以从题目或试卷开始。</p>
            </div>
          </LtCard>
          <KnowledgePointFactsPanel
            :course-id="courseId"
            :refresh-key="factsRefreshKey"
            @open-question-bank="emit('openTool', 'QuestionList')"
            @open-review="(id, name) => emit('openKnowledgePointReview', id, name)"
            @open-tutor="(id) => emit('openTutor', id)"
            @open-wrong-questions="(id, name) => emit('openKnowledgePointWrongQuestions', id, name)"
          />
          <LtCard as="section" radius="xl" class="panel tools-panel" aria-labelledby="learning-tools-heading">
            <h2 id="learning-tools-heading">学习工具</h2>
            <div class="tool-list">
              <button
                v-for="tool in tools"
                :key="tool.routeName"
                type="button"
                class="tool-row"
                @click="emit('openTool', tool.routeName)"
              >
                <component :is="tool.icon" class="course-symbol" aria-hidden="true" /><span class="tool-copy"
                  ><strong>{{ tool.title }}</strong
                  ><small>{{ tool.desc }}</small></span
                ><ArrowRight class="course-symbol" aria-hidden="true" />
              </button>
            </div>
          </LtCard>
        </div>
        <LtCard
          v-if="hasLearningActivity"
          as="aside"
          radius="lg"
          class="activity-panel"
          aria-labelledby="activity-heading"
        >
          <h2 id="activity-heading">学习情况</h2>
          <dl class="stats-grid">
            <div>
              <dt>已作答</dt>
              <dd>{{ overview.answeredCount }}</dd>
            </div>
            <div>
              <dt>答对</dt>
              <dd>{{ overview.correctCount }}</dd>
            </div>
            <div>
              <dt>待复习</dt>
              <dd>{{ overview.dueReviewCount }}</dd>
            </div>
            <div>
              <dt>未处理错题</dt>
              <dd>{{ overview.unresolvedWrongCount }}</dd>
            </div>
          </dl>
          <div class="recent-block">
            <h3>最近活动</h3>
            <p class="course-muted">
              {{ overview.lastLearningTime ? formatDateTime(overview.lastLearningTime) : '暂无课程内学习记录' }}
            </p>
            <div v-if="overview.latestStageAssessment" class="latest-assessment">
              <strong>最近阶段测评</strong
              ><span
                >答对 {{ overview.latestStageAssessment.correctCount }} /
                {{ overview.latestStageAssessment.questionCount }} 题</span
              ><small>范围：{{ overview.latestStageAssessment.targetKnowledgePointName || '课程整体' }}</small
              ><small>题源：{{ sourceCompositionText(overview.latestStageAssessment.sourceComposition) }}</small
              ><small v-if="overview.latestStageAssessment.knowledgePointSummary?.length"
                >知识点：{{ knowledgePointSummaryText(overview.latestStageAssessment.knowledgePointSummary) }}</small
              ><small>{{ formatDateTime(overview.latestStageAssessment.completeTime) }}</small
              ><LtButton variant="quiet" @click="emit('openAssessmentDetail', overview.latestStageAssessment.id)"
                >查看逐题复盘</LtButton
              >
              <p v-if="assessmentDetailError" class="course-error" role="alert">{{ assessmentDetailError }}</p>
            </div>
          </div>
          <LtButton variant="quiet" @click="emit('refresh')"
            ><template #icon><RotateCcw aria-hidden="true" /></template>刷新记录</LtButton
          >
        </LtCard>
      </div>
      <LtSheet v-model:open="moreOpen" title="课程操作"
        ><div class="more-actions">
          <LtButton @click="handleMoreCommand('papers')">课程试卷</LtButton
          ><LtButton @click="handleMoreCommand('assessment')">阶段测评</LtButton
          ><LtButton @click="handleMoreCommand('history')">测评历史</LtButton>
        </div></LtSheet
      >
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Component } from 'vue'
import { ArrowRight, BookOpen, Pencil, RotateCcw, Timer, FileText, CircleAlert } from '@lucide/vue'
import { LtBackLink, LtButton, LtCard, LtPageHeader, LtSheet } from '@/components/living'
import CoursePageState from '@/views/course/CoursePageState.vue'
import '@/views/course/course-baseline.css'
import type { CourseOverviewVO, CourseStageAssessmentKnowledgePointSummaryVO, LearningTargetVO } from '@/api/course'
import { formatDateTime } from '@/utils/format'
import KnowledgePointFactsPanel from './KnowledgePointFactsPanel.vue'

interface CourseTool {
  routeName: string
  title: string
  desc: string
  icon: Component
}

const props = defineProps<{
  overview: CourseOverviewVO | null
  loading: boolean
  failed: boolean
  starting: boolean
  primaryActionLabel: string
  primaryActionError: string
  recommendedActionLabel: string
  recommendedTargetTitle: string
  assessmentDetailError: string
  hasRecommendedTarget: boolean
  courseId: number
  factsRefreshKey: number
}>()
const emit = defineEmits<{
  retry: []
  primaryAction: []
  openContent: []
  moreCommand: [command: string]
  openTarget: [target: LearningTargetVO]
  openTool: [routeName: string]
  openTutor: [knowledgePointId: number]
  openAssessmentDetail: [assessmentId: number]
  openKnowledgePointReview: [knowledgePointId: number, knowledgePointName: string]
  openKnowledgePointWrongQuestions: [knowledgePointId: number, knowledgePointName: string]
  refresh: []
}>()
const tools: CourseTool[] = [
  { routeName: 'Practice', title: '练习', desc: '按课程范围随机练习，即时判分并复盘', icon: Pencil },
  { routeName: 'Review', title: '复习', desc: '处理本课程到期的间隔复习', icon: Timer },
  { routeName: 'WrongQuestions', title: '错题', desc: '集中处理本课程尚未掌握的题目', icon: CircleAlert },
  { routeName: 'ExamList', title: '真题与试卷', desc: '学习模式逐题理解，考试模式检验阶段效果', icon: FileText },
  { routeName: 'QuestionList', title: '题目', desc: '按课程、题型和难度浏览题库', icon: BookOpen },
]
const initialOutlineCount = 6
const showAllTutorProgress = ref(false)
const visibleTutorProgress = computed(() =>
  showAllTutorProgress.value
    ? (props.overview?.tutorProgress ?? [])
    : (props.overview?.tutorProgress ?? []).slice(0, initialOutlineCount),
)
const hasLearningActivity = computed(() => {
  const overview = props.overview
  return Boolean(
    overview &&
    (overview.answeredCount ||
      overview.dueReviewCount ||
      overview.unresolvedWrongCount ||
      overview.lastLearningTime ||
      overview.latestStageAssessment),
  )
})
watch(
  () => props.courseId,
  () => {
    showAllTutorProgress.value = false
  },
)
const moreOpen = ref(false)
function handleMoreCommand(command: string) {
  moreOpen.value = false
  emit('moreCommand', command)
}
function tutorStatusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成理解检查'
  if (status === 'IN_PROGRESS') return '已尝试'
  return '未开始'
}
function tutorActionLabel(status: string) {
  if (status === 'IN_PROGRESS') return '继续学习'
  if (status === 'COMPLETED') return '再次学习'
  return '开始学习'
}
function sourceCompositionText(
  composition: NonNullable<CourseOverviewVO['latestStageAssessment']>['sourceComposition'],
) {
  if (!composition) return '暂无题源快照'
  return [
    ['官方原题', composition.officialExamCount],
    ['平台人工题', composition.manualCount],
    ['用户私有题', composition.userPrivateCount],
    ['AI 生成题', composition.aiGeneratedCount],
  ]
    .filter(([, count]) => Number(count) > 0)
    .map(([label, count]) => `${label} ${count}`)
    .join(' · ')
}
function knowledgePointSummaryText(summary: CourseStageAssessmentKnowledgePointSummaryVO[]) {
  return summary.map((item) => `${item.name} ${item.correctCount}/${item.questionCount}`).join(' · ')
}
</script>
<style scoped>
.overview-grid {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--lt-space-6);
}
.overview-grid--single .overview-main {
  flex-basis: 100%;
}
.overview-main {
  flex: 2.2 1 calc(var(--lt-space-10) * 8);
  display: grid;
  gap: var(--lt-space-6);
  min-width: 0;
}
.panel-heading,
.continue-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-5);
}
.panel h2,
.activity-panel h2 {
  margin: 0;
}
.continue-section h2 {
  margin: var(--lt-space-2) 0;
}
.continue-section p {
  margin: 0;
}
.outline-item {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  padding: var(--lt-space-4) 0;
}
.outline-item + .outline-item {
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.outline-status {
  width: var(--lt-space-2);
  height: var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
}
.outline-status[data-status='COMPLETED'] {
  background: var(--lt-green);
}
.outline-status[data-status='IN_PROGRESS'] {
  background: var(--lt-orange);
}
.outline-copy,
.tool-copy {
  display: grid;
  gap: var(--lt-space-1);
  flex: 1;
  min-width: 0;
}
small {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.tool-list {
  display: grid;
  margin-top: var(--lt-space-3);
}
.tool-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: var(--lt-space-4);
  padding: var(--lt-space-4) 0;
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: transparent;
  color: var(--lt-label);
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.tool-row:hover {
  background: var(--lt-fill-3);
}
.activity-panel {
  flex: 1 1 calc(var(--lt-space-10) * 3.6);
  min-width: 0;
  display: grid;
  gap: var(--lt-space-5);
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-5);
  margin: 0;
}
.stats-grid dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.stats-grid dd {
  margin: var(--lt-space-1) 0 0;
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-title2);
  font-variant-numeric: tabular-nums;
}
.recent-block {
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  padding-top: var(--lt-space-4);
}
.recent-block h3 {
  margin: 0;
}
.latest-assessment {
  display: grid;
  gap: var(--lt-space-2);
}
.more-actions {
  display: grid;
  gap: var(--lt-space-3);
  padding-top: var(--lt-space-4);
}
</style>
