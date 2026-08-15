<template>
  <section ref="assetRoot" class="learning-asset">
    <!-- 可折叠模式：仅显示标题栏，点击展开 -->
    <div v-if="collapsible && !expanded" class="asset-collapsed" @click="expandAndLoad">
      <span class="collapsed-icon">📚</span>
      <span class="collapsed-text">AI 深度学习</span>
      <span class="collapsed-hint">点击展开，获取 AI 讲解、步骤拆解、变式题等</span>
      <el-icon class="collapsed-arrow"><ArrowRight /></el-icon>
    </div>

    <!-- 完整内容 -->
    <template v-if="!collapsible || expanded">
      <div class="asset-header">
        <div>
          <div class="asset-title">📚 AI 深度学习</div>
          <div class="asset-subtitle">选择不同维度，把这道题彻底学透。</div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="asset-tabs" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in assetTabs" :key="tab.type" :label="tab.label" :name="tab.type">
          <template #label>
            <span class="tab-label">
              <span class="tab-icon">{{ tab.icon }}</span>
              {{ tab.label }}
            </span>
          </template>

          <div class="asset-content">
            <!-- 已有缓存内容 -->
            <div v-if="tabContent[tab.type]" class="asset-result">
              <div class="result-meta">
                <el-tag size="small" effect="plain" type="success">已缓存</el-tag>
                <span v-if="assetModel[tab.type]" class="model-tag">模型：{{ assetModel[tab.type] }}</span>
              </div>
              <QuestionVisualInteractive v-if="tab.type === 'VISUAL_INTERACTIVE'" :content="tabContent[tab.type]" />
              <AiVariantQuestionCard
                v-else-if="tab.type === 'VARIANT' && variantQuestion"
                :question-id="questionId"
                :question="variantQuestion"
                :training="variantTraining"
                @answered="applyVariantTraining"
              />
              <MarkdownRenderer v-else :content="tabContent[tab.type]" />

              <div v-if="tab.type === 'VARIANT' && !variantQuestion" class="variant-training-panel">
                <div>
                  <strong>{{ variantTraining.completed ? '本组变式训练已完成' : '完成这组变式训练了吗？' }}</strong>
                  <p>请先独立作答并核对解析，再确认完成。该记录是学习者自我确认，不代表系统自动判分。</p>
                </div>
                <el-button
                  type="primary"
                  :loading="variantTrainingSubmitting"
                  :disabled="variantTraining.completed"
                  @click="handleVariantTrainingComplete"
                >
                  {{ variantTraining.completed ? '✓ 已标记完成' : '标记已完成' }}
                </el-button>
              </div>

              <!-- 反馈区域 -->
              <div class="feedback-area">
                <div v-if="feedbackMap[tab.type]?.helpful === null" class="feedback-prompt">
                  <span class="feedback-text">这个讲解对你有帮助吗？</span>
                  <el-button
                    size="small"
                    :type="feedbackMap[tab.type]?.helpful === true ? 'success' : 'default'"
                    :loading="feedbackSubmitting === tab.type"
                    @click="handleFeedback(tab.type, true)"
                  >
                    👍 有帮助
                  </el-button>
                  <el-button
                    size="small"
                    :type="feedbackMap[tab.type]?.helpful === false ? 'danger' : 'default'"
                    :loading="feedbackSubmitting === tab.type"
                    @click="handleFeedback(tab.type, false)"
                  >
                    👎 没帮助
                  </el-button>
                </div>

                <div v-else class="feedback-done">
                  <el-tag :type="feedbackMap[tab.type]?.helpful ? 'success' : 'warning'" size="small" effect="plain">
                    {{ feedbackMap[tab.type]?.helpful ? '👍 已反馈：有帮助' : '👎 已反馈：没帮助' }}
                  </el-tag>
                  <el-button
                    v-if="
                      feedbackMap[tab.type]?.helpful === false &&
                      showCommentInput !== tab.type &&
                      !feedbackMap[tab.type]?.comment
                    "
                    size="small"
                    text
                    type="primary"
                    @click="showCommentInput = tab.type"
                  >
                    补充说明
                  </el-button>
                  <el-button size="small" text type="info" @click="feedbackMap[tab.type].helpful = null">
                    重新反馈
                  </el-button>
                </div>

                <div v-if="showCommentInput === tab.type" class="feedback-comment">
                  <el-input
                    v-model="feedbackMap[tab.type].comment"
                    type="textarea"
                    :rows="2"
                    placeholder="请告诉我们哪里可以改进（可选）..."
                    maxlength="500"
                    show-word-limit
                    size="small"
                  />
                  <el-button
                    size="small"
                    type="primary"
                    :loading="feedbackSubmitting === tab.type"
                    @click="submitFeedbackComment(tab.type)"
                    style="margin-top: 8px"
                  >
                    提交
                  </el-button>
                </div>
              </div>
            </div>

            <!-- 加载中 -->
            <div v-else-if="loadingType === tab.type" class="asset-loading">
              <div class="stream-placeholder">
                <el-icon class="is-loading"><Loading /></el-icon>
                正在生成 {{ tab.label }}，请稍候...
              </div>
              <div v-if="streamBuffer" class="asset-result">
                <QuestionVisualInteractive v-if="activeTab === 'VISUAL_INTERACTIVE'" :content="streamBuffer" />
                <MarkdownRenderer v-else :content="streamBuffer" />
              </div>
            </div>

            <!-- 空状态：未生成 -->
            <div v-else class="asset-empty">
              <div class="empty-icon">{{ tab.icon }}</div>
              <div class="empty-text">{{ tab.description }}</div>
              <el-button type="primary" :loading="loading" @click="generateTab(tab.type)">
                <el-icon><MagicStick /></el-icon>
                生成{{ tab.label }}
              </el-button>
            </div>

            <!-- 错误提示 -->
            <el-alert
              v-if="error && loadingType !== tab.type"
              :title="error"
              type="error"
              show-icon
              :closable="false"
              style="margin-top: 12px"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { errorMessage, isAbortError } from '@/utils/errors'
import { Loading, MagicStick, ArrowRight } from '@element-plus/icons-vue'
import {
  type AiAssetType,
  type QuestionLearningAsset,
  getQuestionAssets,
  generateAsset,
  streamAsset,
  submitAssetFeedback,
  getAssetFeedback,
  recordAssetView,
  completeVariantTraining,
  type AiVariantTrainingStatus,
  type AiVariantQuestion,
} from '@/api/ai'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import QuestionVisualInteractive from '@/components/QuestionVisualInteractive.vue'
import AiVariantQuestionCard from '@/components/AiVariantQuestionCard.vue'
import { ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    questionId: number
    collapsible?: boolean
  }>(),
  {
    collapsible: false,
  },
)

const expanded = ref(false)
const assetRoot = ref<HTMLElement | null>(null)
const isInViewport = ref(false)
let visibilityObserver: IntersectionObserver | null = null

interface AssetTab {
  type: AiAssetType
  label: string
  icon: string
  description: string
}

const assetTabs: AssetTab[] = [
  {
    type: 'FULL_EXPLANATION',
    label: '标准解析',
    icon: '📖',
    description: '包含知识点、正确答案分析、错误选项分析、关键思路和总结。',
  },
  {
    type: 'BEGINNER_EXPLANATION',
    label: '小白版',
    icon: '🌱',
    description: '少术语、多铺垫，用最简单的方式一步一步讲解。',
  },
  { type: 'STEP_BY_STEP', label: '步骤拆解', icon: '🪜', description: '将解题过程拆成明确的、可执行的步骤。' },
  { type: 'WRONG_OPTION_ANALYSIS', label: '错误选项', icon: '🎯', description: '分析每个错误选项利用了什么思维陷阱。' },
  { type: 'COMMON_MISTAKES', label: '常见误区', icon: '🚫', description: '列出学生最容易犯的错误和正确的理解。' },
  {
    type: 'VISUAL_INTERACTIVE',
    label: '可视化讲解',
    icon: '📊',
    description: '用图表、数组、树等可视化元素展示解题过程，适合算法和数据结构题目。',
  },
  { type: 'VARIANT', label: '变式题', icon: '🔄', description: '生成 1 道可提交、可判分的单选变式题，检验知识迁移。' },
]

const activeTab = ref<AiAssetType>('FULL_EXPLANATION')
const loadingType = ref<AiAssetType | null>(null)
const error = ref('')
const streamBuffer = ref('')
const tabContent = reactive<Record<AiAssetType, string>>({
  FULL_EXPLANATION: '',
  BEGINNER_EXPLANATION: '',
  STEP_BY_STEP: '',
  WRONG_OPTION_ANALYSIS: '',
  COMMON_MISTAKES: '',
  VARIANT: '',
  VISUAL_INTERACTIVE: '',
})
const assetModel = reactive<Record<AiAssetType, string>>({
  FULL_EXPLANATION: '',
  BEGINNER_EXPLANATION: '',
  STEP_BY_STEP: '',
  WRONG_OPTION_ANALYSIS: '',
  COMMON_MISTAKES: '',
  VARIANT: '',
  VISUAL_INTERACTIVE: '',
})
const variantQuestion = ref<AiVariantQuestion | null>(null)

// 反馈状态
const feedbackMap = reactive<Record<AiAssetType, { helpful: boolean | null; comment: string }>>({
  FULL_EXPLANATION: { helpful: null, comment: '' },
  BEGINNER_EXPLANATION: { helpful: null, comment: '' },
  STEP_BY_STEP: { helpful: null, comment: '' },
  WRONG_OPTION_ANALYSIS: { helpful: null, comment: '' },
  COMMON_MISTAKES: { helpful: null, comment: '' },
  VARIANT: { helpful: null, comment: '' },
  VISUAL_INTERACTIVE: { helpful: null, comment: '' },
})
const feedbackSubmitting = ref<AiAssetType | null>(null)
const showCommentInput = ref<AiAssetType | null>(null)
const variantTrainingSubmitting = ref(false)
const variantTraining = reactive<{
  status: '' | 'STARTED' | 'COMPLETED'
  completed: boolean
  answered: boolean
  correct: boolean | null
  userAnswer: string
  correctAnswer: string
  analysis: string
  startedTime: string
  answeredTime: string
  completedTime: string
}>({
  status: '',
  completed: false,
  answered: false,
  correct: null,
  userAnswer: '',
  correctAnswer: '',
  analysis: '',
  startedTime: '',
  answeredTime: '',
  completedTime: '',
})

let abortController: AbortController | null = null
const trackedViews = new Set<string>()

const loading = computed(() => loadingType.value !== null)

// 加载已有缓存资产（非折叠模式立即加载，折叠模式展开时加载）
onMounted(() => {
  if (typeof IntersectionObserver === 'undefined') {
    isInViewport.value = true
  } else if (assetRoot.value) {
    visibilityObserver = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        isInViewport.value = Boolean(entry?.isIntersecting)
        if (isInViewport.value) trackVisibleAsset(activeTab.value)
      },
      { threshold: 0.1 },
    )
    visibilityObserver.observe(assetRoot.value)
  }
  if (!props.collapsible) {
    loadExistingAssets()
  }
})

onBeforeUnmount(() => {
  visibilityObserver?.disconnect()
})

watch(
  () => props.questionId,
  () => {
    reset()
    if (!props.collapsible || expanded.value) {
      loadExistingAssets()
    }
  },
)

function expandAndLoad() {
  expanded.value = true
  loadExistingAssets()
}

function reset() {
  abortController?.abort()
  abortController = null
  loadingType.value = null
  error.value = ''
  streamBuffer.value = ''
  showCommentInput.value = null
  feedbackSubmitting.value = null
  variantTrainingSubmitting.value = false
  variantTraining.status = ''
  variantTraining.completed = false
  variantTraining.answered = false
  variantTraining.correct = null
  variantTraining.userAnswer = ''
  variantTraining.correctAnswer = ''
  variantTraining.analysis = ''
  variantTraining.startedTime = ''
  variantTraining.answeredTime = ''
  variantTraining.completedTime = ''
  variantQuestion.value = null
  for (const key of Object.keys(tabContent) as AiAssetType[]) {
    tabContent[key] = ''
    assetModel[key] = ''
    feedbackMap[key] = { helpful: null, comment: '' }
  }
}

async function loadExistingAssets() {
  try {
    const data = await getQuestionAssets(props.questionId)
    if (data?.code === 0 && data.data) {
      for (const asset of data.data as QuestionLearningAsset[]) {
        tabContent[asset.assetType] = asset.content
        assetModel[asset.assetType] = asset.model || ''
        if (asset.assetType === 'VARIANT') {
          variantQuestion.value = asset.variantQuestion || null
        }
      }
      // 加载已有资产的反馈状态
      for (const asset of data.data as QuestionLearningAsset[]) {
        loadFeedback(asset.assetType)
      }
      trackVisibleAsset(activeTab.value)
    }
  } catch {
    // 静默失败，用户可手动触发生成
  }
}

async function loadFeedback(assetType: AiAssetType) {
  try {
    const res = await getAssetFeedback(props.questionId, assetType)
    if (res?.code === 0 && res.data) {
      feedbackMap[assetType].helpful = res.data.helpful
      feedbackMap[assetType].comment = res.data.comment || ''
    }
  } catch {
    // 静默失败
  }
}

async function handleFeedback(assetType: AiAssetType, helpful: boolean) {
  feedbackSubmitting.value = assetType
  try {
    const res = await submitAssetFeedback(props.questionId, assetType, helpful)
    if (res?.code === 0) {
      feedbackMap[assetType].helpful = helpful
      if (helpful) {
        showCommentInput.value = null
      }
    }
  } catch {
    // 静默失败
  } finally {
    feedbackSubmitting.value = null
  }
}

async function submitFeedbackComment(assetType: AiAssetType) {
  if (feedbackMap[assetType].helpful === null) return
  feedbackSubmitting.value = assetType
  try {
    const res = await submitAssetFeedback(
      props.questionId,
      assetType,
      feedbackMap[assetType].helpful!,
      feedbackMap[assetType].comment || undefined,
    )
    if (res?.code === 0) {
      showCommentInput.value = null
    }
  } catch {
    // 静默失败
  } finally {
    feedbackSubmitting.value = null
  }
}

function onTabChange() {
  error.value = ''
  // 切换 tab 时如果正在加载，取消之前的请求
  if (loadingType.value && loadingType.value !== activeTab.value) {
    abortController?.abort()
    abortController = null
    loadingType.value = null
    streamBuffer.value = ''
  }
  trackVisibleAsset(activeTab.value)
}

function trackVisibleAsset(assetType: AiAssetType) {
  if (!isInViewport.value || !tabContent[assetType]) return
  const key = `${props.questionId}:${assetType}`
  if (trackedViews.has(key)) return
  trackedViews.add(key)
  recordAssetView(props.questionId, assetType)
    .then((response) => {
      if (assetType === 'VARIANT' && response.data) applyVariantTraining(response.data)
    })
    .catch(() => {
      trackedViews.delete(key)
    })
}

function applyVariantTraining(training: AiVariantTrainingStatus) {
  variantTraining.status = training.status
  variantTraining.completed = training.completed
  variantTraining.answered = Boolean(training.answered)
  variantTraining.correct = training.correct ?? null
  variantTraining.userAnswer = training.userAnswer || ''
  variantTraining.correctAnswer = training.correctAnswer || ''
  variantTraining.analysis = training.analysis || ''
  variantTraining.startedTime = training.startedTime || ''
  variantTraining.answeredTime = training.answeredTime || ''
  variantTraining.completedTime = training.completedTime || ''
}

async function handleVariantTrainingComplete() {
  if (variantTraining.completed || variantTrainingSubmitting.value) return
  variantTrainingSubmitting.value = true
  try {
    if (!variantTraining.status) {
      const startedResponse = await recordAssetView(props.questionId, 'VARIANT')
      if (startedResponse.data) applyVariantTraining(startedResponse.data)
    }
    const completedResponse = await completeVariantTraining(props.questionId)
    applyVariantTraining(completedResponse.data)
    ElMessage.success('已记录本组变式训练完成')
  } catch (e) {
    ElMessage.error(errorMessage(e, '记录训练完成失败，请稍后重试'))
  } finally {
    variantTrainingSubmitting.value = false
  }
}

async function generateTab(type: AiAssetType) {
  if (tabContent[type]) return // 已有缓存

  loadingType.value = type
  error.value = ''
  streamBuffer.value = ''

  const controller = new AbortController()
  abortController = controller

  try {
    if (type === 'VARIANT') {
      const response = await generateAsset(props.questionId, type)
      const asset = response.data
      tabContent[type] = asset.content
      assetModel[type] = asset.model || ''
      variantQuestion.value = asset.variantQuestion || null
      trackVisibleAsset(type)
      return
    }
    await streamAsset(
      props.questionId,
      type,
      {
        onContent: (content) => {
          streamBuffer.value += content
        },
        onDone: () => {
          // 流式完成，将 buffer 存入缓存内容
          tabContent[type] = streamBuffer.value
          streamBuffer.value = ''
          trackVisibleAsset(type)
        },
      },
      controller.signal,
    )
  } catch (e) {
    if (!isAbortError(e)) {
      error.value = errorMessage(e, 'AI 服务调用失败，请稍后重试')
    }
  } finally {
    if (abortController === controller) {
      abortController = null
      loadingType.value = null
    }
  }
}
</script>

<style scoped>
.learning-asset {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #d9e5f2;
  border-radius: 10px;
  background: #f8fbff;
  text-align: left;
}

.asset-header {
  margin-bottom: 12px;
}

.asset-title {
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 700;
}

.asset-subtitle {
  margin-top: 4px;
  color: #7a8797;
  font-size: 12px;
  line-height: 1.5;
}

.asset-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.asset-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tab-icon {
  font-size: 14px;
}

.asset-content {
  padding: 16px 0 8px;
  min-height: 120px;
}

.asset-result {
  animation: fadeIn 0.3s ease;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.model-tag {
  color: #7a8797;
  font-size: 12px;
}

.asset-loading {
  animation: fadeIn 0.3s ease;
}

.stream-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #7a8797;
  font-size: 13px;
  margin-bottom: 12px;
}

.asset-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 16px;
  text-align: center;
}

.empty-icon {
  font-size: 36px;
  line-height: 1;
}

.empty-text {
  color: #7a8797;
  font-size: 13px;
  line-height: 1.6;
  max-width: 360px;
}

.asset-collapsed {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border: 1px dashed #c0d4e8;
  border-radius: 8px;
  background: #f0f6ff;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.asset-collapsed:hover {
  background: #e4efff;
  border-color: #a0c0e0;
}

.collapsed-icon {
  font-size: 18px;
}

.collapsed-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.collapsed-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.collapsed-arrow {
  margin-left: auto;
  color: #909399;
}

/* 反馈区域 */
.feedback-area {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.variant-training-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid #cce5d5;
  border-radius: 10px;
  background: #f3faf5;
}

.variant-training-panel strong {
  color: #256b43;
  font-size: 14px;
}

.variant-training-panel p {
  margin: 5px 0 0;
  color: #6f7e75;
  font-size: 12px;
  line-height: 1.55;
}

.feedback-prompt {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-text {
  font-size: 13px;
  color: #606266;
}

.feedback-done {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-comment {
  margin-top: 8px;
  max-width: 400px;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 720px) {
  .asset-tabs :deep(.el-tabs__item) {
    padding: 0 8px;
    font-size: 12px;
  }

  .tab-icon {
    font-size: 12px;
  }

  .feedback-prompt,
  .feedback-done {
    gap: 6px;
  }

  .feedback-comment {
    max-width: 100%;
  }

  .variant-training-panel {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
