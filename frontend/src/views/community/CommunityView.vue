<template>
  <div class="page-container community-page">
    <LpPageHeader title="社区共建">
      <template #actions
        ><el-button @click="composer = 'question_bank'">贡献资料</el-button
        ><el-button type="primary" @click="composer = 'TOPIC'">发起讨论</el-button></template
      >
    </LpPageHeader>
    <div class="community-layout">
      <aside class="community-filters" aria-label="社区筛选">
        <label for="community-kind">内容</label>
        <el-select id="community-kind" v-model="filters.contentType" clearable placeholder="全部内容" @change="search">
          <el-option v-for="(label, type) in communityTypes" :key="type" :label="label" :value="type" />
        </el-select>
        <label for="community-exam">考试分类</label>
        <el-select
          id="community-exam"
          v-model="filters.examId"
          clearable
          filterable
          placeholder="全部考试"
          @change="changeExam"
        >
          <el-option v-for="exam in exams" :key="exam.id" :label="exam.name" :value="exam.id" />
        </el-select>
        <label for="community-subject">科目</label>
        <el-select
          id="community-subject"
          v-model="filters.subjectId"
          clearable
          filterable
          placeholder="全部科目"
          @change="search"
        >
          <el-option v-for="subject in subjects" :key="subject.id" :label="subject.name" :value="subject.id" />
        </el-select>
        <label for="community-school">学校分区</label>
        <el-select
          id="community-school"
          v-model="filters.schoolId"
          clearable
          filterable
          placeholder="全部学校"
          @change="search"
        >
          <el-option v-for="school in schools" :key="school.id" :label="school.name" :value="school.id" />
        </el-select>
        <el-checkbox v-model="filters.mine" @change="search">只看我的内容</el-checkbox>
        <el-select
          v-if="filters.mine"
          v-model="filters.status"
          aria-label="审核状态"
          clearable
          placeholder="全部状态"
          @change="search"
        >
          <el-option v-for="(label, status) in communityStatuses" :key="status" :label="label" :value="status" />
        </el-select>
        <router-link to="/submit" class="community-secondary-link">我的题目投稿</router-link>
      </aside>
      <section class="community-feed" aria-label="社区动态">
        <form class="community-search" @submit.prevent="search">
          <el-input
            v-model="filters.keyword"
            maxlength="120"
            clearable
            aria-label="搜索社区内容"
            placeholder="搜索标题、正文或知识点"
            @clear="search"
          /><el-button native-type="submit">搜索</el-button>
        </form>
        <LpSkeleton v-if="loading" :rows="6" />
        <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon
          ><el-button @click="initialize">重试</el-button></el-alert
        >
        <LpEmptyState
          v-else-if="!posts.length"
          title="还没有匹配的内容"
          description="换一个筛选条件，或发起第一条讨论。"
        />
        <template v-else>
          <p class="community-muted" aria-live="polite">{{ total }} 条内容</p>
          <article v-for="post in posts" :key="post.id" class="community-post-row">
            <div class="community-meta">
              <span>{{ communityTypes[post.contentType] }}</span
              ><span>{{ post.subjectName }}</span
              ><el-tag v-if="post.status !== 'APPROVED'" size="small" type="info">{{
                communityStatuses[post.status]
              }}</el-tag>
            </div>
            <h2>
              <router-link :to="`/community/${post.id}`">{{ post.title }}</router-link>
            </h2>
            <p class="community-excerpt">{{ post.body }}</p>
            <div class="community-meta">
              <span>{{ post.authorName }}</span
              ><time>{{ formatTime(post.createdAt) }}</time
              ><span v-if="post.conceptName">{{ post.conceptName }}</span
              ><span>{{ post.commentCount }} 条回复</span><span>{{ post.likeCount }} 人赞同</span>
            </div>
          </article>
          <el-pagination
            :current-page="page"
            :page-size="20"
            :total="total"
            layout="prev, pager, next"
            @current-change="changePage"
          />
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
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
import '@/assets/styles/community.css'
const router = useRouter()
const categories = ref<CommunityCategory[]>([])
const posts = ref<CommunityPost[]>([])
const filters = reactive<CommunityQuery>({ mine: false })
const page = ref(1),
  total = ref(0),
  loading = ref(true),
  error = ref(''),
  composer = ref('')
const exams = computed(() => categories.value.filter((c) => c.kind === 'EXAM'))
const subjects = computed(() =>
  categories.value.filter((c) => c.kind === 'SUBJECT' && (!filters.examId || c.parentId === filters.examId)),
)
const schools = computed(() => categories.value.filter((c) => c.kind === 'SCHOOL'))
let generation = 0
async function load() {
  const ticket = ++generation
  loading.value = true
  error.value = ''
  try {
    const res = await getCommunityPosts({
      ...filters,
      status: filters.mine ? filters.status : undefined,
      pageNum: page.value,
    })
    if (ticket === generation) {
      posts.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    if (ticket === generation) error.value = errorMessage(e, '社区内容加载失败')
  } finally {
    if (ticket === generation) loading.value = false
  }
}
async function initialize() {
  try {
    categories.value = (await getCommunityCategories()).data
    await load()
  } catch (e) {
    error.value = errorMessage(e, '分类加载失败')
    loading.value = false
  }
}
function search() {
  page.value = 1
  void load()
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
onUnmounted(() => {
  generation++
})
</script>
