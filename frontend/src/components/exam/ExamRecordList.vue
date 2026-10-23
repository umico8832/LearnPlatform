<template>
  <div class="record-panel">
    <div v-if="loading" class="state" role="status" aria-busy="true">正在读取考试记录</div>
    <div v-else-if="loadError" class="state" role="alert">
      <h2>暂时无法读取考试记录</h2>
      <p>{{ loadError }}</p>
      <LtButton @click="loadRecords">重试</LtButton>
    </div>
    <div v-else-if="!records.length" class="state" role="status">暂无考试记录</div>
    <div v-else class="record-mobile-list" aria-label="考试记录">
      <article v-for="record in records" :key="record.id" class="record-mobile-card">
        <div class="record-mobile-header">
          <h3>{{ record.examTitle }}</h3>
          <span class="tag" :data-tone="recordStatusTag(record.status)">{{ recordStatusLabel(record.status) }}</span>
        </div>
        <dl class="record-mobile-meta">
          <div>
            <dt>得分</dt>
            <dd :class="['score-text', getScoreClass(record)]">{{ formatScore(record) }}</dd>
          </div>
          <div>
            <dt>开始时间</dt>
            <dd>{{ formatTime(record.startTime) }}</dd>
          </div>
        </dl>
        <div class="record-mobile-action">
          <LtButton v-if="[1, 3].includes(record.status)" @click="viewResult(record.id)">查看结果</LtButton
          ><LtButton v-else-if="record.status === 0" @click="continueExam(record)">继续考试</LtButton
          ><span v-else class="muted">考试已超时，不可继续</span>
        </div>
      </article>
    </div>
    <nav v-if="!loading && !loadError && total > 0" class="pagination-wrapper" aria-label="考试记录分页">
      <span>共 {{ total }} 条 · 第 {{ pageNum }} / {{ Math.ceil(total / 10) }} 页</span
      ><LtButton :disabled="pageNum <= 1" @click="changePage(-1)">上一页</LtButton
      ><LtButton :disabled="pageNum * 10 >= total" @click="changePage(1)">下一页</LtButton>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyExamRecords } from '@/api/exam'
import type { ExamRecordVO, ExamStatus } from '@/api/exam'
import { formatTime } from '@/utils/format'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

import { LtButton } from '@/components/living'

const emit = defineEmits<{
  totalChange: [total: number | null]
}>()

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const records = ref<ExamRecordVO[]>([])
const total = ref(0)
const pageNum = ref(1)
let alive = true
let requestGeneration = 0

function current(generation: number, session: number) {
  return alive && generation === requestGeneration && session === getAuthSessionVersion()
}

onMounted(() => void loadRecords())

const unsubscribeAuth = onAuthSessionChange(() => {
  requestGeneration++
  records.value = []
  total.value = 0
  loadError.value = ''
  emit('totalChange', null)
  if (isAuthenticated()) void loadRecords()
})

onUnmounted(() => {
  alive = false
  requestGeneration++
  unsubscribeAuth()
})

async function loadRecords() {
  const generation = ++requestGeneration
  const session = getAuthSessionVersion()
  loading.value = true
  loadError.value = ''
  try {
    const res = await getMyExamRecords({ pageNum: pageNum.value, pageSize: 10 }, { errorDisplay: 'inline' })
    if (!current(generation, session)) return
    if (res.code !== 0 || !res.data) {
      loadError.value = res.message || '考试记录暂时无法读取。'
      emit('totalChange', null)
      return
    }
    records.value = res.data.records || []
    total.value = res.data.total || 0
    emit('totalChange', total.value)
  } catch {
    if (current(generation, session)) {
      loadError.value = '考试记录暂时无法读取。'
      emit('totalChange', null)
    }
  } finally {
    if (current(generation, session)) loading.value = false
  }
}

const viewResult = (recordId: number) => {
  router.push({ name: 'ExamResult', params: { recordId: String(recordId) } })
}

const continueExam = (record: ExamRecordVO) => {
  router.push({ name: 'ExamTake', params: { recordId: String(record.id) } })
}

const getScoreClass = (record: ExamRecordVO) => {
  if (record.status !== 1 || record.score == null || !record.totalScore || record.totalScore === 0) return ''
  const ratio = record.score / record.totalScore
  if (ratio >= 0.8) return 'score-high'
  if (ratio >= 0.6) return 'score-mid'
  return 'score-low'
}

const formatScore = (record: ExamRecordVO) => {
  if (record.status === 3)
    return record.score == null
      ? `待评分 / ${record.totalScore}（暂定）`
      : `${record.score} / ${record.totalScore}（暂定）`
  if (record.status !== 1 || record.score == null) return `— / ${record.totalScore}`
  return `${record.score} / ${record.totalScore}`
}

const recordStatusLabel = (status: ExamStatus) => {
  if (status === 0) return '进行中'
  if (status === 1) return '已完成'
  if (status === 2) return '已超时'
  if (status === 3) return '待人工批阅'
  return '未知状态'
}

const recordStatusTag = (status: ExamStatus): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 0) return 'warning'
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'warning'
  return 'info'
}

defineExpose({ reload: loadRecords })
function changePage(delta: number) {
  pageNum.value += delta
  void loadRecords()
}
</script>
