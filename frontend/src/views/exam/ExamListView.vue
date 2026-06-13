<template>
  <div class="exam-list-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="可用试卷" name="papers">
        <div v-loading="loading">
          <el-empty v-if="!loading && papers.length === 0" description="暂无可用试卷" />

          <el-card v-for="paper in papers" :key="paper.id" class="exam-card" shadow="hover">
            <div class="exam-card-header">
              <h3>{{ paper.title }}</h3>
              <el-tag type="success" size="small">已发布</el-tag>
            </div>
            <p class="exam-desc" v-if="paper.description">{{ paper.description }}</p>
            <div class="exam-meta">
              <span v-if="paper.courseName"><el-icon><Reading /></el-icon> {{ paper.courseName }}</span>
              <span><el-icon><Document /></el-icon> {{ paper.questionCount }} 题</span>
              <span><el-icon><Timer /></el-icon> {{ paper.duration }} 分钟</span>
              <span>总分：{{ paper.totalScore }} 分</span>
            </div>
            <div class="exam-actions">
              <el-button type="primary" @click="handleStartExam(paper.id)" :loading="startingId === paper.id">
                开始考试
              </el-button>
            </div>
          </el-card>
        </div>

        <div class="pagination-wrapper" v-if="total > 0">
          <el-pagination
            v-model:current-page="pageNum"
            :total="total"
            :page-size="10"
            layout="total, prev, pager, next"
            @current-change="loadPapers"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="考试记录" name="records">
        <div v-loading="recordsLoading">
          <el-empty v-if="!recordsLoading && records.length === 0" description="暂无考试记录" />

            <el-table v-else :data="records as any" stripe>
            <el-table-column prop="examTitle" label="试卷名称" min-width="200" />
            <el-table-column label="得分" width="120">
              <template #default="{ row }">
                <span :class="['score-text', getScoreClass(row as ExamRecordVO)]">
                  {{ (row as ExamRecordVO).score }} / {{ (row as ExamRecordVO).totalScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(row as ExamRecordVO).status === 1 ? 'success' : 'warning'" size="small">
                  {{ (row as ExamRecordVO).status === 1 ? '已完成' : '进行中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="180">
              <template #default="{ row }">{{ formatTime((row as ExamRecordVO).startTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="(row as ExamRecordVO).status === 1" type="primary" link size="small" @click="viewResult((row as ExamRecordVO).id)">
                  查看结果
                </el-button>
                <el-button v-else type="warning" link size="small" @click="continueExam(row as ExamRecordVO)">
                  继续考试
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-wrapper" v-if="recordsTotal > 0">
          <el-pagination
            v-model:current-page="recordsPageNum"
            :total="recordsTotal"
            :page-size="10"
            layout="total, prev, pager, next"
            @current-change="loadRecords"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Reading, Document, Timer } from '@element-plus/icons-vue'
import { getPublishedPapers, startExam, getPaperDetail, getMyExamRecords } from '@/api/exam'
import type { ExamPaperVO, ExamRecordVO } from '@/api/exam'

const router = useRouter()
const activeTab = ref('papers')

// 试卷列表
const loading = ref(false)
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const startingId = ref<number | null>(null)

// 考试记录
const recordsLoading = ref(false)
const records = ref<ExamRecordVO[]>([])
const recordsTotal = ref(0)
const recordsPageNum = ref(1)

onMounted(() => { loadPapers() })

const loadPapers = async () => {
  loading.value = true
  try {
    const res = await getPublishedPapers({ pageNum: pageNum.value, pageSize: 10 })
    if (res.code === 0 && res.data) {
      papers.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('获取试卷列表失败')
  } finally {
    loading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await getMyExamRecords({ pageNum: recordsPageNum.value, pageSize: 10 })
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      recordsTotal.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('获取考试记录失败')
  } finally {
    recordsLoading.value = false
  }
}

const handleTabChange = (tab: string | number) => {
  if (tab === 'records') loadRecords()
}

const handleStartExam = async (paperId: number) => {
  startingId.value = paperId
  try {
    const detailRes = await getPaperDetail(paperId)
    if (detailRes.code !== 0 || !detailRes.data) {
      ElMessage.error('获取试卷详情失败')
      return
    }
    const startRes = await startExam(paperId)
    if (startRes.code === 0 && startRes.data) {
      sessionStorage.setItem('exam_session_' + startRes.data.id, JSON.stringify({
        questions: detailRes.data.questions || [],
        duration: detailRes.data.duration || 60
      }))
      router.push({ name: 'ExamTake', params: { recordId: String(startRes.data.id) } })
    } else {
      ElMessage.error(startRes.message || '开始考试失败')
    }
  } catch (e) {
    ElMessage.error('开始考试失败')
  } finally {
    startingId.value = null
  }
}

const viewResult = (recordId: number) => {
  router.push({ name: 'ExamResult', params: { recordId: String(recordId) } })
}

const continueExam = (record: ExamRecordVO) => {
  router.push({ name: 'ExamTake', params: { recordId: String(record.id) } })
}

const getScoreClass = (row: ExamRecordVO) => {
  if (!row.totalScore || row.totalScore === 0) return ''
  const ratio = row.score / row.totalScore
  if (ratio >= 0.8) return 'score-high'
  if (ratio >= 0.6) return 'score-mid'
  return 'score-low'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.exam-list-container { padding: 24px; max-width: 1000px; margin: 0 auto; }
.exam-card { margin-bottom: 16px; }
.exam-card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.exam-card-header h3 { margin: 0; font-size: 18px; color: #303133; }
.exam-desc { color: #606266; font-size: 14px; margin-bottom: 12px; }
.exam-meta { display: flex; gap: 20px; color: #909399; font-size: 13px; margin-bottom: 16px; }
.exam-meta span { display: flex; align-items: center; gap: 4px; }
.exam-actions { text-align: right; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.score-text { font-weight: 600; }
.score-high { color: #67c23a; }
.score-mid { color: #e6a23c; }
.score-low { color: #f56c6c; }
</style>