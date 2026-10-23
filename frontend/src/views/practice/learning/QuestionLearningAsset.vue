<template>
  <section class="learning-asset" aria-label="AI 深度学习">
    <button
      v-if="collapsible && !expanded"
      type="button"
      class="asset-collapsed"
      :aria-expanded="false"
      @click="expandAndLoad"
    >
      <span>AI 深度学习</span><span aria-hidden="true"><ArrowRight /></span>
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
        <select :id="`asset-kind-${questionId}`" v-model="activeTab" :disabled="existingLoading" @change="onTabChange">
          <option v-for="tab in assetTabs" :key="tab.type" :value="tab.type">{{ tab.label }}</option>
        </select>
      </div>
      <p v-if="existingLoading" role="status" aria-busy="true">正在读取已有讲解…</p>
      <div v-else-if="existingError" role="alert">
        <p>{{ existingError }}</p>
        <LtButton @click="loadExistingAssets">重试</LtButton>
      </div>
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
            <LtButton
              :aria-busy="variantTrainingSubmitting"
              :disabled="variantTraining.completed || variantTrainingSubmitting"
              @click="handleVariantTrainingComplete"
            >
              {{ variantTraining.completed ? '已标记完成' : '标记已完成' }}
            </LtButton>
            <p v-if="completionError" class="asset-error" role="alert">{{ completionError }}</p>
          </div>
          <QuestionAssetFeedback :question-id="questionId" :asset-type="activeTab" :available="true" />
        </template>
        <template v-else>
          <p class="asset-description">{{ activeDefinition.description }}</p>
          <div v-if="loading" class="stream-heading">
            <p role="status">{{ partialContent ? '正在生成讲解…' : '正在连接 AI 服务…' }}</p>
            <LtButton variant="quiet" @click="cancelGeneration">停止生成</LtButton>
          </div>
          <p v-else-if="stopped" class="asset-description" role="status">已停止，当前内容可能不完整。</p>
          <p v-if="error" class="asset-error" role="alert">{{ error }}</p>
          <MarkdownRenderer
            v-if="partialContent && partialType === activeTab && activeTab !== 'VISUAL_INTERACTIVE'"
            :content="partialContent"
          />
          <LtButton v-if="!loading" class="generate-button" @click="generateTab(activeTab)">
            {{ error || stopped ? '重新生成' : `生成${activeDefinition.label}` }}
          </LtButton>
        </template>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { LtButton } from '@/components/living'
import { computed, onMounted, onScopeDispose, ref, toRef, watch } from 'vue'
import { ArrowRight } from '@lucide/vue'
import { isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import QuestionVisualInteractive from './QuestionVisualInteractive.vue'
import AiVariantQuestionCard from './AiVariantQuestionCard.vue'
import QuestionAssetFeedback from './QuestionAssetFeedback.vue'
import { useAssetViewTracking } from '@/components/question-learning/useAssetViewTracking'
import { useLearningAssets } from '@/components/question-learning/useLearningAssets'
import { QUESTION_ASSET_TABS } from '@/components/question-learning/assetState'

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
  margin-top: var(--lt-space-5);
  padding: var(--lt-space-5);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-well);
  text-align: left;
}
.asset-header,
.asset-collapsed,
.stream-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.asset-header h3 {
  margin: 0;
  font-size: var(--lt-text-body);
  font-weight: 600;
}
.asset-collapsed,
.collapse-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lt-accent);
  font: inherit;
  cursor: pointer;
}
.asset-collapsed {
  width: 100%;
  text-align: left;
}
.collapse-button {
  font-size: var(--lt-text-footnote);
}
.asset-controls {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  margin: var(--lt-space-4) 0;
}
.asset-controls label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  flex-shrink: 0;
}
.asset-controls :deep(select) {
  width: calc(var(--lt-space-1) * 52.5);
  max-width: 100%;
}
.asset-content {
  padding-top: var(--lt-space-4);
  border-top: thin solid var(--lt-separator);
}
.asset-description,
.stream-heading p,
.variant-training-panel p {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-ui);
  margin: 0 0 var(--lt-space-3);
}
.stream-heading {
  margin-bottom: var(--lt-space-3);
}
.stream-heading p {
  margin: 0;
}
.asset-error {
  color: var(--lt-orange-text);
  font-size: var(--lt-text-footnote);
}
.generate-button {
  margin-top: var(--lt-space-3);
}
.variant-training-panel {
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-4);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
}
.variant-training-panel strong {
  display: block;
  margin-bottom: var(--lt-space-2);
  font-size: var(--lt-text-footnote);
}
select {
  min-height: var(--lt-control-lg);
  padding: var(--lt-space-2) var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
select option {
  background: var(--lt-surface);
  color: var(--lt-label);
}
</style>
