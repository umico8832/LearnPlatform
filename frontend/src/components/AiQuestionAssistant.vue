<template>
  <section class="ai-assistant">
    <div class="ai-toolbar">
      <div>
        <div class="ai-title">AI 学习助手</div>
        <div class="ai-subtitle">针对当前题目补充思路，或生成一道同类练习。</div>
      </div>
      <div class="ai-actions">
        <el-button
          :type="activeType === 'explanation' ? 'primary' : 'default'"
          :loading="loadingType === 'explanation'"
          :disabled="loading"
          @click="generate('explanation')"
        >
          <el-icon><Reading /></el-icon>
          AI 深度解析
        </el-button>
        <el-button
          :type="activeType === 'variant' ? 'warning' : 'default'"
          :loading="loadingType === 'variant'"
          :disabled="loading"
          @click="generate('variant')"
        >
          <el-icon><MagicStick /></el-icon>
          生成变式题
        </el-button>
      </div>
    </div>

    <div v-if="activeResult || error || loading" class="ai-result">
      <div class="result-heading">
        <span>{{ activeType === 'variant' ? '变式练习' : '补充解析' }}</span>
        <el-tag v-if="activeSource" size="small" effect="plain">{{ sourceLabel }}</el-tag>
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />
      <MarkdownRenderer v-else-if="activeResult" :content="activeResult" />
      <div v-else-if="loading" class="stream-placeholder">正在连接 AI 服务...</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MagicStick, Reading } from '@element-plus/icons-vue'
import { streamQuestionAi } from '@/api/ai'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

type AssistantType = 'explanation' | 'variant'

const props = defineProps<{
  questionId: number
}>()

const activeType = ref<AssistantType | null>(null)
const loadingType = ref<AssistantType | null>(null)
const error = ref('')
const results = ref<Record<AssistantType, string>>({ explanation: '', variant: '' })
const sources = ref<Record<AssistantType, string>>({ explanation: '', variant: '' })
let abortController: AbortController | null = null

const loading = computed(() => loadingType.value !== null)
const activeResult = computed(() => activeType.value ? results.value[activeType.value] : '')
const activeSource = computed(() => activeType.value ? sources.value[activeType.value] : '')
const sourceLabel = computed(() => activeSource.value === 'fallback' ? '本地提示' : 'AI 生成')

watch(() => props.questionId, reset)

function reset() {
  abortController?.abort()
  abortController = null
  activeType.value = null
  loadingType.value = null
  error.value = ''
  results.value = { explanation: '', variant: '' }
  sources.value = { explanation: '', variant: '' }
}

async function generate(type: AssistantType) {
  activeType.value = type
  error.value = ''

  if (results.value[type]) return

  loadingType.value = type
  results.value[type] = ''
  const controller = new AbortController()
  abortController = controller
  try {
    await streamQuestionAi(type, props.questionId, {
      onContent: (content) => {
        results.value[type] += content
      },
      onDone: (source) => {
        sources.value[type] = source
      },
    }, controller.signal)
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      error.value = e?.message || 'AI 服务调用失败，请稍后重试'
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
.ai-assistant {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #d9e5f2;
  border-radius: 10px;
  background: #f8fbff;
  text-align: left;
}

.ai-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ai-title {
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 700;
}

.ai-subtitle {
  margin-top: 4px;
  color: #7a8797;
  font-size: 12px;
  line-height: 1.5;
}

.ai-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.ai-result {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #dfe8f2;
}

.result-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #34495e;
  font-size: 14px;
  font-weight: 700;
}

.stream-placeholder {
  color: #7a8797;
  font-size: 13px;
}

@media (max-width: 720px) {
  .ai-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .ai-actions {
    width: 100%;
  }

  .ai-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }
}
</style>
