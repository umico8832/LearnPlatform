<script setup lang="ts">
import { computed, ref } from 'vue'
import { MessageCircle, ThumbsUp, X } from '@lucide/vue'
import { LtButton } from '@/components/living'
import { useUserStore } from '@/stores/user'
import type { CommunityComment } from '@/api/community'
import { formatTime } from '@/utils/format'
import { COMMENT_PAGE_SIZE } from '@/components/community/useCommunityThread'

/** 社区详情的讨论区：写回复的卡片在上，回复按时间排在下面，超过一页时分页。 */
const props = defineProps<{
  commentCount: number
  comments: CommunityComment[]
  /** Only public content can be discussed. */
  approved: boolean
  busy: boolean
  error: string
  loading: boolean
  page: number
  total: number
}>()

const emit = defineEmits<{
  submit: []
  retry: []
  like: [comment: CommunityComment]
  answer: [comment: CommunityComment]
  remove: [comment: CommunityComment]
  page: [page: number]
}>()

const reply = defineModel<string>('reply', { required: true })
const replyTo = defineModel<CommunityComment | null>('replyTo', { required: true })

const REPLY_LIMIT = 2000
const user = useUserStore()
const field = ref<HTMLTextAreaElement | null>(null)
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / COMMENT_PAGE_SIZE)))

function initial(name: string) {
  return (name || '?').charAt(0).toUpperCase()
}

function canRemove(comment: CommunityComment) {
  return comment.userId === user.userInfo?.id || user.userInfo?.role === 'ADMIN'
}

defineExpose({ focus: () => field.value?.focus() })
</script>

<template>
  <section class="community-comments" aria-labelledby="comments-title">
    <h2 id="comments-title" class="comments-title">讨论 · {{ commentCount }}</h2>

    <form v-if="approved" class="composer" @submit.prevent="emit('submit')">
      <p v-if="replyTo" class="replying">
        <span>回复 {{ replyTo.authorName }}</span>
        <button type="button" class="replying-clear" aria-label="取消回复" @click="replyTo = null">
          <X :size="14" aria-hidden="true" />
        </button>
      </p>
      <textarea
        ref="field"
        v-model="reply"
        class="composer-field"
        rows="3"
        :maxlength="REPLY_LIMIT"
        aria-label="讨论内容"
        placeholder="分享你的思路，或提出一个具体问题"
      />
      <div class="composer-foot">
        <span class="composer-count">{{ reply.length }} / {{ REPLY_LIMIT }}</span>
        <LtButton type="submit" variant="primary" :disabled="busy || !reply.trim()">发表回复</LtButton>
      </div>
    </form>
    <p v-else class="muted">内容公开后才能参与讨论。</p>

    <div v-if="error" class="comments-error" role="alert">
      <span>{{ error }}</span>
      <LtButton size="sm" @click="emit('retry')">重试</LtButton>
    </div>
    <div v-if="loading" class="comment-skeleton" aria-hidden="true">
      <span class="skeleton" style="width: 30%; height: 14px" />
      <span class="skeleton" style="width: 80%; height: 14px; margin-top: 10px" />
    </div>
    <p v-else-if="approved && !comments.length && !error" class="muted">还没有回复，来说第一句。</p>

    <ol v-if="comments.length" class="comments">
      <li v-for="comment in comments" :key="comment.id">
        <article class="community-comment">
          <span class="avatar" aria-hidden="true">{{ initial(comment.authorName) }}</span>
          <div class="comment-main">
            <p class="comment-head">
              <strong>{{ comment.authorName }}</strong>
              <span v-if="comment.replyToName" class="comment-to">回复 {{ comment.replyToName }}</span>
              <time :datetime="comment.createdAt">{{ formatTime(comment.createdAt) }}</time>
            </p>
            <p class="community-body comment-body" :data-deleted="comment.deleted || undefined">
              {{ comment.deleted ? '该回复已删除' : comment.body }}
            </p>
            <div v-if="!comment.deleted" class="comment-actions">
              <button type="button" class="text-action" :disabled="busy || !approved" @click="emit('answer', comment)">
                <MessageCircle :size="14" aria-hidden="true" />回复
              </button>
              <button
                type="button"
                class="text-action"
                :aria-pressed="comment.liked"
                :disabled="busy || !approved"
                @click="emit('like', comment)"
              >
                <ThumbsUp :size="14" aria-hidden="true" />{{ comment.liked ? '已赞同' : '赞同' }} ·
                {{ comment.likeCount }}
              </button>
              <button
                v-if="canRemove(comment)"
                type="button"
                class="text-action danger"
                :disabled="busy"
                @click="emit('remove', comment)"
              >
                删除回复
              </button>
            </div>
          </div>
        </article>
      </li>
    </ol>

    <nav v-if="pageCount > 1" class="pager" aria-label="回复分页">
      <LtButton size="sm" :disabled="page <= 1" @click="emit('page', page - 1)">上一页</LtButton>
      <span class="pager-text">{{ page }} / {{ pageCount }}</span>
      <LtButton size="sm" :disabled="page >= pageCount" @click="emit('page', page + 1)"> 下一页 </LtButton>
    </nav>
  </section>
</template>

<style scoped>
.community-comments {
  margin-top: var(--lt-space-9);
}

.comments-title {
  margin: 0 0 var(--lt-space-4);
  font-size: var(--lt-text-title3);
}

.composer {
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.replying {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 var(--lt-space-2);
  padding: 3px 4px 3px 10px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
  font-size: var(--lt-text-footnote);
}

.replying-clear {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: none;
  color: inherit;
  cursor: pointer;
}

.replying-clear:hover {
  background: var(--lt-fill-2);
}

.composer-field {
  display: block;
  width: 100%;
  min-height: 84px;
  padding: 12px 14px;
  border: 0;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  color: var(--lt-label);
  font: inherit;
  font-size: var(--lt-text-body);
  line-height: 1.6;
  resize: vertical;
}

.composer-field::placeholder {
  color: var(--lt-label-3);
}

.composer-field:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--lt-space-3);
}

.composer-count {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-caption) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.muted {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-subhead);
}

.comments-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-2) var(--lt-space-2) var(--lt-space-2) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
  font-size: var(--lt-text-subhead);
}

.comment-skeleton {
  margin-top: var(--lt-space-5);
}

.comments {
  margin: var(--lt-space-5) 0 0;
  padding: 0;
  list-style: none;
}

.comments > li + li {
  border-top: 0.5px solid var(--lt-separator);
}

.community-comment {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: var(--lt-space-3);
  padding: var(--lt-space-4) 0;
}

.comment-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--lt-space-2);
  margin: 4px 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.comment-head strong {
  color: var(--lt-label);
  font-weight: 600;
}

.comment-body {
  margin: var(--lt-space-2) 0 0;
  line-height: 1.65;
}

.comment-body[data-deleted] {
  color: var(--lt-label-3);
  font-style: italic;
}

.comment-actions {
  display: flex;
  gap: var(--lt-space-1);
  margin: var(--lt-space-2) 0 0 -8px;
}

.text-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: var(--lt-control-sm);
  padding: 0 8px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: none;
  color: var(--lt-label-2);
  font: inherit;
  font-size: var(--lt-text-footnote);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.text-action:hover:not(:disabled) {
  background: var(--lt-fill-3);
  color: var(--lt-label);
}

.text-action[aria-pressed='true'] {
  color: var(--lt-accent);
}

.text-action.danger {
  color: var(--lt-red-text);
}

.text-action:disabled {
  cursor: default;
  opacity: 0.5;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-5);
}

.pager-text {
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: 600 var(--lt-text-caption) / 1 var(--lt-font-rounded);
}

.community-body {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
  animation: pending 1.4s ease-in-out infinite alternate;
}

@keyframes pending {
  to {
    opacity: 0.45;
  }
}
</style>
