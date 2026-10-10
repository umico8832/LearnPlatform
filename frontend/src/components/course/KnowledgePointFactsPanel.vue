<template>
  <section class="panel knowledge-facts-panel" aria-labelledby="knowledge-facts-heading">
    <LpSectionHeading heading-id="knowledge-facts-heading" title="知识点学习记录">
      <template #aside>
        <el-tooltip content="作答与答对按次数统计，错题与复习按题目统计；一道题可能计入多个知识点。" placement="top">
          <el-button text circle aria-label="查看统计口径"
            ><el-icon><InfoFilled /></el-icon
          ></el-button>
        </el-tooltip>
      </template>
    </LpSectionHeading>

    <div v-if="loading" class="facts-skeleton" aria-label="正在加载知识点学习事实">
      <LpSkeleton :rows="5" />
    </div>
    <div v-else-if="failed" class="facts-error" role="alert">
      <p>暂时无法读取知识点学习事实，请重试。</p>
      <el-button type="primary" @click="loadFacts(page)">重新加载</el-button>
    </div>
    <p v-else-if="!records.length" class="facts-empty">
      暂无知识点学习记录。完成课程内作答后，这里会显示有数据的知识点。
    </p>
    <template v-else>
      <div class="facts-list">
        <article
          v-for="fact in visibleRecords"
          :key="factKey(fact)"
          class="fact-row"
          :data-engaged="fact.answeredCount > 0"
          :data-has-unresolved-wrong="fact.unresolvedWrongCount > 0"
        >
          <div class="fact-heading">
            <div>
              <h3><span class="fact-activity-dot" aria-hidden="true" />{{ fact.knowledgePointName }}</h3>
              <p v-if="!fact.available" class="fact-status">
                {{ fact.knowledgePointId === null ? '未关联知识点的课程记录' : '历史知识点记录' }}
              </p>
            </div>
            <el-tag v-if="!fact.available" size="small" type="info" effect="plain">
              {{ fact.knowledgePointId === null ? '未关联' : '历史记录' }}
            </el-tag>
          </div>
          <dl class="fact-metrics">
            <div v-if="fact.answeredCount">
              <dt>作答次数</dt>
              <dd>{{ fact.answeredCount }}</dd>
            </div>
            <div v-if="fact.correctCount">
              <dt>答对次数</dt>
              <dd>{{ fact.correctCount }}</dd>
            </div>
            <div v-if="fact.unresolvedWrongCount">
              <dt>未处理错题</dt>
              <dd>{{ fact.unresolvedWrongCount }}</dd>
            </div>
            <div v-if="fact.dueReviewCount">
              <dt>到期复习题</dt>
              <dd>{{ fact.dueReviewCount }}</dd>
            </div>
          </dl>
          <div class="fact-actions">
            <template v-if="canDeepLink(fact)">
              <el-button
                v-if="fact.dueReviewCount"
                text
                type="primary"
                @click="emit('open-review', fact.knowledgePointId!, fact.knowledgePointName)"
              >
                复习
              </el-button>
              <el-button
                v-if="fact.unresolvedWrongCount"
                text
                type="primary"
                @click="emit('open-wrong-questions', fact.knowledgePointId!, fact.knowledgePointName)"
              >
                错题
              </el-button>
            </template>
            <el-button
              v-if="fact.tutorAvailable && fact.knowledgePointId !== null"
              text
              type="primary"
              @click="emit('open-tutor', fact.knowledgePointId)"
            >
              进入教学
            </el-button>
            <el-button v-if="!canDeepLink(fact)" text type="primary" @click="emit('open-question-bank')">
              查看课程题库
            </el-button>
          </div>
        </article>
      </div>
      <el-button
        v-if="records.length > initialVisibleCount"
        text
        class="facts-toggle"
        :aria-expanded="showAllRecords"
        @click="showAllRecords = !showAllRecords"
        >{{
          showAllRecords ? '收起记录' : `展开${total > pageSize ? '本页' : ''}全部 ${records.length} 条记录`
        }}</el-button
      >
      <el-pagination
        v-if="total > pageSize"
        class="facts-pagination"
        layout="prev, pager, next"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="loadFacts"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { getCourseKnowledgePointFacts, type CourseKnowledgePointFactVO } from '@/api/course'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

const props = defineProps<{
  courseId: number
  refreshKey: number
}>()

const emit = defineEmits<{
  'open-review': [knowledgePointId: number, knowledgePointName: string]
  'open-wrong-questions': [knowledgePointId: number, knowledgePointName: string]
  'open-tutor': [knowledgePointId: number]
  'open-question-bank': []
}>()

const records = ref<CourseKnowledgePointFactVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const initialVisibleCount = 3
const showAllRecords = ref(false)
const visibleRecords = computed(() =>
  showAllRecords.value ? records.value : records.value.slice(0, initialVisibleCount),
)
const loading = ref(false)
const failed = ref(false)
let requestVersion = 0
let disposed = false

function canDeepLink(fact: CourseKnowledgePointFactVO) {
  return fact.available && fact.knowledgePointId !== null
}

function factKey(fact: CourseKnowledgePointFactVO) {
  return fact.knowledgePointId === null ? 'unlinked' : String(fact.knowledgePointId)
}

async function loadFacts(targetPage = page.value) {
  const requestedCourseId = props.courseId
  const version = ++requestVersion
  const session = getAuthSessionVersion()
  const current = () =>
    !disposed &&
    version === requestVersion &&
    requestedCourseId === props.courseId &&
    session === getAuthSessionVersion()
  loading.value = true
  failed.value = false
  try {
    const response = await getCourseKnowledgePointFacts(requestedCourseId, targetPage, pageSize.value, {
      errorDisplay: 'inline',
    })
    if (!current()) return
    const responsePageSize = response.data.size || pageSize.value
    const lastPage = Math.max(1, Math.ceil(response.data.total / responsePageSize))
    if (!response.data.records.length && response.data.total > 0 && response.data.current > lastPage) {
      await loadFacts(lastPage)
      return
    }
    const pageChanged = response.data.current !== page.value
    records.value = response.data.records
    total.value = response.data.total
    page.value = response.data.current
    pageSize.value = responsePageSize
    if (pageChanged) showAllRecords.value = false
  } catch {
    if (!current()) return
    failed.value = true
  } finally {
    if (current()) loading.value = false
  }
}

watch(
  () => [props.courseId, props.refreshKey] as const,
  ([nextCourseId], previous) => {
    const courseChanged = previous !== undefined && nextCourseId !== previous[0]
    if (courseChanged) {
      page.value = 1
      records.value = []
      total.value = 0
      showAllRecords.value = false
    }
    void loadFacts(courseChanged ? 1 : page.value)
  },
  { immediate: true },
)

const unsubscribeAuth = onAuthSessionChange(() => {
  requestVersion += 1
  records.value = []
  total.value = 0
  page.value = 1
  showAllRecords.value = false
  loading.value = false
  failed.value = false
})

onBeforeUnmount(() => {
  disposed = true
  requestVersion += 1
  unsubscribeAuth()
})
</script>

<style scoped>
.knowledge-facts-panel {
  min-width: 0;
}
.facts-skeleton {
  min-height: var(--lp-space-20);
}
.facts-empty {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.facts-toggle {
  justify-self: start;
}
.facts-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-danger-soft);
}
.facts-error p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.facts-list {
  display: grid;
  gap: var(--lp-space-3);
}
.fact-row {
  display: grid;
  gap: var(--lp-space-3);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    box-shadow var(--lp-duration-fast) var(--lp-ease-out);
}
.fact-row[data-engaged='true'] {
  border-color: var(--lp-primary-softer);
  background: linear-gradient(90deg, var(--lp-primary-soft), var(--lp-surface-soft) 34%);
}
.fact-row[data-has-unresolved-wrong='true'] {
  border-left: 3px solid var(--lp-warning);
}
@media (hover: hover) and (pointer: fine) {
  .fact-row:hover {
    box-shadow: var(--lp-shadow-xs);
  }
}
.fact-heading,
.fact-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}
.fact-heading {
  justify-content: space-between;
}
.fact-heading h3 {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-snug);
}
.fact-activity-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-full);
  background: var(--lp-ink-300);
}
.fact-row[data-engaged='true'] .fact-activity-dot {
  background: var(--lp-primary);
  box-shadow: 0 0 0 3px var(--lp-primary-soft);
}
.fact-status {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-snug);
}
.fact-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-2);
  margin: 0;
}
.fact-metrics div {
  display: grid;
  gap: var(--lp-space-1);
  min-width: 0;
  padding: var(--lp-space-2);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
}
.fact-metrics dt {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}
.fact-metrics dd {
  margin: 0;
  color: var(--lp-text);
  font-family: var(--lp-font-mono);
  font-size: var(--lp-text-xl);
  font-variant-numeric: tabular-nums;
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-tight);
}
.fact-actions {
  flex-wrap: wrap;
}
.fact-actions :deep(.el-button) {
  min-height: var(--lp-space-10);
}
.facts-pagination {
  justify-content: flex-end;
}
.fact-actions :deep(.el-button:focus-visible),
.facts-error :deep(.el-button:focus-visible) {
  box-shadow: var(--lp-shadow-focus);
}
@media (prefers-reduced-motion: reduce) {
  .fact-row {
    transition-duration: var(--lp-duration-fast);
  }
}
@media (max-width: 767px) {
  .facts-error {
    align-items: stretch;
    flex-direction: column;
  }
  .facts-error :deep(.el-button) {
    width: 100%;
  }
  .fact-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .facts-pagination {
    justify-content: center;
  }
}
</style>
