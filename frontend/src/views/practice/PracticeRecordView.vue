<template>
  <div class="practice-record-container">
    <div class="page-header">
      <h2>刷题记录</h2>
    </div>

    <!-- 筛选条件 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filter">
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
          <el-button type="primary" @click="loadRecords">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 记录列表 -->
    <el-table :data="records" v-loading="loading" stripe style="width: 100%" empty-text="暂无刷题记录">
      <el-table-column prop="questionContent" label="题干" min-width="280" show-overflow-tooltip>
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
      <el-table-column prop="courseName" label="课程" width="140" show-overflow-tooltip />
      <el-table-column prop="difficulty" label="难度" width="120" align="center">
        <template #default="{ row }">
          <el-rate v-model="row.difficulty" disabled :max="5" />
        </template>
      </el-table-column>
      <el-table-column prop="userAnswer" label="我的答案" width="100" align="center">
        <template #default="{ row }">
          <span :class="row.isCorrect === 1 ? 'answer-correct' : 'answer-wrong'">
            {{ row.userAnswer }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="isCorrect" label="结果" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isCorrect === 1 ? 'success' : 'danger'" size="small">
            {{ row.isCorrect === 1 ? '✓ 正确' : '✗ 错误' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="answerTime" label="耗时" width="80" align="center">
        <template #default="{ row }">
          {{ row.answerTime ? row.answerTime + 's' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="答题时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRecords"
        @size-change="loadRecords"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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
  } catch (e) {
    ElMessage.error('获取记录失败')
  } finally {
    loading.value = false
  }
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

.page-header h2 {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}

.filter-card {
  margin-bottom: 16px;
}

.question-text {
  font-size: 14px;
  color: #303133;
}

.answer-correct {
  color: #67c23a;
  font-weight: 700;
}

.answer-wrong {
  color: #f56c6c;
  font-weight: 700;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>