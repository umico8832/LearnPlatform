import { computed, onScopeDispose, reactive, ref, watch, type Ref } from 'vue'
import {
  completeVariantTraining,
  generateAsset,
  getQuestionAssets,
  recordAssetView,
  streamAsset,
  type AiAssetType,
  type AiVariantQuestion,
  type AiVariantTrainingStatus,
} from '@/api/ai'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage, isAbortError } from '@/utils/errors'
import {
  applyVariantTrainingState,
  createAssetContent,
  createVariantTrainingState,
  resetVariantTrainingState,
} from './assetState'

export function useLearningAssets(questionId: Readonly<Ref<number>>, onContentReady: (type: AiAssetType) => void) {
  const activeTab = ref<AiAssetType>('FULL_EXPLANATION')
  const tabContent = reactive(createAssetContent())
  const variantQuestion = ref<AiVariantQuestion | null>(null)
  const variantTraining = reactive(createVariantTrainingState())
  const variantTrainingSubmitting = ref(false)
  const completionError = ref('')
  const existingLoading = ref(false)
  const existingError = ref('')
  const loadingType = ref<AiAssetType | null>(null)
  const partialType = ref<AiAssetType | null>(null)
  const partialContent = ref('')
  const stopped = ref(false)
  const error = ref('')
  const loading = computed(() => loadingType.value !== null)
  let generation = 0,
    alive = true
  let controller: AbortController | null = null
  let loadController: AbortController | null = null

  function current(version: number, session: number, id: number) {
    return alive && version === generation && session === getAuthSessionVersion() && id === questionId.value
  }
  function cancelGeneration() {
    controller?.abort()
    controller = null
    if (loadingType.value) stopped.value = true
    loadingType.value = null
  }
  function suspend() {
    cancelGeneration()
    loadController?.abort()
    loadController = null
    existingLoading.value = false
  }
  function reset() {
    generation++
    suspend()
    existingError.value = ''
    partialContent.value = ''
    partialType.value = null
    stopped.value = false
    error.value = ''
    completionError.value = ''
    variantTrainingSubmitting.value = false
    resetVariantTrainingState(variantTraining)
    variantQuestion.value = null
    activeTab.value = 'FULL_EXPLANATION'
    Object.assign(tabContent, createAssetContent())
  }
  async function loadExistingAssets() {
    if (existingLoading.value) return
    const version = generation,
      session = getAuthSessionVersion(),
      id = questionId.value
    const request = new AbortController()
    loadController = request
    existingLoading.value = true
    existingError.value = ''
    try {
      const response = await getQuestionAssets(id, { errorDisplay: 'inline', signal: request.signal })
      if (!current(version, session, id) || request.signal.aborted) return
      for (const asset of response.data ?? []) {
        if (!(asset.assetType in tabContent)) continue
        tabContent[asset.assetType] = asset.content
        if (asset.assetType === 'VARIANT') variantQuestion.value = asset.variantQuestion ?? null
      }
      onContentReady(activeTab.value)
    } catch (cause) {
      if (current(version, session, id) && !request.signal.aborted && !isAbortError(cause))
        existingError.value = '已有讲解暂时无法读取，请重试。'
    } finally {
      if (current(version, session, id) && loadController === request) {
        existingLoading.value = false
        loadController = null
      }
    }
  }
  function onTabChange() {
    if (loadingType.value) cancelGeneration()
    partialContent.value = ''
    partialType.value = null
    stopped.value = false
    error.value = ''
    completionError.value = ''
    onContentReady(activeTab.value)
  }
  function applyVariantTraining(training: AiVariantTrainingStatus) {
    if (training.questionId === questionId.value) applyVariantTrainingState(variantTraining, training)
  }
  async function handleVariantTrainingComplete() {
    if (variantTraining.completed || variantTrainingSubmitting.value) return
    const version = generation,
      session = getAuthSessionVersion(),
      id = questionId.value
    variantTrainingSubmitting.value = true
    completionError.value = ''
    try {
      if (!variantTraining.status) {
        const started = await recordAssetView(id, 'VARIANT', { errorDisplay: 'inline' })
        if (!current(version, session, id)) return
        if (started.data) applyVariantTraining(started.data)
      }
      const response = await completeVariantTraining(id, { errorDisplay: 'inline' })
      if (current(version, session, id)) applyVariantTraining(response.data)
    } catch (cause) {
      if (current(version, session, id)) completionError.value = errorMessage(cause, '未能记录完成，请重试。')
    } finally {
      if (current(version, session, id)) variantTrainingSubmitting.value = false
    }
  }
  async function generateTab(type: AiAssetType) {
    if (tabContent[type] || loading.value || existingLoading.value || existingError.value) return
    const version = generation,
      session = getAuthSessionVersion(),
      id = questionId.value
    const request = new AbortController()
    controller = request
    loadingType.value = type
    partialType.value = type
    partialContent.value = ''
    error.value = ''
    stopped.value = false
    const valid = () => current(version, session, id) && !request.signal.aborted
    try {
      if (type === 'VARIANT') {
        const response = await generateAsset(id, type, { errorDisplay: 'inline', signal: request.signal })
        if (!valid()) return
        tabContent[type] = response.data.content
        variantQuestion.value = response.data.variantQuestion ?? null
        onContentReady(type)
        return
      }
      await streamAsset(
        id,
        type,
        {
          onContent: (content) => {
            if (valid()) partialContent.value += content
          },
          onDone: () => {
            if (!valid()) return
            tabContent[type] = partialContent.value
            partialContent.value = ''
            onContentReady(type)
          },
        },
        request.signal,
      )
    } catch (cause) {
      if (valid() && !isAbortError(cause)) error.value = errorMessage(cause, '讲解暂时无法完成，请重试。')
    } finally {
      if (valid()) {
        controller = null
        loadingType.value = null
      }
    }
  }
  watch(questionId, reset, { flush: 'sync' })
  onScopeDispose(onAuthSessionChange(reset))
  onScopeDispose(() => {
    alive = false
    reset()
  })
  return {
    activeTab,
    tabContent,
    variantQuestion,
    variantTraining,
    variantTrainingSubmitting,
    completionError,
    existingLoading,
    existingError,
    loadingType,
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
  }
}
