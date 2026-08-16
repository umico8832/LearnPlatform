<template>
  <div class="question-comment">
    <div class="comment-header">
      <el-icon><ChatLineRound /></el-icon>
      <span>讨论区</span>
      <el-tag v-if="totalComments > 0" size="small" type="info">{{ totalComments }} 条</el-tag>
    </div>

    <!-- 发表评论 -->
    <div class="comment-input">
      <el-input
        v-model="newComment"
        type="textarea"
        :rows="2"
        :placeholder="replyTarget ? `回复 ${replyTarget.nickname}...` : '分享你的解题思路或疑问...'"
        maxlength="2000"
        show-word-limit
      />
      <div class="comment-input-actions">
        <el-button v-if="replyTarget" size="small" @click="cancelReply">取消回复</el-button>
        <el-button type="primary" size="small" :loading="submitting" @click="submitComment">
          {{ replyTarget ? '回复' : '发表评论' }}
        </el-button>
      </div>
    </div>

    <!-- 评论列表 -->
    <div v-loading="loading" class="comment-list">
      <div v-if="comments.length === 0 && !loading" class="comment-empty">
        <el-empty description="暂无讨论，快来发表第一条评论吧" :image-size="80" />
      </div>

      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <div class="comment-avatar">
          <el-avatar :size="32" :src="comment.avatar || undefined">
            {{ (comment.nickname || '?')[0] }}
          </el-avatar>
        </div>
        <div class="comment-body">
          <div class="comment-meta">
            <span class="comment-nickname">{{ comment.nickname }}</span>
            <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
          </div>
          <div class="comment-text">{{ comment.content }}</div>
          <div class="comment-actions">
            <span class="action-item" :class="{ liked: comment.likedByMe }" @click="handleLike(comment)">
              <el-icon><Star /></el-icon>
              {{ comment.likeCount > 0 ? comment.likeCount : '点赞' }}
            </span>
            <span class="action-item" @click="startReply(comment)">
              <el-icon><ChatLineSquare /></el-icon>
              回复
            </span>
            <span v-if="isOwner(comment.userId)" class="action-item action-delete" @click="handleDelete(comment)">
              <el-icon><Delete /></el-icon>
              删除
            </span>
          </div>

          <!-- 子回复 -->
          <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
            <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
              <div class="comment-avatar">
                <el-avatar :size="24" :src="reply.avatar || undefined">
                  {{ (reply.nickname || '?')[0] }}
                </el-avatar>
              </div>
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-nickname">{{ reply.nickname }}</span>
                  <template v-if="reply.replyToNickname">
                    <span class="reply-arrow">回复</span>
                    <span class="comment-nickname reply-to">{{ reply.replyToNickname }}</span>
                  </template>
                  <span class="comment-time">{{ formatTime(reply.createTime) }}</span>
                </div>
                <div class="comment-text">{{ reply.content }}</div>
                <div class="comment-actions">
                  <span class="action-item" :class="{ liked: reply.likedByMe }" @click="handleLike(reply)">
                    <el-icon><Star /></el-icon>
                    {{ reply.likeCount > 0 ? reply.likeCount : '点赞' }}
                  </span>
                  <span class="action-item" @click="startReply(reply, comment)">
                    <el-icon><ChatLineSquare /></el-icon>
                    回复
                  </span>
                  <span
                    v-if="isOwner(reply.userId)"
                    class="action-item action-delete"
                    @click="handleDelete(reply, comment)"
                  >
                    <el-icon><Delete /></el-icon>
                    删除
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineRound, ChatLineSquare, Star, Delete } from '@element-plus/icons-vue'
import { getComments, addComment, deleteComment, toggleLike, type CommentVO } from '@/api/comment'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  questionId: number
}>()

const userStore = useUserStore()
const comments = ref<CommentVO[]>([])
const loading = ref(false)
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
  try {
    const res = await getComments(props.questionId)
    comments.value = res.data || []
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  const content = newComment.value.trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    const res = await addComment({
      questionId: props.questionId,
      content,
      parentId: replyParentId.value,
      replyToUserId: replyTarget.value?.userId,
    })
    if (!res.data) return
    const newC = res.data
    if (replyParentId.value === 0) {
      // 顶级评论，添加到列表头部
      comments.value.unshift(newC)
    } else {
      // 回复，找到父评论并添加到 replies
      const parent = comments.value.find((c) => c.id === replyParentId.value)
      if (parent) {
        if (!parent.replies) parent.replies = []
        parent.replies.push(newC)
      }
    }
    newComment.value = ''
    replyTarget.value = null
    replyParentId.value = 0
    ElMessage.success('评论成功')
  } catch {
    // error handled by interceptor
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
    const res = await toggleLike(comment.id)
    const liked = !!res.data
    comment.likedByMe = liked
    comment.likeCount = liked ? comment.likeCount + 1 : Math.max(0, comment.likeCount - 1)
  } catch {
    // error handled by interceptor
  }
}

async function handleDelete(comment: CommentVO, parentComment?: CommentVO) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteComment(comment.id)
    if (parentComment && parentComment.replies) {
      parentComment.replies = parentComment.replies.filter((r) => r.id !== comment.id)
    } else {
      comments.value = comments.value.filter((c) => c.id !== comment.id)
    }
    ElMessage.success('已删除')
  } catch {
    // cancelled or error
  }
}

onMounted(() => {
  fetchComments()
})
</script>

<style scoped>
.question-comment {
  margin-top: 12px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.comment-input {
  margin-bottom: 16px;
}

.comment-input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.comment-list {
  min-height: 60px;
}

.comment-empty {
  padding: 20px 0;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-avatar {
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.comment-nickname {
  font-weight: 600;
  color: #303133;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.reply-arrow {
  color: #909399;
  font-size: 12px;
}

.reply-to {
  color: #409eff;
  font-size: 12px;
  font-weight: 500;
}

.comment-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 6px;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  transition: color 0.2s;
}

.action-item:hover {
  color: #409eff;
}

.action-item.liked {
  color: #f7ba2a;
}

.action-item.action-delete:hover {
  color: #f56c6c;
}

.replies-list {
  margin-top: 8px;
  background: #f8f9fa;
  border-radius: 6px;
  padding: 8px 12px;
}

.reply-item {
  display: flex;
  gap: 8px;
  padding: 6px 0;
}

.reply-item:not(:last-child) {
  border-bottom: 1px solid #ebeef5;
}
</style>
