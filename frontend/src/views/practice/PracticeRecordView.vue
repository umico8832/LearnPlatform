<template>
  <div class="practice-record-container page-container">
    <section class="page-hero">
      <div>
        <span class="section-kicker">练习复盘</span>
        <h2>刷题记录</h2>
        <p>按题型和结果回看最近练习，快速定位正确率波动和需要回炉的题目。</p>
      </div>
      <el-button type="primary" :icon="EditPen" @click="$router.push('/practice')">
        继续刷题
      </el-button>
    </section>

    <section class="record-summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never" class="record-summary-card">
        <span>{{ item.label }}</span>
        <strong :class="item.tone">{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </el-card>
    </section>

    <el-card class="filter-card" shadow="never">
      <div class="filter-title">
        <strong>筛选记录</strong>
        <span>筛选仅影响当前记录列表，不会改变练习统计。</span>
      </div>
      <el-form :inline="true" :model="filter" class="filter-form">
        <el-form-item label="题型">
          <el-select v-model="filter.questionType" placeholder="全部" clearable>
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filter.isCorrect" placeholder="全部" clearable>
            <el-option label="答对" :value="1" />
            <el-option label="答错" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="record-table-card" shadow="never">
      <div class="record-toolbar">
        <div>
          <strong>记录明细</strong>
          <span>共 {{ total }} 条</span>
        </div>
      </div>

      <el-table :data="records" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="questionContent" label="题干" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="question-text">{{ row.questionContent }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="questionType" label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.questionType)" size="small">
              {{ getTypeLabel(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" width="150" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="130" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.difficulty" disabled :max="5" />
          </template>
        </el-table-column>
        <el-table-column prop="userAnswer" label="我的答案" width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.isCorrect === 1 ? 'answer-correct' : 'answer-wrong'">
              {{ row.userAnswer || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="isCorrect" label="结果" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isCorrect === 1 ? 'success' : 'danger'" size="small">
              {{ row.isCorrect === 1 ? '正确' : '错误' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="answerTime" label="耗时" width="86" align="center">
          <template #default="{ row }">
            {{ row.answerTime ? row.answerTime + 's' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="答题时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无刷题记录">
            <el-button type="primary" :icon="EditPen" @click="$router.push('/practice')">
              去刷第一题
            </el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadRecords"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, RefreshLeft, Search } from '@element-plus/icons-vue'
import { getPracticeRecords } from '@/api/practice'
import type { PracticeRecordVO } from '@/api/practice'

const loading = ref(false)
const records = ref<PracticeRecordVO[]>([])
const total = ref(0)

const filter = reactive({
  questionType: '',
  isCorrect: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

const pageCorrectCount = computed(() => records.value.filter((item) => item.isCorrect === 1).length)
const pageWrongCount = computed(() => records.value.filter((item) => item.isCorrect !== 1).length)
const pageCorrectRate = computed(() => {
  if (records.value.length === 0) return '0%'
  return `${Math.round((pageCorrectCount.value / records.value.length) * 100)}%`
})
const averageAnswerTime = computed(() => {
  const times = records.value.map((item) => item.answerTime || 0).filter((time) => time > 0)
  if (times.length === 0) return '-'
  return `${Math.round(times.reduce((sum, time) => sum + time, 0) / times.length)}s`
})
const summaryCards = computed(() => [
  { label: '当前页记录', value: records.value.length, note: `全部匹配 ${total.value} 条`, tone: 'tone-primary' },
  { label: '当前页正确率', value: pageCorrectRate.value, note: `答对 ${pageCorrectCount.value} 题`, tone: 'tone-success' },
  { label: '当前页错题', value: pageWrongCount.value, note: '可前往错题本复盘', tone: 'tone-danger' },
  { label: '平均耗时', value: averageAnswerTime.value, note: '仅统计有耗时记录', tone: 'tone-warning' },
])

onMounted(() => {
  loadRecords()
})

const loadRecords = async () => {
  loading.value = true
  try {
    const params: any = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (filter.questionType) params.questionType = filter.questionType
    if (filter.isCorrect !== undefined) params.isCorrect = filter.isCorrect

    const res = await getPracticeRecords(params)
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取记录失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadRecords()
}

const handleReset = () => {
  filter.questionType = ''
  filter.isCorrect = undefined
  pagination.pageNum = 1
  loadRecords()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadRecords()
}

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答'
  }
  return map[type] || type
}

const getTypeTag = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '',
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger'
  }
  return (map[type] || '') as any
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.practice-record-container {
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
    linear-gradient(135deg, rgba(23, 105, 170, 0.08), rgba(216, 168, 63, 0.1)),
    var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--lp-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.page-hero h2 {
  margin: 0;
  color: var(--lp-text);
  font-size: 24px;
  font-weight: 850;
}

.page-hero p {
  margin: 8px 0 0;
  max-width: 620px;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.record-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.record-summary-card :deep(.el-card__body) {
  min-height: 108px;
}

.record-summary-card span,
.record-summary-card small {
  display: block;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.record-summary-card strong {
  display: block;
  margin: 8px 0 6px;
  color: var(--lp-text);
  font-size: 28px;
  font-weight: 850;
  line-height: 1.1;
}

.record-summary-card .tone-primary {
  color: var(--lp-primary);
}

.record-summary-card .tone-success {
  color: var(--lp-success);
}

.record-summary-card .tone-danger {
  color: var(--lp-danger);
}

.record-summary-card .tone-warning {
  color: var(--lp-warning);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.filter-title strong,
.record-toolbar strong {
  color: var(--lp-text);
  font-size: 15px;
}

.filter-title span,
.record-toolbar span {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.record-table-card :deep(.el-card__body) {
  padding: 0 !important;
}

.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--lp-border);
  background: var(--lp-surface-soft);
}

.record-toolbar div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.question-text {
  font-size: 14px;
  color: var(--lp-text);
  line-height: 1.6;
}

.answer-correct {
  color: var(--lp-success);
  font-weight: 700;
}

.answer-wrong {
  color: var(--lp-danger);
  font-weight: 700;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
  border-top: 1px solid var(--lp-border);
}

@media (max-width: 900px) {
  .record-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .practice-record-container {
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

  .page-hero .el-button,
  .filter-form,
  .filter-form :deep(.el-form-item),
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-button) {
    width: 100%;
  }

  .filter-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-wrapper {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .record-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
