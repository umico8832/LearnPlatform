<template>
  <section class="community-thread" aria-label="社区内容详情">
    <LpSkeleton v-if="loading" :rows="6" />
    <el-alert v-else-if="loadError" :title="loadError" type="error" :closable="false"
      ><el-button @click="load">重新加载</el-button></el-alert
    >
    <template v-else-if="post">
      <div class="community-meta">
        <el-tag>{{ communityTypes[post.contentType] }}</el-tag
        ><el-tag type="info">{{ communityStatuses[post.status] }}</el-tag
        ><span>{{ post.examName }} · {{ post.subjectName }}</span
        ><span v-if="post.schoolName">{{ post.schoolName }}</span>
      </div>
      <h1>{{ post.title }}</h1>
      <div class="community-meta">
        <span>{{ post.authorName }}</span
        ><time>{{ formatTime(post.createdAt) }}</time
        ><span v-if="post.conceptName">{{ post.conceptName }}</span>
      </div>
      <p class="community-body">{{ post.body }}</p>
      <p v-if="post.courseName" class="community-muted">关联课程：{{ post.courseName }}</p>
      <section v-if="post.sourceNote" class="community-source">
        <h2>来源与使用许可</h2>
        <p class="community-body">{{ post.sourceNote }}</p>
      </section>
      <section v-if="post.attachments.length" class="community-files">
        <h2>题库附件</h2>
        <ul>
          <li v-for="file in post.attachments" :key="file.id">
            <span>{{ file.name }} · {{ formatBytes(file.sizeBytes) }}</span
            ><el-button :loading="downloading === file.id" :disabled="downloading !== null" @click="download(file)"
              >下载</el-button
            >
          </li>
        </ul>
      </section>
      <el-alert v-if="post.reviewNote" :title="`审核意见：${post.reviewNote}`" type="info" :closable="false" />
      <details v-if="reviews.length" class="community-source">
        <summary>审核记录</summary>
        <p v-for="review in reviews" :key="review.id">
          {{ formatTime(review.createdAt) }} · {{ review.reviewerName }} · {{ communityStatuses[review.decision] }}：{{
            review.note
          }}
        </p>
      </details>
      <div class="community-actions">
        <el-button
          :type="post.liked ? 'primary' : 'default'"
          :disabled="post.status !== 'APPROVED' || busy"
          :aria-pressed="post.liked"
          @click="like()"
          >{{ post.liked ? '已赞同' : '赞同' }} · {{ post.likeCount }}</el-button
        >
        <el-button v-if="canManage" type="danger" plain :disabled="busy" @click="removePost">删除内容</el-button>
      </div>
      <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" show-icon />
      <slot name="moderation" :post="post" :reload="load" />
      <section v-if="post.questionLinks.length" class="community-source">
        <h2>题目整理进度</h2>
        <p v-for="link in post.questionLinks" :key="link.submissionId">
          投稿 #{{ link.submissionId }} · {{ ['待审核', '已通过', '已拒绝', '已入库'][link.status]
          }}<span v-if="link.questionId"> · 题目 #{{ link.questionId }}</span>
        </p>
      </section>
      <section class="community-comments" aria-labelledby="comments-title">
        <h2 id="comments-title">讨论 · {{ post.commentCount }}</h2>
        <el-alert v-if="commentsError" :title="commentsError" type="error" :closable="false"
          ><el-button @click="loadComments">重试</el-button></el-alert
        >
        <form v-if="post.status === 'APPROVED'" @submit.prevent="submitReply">
          <p v-if="replyTo" class="community-meta">
            回复 {{ replyTo.authorName }}<el-button link @click="replyTo = null">取消回复</el-button>
          </p>
          <el-input
            ref="replyInput"
            v-model="reply"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            aria-label="讨论内容"
            placeholder="分享你的思路，或提出一个具体问题"
          />
          <div class="community-actions">
            <el-button type="primary" native-type="submit" :loading="busy" :disabled="!reply.trim()"
              >发表回复</el-button
            >
          </div>
        </form>
        <p v-if="!comments.length && !commentsError" class="community-muted">还没有回复。</p>
        <article v-for="comment in comments" :key="comment.id" class="community-comment">
          <div class="community-meta">
            <strong>{{ comment.authorName }}</strong
            ><span v-if="comment.replyToName">回复 {{ comment.replyToName }}</span
            ><time>{{ formatTime(comment.createdAt) }}</time>
          </div>
          <p class="community-body">{{ comment.deleted ? '该回复已删除' : comment.body }}</p>
          <div v-if="!comment.deleted" class="community-actions">
            <el-button link :disabled="busy || post.status !== 'APPROVED'" @click="beginReply(comment)">回复</el-button>
            <el-button
              link
              :type="comment.liked ? 'primary' : 'default'"
              :disabled="busy || post.status !== 'APPROVED'"
              :aria-pressed="comment.liked"
              @click="like(comment)"
              >{{ comment.liked ? '已赞同' : '赞同' }} · {{ comment.likeCount }}</el-button
            >
            <el-button
              v-if="comment.userId === user.userInfo?.id || user.userInfo?.role === 'ADMIN'"
              link
              type="danger"
              :disabled="busy"
              @click="removeComment(comment)"
              >删除回复</el-button
            >
          </div>
        </article>
        <el-pagination
          v-if="commentTotal > 20"
          :current-page="commentPage"
          :total="commentTotal"
          :page-size="20"
          layout="prev, pager, next"
          @current-change="pageComments"
        />
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { ElMessageBox, type InputInstance } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getCommunityPost,
  getCommunityComments,
  getCommunityReviews,
  setCommunityLike,
  deleteCommunityPost,
  deleteCommunityComment,
  addCommunityComment,
  downloadCommunityAttachment,
  communityTypes,
  communityStatuses,
  type CommunityPost,
  type CommunityComment,
  type CommunityAttachment,
  type CommunityReview,
} from '@/api/community'
import { formatTime } from '@/utils/format'
import { errorMessage } from '@/utils/errors'
const props = defineProps<{ id: number }>()
const emit = defineEmits<{ deleted: []; changed: [] }>()
const user = useUserStore()
const post = ref<CommunityPost | null>(null),
  comments = ref<CommunityComment[]>([]),
  reviews = ref<CommunityReview[]>([])
const loading = ref(true),
  busy = ref(false),
  loadError = ref(''),
  actionError = ref(''),
  commentsError = ref(''),
  reply = ref('')
const downloading = ref<number | null>(null),
  replyTo = ref<CommunityComment | null>(null),
  replyInput = ref<InputInstance>()
const commentPage = ref(1),
  commentTotal = ref(0)
const canManage = computed(() => post.value?.userId === user.userInfo?.id || user.userInfo?.role === 'ADMIN')
let generation = 0,
  commentGeneration = 0
async function loadComments() {
  const ticket = ++commentGeneration,
    id = props.id
  commentsError.value = ''
  try {
    const res = await getCommunityComments(id, commentPage.value)
    if (ticket === commentGeneration && id === props.id) {
      comments.value = res.data.records
      commentTotal.value = res.data.total
    }
  } catch (e) {
    if (ticket === commentGeneration) commentsError.value = errorMessage(e, '回复加载失败')
  }
}
async function load() {
  const ticket = ++generation,
    id = props.id
  loading.value = true
  loadError.value = ''
  reviews.value = []
  try {
    const result = await getCommunityPost(id)
    if (ticket !== generation) return
    post.value = result.data
    await loadComments()
    if (canManage.value) {
      const history = await getCommunityReviews(id)
      if (ticket === generation) reviews.value = history.data
    }
  } catch (e) {
    if (ticket === generation) loadError.value = errorMessage(e, '内容加载失败或已不可访问')
  } finally {
    if (ticket === generation) loading.value = false
  }
}
async function action(fn: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  actionError.value = ''
  const id = props.id
  try {
    await fn()
    if (id === props.id) {
      await load()
      emit('changed')
    }
  } catch (e) {
    if (id === props.id) actionError.value = errorMessage(e, '操作失败，请重试')
  } finally {
    busy.value = false
  }
}
async function like(comment?: CommunityComment) {
  if (post.value) await action(() => setCommunityLike(props.id, !(comment?.liked ?? post.value!.liked), comment?.id))
}
async function submitReply() {
  if (!reply.value.trim()) return
  await action(async () => {
    await addCommunityComment(props.id, reply.value, replyTo.value?.id)
    reply.value = ''
    replyTo.value = null
    commentPage.value = Math.ceil((commentTotal.value + 1) / 20)
  })
}
async function beginReply(comment: CommunityComment) {
  replyTo.value = comment
  await nextTick()
  replyInput.value?.focus()
}
async function removeComment(comment: CommunityComment) {
  const id = props.id
  try {
    await ElMessageBox.confirm('删除后不能恢复。', '删除这条回复？', { type: 'warning' })
  } catch {
    return
  }
  if (id === props.id) await action(() => deleteCommunityComment(comment.id))
}
async function removePost() {
  const id = props.id
  try {
    await ElMessageBox.confirm('正文和附件将不再可访问，已整理的题目投稿会保留。', '删除这条内容？', {
      type: 'warning',
    })
  } catch {
    return
  }
  if (id !== props.id) return
  busy.value = true
  try {
    await deleteCommunityPost(id)
    emit('deleted')
  } catch (e) {
    actionError.value = errorMessage(e, '删除失败')
  } finally {
    busy.value = false
  }
}
async function download(file: CommunityAttachment) {
  downloading.value = file.id
  try {
    await downloadCommunityAttachment(file)
  } catch (e) {
    actionError.value = errorMessage(e, '下载失败')
  } finally {
    downloading.value = null
  }
}
function formatBytes(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
function pageComments(page: number) {
  commentPage.value = page
  void loadComments()
}
watch(
  () => props.id,
  () => {
    reply.value = ''
    replyTo.value = null
    post.value = null
    comments.value = []
    commentPage.value = 1
    void load()
  },
  { immediate: true },
)
onUnmounted(() => {
  generation++
  commentGeneration++
})
defineExpose({ reload: load })
</script>
