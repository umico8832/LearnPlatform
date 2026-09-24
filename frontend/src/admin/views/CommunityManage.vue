<template>
  <div class="page-container">
    <LpPageHeader title="社区管理"
      ><template #actions
        ><el-button @click="schoolDialog = true">新增学校分区</el-button
        ><router-link to="/submissions"><el-button>题目投稿管理</el-button></router-link></template
      ></LpPageHeader
    >
    <form class="community-search" @submit.prevent="search">
      <el-select v-model="status" aria-label="审核状态" clearable placeholder="全部状态" @change="search"
        ><el-option v-for="(label, value) in communityStatuses" :key="value" :value="value" :label="label" /></el-select
      ><el-input
        v-model="keyword"
        maxlength="120"
        clearable
        aria-label="搜索社区内容"
        placeholder="搜索社区内容"
      /><el-button native-type="submit">查询</el-button><el-button @click="load">刷新</el-button>
    </form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="posts" empty-text="暂无社区内容">
      <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip />
      <el-table-column label="作者" prop="authorName" width="140" />
      <el-table-column label="类型" width="120"
        ><template #default="{ row }">{{ communityTypes[row.contentType] }}</template></el-table-column
      >
      <el-table-column label="科目" prop="subjectName" width="160" />
      <el-table-column label="状态" width="110"
        ><template #default="{ row }">{{ communityStatuses[row.status] }}</template></el-table-column
      >
      <el-table-column label="操作" width="140"
        ><template #default="{ row }"
          ><el-button type="primary" link @click="selected = row.id">查看与审核</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      :current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="changePage"
    />
    <el-drawer
      :model-value="selected !== null"
      title="社区内容审核"
      size="840px"
      destroy-on-close
      :close-on-click-modal="!reviewing"
      :close-on-press-escape="!reviewing"
      :show-close="!reviewing"
      @close="selected = null"
    >
      <CommunityThread
        v-if="selected !== null"
        :key="selected"
        ref="thread"
        :id="selected"
        @changed="load"
        @deleted="onDeleted"
      >
        <template #moderation="{ post, reload }">
          <section class="community-moderation">
            <h2>内容审核</h2>
            <el-input
              v-model="note"
              type="textarea"
              :rows="3"
              maxlength="500"
              aria-label="审核意见"
              placeholder="记录来源、使用许可与内容核验结果"
              :disabled="reviewing"
            />
            <el-alert v-if="reviewError" :title="reviewError" type="error" :closable="false" />
            <div class="community-actions">
              <el-button
                v-if="post.status !== 'APPROVED'"
                type="primary"
                :disabled="!note.trim() || reviewing"
                @click="review(post, 'APPROVED', reload)"
                >通过并公开</el-button
              >
              <el-button
                v-if="post.status !== 'REJECTED'"
                type="danger"
                plain
                :disabled="!note.trim() || reviewing"
                @click="review(post, 'REJECTED', reload)"
                >驳回</el-button
              >
              <el-button
                v-if="post.status === 'APPROVED'"
                :disabled="!note.trim() || reviewing"
                @click="review(post, 'HIDDEN', reload)"
                >隐藏内容</el-button
              >
              <el-button
                v-if="post.contentType === 'question_bank' && post.status === 'APPROVED'"
                :disabled="reviewing"
                @click="questionPost = post"
                >整理题目</el-button
              >
            </div>
          </section>
        </template>
      </CommunityThread>
    </el-drawer>
    <CommunityQuestionForm
      v-if="questionPost"
      :post="questionPost"
      @close="questionPost = null"
      @saved="questionSaved"
    />
    <el-dialog v-model="schoolDialog" title="新增学校分区" width="480px" :close-on-click-modal="false"
      ><el-form label-position="top"
        ><el-form-item label="学校名称" required><el-input v-model="schoolName" maxlength="80" /></el-form-item
        ><el-form-item label="分区说明"
          ><el-input v-model="schoolDescription" maxlength="240" type="textarea" /></el-form-item></el-form
      ><el-alert v-if="schoolError" :title="schoolError" type="error" :closable="false" /><template #footer
        ><el-button type="primary" :loading="savingSchool" :disabled="!schoolName.trim()" @click="saveSchool"
          >创建分区</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCommunityPosts,
  reviewCommunityPost,
  createCommunitySchool,
  communityStatuses,
  communityTypes,
  type CommunityPost,
} from '@/api/community'
import CommunityThread from '@/components/community/CommunityThread.vue'
import CommunityQuestionForm from './community/CommunityQuestionForm.vue'
import { errorMessage } from '@/utils/errors'
import '@/assets/styles/community.css'
const posts = ref<CommunityPost[]>([]),
  status = ref('PENDING'),
  keyword = ref(''),
  page = ref(1),
  total = ref(0)
const loading = ref(false),
  error = ref(''),
  selected = ref<number | null>(null),
  note = ref(''),
  reviewing = ref(false),
  reviewError = ref('')
const thread = ref<InstanceType<typeof CommunityThread>>(),
  questionPost = ref<CommunityPost | null>(null)
const schoolDialog = ref(false),
  schoolName = ref(''),
  schoolDescription = ref(''),
  schoolError = ref(''),
  savingSchool = ref(false)
let generation = 0
async function load() {
  const ticket = ++generation
  loading.value = true
  error.value = ''
  try {
    const res = await getCommunityPosts({ pageNum: page.value, keyword: keyword.value, status: status.value }, true)
    if (ticket === generation) {
      posts.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    if (ticket === generation) error.value = errorMessage(e, '社区列表加载失败')
  } finally {
    if (ticket === generation) loading.value = false
  }
}
function search() {
  page.value = 1
  void load()
}
function changePage(value: number) {
  page.value = value
  void load()
}
async function review(post: CommunityPost, decision: string, reload: () => Promise<void>) {
  const id = post.id,
    text = note.value.trim()
  if (reviewing.value || !text) return
  reviewing.value = true
  reviewError.value = ''
  try {
    await ElMessageBox.confirm(`将“${post.title}”设为${communityStatuses[decision]}。`, '确认审核', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    if (selected.value !== id) return
    await reviewCommunityPost(id, decision, text)
    note.value = ''
    await reload()
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      reviewError.value = errorMessage(e, '审核失败，请刷新后重试')
      await reload()
    }
  } finally {
    reviewing.value = false
  }
}
async function saveSchool() {
  if (savingSchool.value) return
  savingSchool.value = true
  schoolError.value = ''
  try {
    await createCommunitySchool(schoolName.value, schoolDescription.value)
    schoolDialog.value = false
    schoolName.value = ''
    schoolDescription.value = ''
    ElMessage.success('学校分区已创建')
  } catch (e) {
    schoolError.value = errorMessage(e, '创建失败')
  } finally {
    savingSchool.value = false
  }
}
function onDeleted() {
  selected.value = null
  void load()
}
async function questionSaved() {
  questionPost.value = null
  await thread.value?.reload()
  ElMessage.success('已保存，请在题目投稿管理中审核与入库')
}
watch(selected, () => {
  note.value = ''
  reviewError.value = ''
})
onMounted(load)
onUnmounted(() => {
  generation++
})
</script>
