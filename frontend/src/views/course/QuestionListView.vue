<template>
  <div class="question-list">
    <div class="page-header">
      <h2>题库</h2>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="filters.questionType" placeholder="题型" clearable style="width: 130px" @change="fetchQuestions">
          <el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" />
          <el-option label="判断题" value="TRUE_FALSE" />
          <el-option label="填空题" value="FILL_BLANK" />
          <el-option label="简答题" value="SHORT_ANSWER" />
        </el-select>
        <el-select v-model="filters.courseId" placeholder="所属课程" clearable style="width: 180px" @change="fetchQuestions">
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.difficulty" placeholder="难度" clearable style="width: 110px" @change="fetchQuestions">
          <el-option v-for="d in 5" :key="d" :label="'⭐'.repeat(d)" :value="d" />
        </el-select>
      </div>

      <div v-loading="loading">
        <div v-if="questions.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无题目" />
        </div>

        <div v-for="q in questions" :key="q.id" class="question-card">
          <div class="question-header">
            <el-tag size="small" :type="questionTypeTag(q.questionType)">
              {{ questionTypeLabel(q.questionType) }}
            </el-tag>
            <span class="course-name">{{ q.courseName }}</span>
            <span class="difficulty">{{ '⭐'.repeat(q.difficulty) }}</span>
            <span class="score">分值: {{ q.score }}</span>
            <span class="favorite-btn" @click.stop="toggleFavorite(q.id)">
              <el-icon :size="18" :color="favoriteSet.has(q.id) ? '#f7ba2a' : '#c0c4cc'">
                <StarFilled v-if="favoriteSet.has(q.id)" />
                <Star v-else />
              </el-icon>
            </span>
          </div>
          <div class="question-content">{{ q.content }}</div>
          <div v-if="q.options && q.options.length > 0" class="question-options">
            <div v-for="opt in q.options" :key="opt.id" class="option-item">
              <span class="option-label">{{ opt.optionLabel }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </div>
          <div class="question-footer">
            <div v-if="q.knowledgePointNames && q.knowledgePointNames.length > 0" class="question-tags">
              <el-tag v-for="name in q.knowledgePointNames" :key="name" size="small" type="info" class="kp-tag">
                {{ name }}
              </el-tag>
            </div>
            <span class="comment-toggle" @click="toggleComment(q.id)">
              <el-icon><ChatLineRound /></el-icon>
              讨论
            </span>
          </div>
          <div v-if="expandedComments.has(q.id)" class="comment-section">
            <QuestionComment :question-id="q.id" />
          </div>
        </div>
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchQuestions"
          @size-change="fetchQuestions"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, ChatLineRound } from '@element-plus/icons-vue'
import { getQuestionPage, type QuestionVO } from '@/api/question'
import { getAllCourses, type CourseVO } from '@/api/course'
import { getFavoriteIds, addFavorite, removeFavorite } from '@/api/favorite'
import QuestionComment from '@/components/QuestionComment.vue'

const questions = ref<QuestionVO[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: null as number | null,
})

const courseList = ref<CourseVO[]>([])
const favoriteSet = ref<Set<number>>(new Set())
const expandedComments = ref<Set<number>>(new Set())

function toggleComment(questionId: number) {
  if (expandedComments.value.has(questionId)) {
    expandedComments.value.delete(questionId)
  } else {
    expandedComments.value.add(questionId)
  }
  expandedComments.value = new Set(expandedComments.value)
}

function questionTypeLabel(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

function questionTypeTag(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SINGLE_CHOICE: 'primary',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type] || 'primary'
}

async function fetchQuestions() {
  loading.value = true
  try {
    const res = await getQuestionPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      questionType: filters.questionType || undefined,
      courseId: filters.courseId || undefined,
      difficulty: filters.difficulty || undefined,
    })
    questions.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    courseList.value = res.data.data
  } catch {
    // ignore
  }
}

async function loadFavoriteIds() {
  try {
    const res = await getFavoriteIds()
    if (res.code === 0 && res.data) {
      favoriteSet.value = new Set(res.data)
    }
  } catch {
    // ignore
  }
}

async function toggleFavorite(questionId: number) {
  try {
    if (favoriteSet.value.has(questionId)) {
      await removeFavorite(questionId)
      favoriteSet.value.delete(questionId)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(questionId)
      favoriteSet.value.add(questionId)
      ElMessage.success('已收藏')
    }
    // 触发响应式更新
    favoriteSet.value = new Set(favoriteSet.value)
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

onMounted(() => {
  fetchQuestions()
  fetchCourses()
  loadFavoriteIds()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.question-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 12px;
  transition: box-shadow 0.2s;
}

.question-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.question-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #909399;
}

.course-name {
  font-weight: 500;
}

.question-content {
  font-size: 15px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 12px;
}

.question-options {
  margin-bottom: 10px;
}

.option-item {
  padding: 4px 0;
  font-size: 14px;
  color: #606266;
}

.option-label {
  font-weight: 600;
  margin-right: 6px;
}

.question-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.kp-tag {
  font-size: 12px;
}

.favorite-btn {
  margin-left: auto;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: transform 0.2s;
}

.favorite-btn:hover {
  transform: scale(1.2);
}

.empty-state {
  padding: 40px 0;
}

.question-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
  cursor: pointer;
  transition: color 0.2s;
  white-space: nowrap;
  margin-left: auto;
}

.comment-toggle:hover {
  color: #409eff;
}

.comment-section {
  margin-top: 8px;
}
</style>