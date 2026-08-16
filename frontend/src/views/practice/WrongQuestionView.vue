<template>
  <div class="wrong-question-container page-container">
    <LpPageHeader
      kicker="薄弱项复盘"
      title="错题"
      description="先处理未掌握题，再用相似题扩展练习，避免反复错在同一类问题上。"
    >
      <template #actions>
        <el-button
          type="primary"
          :icon="RefreshRight"
          @click="handleStartWrongPractice"
          :loading="startPracticeLoading"
        >
          重练错题
        </el-button>
      </template>
    </LpPageHeader>

    <section class="stats-grid" v-if="stats">
      <LpStat v-for="item in statCards" :key="item.label" :label="item.label" :value="item.value" :tone="item.tone" />
    </section>

    <section class="filter-panel">
      <LpSectionHeading kicker="筛选" title="筛选错题" description="当前筛选会同步影响「重练错题」范围。" />
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
    <div class="wrong-list" v-loading="loading">
      <section v-if="!loading && records.length === 0" class="state-panel">
        <LpEmptyState title="暂无错题" description="这里会收集你答错的题目，标记掌握后可随时移出。">
          <template #actions>
            <el-button type="primary" :icon="RefreshRight" @click="$router.push('/practice')">去刷题</el-button>
          </template>
        </LpEmptyState>
      </section>

      <el-card v-for="item in records" :key="item.id" class="wrong-card" shadow="never">
        <div class="wrong-card-header">
          <div class="wrong-meta">
            <el-tag :type="getTypeTag(item.questionType)" size="small">
              {{ getTypeLabel(item.questionType) }}
            </el-tag>
            <el-tag v-if="item.courseName" type="info" size="small">{{ item.courseName }}</el-tag>
            <el-rate v-model="item.difficulty" disabled :max="5" />
            <span class="wrong-count">答错 {{ item.wrongCount }} 次</span>
          </div>
          <div class="wrong-actions">
            <el-tag :type="getMasteryTag(item.masteryLevel)" size="small" effect="dark">
              {{ getMasteryLabel(item.masteryLevel) }}
            </el-tag>
          </div>
        </div>

        <div class="wrong-content">{{ item.questionContent }}</div>

        <div v-if="item.lastWrongAnswer" class="wrong-answer">
          <span class="label">上次错误答案：</span>
          <span class="answer-wrong">{{ item.lastWrongAnswer }}</span>
        </div>

        <AiQuestionAssistant :question-id="item.questionId" />

        <!-- AI 深度学习资产（错题本中折叠展示，减少页面长度） -->
        <QuestionLearningAsset :question-id="item.questionId" collapsible />

        <div class="wrong-card-footer">
          <div class="mastery-controls">
            <span class="label">掌握程度：</span>
            <el-radio-group
              v-model="item.masteryLevel"
              size="small"
              @change="(val: any) => handleMasteryChange(item.id, val as number)"
            >
              <el-radio-button :value="0">未掌握</el-radio-button>
              <el-radio-button :value="1">部分掌握</el-radio-button>
              <el-radio-button :value="2">已掌握</el-radio-button>
            </el-radio-group>
          </div>
          <div class="footer-right">
            <span class="time">{{ formatTime(item.updateTime) }}</span>
            <el-button
              type="primary"
              text
              size="small"
              :icon="Search"
              @click="loadSimilarQuestions(item.questionId, item.questionContent)"
            >
              找相似题
            </el-button>
            <el-popconfirm title="确定从错题本移出？" @confirm="handleRemove(item.id)">
              <template #reference>
                <el-button type="danger" text size="small" :icon="Delete">移出错题本</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </el-card>
    </div>

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

    <!-- 相似题推荐弹窗 -->
    <el-dialog v-model="similarDialogVisible" title="相似题推荐" width="800px" destroy-on-close>
      <div v-if="similarLoading" v-loading="true" style="height: 200px"></div>
      <template v-else-if="similarData">
        <div class="similar-source"><strong>原题：</strong>{{ similarSourceContent }}</div>
        <el-table :data="similarData.similarQuestions" stripe style="margin-top: 12px">
          <el-table-column label="题目内容" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.questionContent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="相似度" width="100" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="row.similarityScore"
                :stroke-width="14"
                :text-inside="true"
                :color="getSimilarityColor(row.similarityScore)"
              />
            </template>
          </el-table-column>
          <el-table-column label="相似原因" width="140">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.reason }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="80" align="center">
            <template #default="{ row }">{{ row.questionType }}</template>
          </el-table-column>
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已练过" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.alreadyAttempted ? 'success' : 'info'" size="small">
                {{ row.alreadyAttempted ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无相似题目" />
      <template #footer>
        <el-button @click="similarDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!similarData?.similarQuestions?.length" @click="startSimilarPractice">
          开始练习相似题
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { errorMessage, SemanticTagType } from '@/utils/errors'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete, RefreshRight, Search } from '@element-plus/icons-vue'
import { getWrongQuestions, getWrongQuestionStats, updateMasteryLevel, removeWrongQuestion } from '@/api/wrongQuestion'
import type { WrongQuestionVO, WrongQuestionStatsVO } from '@/api/wrongQuestion'
import { getWrongQuestionPractice } from '@/api/practice'
import { getSimilarQuestions, type SimilarQuestions } from '@/api/statistics'
import { getQuestionById } from '@/api/question'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import QuestionLearningAsset from '@/components/QuestionLearningAsset.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const startPracticeLoading = ref(false)
const records = ref<WrongQuestionVO[]>([])
const total = ref(0)
const stats = ref<WrongQuestionStatsVO | null>(null)

const statCards = computed(() => [
  { label: '总错题数', value: stats.value?.total ?? 0, tone: 'emphasis' as const },
  { label: '未掌握', value: stats.value?.unmastered ?? 0, tone: 'danger' as const },
  { label: '部分掌握', value: stats.value?.partial ?? 0, tone: 'warning' as const },
  { label: '已掌握', value: stats.value?.mastered ?? 0, tone: 'default' as const },
])

// 相似题推荐
const similarDialogVisible = ref(false)
const similarLoading = ref(false)
const similarData = ref<SimilarQuestions | null>(null)
const similarSourceContent = ref('')

async function loadSimilarQuestions(questionId: number, questionContent?: string) {
  similarDialogVisible.value = true
  similarLoading.value = true
  similarData.value = null
  similarSourceContent.value = questionContent || ''
  try {
    const res = await getSimilarQuestions(questionId, 8)
    similarData.value = res.data
  } catch (e) {
    ElMessage.error('加载相似题失败: ' + errorMessage(e, '未知错误'))
  } finally {
    similarLoading.value = false
  }
}

function startSimilarPractice() {
  if (!similarData.value?.similarQuestions?.length) return
  const similar = similarData.value.similarQuestions
  similarDialogVisible.value = false
  // 相似题接口只返回摘要，这里按 ID 拉取完整题目后写入会话存储，
  // 由答题页沿用既有 session 流程，避免 `?questionIds=` 断链。
  Promise.all(similar.map((item) => getQuestionById(item.questionId).then((res) => res.data)))
    .then((questions) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questions))
      sessionStorage.setItem('practice_mode', 'similar')
      router.push({ path: '/practice/session' })
    })
    .catch(() => {
      ElMessage.error('加载相似题失败，请重试')
    })
}

function getSimilarityColor(score: number): string {
  if (score >= 80) return 'var(--lp-success)'
  if (score >= 60) return 'var(--lp-warning)'
  return 'var(--lp-primary)'
}

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
  loadRecords()
  loadStats()
})

const loadRecords = async () => {
  loading.value = true
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

    const res = await getWrongQuestions(params)
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取错题列表失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getWrongQuestionStats()
    if (res.code === 0) {
      stats.value = res.data
    }
  } catch {
    // ignore
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadRecords()
}

const handleMasteryChange = async (id: number, masteryLevel: number) => {
  try {
    const res = await updateMasteryLevel(id, masteryLevel)
    if (res.code === 0) {
      ElMessage.success('掌握程度已更新')
      loadStats()
    }
  } catch {
    ElMessage.error('更新失败')
  }
}

const handleRemove = async (id: number) => {
  try {
    const res = await removeWrongQuestion(id)
    if (res.code === 0) {
      ElMessage.success('已移出错题本')
      loadRecords()
      loadStats()
    }
  } catch {
    ElMessage.error('移出失败')
  }
}

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

const getTypeTag = (type: string) => {
  const map: Record<string, SemanticTagType> = {
    SINGLE_CHOICE: undefined,
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type]
}

const getMasteryLabel = (level: number) => {
  const map: Record<number, string> = { 0: '未掌握', 1: '部分掌握', 2: '已掌握' }
  return map[level] || '未知'
}

const getMasteryTag = (level: number) => {
  const map: Record<number, SemanticTagType> = { 0: 'danger', 1: 'warning', 2: 'success' }
  return map[level] || 'info'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const handleStartWrongPractice = async () => {
  if (stats.value && stats.value.total === 0) {
    ElMessage.warning('错题本为空，暂无错题可重练')
    return
  }

  startPracticeLoading.value = true
  try {
    const params: { masteryLevel?: number; count?: number } = { count: 10 }
    if (filter.masteryLevel !== undefined) {
      params.masteryLevel = filter.masteryLevel
    }
    const res = await getWrongQuestionPractice(params)
    if (res.code === 0 && res.data) {
      if (res.data.length === 0) {
        ElMessage.warning('当前筛选条件下暂无错题可重练')
        return
      }
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.setItem('practice_mode', 'wrong_question')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.error(res.message || '获取错题失败')
    }
  } catch {
    ElMessage.error('获取错题重练题目失败')
  } finally {
    startPracticeLoading.value = false
  }
}
</script>

<style scoped>
.wrong-question-container {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-6);
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
  display: flex;
  align-items: center;
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

.time {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
}

.similar-source {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
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
    flex-direction: column;
  }
}
</style>
