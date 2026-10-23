<template>
  <LtCard
    as="section"
    radius="xl"
    class="lt panel knowledge-facts-panel"
    data-appearance="dark"
    aria-labelledby="knowledge-facts-heading"
  >
    <div class="facts-heading">
      <h2 id="knowledge-facts-heading">知识点学习记录</h2>
      <LtButton variant="quiet" @click="helpOpen = true">查看统计口径</LtButton>
    </div>
    <LtSheet v-model:open="helpOpen" title="统计口径"
      ><p>作答与答对按次数统计，错题与复习按题目统计；一道题可能计入多个知识点。</p></LtSheet
    >

    <div v-if="loading" class="facts-skeleton" aria-label="正在加载知识点学习事实">
      <p role="status">正在加载知识点学习事实</p>
    </div>
    <div v-else-if="failed" class="facts-error" role="alert">
      <p>暂时无法读取知识点学习事实，请重试。</p>
      <LtButton variant="quiet" @click="loadFacts(page)">重新加载</LtButton>
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
            <span v-if="!fact.available" class="fact-badge">
              {{ fact.knowledgePointId === null ? '未关联' : '历史记录' }}
            </span>
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
              <LtButton
                v-if="fact.dueReviewCount"
                variant="quiet"
                @click="emit('open-review', fact.knowledgePointId!, fact.knowledgePointName)"
              >
                复习
              </LtButton>
              <LtButton
                v-if="fact.unresolvedWrongCount"
                variant="quiet"
                @click="emit('open-wrong-questions', fact.knowledgePointId!, fact.knowledgePointName)"
              >
                错题
              </LtButton>
            </template>
            <LtButton
              v-if="fact.tutorAvailable && fact.knowledgePointId !== null"
              variant="quiet"
              @click="emit('open-tutor', fact.knowledgePointId)"
            >
              进入教学
            </LtButton>
            <LtButton v-if="!canDeepLink(fact)" variant="quiet" @click="emit('open-question-bank')">
              查看课程题库
            </LtButton>
          </div>
        </article>
      </div>
      <LtButton
        v-if="records.length > initialVisibleCount"
        class="facts-toggle"
        :aria-expanded="showAllRecords"
        @click="showAllRecords = !showAllRecords"
        >{{
          showAllRecords ? '收起记录' : `展开${total > pageSize ? '本页' : ''}全部 ${records.length} 条记录`
        }}</LtButton
      >
      <nav v-if="total > pageSize" class="facts-pagination" aria-label="知识点学习记录翻页">
        <LtButton :disabled="page <= 1" @click="loadFacts(page - 1)">上一页</LtButton
        ><span>第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页</span
        ><LtButton :disabled="page >= Math.ceil(total / pageSize)" @click="loadFacts(page + 1)">下一页</LtButton>
      </nav>
    </template>
  </LtCard>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LtButton, LtCard, LtSheet } from '@/components/living'
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

const helpOpen = ref(false)
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
.facts-heading,
.fact-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.facts-heading {
  margin-bottom: var(--lt-space-4);
}
h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
h3 {
  margin: 0;
  font-size: var(--lt-text-headline);
}
p {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.facts-error {
  color: var(--lt-red-text);
}
.facts-list {
  display: grid;
}
.fact-row {
  display: grid;
  gap: var(--lt-space-3);
  padding: var(--lt-space-4) 0;
}
.fact-row + .fact-row {
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.fact-status {
  margin: var(--lt-space-1) 0 0;
  font-size: var(--lt-text-footnote);
}
.fact-badge {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.fact-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-5);
  margin: 0;
}
.fact-metrics div {
  display: flex;
  align-items: baseline;
  gap: var(--lt-space-2);
}
.fact-metrics dt {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}
.fact-metrics dd {
  margin: 0;
  font-family: var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
.fact-actions,
.facts-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-2);
}
.facts-pagination {
  justify-content: center;
  margin-top: var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.facts-toggle {
  margin-top: var(--lt-space-3);
}
</style>
