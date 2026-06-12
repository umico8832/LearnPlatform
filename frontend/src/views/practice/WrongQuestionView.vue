<template>
  <div class="wrong-question-container">
    <div class="page-header">
      <h2>错题本</h2>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="stats">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">总错题数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-unmastered">
            <div class="stat-value">{{ stats.unmastered }}</div>
            <div class="stat-label">未掌握</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-partial">
            <div class="stat-value">{{ stats.partial }}</div>
            <div class="stat-label">部分掌握</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-mastered">
            <div class="stat-value">{{ stats.mastered }}</div>
            <div class="stat-label">已掌握</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 筛选条件 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filter">
        <el-form-item label="掌握程度">
          <el-select v-model="filter.masteryLevel" placeholder="全部" clearable>
            <el-option label="未掌握" :value="0" />
            <el-option label="部分掌握" :value="1" />
            <el-option label="已掌握" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 错题列表 -->
    <div class="wrong-list" v-loading="loading">
      <el-empty v-if="!loading && records.length === 0" description="暂无错题" />

      <el-card v-for="item in records" :key="item.id" class="wrong-card" shadow="hover">
        <div class="wrong-card-header">
          <div class="wrong-meta">
            <el-tag :type="getTypeTag(item.questionType)" size="small">
              {{ getTypeLabel(item.questionType) }}
            </el-tag>
            <el-tag v-if="item.courseName" type="info" size="small">{{ item.courseName }}</el-tag>
            <el-rate v-model="item.difficulty" disabled :max="5" style="margin-left: 8px" />
            <span class="wrong-count">答错 {{ item.wrongCount }} 次</span>
          </div>
          <div class="wrong-actions">
            <el-tag :type="getMasteryTag(item.masteryLevel)" size="small" effect="dark">
              {{ getMasteryLabel(item.masteryLevel) }}
            </el-tag>
          </div>
        </div>

        <div class="wrong-content">{{ item.questionContent }}</div>

        <div v-if="item.lastWrongAnswer" class="wrong-answer">
          <span class="label">上次错误答案：</span>
          <span class="answer-wrong">{{ item.lastWrongAnswer }}</span>
        </div>

        <div class="wrong-card-footer">
          <div class="mastery-controls">
            <span class="label">掌握程度：</span>
            <el-radio-group v-model="item.masteryLevel" size="small" @change="(val: any) => handleMasteryChange(item.id, val as number)">
              <el-radio-button :value="0">未掌握</el-radio-button>
              <el-radio-button :value="1">部分掌握</el-radio-button>
              <el-radio-button :value="2">已掌握</el-radio-button>
            </el-radio-group>
          </div>
          <div class="footer-right">
            <span class="time">{{ formatTime(item.updateTime) }}</span>
            <el-popconfirm title="确定从错题本移出？" @confirm="handleRemove(item.id)">
              <template #reference>
                <el-button type="danger" text size="small">移出错题本</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </el-card>
    </div>

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
import { getWrongQuestions, getWrongQuestionStats, updateMasteryLevel, removeWrongQuestion } from '@/api/wrongQuestion'
import type { WrongQuestionVO, WrongQuestionStatsVO } from '@/api/wrongQuestion'

const loading = ref(false)
const records = ref<WrongQuestionVO[]>([])
const total = ref(0)
const stats = ref<WrongQuestionStatsVO | null>(null)

const filter = reactive({
  masteryLevel: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

onMounted(() => {
  loadRecords()
  loadStats()
})

const loadRecords = async () => {
  loading.value = true
  try {
    const params: any = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (filter.masteryLevel !== undefined) params.masteryLevel = filter.masteryLevel

    const res = await getWrongQuestions(params)
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('获取错题列表失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getWrongQuestionStats()
    if (res.code === 0) {
      stats.value = res.data
    }
  } catch (e) {
    // ignore
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadRecords()
}

const handleMasteryChange = async (id: number, masteryLevel: number) => {
  try {
    const res = await updateMasteryLevel(id, masteryLevel)
    if (res.code === 0) {
      ElMessage.success('掌握程度已更新')
      loadStats()
    }
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

const handleRemove = async (id: number) => {
  try {
    const res = await removeWrongQuestion(id)
    if (res.code === 0) {
      ElMessage.success('已移出错题本')
      loadRecords()
      loadStats()
    }
  } catch (e) {
    ElMessage.error('移出失败')
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

const getMasteryLabel = (level: number) => {
  const map: Record<number, string> = { 0: '未掌握', 1: '部分掌握', 2: '已掌握' }
  return map[level] || '未知'
}

const getMasteryTag = (level: number) => {
  const map: Record<number, string> = { 0: 'danger', 1: 'warning', 2: 'success' }
  return (map[level] || 'info') as any
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.wrong-question-container {
  padding: 24px;
}

.page-header h2 {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  padding: 16px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.stat-unmastered .stat-value {
  color: #f56c6c;
}

.stat-partial .stat-value {
  color: #e6a23c;
}

.stat-mastered .stat-value {
  color: #67c23a;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.filter-card {
  margin-bottom: 16px;
}

.wrong-card {
  margin-bottom: 12px;
}

.wrong-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.wrong-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wrong-count {
  font-size: 13px;
  color: #f56c6c;
  font-weight: 600;
}

.wrong-content {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
  margin-bottom: 12px;
  white-space: pre-wrap;
}

.wrong-answer {
  font-size: 13px;
  margin-bottom: 12px;
}

.wrong-answer .label {
  color: #909399;
}

.answer-wrong {
  color: #f56c6c;
  font-weight: 600;
}

.wrong-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.mastery-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mastery-controls .label {
  font-size: 13px;
  color: #909399;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>