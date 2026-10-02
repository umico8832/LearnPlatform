<template>
  <div class="wrong-question-container page-container">
    <LpPageHeader title="错题">
      <template #actions>
        <el-button
          type="primary"
          :icon="RefreshRight"
          @click="handleStartWrongPractice"
          :loading="startPracticeLoading"
        >
          练习当前范围
        </el-button>
      </template>
    </LpPageHeader>
    <p v-if="startPracticeError" class="page-action-error" role="alert">
      {{ startPracticeError }}
      <button type="button" :disabled="startPracticeLoading" @click="handleStartWrongPractice">重试</button>
    </p>

    <section class="stats-grid" v-if="stats && !statsLoading">
      <LpStat v-for="item in statCards" :key="item.label" :label="item.label" :value="item.value" :tone="item.tone" />
    </section>

    <section class="filter-panel" aria-label="错题筛选">
      <LpSectionHeading title="当前范围" description="筛选会同步决定本次练习范围。" />
      <el-form :inline="true" :model="filter" class="filter-form">
        <el-form-item label="掌握程度">
          <el-select v-model="filter.masteryLevel" placeholder="全部" clearable>
            <el-option label="未掌握" :value="0" />
            <el-option label="部分掌握" :value="1" />
            <el-option label="已掌握" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
      <div v-if="targetKnowledgePointName" class="kp-filter-chip">
        <el-tag type="info" effect="plain" closable @close="clearKnowledgePointFilter">
          知识点：{{ targetKnowledgePointName }}
        </el-tag>
      </div>
    </section>

    <!-- 错题列表 -->
    <div class="wrong-list">
      <LpStatePanel v-if="loading" state="loading" loading-label="正在读取错题" />
      <LpStatePanel v-else-if="listError" state="error" :description="listError" @retry="loadRecords" />
      <section v-else-if="records.length === 0" class="state-panel">
        <LpEmptyState title="暂无错题" description="这里会收集你答错的题目，标记掌握后可随时移出。">
          <template #actions>
            <el-button type="primary" :icon="RefreshRight" @click="$router.push('/practice')">去刷题</el-button>
          </template>
        </LpEmptyState>
      </section>

      <TransitionGroup v-else name="lp-list" tag="div" class="wrong-card-list">
        <el-card v-for="item in records" :key="item.id" class="wrong-card" shadow="never">
          <div class="wrong-card-header">
            <div class="wrong-meta">
              <el-tag :type="wrongQuestionTypeTag(item.questionType)" size="small">
                {{ wrongQuestionTypeLabel(item.questionType) }}
              </el-tag>
              <el-tag v-if="item.courseName" type="info" size="small">{{ item.courseName }}</el-tag>
              <el-rate v-model="item.difficulty" disabled :max="5" />
              <span class="wrong-count">答错 {{ item.wrongCount }} 次</span>
            </div>
            <div class="wrong-actions">
              <el-tag :type="masteryTag(item.masteryLevel)" size="small" effect="dark">
                {{ masteryLabel(item.masteryLevel) }}
              </el-tag>
            </div>
          </div>

          <div class="wrong-content">{{ item.questionContent }}</div>

          <div v-if="item.lastWrongAnswer" class="wrong-answer">
            <span class="label">上次作答：</span>
            <span class="answer-wrong">{{ item.lastWrongAnswer }}</span>
          </div>

          <details class="question-assistance">
            <summary>学习辅助</summary>
            <AiQuestionAssistant :question-id="item.questionId" />
          </details>

          <div class="wrong-card-footer">
            <div class="mastery-controls">
              <span class="label">掌握程度：</span>
              <el-radio-group
                :model-value="item.masteryLevel"
                size="small"
                :disabled="updatingIds.has(item.id)"
                @change="(val: any) => handleMasteryChange(item.id, val as number)"
              >
                <el-radio-button :value="0">未掌握</el-radio-button>
                <el-radio-button :value="1">部分掌握</el-radio-button>
                <el-radio-button :value="2">已掌握</el-radio-button>
              </el-radio-group>
              <p v-if="masteryErrors[item.id]" class="wrong-action-error" role="alert">
                {{ masteryErrors[item.id].message }}
                <button
                  type="button"
                  :disabled="updatingIds.has(item.id)"
                  @click="handleMasteryChange(item.id, masteryErrors[item.id].masteryLevel)"
                >
                  重试
                </button>
              </p>
            </div>
            <div class="footer-right">
              <el-button
                text
                size="small"
                :icon="Search"
                @click="openSimilarQuestions(item.questionId, item.questionContent)"
              >
                找相似题
              </el-button>
              <span class="time">{{ formatTime(item.updateTime) }}</span>
              <el-popconfirm title="确定从错题本移出？" @confirm="handleRemove(item.id)">
                <template #reference>
                  <el-button type="danger" text size="small" :icon="Delete" :loading="removingIds.has(item.id)"
                    >移出</el-button
                  >
                </template>
              </el-popconfirm>
              <p v-if="removeErrors[item.id]" class="wrong-action-error" role="alert">
                {{ removeErrors[item.id] }}
                <button type="button" :disabled="removingIds.has(item.id)" @click="handleRemove(item.id)">重试</button>
              </p>
            </div>
          </div>
        </el-card>
      </TransitionGroup>
    </div>

    <SimilarQuestionsDialog ref="similarQuestionsDialog" />

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRecords"
        @size-change="loadRecords"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { savePracticeSession } from '@/utils/practiceSession'
import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, RefreshRight, Search } from '@element-plus/icons-vue'
import { getWrongQuestions, getWrongQuestionStats, updateMasteryLevel, removeWrongQuestion } from '@/api/wrongQuestion'
import type { WrongQuestionVO, WrongQuestionStatsVO } from '@/api/wrongQuestion'
import { getWrongQuestionPractice } from '@/api/practice'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import SimilarQuestionsDialog from '@/components/practice/SimilarQuestionsDialog.vue'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import { masteryLabel, masteryTag, wrongQuestionTypeLabel, wrongQuestionTypeTag } from './wrongQuestionPresentation'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const statsLoading = ref(false)
const listError = ref('')
const startPracticeLoading = ref(false)
const startPracticeError = ref('')
const records = ref<WrongQuestionVO[]>([])
const total = ref(0)
const stats = ref<WrongQuestionStatsVO | null>(null)
const updatingIds = ref<Set<number>>(new Set())
const removingIds = ref<Set<number>>(new Set())
const masteryErrors = ref<Record<number, { message: string; masteryLevel: number }>>({})
const removeErrors = ref<Record<number, string>>({})
const similarQuestionsDialog = ref<InstanceType<typeof SimilarQuestionsDialog>>()
let generation = 0
let listVersion = 0
let statsVersion = 0
let alive = true
let practiceRequestVersion = 0

const statCards = computed(() => [
  { label: '待处理', value: (stats.value?.unmastered ?? 0) + (stats.value?.partial ?? 0), tone: 'emphasis' as const },
  { label: '已掌握', value: stats.value?.mastered ?? 0, tone: 'default' as const },
])

const filter = reactive({
  masteryLevel: undefined as number | undefined,
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
})

function positiveQueryNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

const targetCourseId = computed(() => positiveQueryNumber(route.query.courseId))
const targetQuestionId = computed(() => positiveQueryNumber(route.query.questionId))
const targetKnowledgePointId = computed(() => positiveQueryNumber(route.query.knowledgePointId))
const targetKnowledgePointName = computed(() =>
  typeof route.query.knowledgePointName === 'string' ? route.query.knowledgePointName : '',
)

async function clearKnowledgePointFilter() {
  const query = { ...route.query }
  delete query.knowledgePointId
  delete query.knowledgePointName
  await router.replace({ query })
  await loadRecords()
}

onMounted(() => {
  void loadRecords()
  void loadStats()
})

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  records.value = []
  total.value = 0
  stats.value = null
  listError.value = ''
  loading.value = false
  statsLoading.value = false
  startPracticeError.value = ''
  masteryErrors.value = {}
  removeErrors.value = {}
  startPracticeLoading.value = false
  updatingIds.value = new Set()
  removingIds.value = new Set()
  practiceRequestVersion++
})
onBeforeUnmount(() => {
  alive = false
  generation++
  practiceRequestVersion++
  unsubscribeSession()
})

const loadRecords = async () => {
  const requestGeneration = generation
  const version = ++listVersion
  const session = getAuthSessionVersion()
  const current = () =>
    alive && requestGeneration === generation && version === listVersion && session === getAuthSessionVersion()
  loading.value = true
  listError.value = ''
  try {
    const params: {
      pageNum: number
      pageSize: number
      courseId?: number
      questionId?: number
      knowledgePointId?: number
      masteryLevel?: number
    } = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      courseId: targetCourseId.value,
      questionId: targetQuestionId.value,
    }
    if (targetKnowledgePointId.value !== undefined) params.knowledgePointId = targetKnowledgePointId.value
    if (filter.masteryLevel !== undefined) params.masteryLevel = filter.masteryLevel

    const res = await getWrongQuestions(params, { errorDisplay: 'inline' })
    if (!current()) return
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    if (current()) listError.value = '错题暂时无法加载，请重试'
  } finally {
    if (current()) loading.value = false
  }
}

const loadStats = async () => {
  const requestGeneration = generation
  const version = ++statsVersion
  const session = getAuthSessionVersion()
  const current = () =>
    alive && requestGeneration === generation && version === statsVersion && session === getAuthSessionVersion()
  statsLoading.value = true
  try {
    const res = await getWrongQuestionStats({ errorDisplay: 'inline' })
    if (res.code === 0) {
      if (current()) stats.value = res.data
    }
  } catch {
  } finally {
    if (current()) statsLoading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadRecords()
}

const handleMasteryChange = async (id: number, masteryLevel: number) => {
  if (updatingIds.value.has(id)) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  const remainingErrors = { ...masteryErrors.value }
  delete remainingErrors[id]
  masteryErrors.value = remainingErrors
  updatingIds.value = new Set(updatingIds.value).add(id)
  try {
    const res = await updateMasteryLevel(id, masteryLevel, { errorDisplay: 'inline' })
    if (alive && requestGeneration === generation && session === getAuthSessionVersion() && res.code === 0) {
      const record = records.value.find((item) => item.id === id)
      if (record) record.masteryLevel = masteryLevel
      void loadStats()
    } else if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      masteryErrors.value = {
        ...masteryErrors.value,
        [id]: { message: res.message || '更新掌握程度失败，请重试', masteryLevel },
      }
    }
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      masteryErrors.value = { ...masteryErrors.value, [id]: { message: '更新掌握程度失败，请重试', masteryLevel } }
    }
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      const next = new Set(updatingIds.value)
      next.delete(id)
      updatingIds.value = next
    }
  }
}

const handleRemove = async (id: number) => {
  if (removingIds.value.has(id)) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  const remainingErrors = { ...removeErrors.value }
  delete remainingErrors[id]
  removeErrors.value = remainingErrors
  removingIds.value = new Set(removingIds.value).add(id)
  try {
    const res = await removeWrongQuestion(id, { errorDisplay: 'inline' })
    if (alive && requestGeneration === generation && session === getAuthSessionVersion() && res.code === 0) {
      const next = new Set(removingIds.value)
      next.delete(id)
      removingIds.value = next
      void loadRecords()
      void loadStats()
      return
    } else if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      removeErrors.value = { ...removeErrors.value, [id]: res.message || '移出错题失败，请重试' }
    }
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      removeErrors.value = { ...removeErrors.value, [id]: '移出错题失败，请重试' }
    }
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      const next = new Set(removingIds.value)
      next.delete(id)
      removingIds.value = next
    }
  }
}

function openSimilarQuestions(questionId: number, questionContent: string) {
  similarQuestionsDialog.value?.open(questionId, questionContent)
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const handleStartWrongPractice = async () => {
  if (startPracticeLoading.value) return
  const userId = useUserStore().userInfo?.id
  const session = getAuthSessionVersion()
  const requestVersion = ++practiceRequestVersion
  startPracticeError.value = ''
  if (stats.value && stats.value.total === 0) {
    startPracticeError.value = '错题本为空，暂无错题可重练。'
    return
  }

  startPracticeLoading.value = true
  try {
    const params: {
      masteryLevel?: number
      count?: number
      courseId?: number
      questionId?: number
      knowledgePointId?: number
    } = { count: 10 }
    if (targetCourseId.value !== undefined) params.courseId = targetCourseId.value
    if (targetQuestionId.value !== undefined) params.questionId = targetQuestionId.value
    if (targetKnowledgePointId.value !== undefined) params.knowledgePointId = targetKnowledgePointId.value
    if (filter.masteryLevel !== undefined) {
      params.masteryLevel = filter.masteryLevel
    }
    const res = await getWrongQuestionPractice(params, { errorDisplay: 'inline' })
    if (!alive || requestVersion !== practiceRequestVersion || session !== getAuthSessionVersion()) return
    if (res.code === 0 && res.data) {
      if (res.data.length === 0) {
        startPracticeError.value = '当前筛选条件下暂无错题可重练。'
        return
      }
      if (userId !== useUserStore().userInfo?.id || !savePracticeSession(userId, res.data, 'wrong_question')) return
      router.push({ name: 'PracticeSession' })
    } else {
      startPracticeError.value = res.message || '获取错题失败，请重试'
    }
  } catch {
    if (alive && requestVersion === practiceRequestVersion && session === getAuthSessionVersion())
      startPracticeError.value = '获取错题重练题目失败，请重试'
  } finally {
    if (alive && requestVersion === practiceRequestVersion && session === getAuthSessionVersion())
      startPracticeLoading.value = false
  }
}
</script>

<style scoped>
.wrong-card-list {
  position: relative;
}
.wrong-card-list-move,
.wrong-card-list-leave-active {
  transition:
    transform var(--lp-duration-slow) var(--lp-ease-out),
    opacity var(--lp-duration-normal) var(--lp-ease-out);
}
.wrong-card-list-leave-active {
  position: absolute;
  left: 0;
  right: 0;
}
.wrong-card-list-leave-to {
  opacity: 0;
  transform: translateX(var(--lp-space-2));
}
@media (prefers-reduced-motion: reduce) {
  .wrong-card-list-move,
  .wrong-card-list-leave-active {
    transition: none;
  }
  .wrong-card-list-leave-to {
    transform: none;
  }
}

.wrong-question-container {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-6);
}
.page-action-error,
.wrong-action-error {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  margin: 0;
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.page-action-error {
  margin-top: calc(var(--lp-space-4) * -1);
}
.page-action-error button,
.wrong-action-error button {
  padding: 0;
  border: 0;
  border-radius: var(--lp-radius-sm);
  color: var(--lp-primary);
  background: transparent;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.page-action-error button:focus-visible,
.wrong-action-error button:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-3);
}

.filter-panel {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.filter-form {
  display: flex;
  align-items: flex-end;
  gap: var(--lp-space-3);
}

.filter-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 0;
}

.kp-filter-chip {
  margin-top: var(--lp-space-2);
}

.wrong-list {
  display: grid;
  gap: var(--lp-space-4);
}

.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.wrong-card {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.wrong-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-3);
}

.wrong-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
}

.wrong-count {
  font-size: var(--lp-text-sm);
  color: var(--lp-danger);
  font-weight: var(--lp-weight-semibold);
}

.wrong-content {
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
  margin-bottom: var(--lp-space-3);
  white-space: pre-wrap;
}

.wrong-answer {
  font-size: var(--lp-text-sm);
  margin-bottom: var(--lp-space-3);
}

.question-assistance {
  margin-top: var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.question-assistance summary {
  width: fit-content;
  cursor: pointer;
  color: var(--lp-primary);
}

.question-assistance summary:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  border-radius: var(--lp-radius-sm);
}

.wrong-answer .label {
  color: var(--lp-text-muted);
}

.answer-wrong {
  color: var(--lp-danger);
  font-weight: var(--lp-weight-semibold);
}

.wrong-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
}

.mastery-controls {
  display: grid;
  align-items: center;
  grid-template-columns: auto auto;
  gap: var(--lp-space-2);
}

.mastery-controls .label {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}
.mastery-controls .wrong-action-error,
.footer-right .wrong-action-error {
  grid-column: 1 / -1;
}

.time {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .wrong-card-header,
  .wrong-card-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .filter-panel {
    padding: var(--lp-space-4);
  }

  .filter-form,
  .filter-form :deep(.el-select) {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .mastery-controls {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }
}
</style>
