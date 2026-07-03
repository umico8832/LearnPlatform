<template>
  <div class="review-container page-container">
    <section class="page-hero">
      <div>
        <span class="section-kicker">AI STUDY COACH</span>
        <h2>AI 复习建议</h2>
        <p>结合错题、练习和课程范围生成一份可执行的复习清单，适合每天开练前快速定方向。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :icon="MagicStick" :loading="loading" @click="generate">
          {{ result ? '重新生成' : '生成建议' }}
        </el-button>
        <el-button v-if="loading" :icon="CircleClose" @click="stopGenerate">停止</el-button>
      </div>
    </section>

    <section class="coach-layout">
      <el-card shadow="never" class="control-card">
        <div class="card-heading">
          <span class="card-kicker">生成范围</span>
          <strong>选择本次复盘焦点</strong>
          <p>不选择课程时，AI 会综合你近期全部错题和练习情况。</p>
        </div>

        <el-form label-position="top" class="coach-form">
          <el-form-item label="针对课程">
            <el-select v-model="courseId" placeholder="全部课程" clearable filterable>
              <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="scope-panel">
          <span>当前范围</span>
          <strong>{{ selectedCourseName }}</strong>
          <small>{{ scopeDescription }}</small>
        </div>

        <div class="guide-list">
          <div v-for="item in guideItems" :key="item.title" class="guide-item">
            <el-icon><component :is="item.icon" /></el-icon>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="result-card">
        <template #header>
          <div class="result-header">
            <div>
              <strong>建议结果</strong>
              <span>{{ resultMeta }}</span>
            </div>
            <el-button
              v-if="result"
              text
              type="primary"
              :icon="DocumentCopy"
              @click="copyResult"
            >
              复制
            </el-button>
          </div>
        </template>

        <el-alert
          v-if="error"
          :title="error"
          type="error"
          show-icon
          :closable="false"
          class="result-alert"
        />

        <div v-if="loading && !result" class="stream-placeholder">
          <el-icon class="is-loading" :size="22"><Loading /></el-icon>
          <div>
            <strong>正在连接 AI 服务</strong>
            <span>稍等片刻，建议会以流式方式逐段出现。</span>
          </div>
        </div>

        <div v-else-if="result" class="markdown-shell">
          <MarkdownRenderer :content="result" />
        </div>

        <el-empty
          v-else-if="!error"
          description="选择范围后点击生成，AI 会给出今日复习优先级、薄弱点和行动建议"
        >
          <el-button type="primary" :icon="MagicStick" :loading="loading" @click="generate">
            生成第一份建议
          </el-button>
        </el-empty>
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Aim,
  CircleClose,
  DataAnalysis,
  DocumentCopy,
  Loading,
  MagicStick,
  Timer,
} from '@element-plus/icons-vue'
import { streamReviewSuggestion } from '@/api/ai'
import { getCoursePage } from '@/api/course'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const courseId = ref<number | undefined>(undefined)
const courseList = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const result = ref('')
const error = ref('')
const generatedAt = ref('')
const controller = ref<AbortController | null>(null)

const selectedCourseName = computed(() => {
  if (!courseId.value) return '全部课程'
  return courseList.value.find((course) => course.id === courseId.value)?.name || '已选课程'
})

const scopeDescription = computed(() => (
  courseId.value
    ? '聚焦该课程下的错题、正确率和薄弱知识点。'
    : '综合所有课程生成跨学科复盘建议。'
))

const resultMeta = computed(() => {
  if (loading.value) return result.value ? '生成中，内容持续更新' : '等待 AI 返回'
  if (!result.value) return '尚未生成'
  const length = result.value.replace(/\s/g, '').length
  return `${generatedAt.value || '刚刚生成'} · 约 ${length} 字`
})

const guideItems = [
  { icon: Aim, title: '优先级', text: '先复盘近期错得多、正确率波动大的内容。' },
  { icon: DataAnalysis, title: '学习证据', text: '基于已有练习记录生成，不需要额外录入。' },
  { icon: Timer, title: '行动清单', text: '更适合转化为今天的复习安排。' },
]

onMounted(async () => {
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    if ((res as any).code === 0 && (res as any).data) {
      courseList.value = ((res as any).data.records || []).map((c: any) => ({ id: c.id, name: c.name }))
    }
  } catch {}
})

const generate = async () => {
  controller.value?.abort()
  controller.value = new AbortController()
  loading.value = true
  result.value = ''
  error.value = ''
  generatedAt.value = ''
  try {
    await streamReviewSuggestion(courseId.value, {
      onContent: (content) => {
        result.value += content
      },
    }, controller.value.signal)
    generatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      error.value = e?.response?.data?.message || e?.message || 'AI 服务调用失败，请检查配置'
    }
  } finally {
    loading.value = false
    controller.value = null
  }
}

const stopGenerate = () => {
  controller.value?.abort()
  loading.value = false
}

const copyResult = async () => {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value)
    ElMessage.success('复习建议已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

onBeforeUnmount(() => {
  controller.value?.abort()
})
</script>

<style scoped>
.review-container {
  padding: 24px;
}

.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background:
    linear-gradient(135deg, rgba(23, 105, 170, 0.09), rgba(47, 133, 90, 0.09)),
    var(--lp-surface);
}

.section-kicker,
.card-kicker {
  display: inline-block;
  color: var(--lp-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.section-kicker {
  margin-bottom: 8px;
}

.page-hero h2 {
  margin: 0;
  color: var(--lp-text);
  font-size: 24px;
  font-weight: 850;
}

.page-hero p {
  margin: 8px 0 0;
  max-width: 650px;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.coach-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.card-heading strong {
  display: block;
  margin-top: 8px;
  color: var(--lp-text);
  font-size: 18px;
  font-weight: 850;
}

.card-heading p {
  margin: 8px 0 18px;
  color: var(--lp-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.coach-form :deep(.el-select) {
  width: 100%;
}

.scope-panel {
  margin: 4px 0 16px;
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface-soft);
}

.scope-panel span,
.scope-panel small {
  display: block;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.scope-panel strong {
  display: block;
  margin: 7px 0 5px;
  color: var(--lp-primary);
  font-size: 24px;
  font-weight: 850;
  line-height: 1.2;
}

.guide-list {
  display: grid;
  gap: 10px;
}

.guide-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: #fff;
}

.guide-item .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--lp-accent);
  font-size: 18px;
}

.guide-item strong,
.guide-item span {
  display: block;
}

.guide-item strong {
  color: var(--lp-text);
  font-size: 14px;
}

.guide-item span {
  margin-top: 4px;
  color: var(--lp-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.result-card {
  min-height: 420px;
}

.result-card :deep(.el-card__body) {
  min-height: 338px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-header strong,
.result-header span {
  display: block;
}

.result-header strong {
  color: var(--lp-text);
  font-size: 15px;
}

.result-header span {
  margin-top: 4px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.result-alert {
  margin-bottom: 16px;
}

.stream-placeholder {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 240px;
  color: var(--lp-text-muted);
  font-size: 14px;
}

.stream-placeholder .el-icon {
  color: var(--lp-primary);
}

.stream-placeholder strong,
.stream-placeholder span {
  display: block;
}

.stream-placeholder strong {
  color: var(--lp-text);
  font-size: 15px;
}

.stream-placeholder span {
  margin-top: 5px;
  line-height: 1.6;
}

.markdown-shell {
  padding: 2px 4px;
}

@media (max-width: 900px) {
  .coach-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .review-container {
    padding: 16px;
  }

  .page-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 18px;
  }

  .page-hero h2 {
    font-size: 21px;
  }

  .hero-actions,
  .hero-actions .el-button {
    width: 100%;
  }
}
</style>
