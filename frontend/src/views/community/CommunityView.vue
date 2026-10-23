<template>
  <div class="community-page lt" data-appearance="dark" aria-labelledby="community-title">
    <LtPageHeader title="社区共建" title-id="community-title">
      <template #actions>
        <LtButton :disabled="categoriesLoading || !!categoryError" @click="composer = 'question_bank'"
          ><template #icon><FilePlus aria-hidden="true" /></template>贡献资料</LtButton
        >
        <LtButton variant="primary" :disabled="categoriesLoading || !!categoryError" @click="composer = 'TOPIC'"
          ><template #icon><MessageSquarePlus aria-hidden="true" /></template>发起讨论</LtButton
        >
      </template>
    </LtPageHeader>
    <div class="community-layout">
      <aside class="filters" aria-label="社区筛选">
        <div class="filters-head">
          <h2>筛选内容</h2>
          <RouterLink to="/submit">我的题目投稿</RouterLink>
        </div>
        <p v-if="categoriesLoading" class="quiet" role="status">正在读取分类…</p>
        <div v-else-if="categoryError" class="notice" role="alert">
          <p>{{ categoryError }}</p>
          <LtButton size="sm" @click="loadCategories">重试分类</LtButton>
        </div>
        <template v-else>
          <label class="select-field" for="community-kind"
            ><span>内容</span
            ><select id="community-kind" v-model="filters.contentType" @change="search">
              <option value="">全部内容</option>
              <option v-for="(label, type) in communityTypes" :key="type" :value="type">{{ label }}</option>
            </select></label
          >
          <label class="select-field" for="community-exam"
            ><span>考试分类</span
            ><select id="community-exam" v-model="filters.examId" @change="changeExam">
              <option value="">全部考试</option>
              <option v-for="exam in exams" :key="exam.id" :value="exam.id">{{ exam.name }}</option>
            </select></label
          >
          <label class="select-field" for="community-subject"
            ><span>科目</span
            ><select id="community-subject" v-model="filters.subjectId" @change="search">
              <option value="">全部科目</option>
              <option v-for="subject in subjects" :key="subject.id" :value="subject.id">{{ subject.name }}</option>
            </select></label
          >
          <label class="select-field" for="community-school"
            ><span>学校分区</span
            ><select id="community-school" v-model="filters.schoolId" @change="search">
              <option value="">全部学校</option>
              <option v-for="school in schools" :key="school.id" :value="school.id">{{ school.name }}</option>
            </select></label
          >
          <label class="check-field"
            ><input v-model="filters.mine" type="checkbox" @change="search" /><span>只看我的内容</span></label
          >
          <label v-if="filters.mine" class="select-field" for="community-status"
            ><span>审核状态</span
            ><select id="community-status" v-model="filters.status" @change="search">
              <option value="">全部状态</option>
              <option v-for="(label, status) in communityStatuses" :key="status" :value="status">{{ label }}</option>
            </select></label
          >
        </template>
      </aside>
      <section class="feed" aria-label="社区动态">
        <form class="search" @submit.prevent="search">
          <label class="lt-sr-only" for="community-query">搜索社区内容</label><Search aria-hidden="true" /><input
            id="community-query"
            v-model="filters.keyword"
            type="search"
            maxlength="120"
            placeholder="搜索标题、正文或知识点"
            @search="search"
          /><LtButton v-if="filters.keyword" type="button" variant="quiet" @click="clearSearch">清空搜索</LtButton
          ><LtButton type="submit">搜索</LtButton>
        </form>
        <div v-if="loading" class="feed-skeleton" aria-busy="true">
          <span v-for="n in 6" :key="n" class="skeleton" /><span class="lt-sr-only" role="status"
            >正在读取社区内容</span
          >
        </div>
        <div v-else-if="error" class="state error" role="alert">
          <h2>内容没有加载出来</h2>
          <p>{{ error }}</p>
          <LtButton variant="primary" @click="load"
            ><template #icon><RotateCcw aria-hidden="true" /></template>重新加载</LtButton
          >
        </div>
        <div v-else-if="!posts.length" class="state">
          <MessageCircleQuestion aria-hidden="true" />
          <h2>还没有匹配的内容</h2>
          <p>换一个筛选条件，或发起第一条讨论。</p>
        </div>
        <template v-else>
          <p class="result-count" aria-live="polite">{{ total }} 条内容</p>
          <ol class="post-list">
            <li v-for="post in posts" :key="post.id">
              <article class="post">
                <div class="post-tags">
                  <span>{{ communityTypes[post.contentType] || post.contentType }}</span
                  ><span v-if="post.status !== 'APPROVED'" data-status="attention">{{
                    communityStatuses[post.status] || post.status
                  }}</span>
                </div>
                <h2>
                  <RouterLink :to="`/community/${post.id}`">{{ post.title }}</RouterLink>
                </h2>
                <p class="excerpt">{{ post.body }}</p>
                <footer>
                  <span>{{ post.authorName }}</span
                  ><time :datetime="post.createdAt">{{ formatTime(post.createdAt) }}</time
                  ><span v-if="post.subjectName">{{ post.subjectName }}</span
                  ><span v-if="post.conceptName">{{ post.conceptName }}</span
                  ><span>{{ post.commentCount }} 条回复</span><span>{{ post.likeCount }} 人赞同</span>
                </footer>
              </article>
            </li>
          </ol>
          <nav v-if="total > 20" class="pager" aria-label="社区分页">
            <LtButton size="sm" :disabled="page <= 1" @click="changePage(page - 1)">上一页</LtButton
            ><span>{{ page }}</span
            ><LtButton size="sm" :disabled="posts.length < 20" @click="changePage(page + 1)">下一页</LtButton>
          </nav>
        </template>
      </section>
    </div>
    <CommunityComposer
      v-if="composer"
      :initial-type="composer"
      :categories="categories"
      @close="composer = ''"
      @created="created"
    />
  </div>
</template>

<script setup lang="ts">
import LtPageHeader from '@/components/living/LtPageHeader.vue'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { FilePlus, MessageCircleQuestion, MessageSquarePlus, RotateCcw, Search } from '@lucide/vue'
import { LtButton } from '@/components/living'
import {
  getCommunityCategories,
  getCommunityPosts,
  communityTypes,
  communityStatuses,
  type CommunityCategory,
  type CommunityPost,
  type CommunityQuery,
} from '@/api/community'
import CommunityComposer from '@/components/community/CommunityComposer.vue'
import { errorMessage } from '@/utils/errors'
import { formatTime } from '@/utils/format'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
const router = useRouter()
const categories = ref<CommunityCategory[]>([])
const posts = ref<CommunityPost[]>([])
const filters = reactive<CommunityQuery>({ mine: false })
const categoryError = ref('')
const categoriesLoading = ref(false)
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const error = ref('')
const composer = ref('')
const exams = computed(() => categories.value.filter((c) => c.kind === 'EXAM'))
const subjects = computed(() =>
  categories.value.filter((c) => c.kind === 'SUBJECT' && (!filters.examId || c.parentId === filters.examId)),
)
const schools = computed(() => categories.value.filter((c) => c.kind === 'SCHOOL'))
let generation = 0
let categoryGeneration = 0
let alive = true
function current(ticket: number, session: number) {
  return alive && ticket === generation && session === getAuthSessionVersion() && isAuthenticated()
}
async function load() {
  const ticket = ++generation
  const session = getAuthSessionVersion()
  loading.value = true
  error.value = ''
  try {
    const res = await getCommunityPosts(
      { ...filters, status: filters.mine ? filters.status : undefined, pageNum: page.value },
      false,
      { errorDisplay: 'inline' },
    )
    if (current(ticket, session)) {
      posts.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    if (current(ticket, session)) error.value = errorMessage(e, '社区内容加载失败')
  } finally {
    if (current(ticket, session)) loading.value = false
  }
}
async function loadCategories() {
  const request = ++categoryGeneration
  const session = getAuthSessionVersion()
  categoriesLoading.value = true
  categoryError.value = ''
  try {
    const result = await getCommunityCategories({ errorDisplay: 'inline' })
    if (alive && request === categoryGeneration && session === getAuthSessionVersion()) categories.value = result.data
  } catch (e) {
    if (alive && request === categoryGeneration && session === getAuthSessionVersion())
      categoryError.value = errorMessage(e, '分类暂时无法读取，请重试后筛选或发布。')
  } finally {
    if (alive && request === categoryGeneration && session === getAuthSessionVersion()) categoriesLoading.value = false
  }
}
function initialize() {
  void loadCategories()
  void load()
}
function search() {
  page.value = 1
  void load()
}
function clearSearch() {
  filters.keyword = undefined
  search()
}
function changeExam() {
  filters.subjectId = undefined
  search()
}
function changePage(value: number) {
  page.value = value
  void load()
}
function created(id: number) {
  composer.value = ''
  void router.push(`/community/${id}`)
}
onMounted(initialize)
const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  categoryGeneration++
  composer.value = ''
  categoryError.value = ''
  categoriesLoading.value = false
  categories.value = []
  posts.value = []
  total.value = 0
  error.value = ''
  if (isAuthenticated()) void initialize()
})
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>

<style scoped>
.result-count {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
}
.community-layout {
  display: grid;
  grid-template-columns: calc(var(--lt-space-10) * 4) minmax(0, 1fr);
  gap: var(--lt-space-8);
  align-items: start;
}
.filters {
  display: grid;
  gap: var(--lt-space-4);
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.filters-head {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-2);
  align-items: baseline;
}
.filters h2 {
  margin: 0;
  font-size: var(--lt-text-headline);
}
.filters a {
  color: var(--lt-accent);
  font-size: var(--lt-text-footnote);
}
.select-field {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.select-field select {
  width: 100%;
  height: var(--lt-control-lg);
  padding: 0 var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-md);
  outline: 0;
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 1px var(--lt-separator);
  color: var(--lt-label);
  font: inherit;
}
.select-field select:focus-visible {
  box-shadow: inset 0 0 0 2px var(--lt-accent);
}
.check-field {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.check-field input {
  accent-color: var(--lt-accent);
}
.quiet {
  margin: 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}
.notice {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.notice p {
  margin: 0 0 var(--lt-space-3);
  font-size: var(--lt-text-footnote);
}
.feed {
  min-width: 0;
}
.search {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  margin-bottom: var(--lt-space-5);
  padding: var(--lt-space-2) var(--lt-space-2) var(--lt-space-2) var(--lt-space-4);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
  color: var(--lt-label-3);
}
.search input {
  flex: 1;
  min-width: 0;
  height: var(--lt-control-lg);
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--lt-label);
  font: var(--lt-text-body)/1 var(--lt-font);
}
.search input::placeholder {
  color: var(--lt-label-3);
}
.post-list {
  margin: var(--lt-space-3) 0 0;
  padding: 0;
  list-style: none;
}
.post-list > li + li {
  border-top: 1px solid var(--lt-separator);
}
.post {
  padding: var(--lt-space-5) var(--lt-space-4);
}
.post-tags,
.post footer {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
.post-tags span {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.post-tags [data-status='attention'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.post h2 {
  margin: var(--lt-space-3) 0 var(--lt-space-2);
  font-size: var(--lt-text-title3);
}
.post h2 a {
  color: var(--lt-label);
  text-decoration: none;
}
.post h2 a:hover {
  color: var(--lt-accent);
}
.excerpt {
  display: -webkit-box;
  margin: 0 0 var(--lt-space-3);
  overflow: hidden;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-9) var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.state h2,
.state p {
  margin: 0;
}
.state p {
  color: var(--lt-label-2);
}
.state.error {
  color: var(--lt-red-text);
}
.feed-skeleton {
  display: grid;
  gap: var(--lt-space-3);
}
.skeleton {
  display: block;
  height: var(--lt-space-9);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-fill-2);
}
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-6);
  color: var(--lt-label-2);
  font: var(--lt-text-footnote)/1 var(--lt-font-rounded);
}
@media (max-width: 720px) {
  .community-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
