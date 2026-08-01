<template>
  <div class="turnstile-box">
    <div ref="containerRef" class="turnstile-container" aria-label="人机验证"></div>
    <p v-if="errorMessage" class="turnstile-error" role="alert">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{ modelValue: string; theme?: TurnstileTheme }>(), {
  theme: 'dark',
})
const emit = defineEmits<{
  'update:modelValue': [value: string]
  expired: []
  error: []
}>()

const SCRIPT_ID = 'cloudflare-turnstile-script'
const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let scriptPromise: Promise<void> | null = null
const containerRef = ref<HTMLElement>()
const widgetId = ref('')
const errorMessage = ref('')
let disposed = false

function loadScript() {
  if (window.turnstile) return Promise.resolve()
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise((resolve, reject) => {
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', reject, { once: true })
      return
    }
    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => {
      script.remove()
      scriptPromise = null
      reject(new Error('Turnstile script failed to load'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

function clearToken() {
  emit('update:modelValue', '')
}

async function renderWidget() {
  const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY
  if (!siteKey) {
    errorMessage.value = '人机验证尚未配置，请联系管理员'
    clearToken()
    emit('error')
    return
  }
  try {
    await loadScript()
    await nextTick()
    if (disposed || !containerRef.value || !window.turnstile) return
    widgetId.value = window.turnstile.render(containerRef.value, {
      sitekey: siteKey,
      theme: props.theme,
      size: 'flexible',
      callback: (token: string) => {
        errorMessage.value = ''
        emit('update:modelValue', token)
      },
      'expired-callback': () => {
        clearToken()
        errorMessage.value = '验证已过期，请重新验证'
        emit('expired')
      },
      'error-callback': () => {
        clearToken()
        errorMessage.value = '验证失败，请刷新后重试'
        emit('error')
      },
    })
  } catch {
    errorMessage.value = '人机验证加载失败，请检查网络后重试'
    clearToken()
    emit('error')
  }
}

function reset() {
  clearToken()
  errorMessage.value = ''
  if (widgetId.value && window.turnstile) window.turnstile.reset(widgetId.value)
  else renderWidget()
}

onMounted(renderWidget)
onBeforeUnmount(() => {
  disposed = true
  if (widgetId.value && window.turnstile) window.turnstile.remove(widgetId.value)
})
defineExpose({ reset })
</script>

<style scoped>
.turnstile-box { width: 100%; }
.turnstile-container { min-height: 65px; width: 100%; }
.turnstile-error { margin: 7px 0 0; color: #fca5a5; font-size: 12px; line-height: 1.5; }
</style>
