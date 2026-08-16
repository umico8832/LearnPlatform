<template>
  <div class="practice-record-container page-container">
    <LpPageHeader
      kicker="练习复盘"
      title="刷题记录"
      description="按题型和结果回看最近练习，快速定位正确率波动和需要回炉的题目。"
    >
      <template #actions>
        <el-button type="primary" :icon="EditPen" @click="$router.push('/practice')">继续刷题</el-button>
      </template>
    </LpPageHeader>

    <section class="record-summary-grid">
      <LpStat
        v-for="item in summaryCards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :note="item.note"
        :tone="item.tone"
      />
    </section>

    <section class="filter-panel">
      <LpSectionHeading kicker="筛选" title="筛选记录" description="筛选仅影响当前记录列表，不会改变练习统计。" />
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
    </section>

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
            <el-button type="primary" :icon="EditPen" @click="$router.push('/practice')">去刷第一题</el-button>
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
import { SemanticTagType } from '@/utils/errors'
import { ElMessage } from 'element-plus'
import { EditPen, RefreshLeft, Search } from '@element-plus/icons-vue'
import { getPracticeRecords } from '@/api/practice'
import type { PracticeRecordVO } from '@/api/practice'

const loading = ref(false)
const records = ref<PracticeRecordVO[]>([])
const total = ref(0)

const filter = reactive({
  questionType: '',
  isCorrect: undefined as number | undefined,
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
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
  {
    label: '当前页记录',
    value: records.value.length,
    note: `全部匹配 ${total.value} 条`,
    tone: 'emphasis' as const,
  },
  {
    label: '当前页正确率',
    value: pageCorrectRate.value,
    note: `答对 ${pageCorrectCount.value} 题`,
    tone: 'default' as const,
  },
  { label: '当前页错题', value: pageWrongCount.value, note: '可前往错题本复盘', tone: 'danger' as const },
  { label: '平均耗时', value: averageAnswerTime.value, note: '仅统计有耗时记录', tone: 'warning' as const },
])

onMounted(() => {
  loadRecords()
})

const loadRecords = async () => {
  loading.value = true
  try {
    const params: Parameters<typeof getPracticeRecords>[0] = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
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
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

const getTypeTag = (type: string) => {
  const map: Record<string, SemanticTagType> = {
    SINGLE_CHOICE: undefined,
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type]
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.practice-record-container {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-6);
}

.record-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-3);
}

.filter-panel {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-1) var(--lp-space-3);
}

.filter-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 0;
}

.record-table-card {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.record-table-card :deep(.el-card__body) {
  padding: 0 !important;
}

.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  padding: var(--lp-space-4) var(--lp-space-5);
  border-bottom: var(--lp-border-hairline);
  background: var(--lp-surface-soft);
}

.record-toolbar div {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}

.record-toolbar strong {
  color: var(--lp-text);
  font-size: var(--lp-text-md);
}

.record-toolbar span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.question-text {
  font-size: var(--lp-text-base);
  color: var(--lp-text);
  line-height: var(--lp-leading-body);
}

.answer-correct {
  color: var(--lp-success);
  font-weight: var(--lp-weight-bold);
}

.answer-wrong {
  color: var(--lp-danger);
  font-weight: var(--lp-weight-bold);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: var(--lp-space-3) var(--lp-space-4) var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}

@media (max-width: 900px) {
  .record-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .filter-panel {
    padding: var(--lp-space-4);
  }

  .filter-form,
  .filter-form :deep(.el-form-item),
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-button) {
    width: 100%;
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
