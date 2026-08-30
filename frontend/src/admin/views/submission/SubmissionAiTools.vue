<template>
  <el-dialog v-model="knowledgeVisible" title="AI 知识点标注" width="720px">
    <div
      v-if="knowledgeLoading"
      v-loading="true"
      element-loading-text="AI 正在分析题目知识点归属，请稍候..."
      style="min-height: 120px"
    />
    <div v-else-if="knowledgeResult">
      <el-alert :title="knowledgeResult.analysis" type="info" show-icon :closable="false" style="margin-bottom: 16px" />

      <div v-if="knowledgeResult.recommendations.length > 0">
        <div style="font-weight: 600; margin-bottom: 8px">
          推荐知识点（共 {{ knowledgeResult.recommendations.length }} 个）
        </div>
        <el-table :data="knowledgeResult.recommendations" border size="small" style="margin-bottom: 16px">
          <el-table-column label="知识点" prop="name" min-width="120" />
          <el-table-column label="课程" prop="courseName" width="120" />
          <el-table-column label="置信度" width="100">
            <template #default="{ row }">
              <el-tag :type="confidenceType(row.confidence)" size="small">{{ confidenceLabel(row.confidence) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="推荐理由" prop="reason" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-card shadow="never" style="background: #f0f9ff">
          <div style="font-size: 13px; color: #606266; margin-bottom: 8px">
            <strong>一键应用：</strong>将以下知识点 ID 应用到投稿的「知识点IDs」字段
          </div>
          <div style="display: flex; align-items: center; gap: 12px">
            <el-input v-model="suggestedIds" readonly style="flex: 1" />
            <el-button type="primary" :loading="applyingKnowledge" @click="applyKnowledge">应用到投稿</el-button>
          </div>
        </el-card>
      </div>
      <el-empty v-else description="未找到匹配的知识点" />
    </div>
    <template #footer>
      <el-button @click="knowledgeVisible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="qualityVisible" title="AI 质检报告" width="720px">
    <div
      v-if="qualityLoading"
      v-loading="true"
      element-loading-text="AI 正在分析题目质量，请稍候..."
      style="min-height: 120px"
    />
    <div v-else-if="qualityResult">
      <el-card shadow="never" style="margin-bottom: 16px">
        <div style="display: flex; align-items: center; justify-content: space-between">
          <div>
            <span style="font-size: 16px; font-weight: 600">综合评分：</span>
            <el-tag :type="qualityScoreType" size="large" style="font-size: 18px; margin-left: 8px">
              {{ qualityResult.qualityScore }} 分
            </el-tag>
          </div>
          <el-tag :type="recommendationType(qualityResult.recommendation)" size="large">
            {{ recommendationLabel(qualityResult.recommendation) }}
          </el-tag>
        </div>
        <p style="margin-top: 10px; color: #606266">{{ qualityResult.summary }}</p>
      </el-card>

      <el-row :gutter="12" style="margin-bottom: 16px">
        <el-col v-for="(item, index) in qualityCheckItems" :key="index" :span="12" style="margin-bottom: 8px">
          <div
            style="
              display: flex;
              align-items: flex-start;
              gap: 8px;
              padding: 8px 12px;
              background: #f5f7fa;
              border-radius: 6px;
            "
          >
            <el-tag :type="checkStatusType(item.status)" size="small" style="flex-shrink: 0">
              {{ checkStatusLabel(item.status) }}
            </el-tag>
            <div>
              <div style="font-weight: 600; font-size: 13px">{{ item.label }}</div>
              <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ item.detail }}</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-card v-if="qualityResult.riskPoints.length > 0" shadow="never" style="margin-bottom: 12px">
        <template #header><span style="color: #e6a23c; font-weight: 600">⚠ 风险点</span></template>
        <ul style="margin: 0; padding-left: 20px">
          <li
            v-for="(point, index) in qualityResult.riskPoints"
            :key="index"
            style="color: #e6a23c; margin-bottom: 4px"
          >
            {{ point }}
          </li>
        </ul>
      </el-card>

      <el-card v-if="qualityResult.suggestions.length > 0" shadow="never">
        <template #header><span style="color: #409eff; font-weight: 600">💡 修改建议</span></template>
        <ul style="margin: 0; padding-left: 20px">
          <li
            v-for="(suggestion, index) in qualityResult.suggestions"
            :key="index"
            style="color: #409eff; margin-bottom: 4px"
          >
            {{ suggestion }}
          </li>
        </ul>
      </el-card>
    </div>
    <template #footer>
      <el-button @click="qualityVisible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="difficultyVisible" title="AI 难度评估报告" width="720px">
    <div
      v-if="difficultyLoading"
      v-loading="true"
      element-loading-text="AI 正在评估题目难度，请稍候..."
      style="min-height: 120px"
    />
    <div v-else-if="difficultyResult">
      <el-card shadow="never" style="margin-bottom: 16px">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
          <div>
            <span style="font-size: 16px; font-weight: 600">AI 评估难度：</span>
            <el-rate
              :model-value="difficultyResult.suggestedDifficulty"
              disabled
              :max="5"
              style="display: inline-flex; margin-left: 8px"
            />
            <el-tag :type="difficultyConfidenceType(difficultyResult.confidence)" size="small" style="margin-left: 8px">
              {{ difficultyConfidenceLabel(difficultyResult.confidence) }}
            </el-tag>
          </div>
          <div v-if="difficultyResult.originalDifficulty">
            <span style="font-size: 13px; color: #909399">投稿者标注：</span>
            <el-rate
              :model-value="difficultyResult.originalDifficulty"
              disabled
              :max="5"
              style="display: inline-flex; margin-left: 4px"
            />
            <el-tag v-if="difficultyResult.difficultyMatch" type="success" size="small" style="margin-left: 4px">
              一致
            </el-tag>
            <el-tag v-else type="warning" size="small" style="margin-left: 4px">不一致</el-tag>
          </div>
        </div>
        <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 8px">
          <span style="font-size: 13px; color: #606266">
            认知层次：<el-tag size="small">{{ difficultyResult.cognitiveLevel }}</el-tag>
          </span>
        </div>
        <p style="color: #606266; margin: 0">{{ difficultyResult.reason }}</p>
      </el-card>

      <div v-if="difficultyResult.factors.length > 0" style="margin-bottom: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">影响难度的因素</div>
        <el-table :data="difficultyResult.factors" border size="small">
          <el-table-column label="因素" prop="name" width="120" />
          <el-table-column label="说明" prop="description" min-width="200" show-overflow-tooltip />
          <el-table-column label="影响" width="100">
            <template #default="{ row }">
              <el-tag :type="impactType(row.impact)" size="small">{{ impactLabel(row.impact) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-card shadow="never" style="background: #f0f9ff">
        <div style="font-size: 13px; color: #606266"><strong>总结：</strong>{{ difficultyResult.summary }}</div>
      </el-card>
    </div>
    <template #footer>
      <el-button @click="difficultyVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  applyKnowledgePoints,
  assessDifficulty,
  kpTaggingSubmission,
  qualityCheckSubmission,
  type QuestionSubmissionVO,
  type SubmissionDifficultyAssessment,
  type SubmissionKPTagging,
  type SubmissionQualityCheck,
} from '@/api/submission'
import type { SemanticTagType } from '@/utils/errors'

type AiToolCommand = 'quality' | 'tagging' | 'difficulty'

const emit = defineEmits<{ updated: [] }>()

const qualityVisible = ref(false)
const qualityLoading = ref(false)
const qualityResult = ref<SubmissionQualityCheck | null>(null)

const knowledgeVisible = ref(false)
const knowledgeLoading = ref(false)
const knowledgeResult = ref<SubmissionKPTagging | null>(null)
const suggestedIds = ref('')
const knowledgeTargetId = ref<number>()
const applyingKnowledge = ref(false)

const difficultyVisible = ref(false)
const difficultyLoading = ref(false)
const difficultyResult = ref<SubmissionDifficultyAssessment | null>(null)

async function open(command: AiToolCommand, submission: Pick<QuestionSubmissionVO, 'id'>) {
  if (command === 'quality') {
    await openQuality(submission.id)
  } else if (command === 'tagging') {
    await openKnowledge(submission.id)
  } else {
    await openDifficulty(submission.id)
  }
}

async function openQuality(submissionId: number) {
  qualityResult.value = null
  qualityLoading.value = true
  qualityVisible.value = true
  try {
    const response = await qualityCheckSubmission(submissionId)
    if (response.code === 0 && response.data) {
      qualityResult.value = response.data
    } else {
      ElMessage.error(response.message || '质检失败')
      qualityVisible.value = false
    }
  } catch {
    ElMessage.error('质检请求失败')
    qualityVisible.value = false
  } finally {
    qualityLoading.value = false
  }
}

async function openKnowledge(submissionId: number) {
  knowledgeResult.value = null
  suggestedIds.value = ''
  knowledgeTargetId.value = submissionId
  knowledgeLoading.value = true
  knowledgeVisible.value = true
  try {
    const response = await kpTaggingSubmission(submissionId)
    if (response.code === 0 && response.data) {
      knowledgeResult.value = response.data
      suggestedIds.value = response.data.suggestedIds
    } else {
      ElMessage.error(response.message || '知识点标注失败')
      knowledgeVisible.value = false
    }
  } catch {
    ElMessage.error('标注请求失败')
    knowledgeVisible.value = false
  } finally {
    knowledgeLoading.value = false
  }
}

async function applyKnowledge() {
  if (!knowledgeTargetId.value || !suggestedIds.value) {
    ElMessage.warning('没有可应用的知识点')
    return
  }
  applyingKnowledge.value = true
  try {
    const response = await applyKnowledgePoints(knowledgeTargetId.value, suggestedIds.value)
    if (response.code === 0) {
      ElMessage.success('知识点已应用到投稿')
      knowledgeVisible.value = false
      emit('updated')
    } else {
      ElMessage.error(response.message || '应用失败')
    }
  } finally {
    applyingKnowledge.value = false
  }
}

async function openDifficulty(submissionId: number) {
  difficultyResult.value = null
  difficultyLoading.value = true
  difficultyVisible.value = true
  try {
    const response = await assessDifficulty(submissionId)
    if (response.code === 0 && response.data) {
      difficultyResult.value = response.data
    } else {
      ElMessage.error(response.message || '难度评估失败')
      difficultyVisible.value = false
    }
  } catch {
    ElMessage.error('难度评估请求失败')
    difficultyVisible.value = false
  } finally {
    difficultyLoading.value = false
  }
}

const qualityScoreType = computed<SemanticTagType>(() => {
  if (!qualityResult.value) return 'info'
  if (qualityResult.value.qualityScore >= 80) return 'success'
  if (qualityResult.value.qualityScore >= 50) return 'warning'
  return 'danger'
})

const qualityCheckItems = computed(() => {
  if (!qualityResult.value) return []
  const result = qualityResult.value
  return [
    { label: '格式规范', status: result.formatCheck.status, detail: result.formatCheck.detail },
    { label: '内容完整性', status: result.completenessCheck.status, detail: result.completenessCheck.detail },
    { label: '答案正确性', status: result.answerCheck.status, detail: result.answerCheck.detail },
    { label: '解析质量', status: result.analysisCheck.status, detail: result.analysisCheck.detail },
    { label: '知识点相关性', status: result.knowledgePointCheck.status, detail: result.knowledgePointCheck.detail },
  ]
})

function recommendationLabel(recommendation: string): string {
  return { APPROVE: '推荐通过', REVISE: '建议修改', REJECT: '建议拒绝' }[recommendation] || recommendation
}

function recommendationType(recommendation: string): SemanticTagType {
  return { APPROVE: 'success', REVISE: 'warning', REJECT: 'danger' }[recommendation] as SemanticTagType
}

function checkStatusLabel(status: string): string {
  return { PASS: '通过', WARNING: '警告', FAIL: '不通过' }[status] || status
}

function checkStatusType(status: string): SemanticTagType {
  return ({ PASS: 'success', WARNING: 'warning', FAIL: 'danger' }[status] || 'info') as SemanticTagType
}

function confidenceLabel(confidence: string): string {
  return { HIGH: '高度相关', MEDIUM: '中等相关', LOW: '可能相关' }[confidence] || confidence
}

function confidenceType(confidence: string): SemanticTagType {
  return ({ HIGH: 'success', MEDIUM: undefined, LOW: 'info' }[confidence] || 'info') as SemanticTagType
}

function difficultyConfidenceLabel(confidence: string): string {
  return { HIGH: '高度可信', MEDIUM: '较为可信', LOW: '仅供参考' }[confidence] || confidence
}

function difficultyConfidenceType(confidence: string): SemanticTagType {
  return ({ HIGH: 'success', MEDIUM: undefined, LOW: 'info' }[confidence] || 'info') as SemanticTagType
}

function impactLabel(impact: string): string {
  return { INCREASE: '↑ 增难', DECREASE: '↓ 降难', NEUTRAL: '— 中性' }[impact] || impact
}

function impactType(impact: string): SemanticTagType {
  return ({ INCREASE: 'danger', DECREASE: 'success', NEUTRAL: 'info' }[impact] || 'info') as SemanticTagType
}

defineExpose({ open })
</script>
