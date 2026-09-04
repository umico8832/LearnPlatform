<template>
  <div class="course-overview page-container">
    <template v-if="loading">
      <LpSkeleton card :rows="3" />
      <div class="overview-grid"><LpSkeleton card :rows="8" /><LpSkeleton card :rows="6" /></div>
    </template>
    <template v-else-if="failed || !overview">
      <section class="state-panel">
        <LpEmptyState title="暂时无法读取课程空间" description="请刷新重试；如果课程尚未加入课程库，请先从课程库加入。">
          <template #actions><el-button type="primary" @click="emit('retry')">重新加载</el-button></template>
        </LpEmptyState>
      </section>
    </template>
    <template v-else>
      <div class="back-row"><el-button text :icon="ArrowLeft" @click="emit('back')">返回我的课程</el-button></div>
      <section class="overview-hero">
        <div class="hero-copy">
          <LpKicker>课程空间</LpKicker>
          <h1 class="hero-title">{{ overview.courseName }}</h1>
          <p class="hero-desc">课程内的作答、错题与复习记录，都会真实汇总在这里。</p>
        </div>
        <div class="hero-actions">
          <el-button
            type="primary"
            size="large"
            :icon="ArrowRight"
            :loading="starting"
            :aria-label="primaryActionLabel"
            @click="emit('primaryAction')"
            >{{ primaryActionLabel }}</el-button
          >
          <el-button size="large" :icon="Collection" @click="emit('openContent')">课程目录</el-button>
          <el-dropdown trigger="click" @command="handleMoreCommand">
            <el-button size="large" :icon="MoreFilled"
              >更多<el-icon class="more-caret"><ArrowDown /></el-icon
            ></el-button>
            <template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item command="papers" :icon="Document">课程试卷</el-dropdown-item
                ><el-dropdown-item command="assessment" :icon="DataAnalysis">阶段测评</el-dropdown-item
                ><el-dropdown-item command="history" :icon="Clock">测评历史</el-dropdown-item></el-dropdown-menu
              ></template
            >
          </el-dropdown>
        </div>
      </section>
      <section v-if="overview.recommendedTargets.length" class="continue-section">
        <div class="continue-main">
          <LpKicker>继续学习</LpKicker>
          <h2 class="continue-title">{{ overview.recommendedTargets[0].title }}</h2>
          <p class="continue-reason">{{ overview.recommendedTargets[0].reason }}</p>
          <el-button type="primary" :icon="ArrowRight" @click="emit('openTarget', overview.recommendedTargets[0])"
            >继续</el-button
          >
        </div>
        <div v-if="overview.recommendedTargets.length > 1" class="continue-alternatives">
          <button
            v-for="target in overview.recommendedTargets.slice(1)"
            :key="target.type + (target.knowledgePointId ?? target.questionId ?? '')"
            type="button"
            class="alternative-item"
            @click="emit('openTarget', target)"
          >
            <span class="alternative-title">{{ target.title }}</span
            ><span class="alternative-reason">{{ target.reason }}</span>
          </button>
        </div>
      </section>
      <div class="overview-grid">
        <div class="overview-main">
          <section class="panel" aria-labelledby="tools-heading">
            <LpSectionHeading
              kicker="学习工具"
              title="继续这一门课"
              description="练习、复习与考试都按本课程的范围进行。"
            />
            <div class="tool-list">
              <button
                v-for="tool in tools"
                :key="tool.routeName"
                type="button"
                class="tool-row"
                @click="emit('openTool', tool.routeName)"
              >
                <span class="tool-icon" aria-hidden="true"
                  ><el-icon :size="17"><component :is="tool.icon" /></el-icon></span
                ><span class="tool-copy"
                  ><strong>{{ tool.title }}</strong
                  ><small>{{ tool.desc }}</small></span
                ><el-icon class="tool-arrow" :size="15" aria-hidden="true"><ArrowRight /></el-icon>
              </button>
            </div>
          </section>
          <section class="panel" aria-labelledby="outline-heading">
            <LpSectionHeading
              kicker="课程目录"
              title="教学内容"
              description="目录状态来自真实完成的理解检查；尚未迁入的内容不会显示进度。"
              ><template #aside
                ><el-button text type="primary" :icon="Collection" @click="emit('openContent')"
                  >完整知识结构</el-button
                ></template
              ></LpSectionHeading
            >
            <div v-if="overview.tutorProgress.length" class="outline-list">
              <div v-for="item in overview.tutorProgress" :key="item.knowledgePointId" class="outline-item">
                <span class="outline-status" :data-status="item.status" aria-hidden="true" />
                <div class="outline-copy">
                  <strong>{{ item.title }}</strong
                  ><small>{{ tutorStatusLabel(item.status) }}</small>
                </div>
                <el-button
                  :type="item.status === 'IN_PROGRESS' ? 'primary' : 'default'"
                  size="small"
                  @click="emit('openTutor', item.knowledgePointId)"
                  >{{ tutorActionLabel(item.status) }}</el-button
                >
              </div>
            </div>
            <LpEmptyState
              v-else
              compact
              title="暂无已迁入的教学内容"
              description="课程内容还在制作中，可以从题目或试卷开始。"
            />
          </section>
        </div>
        <aside class="activity-panel" aria-labelledby="activity-heading">
          <LpKicker>学习情况</LpKicker>
          <h2 id="activity-heading" class="activity-title">最近学习</h2>
          <div class="stats-grid">
            <LpStat label="已作答" :value="overview.answeredCount" note="来自课程内真实判分" /><LpStat
              label="答对"
              :value="overview.correctCount"
              tone="emphasis"
              note="不等同于掌握度"
            /><LpStat label="待复习" :value="overview.dueReviewCount" tone="warning" note="已到间隔复习时间" /><LpStat
              label="未处理错题"
              :value="overview.unresolvedWrongCount"
              tone="danger"
              note="尚未标记为已掌握"
            />
          </div>
          <LpDivider />
          <div class="recent-block">
            <h3 class="recent-heading">最近学习</h3>
            <p v-if="overview.lastLearningTime" class="recent-time">
              最近一次课程内判分：{{ formatDateTime(overview.lastLearningTime) }}
            </p>
            <p v-else class="recent-time">还没有课程内学习记录。可从「继续学习」或课程目录开始。</p>
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
              ><el-button text @click="emit('openAssessmentDetail', overview.latestStageAssessment.id)"
                >查看逐题复盘</el-button
              >
            </div>
          </div>
          <el-button text :icon="Refresh" :loading="loading" @click="emit('refresh')">刷新记录</el-button>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Clock,
  Collection,
  DataAnalysis,
  Document,
  EditPen,
  MoreFilled,
  Promotion,
  Refresh,
  Timer,
  Trophy,
  WarningFilled,
} from '@element-plus/icons-vue'
import type { CourseOverviewVO, CourseStageAssessmentKnowledgePointSummaryVO, LearningTargetVO } from '@/api/course'
import { formatDateTime } from '@/utils/format'

interface CourseTool {
  routeName: string
  title: string
  desc: string
  icon: Component
}

defineProps<{
  overview: CourseOverviewVO | null
  loading: boolean
  failed: boolean
  starting: boolean
  primaryActionLabel: string
}>()
const emit = defineEmits<{
  back: []
  retry: []
  primaryAction: []
  openContent: []
  moreCommand: [command: string]
  openTarget: [target: LearningTargetVO]
  openTool: [routeName: string]
  openTutor: [knowledgePointId: number]
  openAssessmentDetail: [assessmentId: number]
  refresh: []
}>()
const tools: CourseTool[] = [
  { routeName: 'Practice', title: '练习', desc: '按课程范围随机练习，即时判分并复盘', icon: Promotion },
  { routeName: 'Review', title: '复习', desc: '处理本课程到期的间隔复习', icon: Timer },
  { routeName: 'WrongQuestions', title: '错题', desc: '集中处理本课程尚未掌握的题目', icon: WarningFilled },
  { routeName: 'ExamList', title: '真题与试卷', desc: '学习模式逐题理解，考试模式检验阶段效果', icon: Trophy },
  { routeName: 'QuestionList', title: '题目', desc: '按课程、题型和难度浏览题库', icon: EditPen },
]
function handleMoreCommand(command: string) {
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
.course-overview {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-6);
}
.back-row {
  display: flex;
  align-items: center;
}
.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
.overview-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-6);
  padding: var(--lp-space-6) var(--lp-space-8);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
.hero-copy {
  min-width: 0;
}
.hero-title {
  margin-top: var(--lp-space-2);
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-display);
  color: var(--lp-text);
}
.hero-desc {
  margin: var(--lp-space-2) 0 0;
  max-width: 640px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-body);
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.more-caret {
  margin-left: 2px;
}
.continue-section {
  display: flex;
  align-items: stretch;
  gap: var(--lp-space-5);
  padding: var(--lp-space-6);
  background: linear-gradient(120deg, var(--lp-primary-soft), var(--lp-surface) 60%);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
}
.continue-main {
  flex: 1.4;
  min-width: 0;
}
.continue-title {
  margin-top: var(--lp-space-2);
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
}
.continue-reason {
  margin: var(--lp-space-1) 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}
.continue-alternatives {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: var(--lp-space-2);
  border-left: var(--lp-border-hairline);
  padding-left: var(--lp-space-5);
}
.alternative-item {
  display: grid;
  gap: 2px;
  padding: var(--lp-space-3);
  border: 0;
  border-radius: var(--lp-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}
.alternative-item:hover {
  background: var(--lp-surface);
}
.alternative-title {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.alternative-reason {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-snug);
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(280px, 0.85fr);
  gap: var(--lp-space-5);
  align-items: start;
}
.overview-main {
  display: grid;
  gap: var(--lp-space-5);
  min-width: 0;
}
.panel {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
.tool-list {
  display: grid;
  gap: var(--lp-space-2);
}
.tool-row {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: 0;
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    transform var(--lp-duration-fast) var(--lp-ease-out);
}
.tool-row:hover {
  background: var(--lp-primary-soft);
}
.tool-row:hover .tool-arrow {
  color: var(--lp-primary);
  transform: translateX(2px);
}
.tool-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-primary);
}
.tool-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}
.tool-copy strong {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.tool-copy small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.tool-arrow {
  color: var(--lp-text-muted);
  transition:
    color var(--lp-duration-fast) var(--lp-ease-out),
    transform var(--lp-duration-fast) var(--lp-ease-out);
}
.outline-list {
  display: grid;
  gap: var(--lp-space-1);
}
.outline-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-3);
  border-radius: var(--lp-radius-sm);
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}
.outline-item:hover {
  background: var(--lp-surface-soft);
}
.outline-status {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-full);
  background: var(--lp-ink-200);
}
.outline-status[data-status='IN_PROGRESS'] {
  background: var(--lp-primary);
}
.outline-status[data-status='COMPLETED'] {
  background: var(--lp-success);
}
.outline-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}
.outline-copy strong {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-medium);
}
.outline-copy small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.activity-panel {
  position: sticky;
  top: calc(var(--lp-header-height) + var(--lp-space-4));
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
.activity-title {
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-2);
}
.recent-block {
  display: grid;
  gap: var(--lp-space-2);
}
.recent-heading {
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}
.recent-time {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.latest-assessment {
  display: grid;
  gap: 3px;
  margin-top: var(--lp-space-2);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}
.latest-assessment strong {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.latest-assessment span {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.latest-assessment small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}
@media (max-width: 1080px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .activity-panel {
    position: static;
  }
}
@media (max-width: 900px) {
  .overview-hero {
    align-items: stretch;
    flex-direction: column;
  }
  .hero-actions {
    justify-content: flex-start;
  }
}
@media (max-width: 767px) {
  .overview-hero {
    padding: var(--lp-space-5);
  }
  .hero-title {
    font-size: var(--lp-text-3xl);
  }
  .continue-section {
    flex-direction: column;
  }
  .continue-alternatives {
    border-left: 0;
    border-top: var(--lp-border-hairline);
    padding-left: 0;
    padding-top: var(--lp-space-3);
  }
  .panel,
  .activity-panel {
    padding: var(--lp-space-4);
  }
}
</style>
