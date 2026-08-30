<template>
  <div v-loading="loading" class="record-panel">
    <LpEmptyState v-if="!loading && records.length === 0" title="暂无考试记录" />

    <el-table v-else :data="records" stripe class="record-table">
      <el-table-column prop="examTitle" label="试卷名称" min-width="200" />
      <el-table-column label="得分" width="120">
        <template #default="{ row }">
          <span :class="['score-text', getScoreClass(row as ExamRecordVO)]">
            {{ formatScore(row as ExamRecordVO) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="recordStatusTag((row as ExamRecordVO).status)" size="small">
            {{ recordStatusLabel((row as ExamRecordVO).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="180">
        <template #default="{ row }">{{ formatTime((row as ExamRecordVO).startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="[1, 3].includes((row as ExamRecordVO).status)"
            type="primary"
            link
            size="small"
            :icon="View"
            @click="viewResult((row as ExamRecordVO).id)"
          >
            查看结果
          </el-button>
          <el-button
            v-else-if="(row as ExamRecordVO).status === 0"
            type="warning"
            link
            size="small"
            :icon="EditPen"
            @click="continueExam(row as ExamRecordVO)"
          >
            继续考试
          </el-button>
          <span v-else class="record-finished-hint">不可继续</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="records.length > 0" class="record-mobile-list" aria-label="考试记录">
      <article v-for="record in records" :key="record.id" class="record-mobile-card">
        <div class="record-mobile-header">
          <h3>{{ record.examTitle }}</h3>
          <el-tag :type="recordStatusTag(record.status)" size="small">
            {{ recordStatusLabel(record.status) }}
          </el-tag>
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
          <el-button v-if="[1, 3].includes(record.status)" type="primary" :icon="View" @click="viewResult(record.id)">
            查看结果
          </el-button>
          <el-button v-else-if="record.status === 0" type="warning" :icon="EditPen" @click="continueExam(record)">
            继续考试
          </el-button>
          <span v-else class="record-finished-hint">考试已超时，不可继续</span>
        </div>
      </article>
    </div>
  </div>

  <div v-if="total > 0" class="pagination-wrapper">
    <el-pagination
      v-model:current-page="pageNum"
      :total="total"
      :page-size="10"
      layout="total, prev, pager, next"
      @current-change="loadRecords"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditPen, View } from '@element-plus/icons-vue'
import { getMyExamRecords } from '@/api/exam'
import type { ExamRecordVO, ExamStatus } from '@/api/exam'
import { formatTime } from '@/utils/format'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'

const emit = defineEmits<{
  totalChange: [total: number]
}>()

const router = useRouter()
const loading = ref(false)
const records = ref<ExamRecordVO[]>([])
const total = ref(0)
const pageNum = ref(1)

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  try {
    const res = await getMyExamRecords({ pageNum: pageNum.value, pageSize: 10 })
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
      emit('totalChange', total.value)
    }
  } catch {
    ElMessage.error('获取考试记录失败')
  } finally {
    loading.value = false
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
  if (record.status === 3) return `${record.score ?? 0} / ${record.totalScore}（暂定）`
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
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  if (status === 3) return 'warning'
  return 'info'
}

defineExpose({ reload: loadRecords })
</script>

<style scoped>
.record-panel {
  min-height: 180px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lp-space-4);
}

.score-text {
  font-weight: var(--lp-weight-semibold);
}

.score-high {
  color: var(--lp-success);
}

.score-mid {
  color: var(--lp-warning);
}

.score-low {
  color: var(--lp-danger);
}

.record-finished-hint {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.record-mobile-list {
  display: none;
}

@media (max-width: 860px) {
  .record-panel > .el-table {
    display: none;
  }

  .record-mobile-list {
    display: grid;
    gap: var(--lp-space-3);
  }

  .record-mobile-card {
    padding: var(--lp-space-4);
    background: var(--lp-surface);
    border: var(--lp-border-hairline);
    border-radius: var(--lp-radius-md);
  }

  .record-mobile-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--lp-space-3);
  }

  .record-mobile-header h3 {
    min-width: 0;
    margin: 0;
    color: var(--lp-text);
    font-size: var(--lp-text-lg);
    line-height: var(--lp-leading-snug);
  }

  .record-mobile-meta {
    display: grid;
    grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
    gap: var(--lp-space-3);
    margin: var(--lp-space-4) 0;
  }

  .record-mobile-meta div {
    min-width: 0;
  }

  .record-mobile-meta dt {
    margin-bottom: var(--lp-space-1);
    color: var(--lp-text-secondary);
    font-size: var(--lp-text-xs);
  }

  .record-mobile-meta dd {
    margin: 0;
    overflow-wrap: anywhere;
    color: var(--lp-text);
    font-size: var(--lp-text-sm);
    line-height: var(--lp-leading-snug);
  }

  .record-mobile-action {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    min-height: 44px;
  }

  .record-mobile-action .el-button {
    min-height: 44px;
  }
}
</style>
