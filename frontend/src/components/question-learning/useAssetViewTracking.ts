import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { Ref } from 'vue'
import { recordAssetView } from '@/api/ai'
import type { AiAssetType, AiVariantTrainingStatus } from '@/api/ai'

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
  let visibilityObserver: IntersectionObserver | null = null

  function trackVisibleAsset(assetType: AiAssetType) {
    if (!isInViewport.value || !options.hasContent(assetType)) return
    const key = `${options.questionId.value}:${assetType}`
    if (trackedViews.has(key)) return
    trackedViews.add(key)
    recordAssetView(options.questionId.value, assetType)
      .then((response) => {
        if (assetType === 'VARIANT' && response.data) options.onVariantTraining(response.data)
      })
      .catch(() => {
        trackedViews.delete(key)
      })
  }

  onMounted(() => {
    if (typeof IntersectionObserver === 'undefined') {
      isInViewport.value = true
      return
    }
    if (!assetRoot.value) return
    visibilityObserver = new IntersectionObserver(
      (entries) => {
        isInViewport.value = Boolean(entries[0]?.isIntersecting)
        if (isInViewport.value) trackVisibleAsset(options.activeType.value)
      },
      { threshold: 0.1 },
    )
    visibilityObserver.observe(assetRoot.value)
  })

  onBeforeUnmount(() => visibilityObserver?.disconnect())

  return { assetRoot, trackVisibleAsset }
}
