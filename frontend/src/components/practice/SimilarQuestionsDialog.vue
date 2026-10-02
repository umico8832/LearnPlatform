<template>
  <el-dialog v-model="visible" title="相似题推荐" width="800px" destroy-on-close>
    <div v-if="sourceContent" class="similar-source"><strong>原题：</strong>{{ sourceContent }}</div>
    <div v-if="loading" v-loading="true" class="loading-panel" role="status" aria-label="正在读取相似题" />
    <div v-else-if="loadError" class="similar-error" role="alert">
      <p>{{ loadError }}</p>
      <el-button data-testid="similar-retry" @click="load">重试读取</el-button>
    </div>
    <template v-else-if="data?.similarQuestions.length">
      <el-table :data="data.similarQuestions" stripe class="similar-table">
        <el-table-column label="题目内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.questionContent }}</template>
        </el-table-column>
        <el-table-column label="相似度" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.similarityScore"
              :stroke-width="14"
              :text-inside="true"
              :color="similarityColor(row.similarityScore)"
            />
          </template>
        </el-table-column>
        <el-table-column label="相似原因" width="140">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.reason }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="80" align="center">
          <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.difficulty">难度 {{ row.difficulty }}</span>
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
      <p v-if="practiceError" class="similar-error" role="alert">{{ practiceError }}</p>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="starting"
        :disabled="starting || loading || !data?.similarQuestions?.length"
        @click="startPractice"
      >
        开始练习相似题
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { savePracticeSession } from '@/utils/practiceSession'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getQuestionById } from '@/api/question'
import { getSimilarQuestions } from '@/api/statistics'
import type { SimilarQuestions } from '@/api/statistics'
import { errorMessage } from '@/utils/errors'
import { questionTypeLabel } from '@/components/statistics/diagnosisDisplay'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

const router = useRouter()
const visible = ref(false)
const loading = ref(false)
const data = ref<SimilarQuestions | null>(null)
const sourceContent = ref('')
const sourceId = ref<number>()
const loadError = ref('')
const practiceError = ref('')
const starting = ref(false)
let alive = true
let generation = 0
let controller: AbortController | undefined

function current(request: number, session: number) {
  return alive && visible.value && request === generation && session === getAuthSessionVersion() && isAuthenticated()
}

async function open(questionId: number, questionContent?: string) {
  if (!alive) return
  visible.value = true
  sourceId.value = questionId
  sourceContent.value = questionContent || ''
  await load()
}

async function load() {
  if (!sourceId.value || !visible.value) return
  const request = ++generation,
    session = getAuthSessionVersion()
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  starting.value = false
  data.value = null
  loadError.value = ''
  practiceError.value = ''
  try {
    const response = await getSimilarQuestions(sourceId.value, 8, { errorDisplay: 'inline', signal: controller.signal })
    if (current(request, session)) data.value = response.data
  } catch (error) {
    if (current(request, session)) loadError.value = errorMessage(error, '相似题暂时无法读取，请重试。')
  } finally {
    if (current(request, session)) loading.value = false
  }
}

async function startPractice() {
  const userId = useUserStore().userInfo?.id
  const summaries = data.value?.similarQuestions
  if (!summaries?.length || starting.value || loading.value) return
  const request = generation,
    session = getAuthSessionVersion()
  starting.value = true
  practiceError.value = ''
  try {
    const questions = await Promise.all(
      summaries.map((item) =>
        getQuestionById(item.questionId, { errorDisplay: 'inline' }).then((response) => response.data),
      ),
    )
    if (
      !current(request, session) ||
      userId !== useUserStore().userInfo?.id ||
      !savePracticeSession(userId, questions, 'similar')
    )
      return
    visible.value = false
    await router.push({ path: '/practice/session' })
  } catch (error) {
    if (current(request, session)) practiceError.value = errorMessage(error, '题面暂时无法读取，请重试。')
  } finally {
    if (current(request, session)) starting.value = false
  }
}

function clear() {
  generation++
  controller?.abort()
  data.value = null
  sourceId.value = undefined
  sourceContent.value = ''
  loadError.value = ''
  practiceError.value = ''
  loading.value = false
  starting.value = false
}
watch(
  visible,
  (value) => {
    if (!value) clear()
  },
  { flush: 'sync' },
)
const unsubscribe = onAuthSessionChange(() => {
  visible.value = false
  clear()
})
onBeforeUnmount(() => {
  alive = false
  clear()
  unsubscribe()
})

function similarityColor(score: number) {
  if (score >= 80) return 'var(--lp-success)'
  if (score >= 60) return 'var(--lp-warning)'
  return 'var(--lp-primary)'
}

defineExpose({ open })
</script>

<style scoped>
.loading-panel {
  height: 200px;
}

.similar-source {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}

.similar-table {
  margin-top: var(--lp-space-3);
}
.similar-error {
  margin: var(--lp-space-3) 0;
  color: var(--lp-danger);
  overflow-wrap: anywhere;
}
.similar-source {
  overflow-wrap: anywhere;
}
</style>
