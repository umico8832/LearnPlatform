<template>
  <el-dialog v-model="visible" title="相似题推荐" width="800px" destroy-on-close>
    <div v-if="loading" v-loading="true" class="loading-panel" />
    <template v-else-if="data">
      <div class="similar-source"><strong>原题：</strong>{{ sourceContent }}</div>
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
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :disabled="!data?.similarQuestions?.length" @click="startPractice">
        开始练习相似题
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestionById } from '@/api/question'
import { getSimilarQuestions } from '@/api/statistics'
import type { SimilarQuestions } from '@/api/statistics'
import { errorMessage } from '@/utils/errors'

const router = useRouter()
const visible = ref(false)
const loading = ref(false)
const data = ref<SimilarQuestions | null>(null)
const sourceContent = ref('')

async function open(questionId: number, questionContent?: string) {
  visible.value = true
  loading.value = true
  data.value = null
  sourceContent.value = questionContent || ''
  try {
    const response = await getSimilarQuestions(questionId, 8)
    data.value = response.data
  } catch (error) {
    ElMessage.error(`加载相似题失败: ${errorMessage(error, '未知错误')}`)
  } finally {
    loading.value = false
  }
}

async function startPractice() {
  const summaries = data.value?.similarQuestions
  if (!summaries?.length) return
  try {
    const questions = await Promise.all(
      summaries.map((item) => getQuestionById(item.questionId).then((response) => response.data)),
    )
    sessionStorage.setItem('practice_questions', JSON.stringify(questions))
    sessionStorage.setItem('practice_mode', 'similar')
    visible.value = false
    await router.push({ path: '/practice/session' })
  } catch {
    ElMessage.error('加载相似题失败，请重试')
  }
}

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
</style>
