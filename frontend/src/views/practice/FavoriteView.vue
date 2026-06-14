<template>
  <div class="favorite-container">
    <div class="page-header">
      <div>
        <h2>⭐ 我的收藏</h2>
        <p class="page-subtitle">集中复习自己标记过的重点题目</p>
      </div>
      <div class="header-actions">
        <el-input-number
          v-model="practiceCount"
          :min="1"
          :max="50"
          size="default"
          controls-position="right"
        />
        <el-button
          type="primary"
          :loading="practiceLoading"
          :disabled="total === 0"
          @click="startFavoritePractice"
        >
          收藏题练习
        </el-button>
      </div>
    </div>

    <!-- 收藏列表 -->
    <el-table
      v-loading="loading"
      :data="favorites"
      stripe
      style="width: 100%"
      empty-text="暂无收藏题目"
    >
      <el-table-column label="题干" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="question-content">{{ row.questionContent }}</span>
        </template>
      </el-table-column>
      <el-table-column label="题型" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getQuestionTypeTag(row.questionType)">
            {{ getQuestionTypeLabel(row.questionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" width="150" show-overflow-tooltip />
      <el-table-column label="难度" width="100" align="center">
        <template #default="{ row }">
          <el-rate v-model="row.difficulty" disabled :max="5" style="display: inline-flex;" />
        </template>
      </el-table-column>
      <el-table-column label="收藏时间" width="180" align="center">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" size="small" link @click="startSingleFavoritePractice(row as FavoriteQuestionVO)">
              练习
            </el-button>
            <el-popconfirm
              title="确定取消收藏该题目？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              @confirm="handleRemoveFavorite(row as FavoriteQuestionVO)"
            >
              <template #reference>
                <el-button type="danger" size="small" link>取消收藏</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadFavorites"
        @current-change="loadFavorites"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

const questionTypeMap: Record<string, { label: string; tag: string }> = {
  SINGLE_CHOICE: { label: '单选题', tag: '' },
  MULTIPLE_CHOICE: { label: '多选题', tag: 'warning' },
  TRUE_FALSE: { label: '判断题', tag: 'success' },
  FILL_BLANK: { label: '填空题', tag: 'info' },
  SHORT_ANSWER: { label: '简答题', tag: 'danger' },
}

function getQuestionTypeLabel(type: string) {
  return questionTypeMap[type]?.label || type
}

function getQuestionTypeTag(type: string) {
  return (questionTypeMap[type]?.tag || '') as any
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
  } catch (e: any) {
    ElMessage.error(e.message || '加载收藏列表失败')
  } finally {
    loading.value = false
  }
}

async function handleRemoveFavorite(row: FavoriteQuestionVO) {
  try {
    await removeFavoriteApi(row.questionId)
    ElMessage.success('已取消收藏')
    loadFavorites()
  } catch (e: any) {
    ElMessage.error(e.message || '取消收藏失败')
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
  } catch (e: any) {
    ElMessage.error(e.message || '获取收藏练习题失败')
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
  } catch (e: any) {
    ElMessage.error(e.message || '获取收藏练习题失败')
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
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.question-content {
  color: #303133;
  line-height: 1.6;
}
.table-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .favorite-container {
    padding: 12px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
