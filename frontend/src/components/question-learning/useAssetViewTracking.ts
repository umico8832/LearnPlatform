import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { recordAssetView, type AiAssetType, type AiVariantTrainingStatus } from '@/api/ai'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

interface AssetViewTrackingOptions {
  questionId: Readonly<Ref<number>>
  activeType: Ref<AiAssetType>
  hasContent: (assetType: AiAssetType) => boolean
  onVariantTraining: (training: AiVariantTrainingStatus) => void
}

export function useAssetViewTracking(options: AssetViewTrackingOptions) {
  const assetRoot = ref<HTMLElement | null>(null)
  const isInViewport = ref(false)
  const trackedViews = new Set<string>()
  let observer: IntersectionObserver | null = null
  let alive = true,
    generation = 0

  async function trackVisibleAsset(assetType: AiAssetType) {
    await nextTick()
    if (!alive || !isInViewport.value || options.activeType.value !== assetType || !options.hasContent(assetType))
      return
    const id = options.questionId.value,
      session = getAuthSessionVersion(),
      version = generation
    const key = `${session}:${id}:${assetType}`
    if (trackedViews.has(key)) return
    trackedViews.add(key)
    try {
      const response = await recordAssetView(id, assetType, { errorDisplay: 'inline' })
      if (!alive || version !== generation || session !== getAuthSessionVersion() || id !== options.questionId.value)
        return
      if (assetType === 'VARIANT' && response.data) options.onVariantTraining(response.data)
    } catch {
      if (version === generation) trackedViews.delete(key)
    }
  }
  function observeTarget(el: HTMLElement | null, old: HTMLElement | null) {
    if (old) observer?.unobserve(old)
    isInViewport.value = false
    if (el) observer?.observe(el)
  }
  onMounted(() => {
    if (typeof IntersectionObserver === 'undefined') return
    observer = new IntersectionObserver(
      (entries) => {
        const entry = entries.find((item) => item.target === assetRoot.value)
        if (!entry) return
        isInViewport.value = entry.isIntersecting
        if (entry.isIntersecting) void trackVisibleAsset(options.activeType.value)
      },
      { threshold: 0.1 },
    )
    if (assetRoot.value) observer.observe(assetRoot.value)
  })
  watch(assetRoot, observeTarget, { flush: 'post' })
  watch(options.questionId, () => {
    generation++
    trackedViews.clear()
  })
  const unsubscribe = onAuthSessionChange(() => {
    generation++
    trackedViews.clear()
  })
  onBeforeUnmount(() => {
    alive = false
    generation++
    observer?.disconnect()
    unsubscribe()
  })
  return { assetRoot, trackVisibleAsset }
}
