<template>
  <div class="course-list page-container">
    <LpPageHeader
      kicker="课程库"
      title="课程库"
      description="先找到适合的课程，了解它的结构，再加入你的个人课程库持续学习。"
    >
      <template #actions>
        <el-input v-model="keyword" class="course-search" :prefix-icon="Search" placeholder="搜索课程" clearable />
      </template>
    </LpPageHeader>

    <template v-if="loading">
      <LpSkeleton card :rows="5" />
      <LpSkeleton card :rows="3" />
    </template>

    <template v-else-if="loadFailed">
      <section class="state-panel">
        <LpEmptyState title="暂时无法读取课程库" description="请刷新重试。">
          <template #actions>
            <el-button type="primary" @click="fetchCourses">重新加载</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>

    <template v-else>
      <section v-if="filtered408.length > 0 || keyword.trim() === ''" class="category-section">
        <LpSectionHeading
          kicker="408 计算机统考"
          title="408 计算机学科专业基础"
          description="面向全国硕士研究生招生考试计算机学科专业基础综合的课程。数据结构为完整可学习课程，其余科目在规划中。"
        />
        <div class="category-grid">
          <article
            v-for="course in filtered408"
            :key="course.id || course.name"
            class="course-card"
            :class="{ 'is-placeholder': course.placeholder }"
          >
            <div class="course-card-top">
              <span class="course-icon" :class="{ 'is-planned': course.placeholder }" aria-hidden="true">
                <el-icon :size="course.placeholder ? 18 : 21">
                  <component :is="course.placeholder ? Tools : Reading" />
                </el-icon>
              </span>
              <el-tag v-if="course.placeholder" size="small" type="info" effect="plain">规划中</el-tag>
              <el-tag v-else-if="course.complete" size="small" type="success" effect="plain">完整可学习</el-tag>
            </div>
            <h3 class="course-name">{{ course.name }}</h3>
            <p class="course-desc">{{ course.description }}</p>
            <div class="course-card-footer">
              <el-button
                v-if="!course.placeholder"
                type="primary"
                plain
                :icon="ArrowRight"
                @click="goToDetail(course.id!)"
              >
                查看课程
              </el-button>
              <span v-else class="planned-note">课程内容正在制作中</span>
            </div>
          </article>
        </div>
      </section>

      <section v-if="filteredOthers.length > 0" class="category-section">
        <LpSectionHeading kicker="其他课程" title="更多课程" :description="`共 ${filteredOthers.length} 门课程。`" />
        <div class="category-grid">
          <article v-for="course in filteredOthers" :key="course.id ?? course.name" class="course-card">
            <div class="course-card-top">
              <span class="course-icon" aria-hidden="true">
                <el-icon :size="21"><Reading /></el-icon>
              </span>
              <el-tag v-if="course.complete" size="small" type="success" effect="plain">可学习</el-tag>
            </div>
            <h3 class="course-name">{{ course.name }}</h3>
            <p class="course-desc">{{ course.description }}</p>
            <div class="course-card-footer">
              <el-button type="primary" plain :icon="ArrowRight" @click="goToDetail(course.id!)"> 查看课程 </el-button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="filtered408.length === 0 && filteredOthers.length === 0 && !loading" class="state-panel">
        <LpEmptyState title="没有匹配的课程" description="换一个关键词再试试。">
          <template #actions>
            <el-button type="primary" @click="keyword = ''">清空搜索</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Reading, Search, Tools } from '@element-plus/icons-vue'
import { getAllCourses, type CourseVO } from '@/api/course'

interface LibraryCourse {
  id: number | null
  name: string
  description: string
  placeholder?: boolean
  complete?: boolean
}

/** 规划中的 408 占位课程：视觉合理的占位，不进入可加入流程。 */
const PLACEHOLDER_408: LibraryCourse[] = [
  {
    id: null,
    name: '操作系统',
    description: '进程与线程、内存管理、文件系统、输入输出与死锁。',
    placeholder: true,
  },
  {
    id: null,
    name: '计算机组成原理',
    description: '数据的表示与运算、存储系统、指令系统与中央处理器。',
    placeholder: true,
  },
  {
    id: null,
    name: '计算机网络',
    description: '分层体系结构、TCP/IP 协议族、应用层协议与网络安全基础。',
    placeholder: true,
  },
]

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const keyword = ref('')

const is408Course = (course: CourseVO) => course.name.includes('408') || (course.contentKey || '').startsWith('cs408-')

const real408 = computed<LibraryCourse[]>(() =>
  courses.value
    .filter(is408Course)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map((course) => ({
      id: course.id,
      name: course.name,
      description: course.description,
      complete: course.name.includes('数据结构'),
    })),
)

const realOthers = computed<LibraryCourse[]>(() =>
  courses.value
    .filter((course) => !is408Course(course))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map((course) => ({
      id: course.id,
      name: course.name,
      description: course.description,
      complete: true,
    })),
)

const all408 = computed<LibraryCourse[]>(() => {
  const names = new Set(real408.value.map((course) => course.name))
  return [...real408.value, ...PLACEHOLDER_408.filter((course) => !names.has(course.name))]
})

const filtered408 = computed(() => filterByKeyword(all408.value))
const filteredOthers = computed(() => filterByKeyword(realOthers.value))

function filterByKeyword(list: LibraryCourse[]) {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return list
  return list.filter(
    (course) => course.name.toLowerCase().includes(query) || course.description.toLowerCase().includes(query),
  )
}

async function fetchCourses() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getAllCourses()
    courses.value = res.data || []
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number) {
  router.push({ name: 'CourseDetail', params: { id } })
}

onMounted(fetchCourses)
</script>

<style scoped>
.course-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-8);
}

.course-search {
  width: 260px;
}

.category-section {
  display: grid;
  gap: var(--lp-space-4);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(264px, 1fr));
  gap: var(--lp-space-4);
}

.course-card {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
  min-height: 208px;
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    box-shadow var(--lp-duration-fast) var(--lp-ease-out);
}

.course-card:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-sm);
}

.course-card.is-placeholder {
  background: var(--lp-surface-subtle);
}

.course-card.is-placeholder:hover {
  border-color: var(--lp-border);
  box-shadow: var(--lp-shadow-xs);
}

.course-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-2);
}

.course-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--lp-radius-md);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}

.course-icon.is-planned {
  background: var(--lp-surface-inset);
  color: var(--lp-text-muted);
}

.course-name {
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
  line-height: var(--lp-leading-snug);
}

.course-desc {
  margin: 0;
  flex: 1;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}

.course-card-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 36px;
}

.planned-note {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

@media (max-width: 767px) {
  .course-search {
    width: 100%;
  }
}
</style>
