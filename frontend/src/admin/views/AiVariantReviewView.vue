<template>
  <div class="admin-page ai-variant-review-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">AI CONTENT REVIEW</p>
        <h2>AI 变式题审查</h2>
        <p class="admin-page-description">
          核对母题范围、题干、选项、答案和解析。只有管理员批准后才发布为可进入阶段测评的正式题目。
        </p>
      </div>
      <el-select v-model="reviewStatus" aria-label="审查状态" style="width: 140px" @change="load(1)">
        <el-option label="待审查" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已拒绝" value="REJECTED" />
      </el-select>
    </header>

    <el-result v-if="loadFailed" icon="error" title="暂时无法读取审查队列">
      <template #extra><el-button type="primary" @click="load(page)">重新加载</el-button></template>
    </el-result>
    <el-empty v-else-if="!loading && !items.length" description="当前状态下没有 AI 变式题" />
    <section v-else v-loading="loading" class="review-list" aria-label="AI 变式题审查队列">
      <el-card v-for="item in items" :key="item.id" shadow="never" class="review-card">
        <div class="review-card-header">
          <div>
            <el-tag effect="plain">{{ item.courseName }}</el-tag>
            <el-tag :type="statusTag(item.reviewStatus)">{{ statusLabel(item.reviewStatus) }}</el-tag>
          </div>
          <span>难度 {{ item.difficulty }}</span>
        </div>
        <div class="question-comparison">
          <article>
            <small>母题 #{{ item.motherQuestionId }}</small>
            <p>{{ item.motherQuestionContent }}</p>
          </article>
          <article>
            <small>待审 AI 生成题 #{{ item.id }}</small>
            <h3>{{ item.questionContent }}</h3>
            <ol>
              <li v-for="option in item.options" :key="option.label">{{ option.label }}. {{ option.content }}</li>
            </ol>
            <p>
              <strong>正确答案：{{ item.correctAnswer }}</strong>
            </p>
            <p>解析：{{ item.analysis }}</p>
          </article>
        </div>
        <p v-if="item.reviewNote" class="review-note">审查说明：{{ item.reviewNote }}</p>
        <div v-if="item.reviewStatus === 'PENDING'" class="review-actions">
          <el-button type="danger" plain @click="reject(item.id)">拒绝</el-button>
          <el-button type="primary" @click="approve(item.id)">批准并发布</el-button>
        </div>
        <p v-else-if="item.publishedQuestionId" class="published-reference">
          已发布正式题目 #{{ item.publishedQuestionId }}，来源保留为 AI_GENERATED。
        </p>
      </el-card>
    </section>
    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiVariantReviews,
  reviewAiVariant,
  type AiVariantReviewStatus,
  type AiVariantReviewVO,
} from '@/api/aiVariantReview'

const reviewStatus = ref<AiVariantReviewStatus>('PENDING')
const items = ref<AiVariantReviewVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)

async function load(targetPage = 1) {
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getAiVariantReviews(reviewStatus.value, targetPage, pageSize)
    items.value = response.data.records
    page.value = response.data.current
    total.value = response.data.total
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function approve(id: number) {
  await ElMessageBox.confirm('确认已核验题干、选项、答案、解析和母题课程范围？', '批准 AI 生成题', {
    type: 'warning',
  })
  await reviewAiVariant(id, 'APPROVE', '管理员核验题干、选项、答案与解析后通过')
  ElMessage.success('已发布为正式 AI 生成题')
  await load(page.value)
}

async function reject(id: number) {
  const result = await ElMessageBox.prompt('请填写拒绝原因', '拒绝 AI 生成题', {
    inputValidator: (value) => Boolean(value?.trim()) || '拒绝原因不能为空',
  })
  await reviewAiVariant(id, 'REJECT', result.value.trim())
  ElMessage.success('已拒绝该变式题')
  await load(page.value)
}

function statusLabel(status: AiVariantReviewStatus) {
  return status === 'PENDING' ? '待审查' : status === 'APPROVED' ? '已通过' : '已拒绝'
}

function statusTag(status: AiVariantReviewStatus) {
  return status === 'PENDING' ? 'warning' : status === 'APPROVED' ? 'success' : 'danger'
}

onMounted(() => load())
</script>

<style scoped>
.review-list {
  display: grid;
  gap: 16px;
  min-height: 120px;
}
.review-card-header,
.review-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.review-card-header > div {
  display: flex;
  gap: 8px;
}
.question-comparison {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 14px;
  margin-top: 14px;
}
.question-comparison article {
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.question-comparison p,
.question-comparison h3 {
  margin: 8px 0 0;
  line-height: 1.65;
}
.question-comparison ol {
  margin: 10px 0;
  padding-left: 22px;
}
.review-note,
.published-reference {
  color: var(--el-text-color-secondary);
}
.review-actions {
  justify-content: flex-end;
  margin-top: 14px;
}
@media (max-width: 767px) {
  .question-comparison {
    grid-template-columns: 1fr;
  }
  .review-actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }
  .review-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
