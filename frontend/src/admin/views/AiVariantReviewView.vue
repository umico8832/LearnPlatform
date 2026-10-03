<template>
  <div class="admin-page ai-variant-review-page">
    <header class="admin-page-header">
      <div><h1>AI 变式题审查</h1></div>
      <el-select
        v-model="reviewStatus"
        aria-label="审查状态"
        style="width: 140px"
        :disabled="loading || !!actionId"
        @change="load(1)"
        ><el-option label="待审查" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option
          label="已拒绝"
          value="REJECTED"
      /></el-select>
    </header>
    <LpStatePanel v-if="loading" state="loading" loading-label="正在读取审查队列" />
    <LpStatePanel
      v-else-if="loadError"
      state="error"
      title="暂时无法读取审查队列"
      :description="loadError"
      @retry="load(page)"
    />
    <LpEmptyState v-else-if="loaded && !items.length" title="当前状态下没有 AI 变式题" />
    <section v-else class="review-list" aria-label="AI 变式题审查队列">
      <el-card v-for="item in items" :key="item.id" shadow="never" class="review-card">
        <div class="review-card-header">
          <div>
            <el-tag effect="plain">{{ item.courseName }}</el-tag
            ><el-tag :type="statusTag(item.reviewStatus)">{{ statusLabel(item.reviewStatus) }}</el-tag>
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
            <details>
              <summary>查看解析</summary>
              <p>解析：{{ item.analysis }}</p>
            </details>
          </article>
        </div>
        <p v-if="actionError[item.id]" class="action-error" role="alert">
          {{ actionError[item.id] }} <button type="button" :disabled="!!actionId" @click="retry(item)">重试</button>
        </p>
        <p v-if="item.reviewNote" class="review-note">审查说明：{{ item.reviewNote }}</p>
        <div v-if="item.reviewStatus === 'PENDING'" class="review-actions">
          <el-button type="danger" plain :loading="actionId === item.id" :disabled="!!actionId" @click="reject(item.id)"
            >拒绝</el-button
          ><el-button type="primary" :loading="actionId === item.id" :disabled="!!actionId" @click="approve(item.id)"
            >批准并发布</el-button
          >
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
      :disabled="loading || !!actionId"
      @current-change="load"
    />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { LpEmptyState, LpStatePanel } from '@/components/ui'
import {
  getAiVariantReviews,
  reviewAiVariant,
  type AiVariantReviewStatus,
  type AiVariantReviewVO,
} from '@/api/aiVariantReview'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
const reviewStatus = ref<AiVariantReviewStatus>('PENDING'),
  items = ref<AiVariantReviewVO[]>([]),
  loading = ref(false),
  loaded = ref(false),
  loadError = ref(''),
  page = ref(1),
  total = ref(0),
  actionId = ref<number | null>(null)
const actionError = ref<Record<number, string>>({}),
  pageSize = 10
let requestVersion = 0
let actionVersion = 0
let alive = true
const failedAction = ref<Record<number, { decision: 'APPROVE' | 'REJECT'; note: string }>>({})
async function load(targetPage = 1) {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  try {
    const response = await getAiVariantReviews(reviewStatus.value, targetPage, pageSize, { errorDisplay: 'inline' })
    if (version !== requestVersion) return
    items.value = response.data.records
    page.value = response.data.current
    total.value = response.data.total
    loaded.value = true
  } catch {
    if (version === requestVersion) {
      loaded.value = true
      loadError.value = '审查队列加载失败，请重试'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
async function approve(id: number) {
  await begin(id, 'APPROVE', '管理员核验题干、选项、答案与解析后通过', true)
}
async function reject(id: number) {
  if (actionId.value) return
  const version = ++actionVersion,
    session = getAuthSessionVersion()
  actionId.value = id
  try {
    const result = await ElMessageBox.prompt('请填写拒绝原因', '拒绝 AI 生成题', {
      inputValidator: (v) => Boolean(v?.trim()) || '拒绝原因不能为空',
    })
    if (!current(version, session)) return
    await act(id, 'REJECT', result.value.trim(), version, session)
    if (current(version, session)) ElMessage.success('已拒绝该变式题')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && current(version, session)) actionError.value[id] = '审核失败，请重试'
  } finally {
    if (current(version, session)) actionId.value = null
  }
}
async function begin(id: number, decision: 'APPROVE' | 'REJECT', note: string, confirm: boolean) {
  if (actionId.value) return
  const version = ++actionVersion,
    session = getAuthSessionVersion()
  actionId.value = id
  try {
    if (confirm)
      await ElMessageBox.confirm('确认已核验题干、选项、答案、解析和母题课程范围？', '批准 AI 生成题', {
        type: 'warning',
      })
    if (!current(version, session)) return
    await act(id, decision, note, version, session)
    if (current(version, session))
      ElMessage.success(decision === 'APPROVE' ? '已发布为正式 AI 生成题' : '已拒绝该变式题')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && current(version, session)) actionError.value[id] = '审核失败，请重试'
  } finally {
    if (current(version, session)) actionId.value = null
  }
}
function current(version: number, session: number) {
  return alive && version === actionVersion && session === getAuthSessionVersion()
}
async function act(id: number, decision: 'APPROVE' | 'REJECT', note: string, version: number, session: number) {
  delete actionError.value[id]
  delete failedAction.value[id]
  try {
    if (!current(version, session)) return
    await reviewAiVariant(id, decision, note, { errorDisplay: 'inline' })
    if (current(version, session)) await load(page.value)
  } catch (error) {
    if (current(version, session)) {
      failedAction.value[id] = { decision, note }
      actionError.value[id] = '审核失败，请重试'
    }
    throw error
  }
}
function retry(item: AiVariantReviewVO) {
  const failed = failedAction.value[item.id]
  if (failed) void begin(item.id, failed.decision, failed.note, false)
}
function statusLabel(status: AiVariantReviewStatus) {
  return status === 'PENDING' ? '待审查' : status === 'APPROVED' ? '已通过' : '已拒绝'
}
function statusTag(status: AiVariantReviewStatus) {
  return status === 'PENDING' ? 'warning' : status === 'APPROVED' ? 'success' : 'danger'
}
const unsubscribeSession = onAuthSessionChange(() => {
  requestVersion++
  actionVersion++
  actionId.value = null
  items.value = []
  total.value = 0
  loadError.value = ''
  loaded.value = false
})
onMounted(() => void load())
onUnmounted(() => {
  alive = false
  requestVersion++
  unsubscribeSession()
})
</script>
<style scoped>
.review-list {
  display: grid;
  gap: var(--lp-space-4);
  min-height: 120px;
}
.review-card-header,
.review-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
}
.review-card-header > div {
  display: flex;
  gap: var(--lp-space-2);
}
.question-comparison {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: var(--lp-space-4);
  margin-top: var(--lp-space-4);
}
.question-comparison article {
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-subtle);
}
.question-comparison p,
.question-comparison h3 {
  margin: var(--lp-space-2) 0 0;
  line-height: var(--lp-leading-body);
}
.question-comparison ol {
  margin: var(--lp-space-3) 0;
  padding-left: var(--lp-space-5);
}
.review-note,
.published-reference {
  color: var(--lp-text-secondary);
}
.review-actions {
  justify-content: flex-end;
  margin-top: var(--lp-space-4);
}
.action-error {
  color: var(--lp-danger);
  margin: var(--lp-space-3) 0 0;
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
