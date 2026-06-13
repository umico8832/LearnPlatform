<template>
  <div class="exam-result-container">
    <div v-loading="loading">
      <template v-if="result">
        <!-- 成绩卡片 -->
        <div class="result-header">
          <el-card shadow="hover" class="score-card">
            <div class="score-main">
              <div class="score-circle">
                <span class="score-number">{{ result.score }}</span>
                <span class="score-total">/ {{ result.totalScore }}</span>
              </div>
              <div class="score-rate">
                <span class="rate-value">{{ scoreRate }}%</span>
                <span class="rate-label">正确率</span>
              </div>
            </div>
            <div class="score-meta">
              <div class="meta-item">
                <span class="meta-label">试卷</span>
                <span class="meta-value">{{ result.examTitle }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">用时</span>
                <span class="meta-value">{{ timeUsed }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">题数</span>
                <span class="meta-value">{{ result.answers?.length || 0 }} 题</span>
              </div>
            </div>
            <div class="score-actions">
              <el-button @click="router.push('/exams')">返回考试列表</el-button>
            </div>
          </el-card>
        </div>

        <!-- 答题详情 -->
        <div class="answers-section">
          <h3>答题详情</h3>
          <div v-for="(answer, idx) in result.answers" :key="answer.questionId" class="answer-item">
            <el-card shadow="hover">
              <div class="answer-header">
                <span class="q-index">{{ idx + 1 }}.</span>
                <el-tag size="small">{{ getTypeLabel(answer.questionType) }}</el-tag>
                <span class="q-score-tag">满分 {{ answer.fullScore }} 分</span>
                <el-tag :type="answer.isCorrect === 1 ? 'success' : 'danger'" size="small" class="result-tag">
                  {{ answer.isCorrect === 1 ? '✓ 正确' : '✗ 错误' }}
                </el-tag>
                <span class="earned-score">得 {{ answer.score }} 分</span>
              </div>
              <div class="answer-content">{{ answer.content }}</div>
              <div class="answer-detail">
                <div class="detail-row">
                  <span class="detail-label">我的答案：</span>
                  <span :class="['detail-value', answer.isCorrect === 1 ? 'correct' : 'wrong']">
                    {{ answer.userAnswer || '未作答' }}
                  </span>
                </div>
                <div class="detail-row" v-if="answer.isCorrect !== 1">
                  <span class="detail-label">正确答案：</span>
                  <span class="detail-value correct">{{ answer.correctAnswer }}</span>
                </div>
                <div class="detail-row" v-if="answer.analysis">
                  <span class="detail-label">解析：</span>
                  <span class="detail-value analysis">{{ answer.analysis }}</span>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="考试结果不存在">
        <el-button type="primary" @click="router.push('/exams')">返回考试列表</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamResult } from '@/api/exam'
import type { ExamRecordVO } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const result = ref<ExamRecordVO | null>(null)

const scoreRate = computed(() => {
  if (!result.value || !result.value.totalScore || result.value.totalScore === 0) return 0
  return Math.round((result.value.score / result.value.totalScore) * 100)
})

const timeUsed = computed(() => {
  if (!result.value || !result.value.startTime || !result.value.endTime) return '-'
  const start = new Date(result.value.startTime).getTime()
  const end = new Date(result.value.endTime).getTime()
  const diff = Math.floor((end - start) / 1000)
  const minutes = Math.floor(diff / 60)
  const seconds = diff % 60
  if (minutes > 0) return `${minutes} 分 ${seconds} 秒`
  return `${seconds} 秒`
})

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断',
    FILL_BLANK: '填空', SHORT_ANSWER: '简答'
  }
  return map[type] || type
}

onMounted(async () => {
  const recordId = Number(route.params.recordId)
  // 先尝试从 sessionStorage 获取
  const stored = sessionStorage.getItem('exam_result_' + recordId)
  if (stored) {
    try {
      result.value = JSON.parse(stored)
      loading.value = false
      return
    } catch {}
  }
  // 从 API 获取
  try {
    const res = await getExamResult(recordId)
    if (res.code === 0 && res.data) {
      result.value = res.data
    } else {
      ElMessage.error('获取考试结果失败')
    }
  } catch (e) {
    ElMessage.error('获取考试结果失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.exam-result-container { padding: 24px; max-width: 900px; margin: 0 auto; }

.result-header { margin-bottom: 24px; }

.score-card { text-align: center; }

.score-main { display: flex; align-items: center; justify-content: center; gap: 40px; margin-bottom: 24px; }

.score-circle { display: flex; flex-direction: column; align-items: center; }
.score-number { font-size: 48px; font-weight: 700; color: #409eff; line-height: 1; }
.score-total { font-size: 16px; color: #909399; margin-top: 4px; }

.score-rate { display: flex; flex-direction: column; align-items: center; }
.rate-value { font-size: 36px; font-weight: 700; color: #67c23a; line-height: 1; }
.rate-label { font-size: 14px; color: #909399; margin-top: 4px; }

.score-meta { display: flex; justify-content: center; gap: 40px; margin-bottom: 20px; padding: 16px 0; border-top: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; }
.meta-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.meta-label { font-size: 12px; color: #909399; }
.meta-value { font-size: 14px; color: #303133; font-weight: 500; }

.score-actions { margin-top: 16px; }

.answers-section { margin-top: 24px; }
.answers-section h3 { margin: 0 0 16px; font-size: 18px; color: #303133; }

.answer-item { margin-bottom: 12px; }
.answer-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.q-index { font-weight: 700; font-size: 16px; color: #303133; }
.q-score-tag { font-size: 12px; color: #909399; }
.result-tag { margin-left: auto; }
.earned-score { font-size: 13px; color: #606266; font-weight: 500; }

.answer-content { font-size: 15px; line-height: 1.8; margin-bottom: 16px; white-space: pre-wrap; }

.answer-detail { background: #f5f7fa; padding: 16px; border-radius: 8px; }
.detail-row { margin-bottom: 8px; }
.detail-row:last-child { margin-bottom: 0; }
.detail-label { font-size: 13px; color: #909399; }
.detail-value { font-size: 14px; }
.detail-value.correct { color: #67c23a; }
.detail-value.wrong { color: #f56c6c; }
.detail-value.analysis { color: #606266; }
</style>