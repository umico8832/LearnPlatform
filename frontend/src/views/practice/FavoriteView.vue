<template>
  <div class="favorite-container page-container">
    <section class="page-hero">
      <div>
        <span class="section-kicker">重点题库</span>
        <h1>我的收藏</h1>
        <p>把标记过的题目集中回看，按收藏清单快速进入针对性练习。</p>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="EditPen"
          :loading="practiceLoading"
          :disabled="total === 0"
          @click="startFavoritePractice"
        >
          收藏题练习
        </el-button>
        <el-button :icon="Search" @click="router.push('/questions')"> 浏览题库 </el-button>
      </div>
    </section>

    <section class="favorite-summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never" class="favorite-summary-card">
        <span>{{ item.label }}</span>
        <strong :class="item.tone">{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </el-card>
    </section>

    <el-card class="practice-card" shadow="never">
      <div class="practice-card-copy">
        <strong>生成收藏题练习</strong>
        <span>从收藏清单中抽取指定数量题目，适合考前复盘或专项回炉。</span>
      </div>
      <div class="practice-controls">
        <span class="control-label">题目数</span>
        <el-input-number v-model="practiceCount" :min="1" :max="50" controls-position="right" />
        <el-button
          type="primary"
          :icon="VideoPlay"
          :loading="practiceLoading"
          :disabled="total === 0"
          @click="startFavoritePractice"
        >
          开始练习
        </el-button>
      </div>
    </el-card>

    <el-card class="favorite-table-card" shadow="never">
      <div class="table-toolbar">
        <div>
          <strong>收藏明细</strong>
          <span>共 {{ total }} 道</span>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="loadFavorites">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="favorites" stripe style="width: 100%">
        <el-table-column label="题干" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="question-content">{{ row.questionContent }}</span>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getQuestionTypeTag(row.questionType)">
              {{ getQuestionTypeLabel(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.courseName || '未关联课程' }}
          </template>
        </el-table-column>
        <el-table-column label="难度" width="126" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.difficulty" disabled :max="5" />
          </template>
        </el-table-column>
        <el-table-column label="收藏时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="178" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                type="primary"
                size="small"
                link
                :icon="EditPen"
                @click="startSingleFavoritePractice(row as FavoriteQuestionVO)"
              >
                练习
              </el-button>
              <el-popconfirm
                title="确定取消收藏该题目？"
                confirm-button-text="确定"
                cancel-button-text="取消"
                @confirm="handleRemoveFavorite(row as FavoriteQuestionVO)"
              >
                <template #reference>
                  <el-button type="danger" size="small" link :icon="Delete">取消</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无收藏题目">
            <el-button type="primary" :icon="Search" @click="router.push('/questions')"> 去题库收藏 </el-button>
          </el-empty>
        </template>
      </el-table>

      <div v-if="total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadFavorites"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { errorMessage, SemanticTagType } from '@/utils/errors'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete, EditPen, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import { getFavorites, removeFavorite as removeFavoriteApi, type FavoriteQuestionVO } from '@/api/favorite'
import { getFavoritePractice } from '@/api/practice'

const router = useRouter()
const loading = ref(false)
const practiceLoading = ref(false)
const favorites = ref<FavoriteQuestionVO[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const practiceCount = ref(10)

const courseCount = computed(() => new Set(favorites.value.map((item) => item.courseName || '未关联课程')).size)
const averageDifficulty = computed(() => {
  if (favorites.value.length === 0) return '-'
  const totalDifficulty = favorites.value.reduce((sum, item) => sum + (item.difficulty || 0), 0)
  return `${(totalDifficulty / favorites.value.length).toFixed(1)} 星`
})
const latestFavoriteTime = computed(() => {
  if (favorites.value.length === 0) return '-'
  return formatTime(favorites.value[0].createTime).slice(0, 10)
})
const summaryCards = computed(() => [
  { label: '收藏总数', value: total.value, note: '全部收藏题目', tone: 'tone-primary' },
  { label: '当前页课程', value: courseCount.value, note: '按课程分布复盘', tone: 'tone-success' },
  { label: '平均难度', value: averageDifficulty.value, note: '当前页收藏估算', tone: 'tone-warning' },
  { label: '最近收藏', value: latestFavoriteTime.value, note: '优先回看新标记题', tone: 'tone-danger' },
])

const questionTypeMap: Record<string, { label: string; tag: SemanticTagType }> = {
  SINGLE_CHOICE: { label: '单选题', tag: 'primary' },
  MULTIPLE_CHOICE: { label: '多选题', tag: 'warning' },
  TRUE_FALSE: { label: '判断题', tag: 'success' },
  FILL_BLANK: { label: '填空题', tag: 'info' },
  SHORT_ANSWER: { label: '简答题', tag: 'danger' },
}

function getQuestionTypeLabel(type: string) {
  return questionTypeMap[type]?.label || type
}

function getQuestionTypeTag(type: string) {
  return questionTypeMap[type]?.tag
}

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await getFavorites({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.code === 0 && res.data) {
      favorites.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '加载收藏列表失败'))
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  pageNum.value = 1
  loadFavorites()
}

async function handleRemoveFavorite(row: FavoriteQuestionVO) {
  try {
    await removeFavoriteApi(row.questionId)
    ElMessage.success('已取消收藏')
    if (favorites.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadFavorites()
  } catch (e) {
    ElMessage.error(errorMessage(e, '取消收藏失败'))
  }
}

async function startFavoritePractice() {
  practiceLoading.value = true
  try {
    const res = await getFavoritePractice({ count: practiceCount.value })
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.setItem('practice_mode', 'favorite')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('暂无可练习的收藏题目')
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '获取收藏练习题失败'))
  } finally {
    practiceLoading.value = false
  }
}

async function startSingleFavoritePractice(row: FavoriteQuestionVO) {
  practiceLoading.value = true
  try {
    const res = await getFavoritePractice({ questionId: row.questionId, count: 1 })
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.setItem('practice_mode', 'favorite')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('该收藏题暂不可练习')
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '获取收藏练习题失败'))
  } finally {
    practiceLoading.value = false
  }
}

onMounted(() => {
  loadFavorites()
})
</script>

<style scoped>
.favorite-container {
  padding: var(--lp-space-6);
}

.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
  padding: var(--lp-space-6);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: linear-gradient(135deg, var(--lp-primary-soft) 0%, var(--lp-surface) 58%), var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: var(--lp-space-2);
  color: var(--lp-primary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-wide);
}

.page-hero h1 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-tight);
}

.page-hero p {
  margin: var(--lp-space-2) 0 0;
  max-width: var(--lp-reading-measure);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.favorite-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-4);
  margin-bottom: var(--lp-space-4);
}

.favorite-summary-card :deep(.el-card__body) {
  min-height: 108px;
}

.favorite-summary-card span,
.favorite-summary-card small {
  display: block;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.favorite-summary-card strong {
  display: block;
  margin: var(--lp-space-2) 0 var(--lp-space-1);
  color: var(--lp-text);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-heavy);
  line-height: var(--lp-leading-display);
}

.favorite-summary-card .tone-primary {
  color: var(--lp-primary);
}

.favorite-summary-card .tone-success {
  color: var(--lp-success);
}

.favorite-summary-card .tone-warning {
  color: var(--lp-warning);
}

.favorite-summary-card .tone-danger {
  color: var(--lp-danger);
}

.practice-card {
  margin-bottom: var(--lp-space-4);
}

.practice-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
}

.practice-card-copy strong,
.table-toolbar strong {
  display: block;
  color: var(--lp-text);
  font-size: var(--lp-text-md);
}

.practice-card-copy span,
.table-toolbar span,
.control-label {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.practice-card-copy span {
  display: block;
  margin-top: var(--lp-space-1);
  line-height: var(--lp-leading-body);
}

.practice-controls {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.favorite-table-card :deep(.el-card__body) {
  padding: 0 !important;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  padding: var(--lp-space-4);
  border-bottom: var(--lp-border-hairline);
  background: var(--lp-surface-soft);
}

.table-toolbar div {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}

.question-content {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}

.table-actions {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: var(--lp-space-3) var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}

@media (max-width: 900px) {
  .favorite-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .favorite-container {
    padding: var(--lp-space-4);
  }

  .page-hero,
  .practice-card :deep(.el-card__body) {
    align-items: stretch;
    flex-direction: column;
    padding: var(--lp-space-4);
  }

  .page-hero h1 {
    font-size: var(--lp-text-2xl);
  }

  .header-actions {
    justify-content: flex-start;
  }

  .header-actions .el-button,
  .practice-controls,
  .practice-controls .el-button,
  .practice-controls .el-input-number {
    width: 100%;
  }

  .practice-controls {
    align-items: stretch;
    justify-content: flex-start;
  }

  .pagination-wrapper {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .favorite-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
