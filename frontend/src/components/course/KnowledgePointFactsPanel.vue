<template>
  <section class="panel knowledge-facts-panel" aria-label="知识点学习事实">
    <LpSectionHeading title="知识点学习记录">
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
    <LpEmptyState
      v-else-if="!records.length"
      compact
      title="暂无知识点学习记录"
      description="完成课程内作答后，这里会按知识点展示对应记录。"
    />
    <template v-else>
      <div class="facts-list">
        <article v-for="fact in records" :key="factKey(fact)" class="fact-row">
          <div class="fact-heading">
            <div>
              <h3>{{ fact.knowledgePointName }}</h3>
              <p v-if="!fact.available" class="fact-status">
                {{ fact.knowledgePointId === null ? '未关联知识点的课程记录' : '历史知识点记录' }}
              </p>
            </div>
            <el-tag v-if="!fact.available" size="small" type="info" effect="plain">
              {{ fact.knowledgePointId === null ? '未关联' : '历史记录' }}
            </el-tag>
          </div>
          <dl class="fact-metrics">
            <div>
              <dt>作答次数</dt>
              <dd>{{ fact.answeredCount }}</dd>
            </div>
            <div>
              <dt>答对次数</dt>
              <dd>{{ fact.correctCount }}</dd>
            </div>
            <div>
              <dt>未处理错题</dt>
              <dd>{{ fact.unresolvedWrongCount }}</dd>
            </div>
            <div>
              <dt>到期复习题</dt>
              <dd>{{ fact.dueReviewCount }}</dd>
            </div>
          </dl>
          <div class="fact-actions">
            <template v-if="canDeepLink(fact)">
              <el-button
                text
                type="primary"
                @click="emit('open-review', fact.knowledgePointId!, fact.knowledgePointName)"
              >
                复习
              </el-button>
              <el-button
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
import { onBeforeUnmount, ref, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { getCourseKnowledgePointFacts, type CourseKnowledgePointFactVO } from '@/api/course'

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
  loading.value = true
  failed.value = false
  try {
    const response = await getCourseKnowledgePointFacts(requestedCourseId, targetPage, pageSize.value)
    if (disposed || version !== requestVersion || requestedCourseId !== props.courseId) return
    const responsePageSize = response.data.size || pageSize.value
    const lastPage = Math.max(1, Math.ceil(response.data.total / responsePageSize))
    if (!response.data.records.length && response.data.total > 0 && response.data.current > lastPage) {
      await loadFacts(lastPage)
      return
    }
    records.value = response.data.records
    total.value = response.data.total
    page.value = response.data.current
    pageSize.value = responsePageSize
  } catch {
    if (disposed || version !== requestVersion || requestedCourseId !== props.courseId) return
    failed.value = true
  } finally {
    if (!disposed && version === requestVersion && requestedCourseId === props.courseId) loading.value = false
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
    }
    void loadFacts(courseChanged ? 1 : page.value)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  requestVersion += 1
})
</script>

<style scoped>
.knowledge-facts-panel {
  min-width: 0;
}
.facts-skeleton {
  min-height: var(--lp-space-20);
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
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-snug);
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
