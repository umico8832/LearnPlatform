<template>
  <section class="discussion" aria-label="讨论区">
    <h3>
      讨论区 <span v-if="!loading && !loadError">{{ totalComments }} 条</span>
    </h3>
    <form @submit.prevent="submitComment">
      <label :for="`comment-${questionId}`">{{
        replyTarget ? `回复 ${replyTarget.nickname}` : '分享你的解题思路或疑问'
      }}</label
      ><textarea
        :id="`comment-${questionId}`"
        v-model="newComment"
        rows="2"
        maxlength="2000"
        :disabled="submitting"
        :placeholder="replyTarget ? `回复 ${replyTarget.nickname}...` : '分享你的解题思路或疑问...'"
      />
      <div class="actions">
        <LtButton v-if="replyTarget" @click="cancelReply">取消回复</LtButton
        ><LtButton type="submit" variant="primary" :disabled="submitting">{{
          submitting ? '正在提交…' : replyTarget ? '回复' : '发表评论'
        }}</LtButton>
      </div>
    </form>
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loading" role="status" aria-busy="true">正在加载讨论…</p>
    <div v-else-if="loadError" role="alert">
      <p>{{ loadError }}</p>
      <LtButton @click="fetchComments">重试</LtButton>
    </div>
    <p v-else-if="!comments.length">暂无讨论</p>
    <article v-for="comment in comments" v-else :key="comment.id" class="comment-item">
      <header>
        <strong>{{ comment.nickname }}</strong
        ><time>{{ formatTime(comment.createTime) }}</time>
      </header>
      <p class="comment-text">{{ comment.content }}</p>
      <div class="actions">
        <LtButton size="sm" :aria-pressed="comment.likedByMe" @click="handleLike(comment)">{{
          comment.likeCount > 0 ? comment.likeCount : '点赞'
        }}</LtButton
        ><LtButton size="sm" @click="startReply(comment)">回复</LtButton
        ><LtButton v-if="isOwner(comment.userId)" size="sm" @click="handleDelete(comment)">删除</LtButton>
      </div>
      <div v-if="comment.replies?.length" class="replies-list">
        <article v-for="reply in comment.replies" :key="reply.id" class="reply-item">
          <header>
            <strong>{{ reply.nickname }}</strong
            ><span v-if="reply.replyToNickname">回复 {{ reply.replyToNickname }}</span
            ><time>{{ formatTime(reply.createTime) }}</time>
          </header>
          <p class="comment-text">{{ reply.content }}</p>
          <div class="actions">
            <LtButton size="sm" :aria-pressed="reply.likedByMe" @click="handleLike(reply)">{{
              reply.likeCount > 0 ? reply.likeCount : '点赞'
            }}</LtButton
            ><LtButton size="sm" @click="startReply(reply, comment)">回复</LtButton
            ><LtButton v-if="isOwner(reply.userId)" size="sm" @click="handleDelete(reply, comment)">删除</LtButton>
          </div>
        </article>
      </div>
    </article>
    <LtSheet v-model:open="deleteOpen" title="删除评论"
      ><p>确定删除这条评论吗？</p>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <div class="actions">
        <LtButton :disabled="deleting" @click="deleteOpen = false">取消</LtButton
        ><LtButton :disabled="deleting" @click="confirmDelete">{{ deleting ? '正在删除…' : '确定' }}</LtButton>
      </div></LtSheet
    >
  </section>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { LtButton, LtSheet } from '@/components/living'

import { getComments, addComment, deleteComment, toggleLike, type CommentVO } from '@/api/comment'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  questionId: number
}>()

const userStore = useUserStore()
const comments = ref<CommentVO[]>([])
const loading = ref(false)
const loadError = ref('')
const notice = ref('')
const actionError = ref('')
const deleting = ref(false)
const deleteOpen = ref(false)
const deleteTarget = ref<{ comment: CommentVO; parent?: CommentVO } | null>(null)
const submitting = ref(false)
const newComment = ref('')
const replyTarget = ref<CommentVO | null>(null)
const replyParentId = ref<number>(0)

const totalComments = computed(() => {
  let count = comments.value.length
  for (const c of comments.value) {
    count += c.replies?.length || 0
  }
  return count
})

function isOwner(userId: number): boolean {
  return userStore.userInfo?.id === userId
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

async function fetchComments() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getComments(props.questionId, { errorDisplay: 'inline' })
    comments.value = res.data || []
  } catch {
    loadError.value = '讨论暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (submitting.value) return
  actionError.value = ''
  notice.value = ''
  const content = newComment.value.trim()
  if (!content) {
    actionError.value = '请输入评论内容'
    return
  }
  submitting.value = true
  try {
    const res = await addComment(
      {
        questionId: props.questionId,
        content,
        parentId: replyParentId.value,
        replyToUserId: replyTarget.value?.userId,
      },
      { errorDisplay: 'inline' },
    )
    if (!res.data) return
    const newC = res.data
    if (replyParentId.value === 0) {
      comments.value.unshift(newC)
    } else {
      const parent = comments.value.find((c) => c.id === replyParentId.value)
      if (parent) {
        if (!parent.replies) parent.replies = []
        parent.replies.push(newC)
      }
    }
    newComment.value = ''
    replyTarget.value = null
    replyParentId.value = 0
    notice.value = '评论成功'
  } catch {
    actionError.value = '评论提交失败，请重试。'
  } finally {
    submitting.value = false
  }
}

function startReply(comment: CommentVO, parentComment?: CommentVO) {
  replyTarget.value = comment
  replyParentId.value = parentComment ? parentComment.id : comment.id
  newComment.value = ''
}

function cancelReply() {
  replyTarget.value = null
  replyParentId.value = 0
}

async function handleLike(comment: CommentVO) {
  try {
    const res = await toggleLike(comment.id, { errorDisplay: 'inline' })
    const liked = !!res.data
    comment.likedByMe = liked
    comment.likeCount = liked ? comment.likeCount + 1 : Math.max(0, comment.likeCount - 1)
  } catch {
    actionError.value = '点赞未能保存，请重试。'
  }
}

function handleDelete(comment: CommentVO, parentComment?: CommentVO) {
  deleteTarget.value = { comment, parent: parentComment }
  deleteOpen.value = true
}
async function confirmDelete() {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  actionError.value = ''
  const { comment, parent } = deleteTarget.value
  try {
    await deleteComment(comment.id, { errorDisplay: 'inline' })
    if (parent?.replies) parent.replies = parent.replies.filter((reply) => reply.id !== comment.id)
    else comments.value = comments.value.filter((item) => item.id !== comment.id)
    notice.value = '已删除'
    deleteOpen.value = false
  } catch {
    actionError.value = '删除失败，请重试。'
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  fetchComments()
})
</script>

<style scoped>
.discussion {
  display: grid;
  gap: var(--lt-space-4);
}
h3 {
  margin: 0;
  font-size: var(--lt-text-headline);
}
h3 span,
time,
header > span {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
form {
  display: grid;
  gap: var(--lt-space-3);
}
label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
textarea {
  box-sizing: border-box;
  width: 100%;
  resize: vertical;
  padding: var(--lt-space-3);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-well);
  color: var(--lt-label);
  font: inherit;
}
.actions,
header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
}
form .actions {
  justify-content: flex-end;
}
.comment-item {
  padding: var(--lt-space-4) 0;
  border-top: thin solid var(--lt-separator);
}
.comment-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: var(--lt-leading-reading);
}
.replies-list {
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-well);
}
.reply-item + .reply-item {
  margin-top: var(--lt-space-4);
  padding-top: var(--lt-space-4);
  border-top: thin solid var(--lt-separator);
}
.error {
  color: var(--lt-red-text);
}
</style>
