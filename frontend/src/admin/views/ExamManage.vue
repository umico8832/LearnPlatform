<template>
  <div class="exam-manage-container admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">EXAM CENTER</p>
        <h2>试卷管理</h2>
        <p class="admin-page-description">管理普通练习与来源可核验的官方试卷，配置题目结构和发布状态。</p>
      </div>
      <div class="admin-header-actions">
        <el-button type="warning" :icon="MagicStick" @click="smartExamDialog?.open()">智能组卷</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增试卷</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in paperStats" :key="item.label" shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">{{ item.label }}</p>
          <div class="admin-summary-value">{{ item.value }}</div>
          <div class="admin-summary-note">{{ item.note }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="loadPapers">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
          <el-button @click="loadPapers" :icon="Refresh">刷新</el-button>
        </div>
        <span class="table-summary">当前筛选 {{ total }} 份试卷</span>
      </div>

      <el-table :data="papers as any" v-loading="loading" stripe class="admin-data-table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="试卷名称" min-width="200" />
        <el-table-column label="性质" width="100">
          <template #default="{ row }">
            <el-tag :type="paperTypeTag(row as ExamPaperVO)" size="small">
              {{ paperTypeLabel(row as ExamPaperVO) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="考试年份" width="110">
          <template #default="{ row }">
            {{ (row as ExamPaperVO).examYear || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" width="120" />
        <el-table-column prop="questionCount" label="题数" width="80" />
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column prop="duration" label="时长(分)" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(row as ExamPaperVO).status === 1 ? 'success' : 'info'" size="small">
              {{ (row as ExamPaperVO).status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime((row as ExamPaperVO).createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="openDialog(row as ExamPaperVO)"
              >编辑</el-button
            >
            <el-button
              v-if="(row as ExamPaperVO).status === 0"
              type="success"
              link
              size="small"
              :icon="Promotion"
              @click="handlePublish(row as ExamPaperVO)"
              >发布</el-button
            >
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row as ExamPaperVO)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="10"
          layout="total, prev, pager, next"
          @current-change="loadPapers"
        />
      </div>
    </el-card>

    <ExamPaperEditorDialog ref="paperEditorDialog" :courses="courseList" @saved="loadPapers" />
    <SmartExamDialog ref="smartExamDialog" :courses="courseList" @created="loadPapers" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Delete, Edit, MagicStick, Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import { deleteExamPaper, getExamPaperList, publishExamPaper } from '@/api/exam'
import type { ExamPaperVO } from '@/api/exam'
import { getCoursePage } from '@/api/course'
import { formatTime } from '@/utils/format'
import ExamPaperEditorDialog from './exam/ExamPaperEditorDialog.vue'
import SmartExamDialog from './exam/SmartExamDialog.vue'
import { paperTypeLabel, paperTypeTag } from './exam/examManagePresentation'

// 试卷列表
const loading = ref(false)
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const filterStatus = ref<number | undefined>(undefined)

// 课程列表
const courseList = ref<{ id: number; name: string }[]>([])
const smartExamDialog = ref<InstanceType<typeof SmartExamDialog>>()
const paperEditorDialog = ref<InstanceType<typeof ExamPaperEditorDialog>>()

const paperStats = computed(() => {
  const published = papers.value.filter((p) => p.status === 1).length
  const draft = papers.value.filter((p) => p.status === 0).length
  const totalQuestions = papers.value.reduce((sum, p) => sum + (p.questionCount || 0), 0)
  const avgDuration = papers.value.length
    ? Math.round(papers.value.reduce((sum, p) => sum + (p.duration || 0), 0) / papers.value.length)
    : 0
  return [
    { label: '当前页试卷', value: papers.value.length, note: `筛选共 ${total.value} 份`, icon: Collection },
    { label: '已发布', value: published, note: `草稿 ${draft} 份`, icon: Promotion },
    { label: '题目总量', value: totalQuestions, note: '当前页题目合计', icon: Collection },
    { label: '平均时长', value: avgDuration ? `${avgDuration}` : '-', note: '分钟 / 当前页', icon: Refresh },
  ]
})

onMounted(() => {
  loadPapers()
  loadCourses()
})

const loadPapers = async () => {
  loading.value = true
  try {
    const res = await getExamPaperList({ pageNum: pageNum.value, pageSize: 10, status: filterStatus.value })
    if (res.code === 0 && res.data) {
      papers.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取试卷列表失败')
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    courseList.value = (res.data?.records ?? []).map((c) => ({ id: c.id, name: c.name }))
  } catch {}
}

const openDialog = (paper?: ExamPaperVO) => {
  void paperEditorDialog.value?.open(paper)
}

const handlePublish = async (paper: ExamPaperVO) => {
  try {
    await ElMessageBox.confirm(`确定发布试卷「${paper.title}」？发布后用户可见`, '发布确认', { type: 'warning' })
    const res = await publishExamPaper(paper.id)
    if (res.code === 0) {
      ElMessage.success('发布成功')
      loadPapers()
    }
  } catch {}
}

const handleDelete = async (paper: ExamPaperVO) => {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${paper.title}」？`, '删除确认', { type: 'warning' })
    const res = await deleteExamPaper(paper.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      loadPapers()
    }
  } catch {}
}
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}
</style>
