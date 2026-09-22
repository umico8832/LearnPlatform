<template>
  <div class="admin-page knowledge-page">
    <header class="admin-page-header">
      <div>
        <h1>知识快照审核</h1>
        <p>核对片段原文与来源使用许可，再批准课程知识版本。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新版本</el-button>
    </header>
    <el-form inline @submit.prevent="filter">
      <el-form-item label="课程内容键"
        ><el-input v-model="courseKey" clearable placeholder="精确匹配课程内容键" @change="filter"
      /></el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="reviewStatus" clearable placeholder="全部状态" class="status-filter" @change="filter">
          <el-option label="待审核" value="PENDING" /><el-option label="已审核" value="REVIEWED" /><el-option
            label="已撤回"
            value="WITHDRAWN"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <el-table v-loading="loading" :data="bundles" class="admin-data-table" empty-text="没有匹配的知识版本">
      <el-table-column prop="courseKey" label="课程内容键" min-width="220" />
      <el-table-column prop="version" label="版本" min-width="120" />
      <el-table-column label="来源质量" min-width="120"
        ><template #default="{ row }">{{ label(row.sourceQualityStatus) }}</template></el-table-column
      >
      <el-table-column label="审核状态" min-width="110"
        ><template #default="{ row }">{{ label(row.reviewStatus) }}</template></el-table-column
      >
      <el-table-column prop="chunkCount" label="片段数" width="90" />
      <el-table-column label="操作" width="120"
        ><template #default="{ row }"
          ><el-button link type="primary" :disabled="submitting" @click="select(row as KnowledgeBundle)"
            >查看原文</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="load"
    />
    <el-drawer v-model="drawer" size="65%" title="快照详情" @closed="closeDetail">
      <div v-loading="detailLoading" class="detail-body">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
        <el-button v-if="detailError" @click="() => loadDetail()">重试读取详情</el-button>
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程">{{ detail.courseKey }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">{{ label(detail.reviewStatus) }}</el-descriptions-item>
            <el-descriptions-item label="来源质量">{{ label(detail.sourceQualityStatus) }}</el-descriptions-item>
            <el-descriptions-item label="来源修订" :span="2">{{ detail.sourceRevision }}</el-descriptions-item>
            <el-descriptions-item label="清单哈希" :span="2"
              ><span class="hash">{{ detail.manifestHash }}</span></el-descriptions-item
            >
            <el-descriptions-item label="导入时间">{{ detail.importedAt || '未记录' }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ detail.reviewedAt || '未审核' }}</el-descriptions-item>
            <el-descriptions-item label="审核人">{{ detail.reviewedBy ?? '未审核' }}</el-descriptions-item>
            <el-descriptions-item label="审核说明">{{ detail.reviewNote || '尚未填写' }}</el-descriptions-item>
          </el-descriptions>
          <section aria-labelledby="knowledge-chunks-title">
            <h2 id="knowledge-chunks-title">原文片段</h2>
            <p>审核应覆盖整个版本；请同时核对片段内容、来源信息和使用许可。</p>
            <el-empty v-if="!chunks.length" description="此页没有片段" />
            <article v-for="chunk in chunks" :key="chunk.id" class="knowledge-chunk">
              <h3>{{ chunk.title }}</h3>
              <p>{{ chunk.chunkId }} · {{ chunk.conceptId }} · {{ label(chunk.sourceQualityStatus) }}</p>
              <pre class="chunk-text">{{ chunk.text }}</pre>
              <details>
                <summary>来源信息与内容哈希</summary>
                <pre class="chunk-text">{{ chunk.metadataJson }}</pre>
                <p class="hash">{{ chunk.contentHash }}</p>
              </details>
            </article>
            <el-pagination
              v-model:current-page="chunkPage"
              :total="chunkTotal"
              :page-size="20"
              layout="total, prev, pager, next"
              @current-change="() => loadDetail()"
            />
          </section>
          <section aria-labelledby="knowledge-indexes-title">
            <h2 id="knowledge-indexes-title">索引状态</h2>
            <el-table :data="indexes" empty-text="尚未构建索引">
              <el-table-column prop="model" label="模型" min-width="180" /><el-table-column
                prop="dimensions"
                label="维度"
                width="80"
              />
              <el-table-column label="状态" min-width="120"
                ><template #default="{ row }">{{ label(row.status) }}</template></el-table-column
              >
              <el-table-column prop="indexedCount" label="已索引片段" width="120" />
            </el-table>
            <el-pagination
              v-if="indexTotal > 20"
              v-model:current-page="indexPage"
              :total="indexTotal"
              :page-size="20"
              layout="total, prev, pager, next"
              @current-change="() => loadDetail()"
            />
          </section>
          <el-form v-if="detail.reviewStatus !== 'WITHDRAWN'" label-position="top" @submit.prevent>
            <el-form-item label="审核说明" required
              ><el-input
                v-model="note"
                type="textarea"
                :rows="3"
                :maxlength="1000"
                show-word-limit
                placeholder="记录内容核验、来源和使用许可的结论"
                :disabled="submitting"
            /></el-form-item>
            <div class="actions">
              <el-button
                v-if="detail.reviewStatus === 'PENDING'"
                type="success"
                :disabled="!note.trim() || detailLoading || !!detailError"
                :loading="submitting"
                @click="confirmReview('REVIEWED')"
                >批准</el-button
              >
              <el-button
                type="danger"
                :disabled="!note.trim() || detailLoading || !!detailError"
                :loading="submitting"
                @click="confirmReview('WITHDRAWN')"
                >撤回</el-button
              >
            </div>
          </el-form>
          <el-alert
            v-else
            title="此版本已撤回，不能重新批准。需要恢复内容时请导入新版本。"
            type="info"
            :closable="false"
          />
        </template>
      </div>
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgeBundles,
  getKnowledgeBundle,
  getKnowledgeChunks,
  getKnowledgeIndexes,
  reviewKnowledgeBundle,
  type KnowledgeBundle,
  type KnowledgeChunk,
  type KnowledgeIndex,
} from '@/api/knowledgeAdmin'
import { errorMessage } from '@/utils/errors'

const bundles = ref<KnowledgeBundle[]>([])
const loading = ref(false)
const listError = ref('')
const courseKey = ref('')
const reviewStatus = ref('')
const page = ref(1)
const total = ref(0)
const drawer = ref(false)
const selectedId = ref<number>()
const detail = ref<KnowledgeBundle>()
const detailLoading = ref(false)
const detailError = ref('')
const chunks = ref<KnowledgeChunk[]>([])
const indexes = ref<KnowledgeIndex[]>([])
const chunkPage = ref(1)
const chunkTotal = ref(0)
const indexPage = ref(1)
const indexTotal = ref(0)
const note = ref('')
const submitting = ref(false)
let requestId = 0
let listRequestId = 0
const labels: Record<string, string> = {
  PENDING: '待审核',
  REVIEWED: '已审核',
  WITHDRAWN: '已撤回',
  review_pending: '来源待审',
  reviewed: '来源已审',
  INDEXING: '构建中',
  READY: '已就绪',
  FAILED: '构建失败',
  DELETED: '待清理',
  PURGED: '已清理',
}
const label = (status: string) => labels[status] || status
function filter() {
  page.value = 1
  void load()
}
async function load() {
  const token = ++listRequestId
  loading.value = true
  listError.value = ''
  try {
    const r = await getKnowledgeBundles({
      courseKey: courseKey.value.trim() || undefined,
      reviewStatus: reviewStatus.value || undefined,
      pageNum: page.value,
      pageSize: 20,
    })
    if (token !== listRequestId) return
    bundles.value = r.data.records
    total.value = r.data.total
  } catch (error) {
    if (token === listRequestId) {
      bundles.value = []
      total.value = 0
      listError.value = errorMessage(error, '无法读取知识版本，请重试')
    }
  } finally {
    if (token === listRequestId) loading.value = false
  }
}
async function select(row: KnowledgeBundle) {
  selectedId.value = row.bundleId
  drawer.value = true
  note.value = ''
  chunkPage.value = 1
  indexPage.value = 1
  await loadDetail(row.bundleId)
}
async function loadDetail(id = selectedId.value) {
  if (!id || id !== selectedId.value || !drawer.value) return
  const token = ++requestId
  detailLoading.value = true
  detailError.value = ''
  detail.value = undefined
  chunks.value = []
  indexes.value = []
  chunkTotal.value = 0
  indexTotal.value = 0
  try {
    const [b, c, i] = await Promise.all([
      getKnowledgeBundle(id),
      getKnowledgeChunks(id, { pageNum: chunkPage.value, pageSize: 20 }),
      getKnowledgeIndexes(id, { pageNum: indexPage.value, pageSize: 20 }),
    ])
    if (token !== requestId) return
    detail.value = b.data
    chunks.value = c.data.records
    chunkTotal.value = c.data.total
    indexes.value = i.data.records
    indexTotal.value = i.data.total
  } catch (error) {
    if (token === requestId) detailError.value = errorMessage(error, '无法读取快照详情，请重试')
  } finally {
    if (token === requestId) detailLoading.value = false
  }
}
function closeDetail() {
  ++requestId
  selectedId.value = undefined
  detail.value = undefined
  note.value = ''
}
async function confirmReview(decision: 'REVIEWED' | 'WITHDRAWN') {
  if (!detail.value || submitting.value || detailLoading.value || detailError.value || !note.value.trim()) return
  if (detail.value.reviewStatus === 'WITHDRAWN' || (decision === 'REVIEWED' && detail.value.reviewStatus !== 'PENDING'))
    return
  const id = detail.value.bundleId
  const reviewNote = note.value.trim()
  const version = detail.value.version
  submitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        decision === 'REVIEWED'
          ? `确认已核对版本 ${version} 的全部内容和来源使用许可，并批准此版本？`
          : `撤回版本 ${version} 后将停止检索，且不能重新批准。确定撤回？`,
        '确认审核',
        { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await reviewKnowledgeBundle(id, decision, reviewNote)
      ElMessage.success('审核状态已更新')
      if (selectedId.value === id) note.value = ''
    } catch (error) {
      ElMessage.error(errorMessage(error, '操作失败，请核对重新加载后的状态'))
    }
    await Promise.all([load(), loadDetail(id)])
  } finally {
    submitting.value = false
  }
}
onMounted(load)
onUnmounted(() => {
  ++requestId
  ++listRequestId
})
</script>
<style scoped>
.knowledge-page {
  display: grid;
  gap: var(--lp-space-5);
}
.admin-page-header p,
.detail-body p {
  color: var(--lp-text-secondary);
}
.status-filter {
  min-width: calc(var(--lp-space-20) * 2);
}
.detail-body {
  display: grid;
  gap: var(--lp-space-6);
  min-height: calc(var(--lp-space-20) * 2);
}
.detail-body h2 {
  font-size: var(--lp-text-xl);
}
.knowledge-chunk {
  border-bottom: 1px solid var(--lp-border);
  padding-block: var(--lp-space-4);
}
.knowledge-chunk h3 {
  font-size: var(--lp-text-base);
}
.chunk-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  line-height: var(--lp-leading-relaxed);
}
.hash {
  overflow-wrap: anywhere;
}
.actions {
  display: flex;
  gap: var(--lp-space-3);
}
summary {
  cursor: pointer;
  color: var(--lp-primary);
}
</style>
