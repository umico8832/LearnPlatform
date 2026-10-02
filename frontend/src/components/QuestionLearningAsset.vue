<template>
  <section class="learning-asset" aria-label="AI 深度学习">
    <button
      v-if="collapsible && !expanded"
      type="button"
      class="asset-collapsed"
      :aria-expanded="false"
      @click="expandAndLoad"
    >
      <span>AI 深度学习</span><el-icon aria-hidden="true"><ArrowRight /></el-icon>
    </button>
    <template v-else>
      <header class="asset-header">
        <h3>AI 深度学习</h3>
        <button v-if="collapsible" type="button" class="collapse-button" :aria-expanded="true" @click="collapse">
          收起
        </button>
      </header>
      <div class="asset-controls">
        <label :for="`asset-kind-${questionId}`">讲解方式</label>
        <el-select
          :id="`asset-kind-${questionId}`"
          v-model="activeTab"
          :disabled="existingLoading"
          @change="onTabChange"
        >
          <el-option v-for="tab in assetTabs" :key="tab.type" :label="tab.label" :value="tab.type" />
        </el-select>
      </div>
      <LpStatePanel v-if="existingLoading" state="loading" loading-label="正在读取已有讲解" />
      <LpStatePanel v-else-if="existingError" state="error" :description="existingError" @retry="loadExistingAssets" />
      <div v-else ref="assetRoot" class="asset-content" :aria-label="activeDefinition.label" :aria-busy="loading">
        <template v-if="tabContent[activeTab]">
          <QuestionVisualInteractive v-if="activeTab === 'VISUAL_INTERACTIVE'" :content="tabContent[activeTab]" />
          <AiVariantQuestionCard
            v-else-if="activeTab === 'VARIANT' && variantQuestion"
            :question-id="questionId"
            :question="variantQuestion"
            :training="variantTraining"
            @answered="applyVariantTraining"
          />
          <MarkdownRenderer v-else :content="tabContent[activeTab]" />
          <div v-if="activeTab === 'VARIANT' && !variantQuestion" class="variant-training-panel">
            <strong>{{ variantTraining.completed ? '本组变式训练已完成' : '完成这组变式练习后，记录你的进度' }}</strong>
            <p>请先独立作答并核对解析。这里记录你的完成确认，不计为已判分答案。</p>
            <el-button
              :loading="variantTrainingSubmitting"
              :disabled="variantTraining.completed"
              @click="handleVariantTrainingComplete"
            >
              {{ variantTraining.completed ? '已标记完成' : '标记已完成' }}
            </el-button>
            <p v-if="completionError" class="asset-error" role="alert">{{ completionError }}</p>
          </div>
          <QuestionAssetFeedback :question-id="questionId" :asset-type="activeTab" :available="true" />
        </template>
        <template v-else>
          <p class="asset-description">{{ activeDefinition.description }}</p>
          <div v-if="loading" class="stream-heading">
            <p role="status">{{ partialContent ? '正在生成讲解…' : '正在连接 AI 服务…' }}</p>
            <el-button text @click="cancelGeneration">停止生成</el-button>
          </div>
          <p v-else-if="stopped" class="asset-description" role="status">已停止，当前内容可能不完整。</p>
          <p v-if="error" class="asset-error" role="alert">{{ error }}</p>
          <MarkdownRenderer
            v-if="partialContent && partialType === activeTab && activeTab !== 'VISUAL_INTERACTIVE'"
            :content="partialContent"
          />
          <el-button v-if="!loading" class="generate-button" @click="generateTab(activeTab)">
            {{ error || stopped ? '重新生成' : `生成${activeDefinition.label}` }}
          </el-button>
        </template>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, toRef, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import QuestionVisualInteractive from '@/components/QuestionVisualInteractive.vue'
import AiVariantQuestionCard from '@/components/AiVariantQuestionCard.vue'
import QuestionAssetFeedback from './question-learning/QuestionAssetFeedback.vue'
import { useAssetViewTracking } from './question-learning/useAssetViewTracking'
import { useLearningAssets } from './question-learning/useLearningAssets'
import { QUESTION_ASSET_TABS } from './question-learning/assetState'

const props = withDefaults(defineProps<{ questionId: number; collapsible?: boolean }>(), { collapsible: false })
const expanded = ref(false)
const assetTabs = QUESTION_ASSET_TABS
const {
  activeTab,
  tabContent,
  variantQuestion,
  variantTraining,
  variantTrainingSubmitting,
  completionError,
  existingLoading,
  existingError,
  loading,
  partialType,
  partialContent,
  stopped,
  error,
  reset,
  suspend,
  loadExistingAssets,
  onTabChange,
  applyVariantTraining,
  handleVariantTrainingComplete,
  generateTab,
  cancelGeneration,
} = useLearningAssets(toRef(props, 'questionId'), (type) => void trackVisibleAsset(type))
const activeDefinition = computed(() => assetTabs.find((tab) => tab.type === activeTab.value)!)
const { assetRoot, trackVisibleAsset } = useAssetViewTracking({
  questionId: toRef(props, 'questionId'),
  activeType: activeTab,
  hasContent: (type) => (!props.collapsible || expanded.value) && Boolean(tabContent[type]),
  onVariantTraining: applyVariantTraining,
})
function expandAndLoad() {
  expanded.value = true
  void loadExistingAssets()
}
function collapse() {
  expanded.value = false
  suspend()
}
onMounted(() => {
  if (!props.collapsible) void loadExistingAssets()
})
watch(
  () => props.questionId,
  () => {
    expanded.value = false
    if (!props.collapsible) void loadExistingAssets()
  },
)
onScopeDispose(
  onAuthSessionChange(() => {
    expanded.value = false
    if (!props.collapsible && isAuthenticated()) void loadExistingAssets()
  }),
)
</script>

<style scoped>
.learning-asset {
  margin-top: var(--lp-space-5);
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-subtle);
  text-align: left;
}
.asset-header,
.asset-collapsed,
.stream-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
}
.asset-header h3 {
  margin: 0;
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.asset-collapsed,
.collapse-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lp-primary);
  font: inherit;
  cursor: pointer;
}
.asset-collapsed {
  width: 100%;
  text-align: left;
}
.collapse-button {
  font-size: var(--lp-text-sm);
}
.asset-controls {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  margin: var(--lp-space-4) 0;
}
.asset-controls label {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  flex-shrink: 0;
}
.asset-controls :deep(.el-select) {
  width: 210px;
  max-width: 100%;
}
.asset-content {
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.asset-description,
.stream-heading p,
.variant-training-panel p {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
  margin: 0 0 var(--lp-space-3);
}
.stream-heading {
  margin-bottom: var(--lp-space-3);
}
.stream-heading p {
  margin: 0;
}
.asset-error {
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
.generate-button {
  margin-top: var(--lp-space-3);
}
.variant-training-panel {
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
}
.variant-training-panel strong {
  display: block;
  margin-bottom: var(--lp-space-2);
  font-size: var(--lp-text-sm);
}
</style>
