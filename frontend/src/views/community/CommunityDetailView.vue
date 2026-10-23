<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Download, FileText, RotateCcw, ThumbsUp, Trash2 } from '@lucide/vue'
import { LtButton, LtSheet } from '@/components/living'
import { communityStatuses, communityTypes } from '@/api/community'
import { formatTime } from '@/utils/format'
import { formatBytes, useCommunityThread } from '@/components/community/useCommunityThread'
import CommunityComments from './CommunityComments.vue'
import { useConfirmSheet } from '@/composables/useConfirmSheet'

/**
 * 社区内容详情（Phase 28 新基线）。正文、来源与讨论直接读在画布上，按阅读宽度排；
 * 要动手的东西（附件下载、写回复）放进卡片；右栏列出它属于哪门考试、哪个科目。
 */
defineOptions({ name: 'CommunityDetailView' })

const SUBMISSION_STATUS = ['待审核', '已通过', '已拒绝', '已入库']
const CONFIRMS = {
  post: { title: '删除这条内容？', text: '正文和附件将不再可访问，已整理的题目投稿会保留。', action: '删除内容' },
  comment: { title: '删除这条回复？', text: '删除后不能恢复。', action: '删除回复' },
} as const

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const commentsRef = ref<InstanceType<typeof CommunityComments> | null>(null)
const confirmSheet = useConfirmSheet()
const confirmOpen = confirmSheet.open
const confirmKind = ref<'post' | 'comment'>('post')

const {
  post,
  comments,
  reviews,
  loading,
  busy,
  loadError,
  actionError,
  commentsError,
  commentsLoading,
  reply,
  replyTo,
  downloading,
  commentPage,
  commentTotal,
  canManage,
  load,
  loadComments,
  like,
  submitReply,
  beginReply,
  removeComment,
  removePost,
  download,
  pageComments,
} = useCommunityThread({
  id: () => id.value,
  confirm: (kind) => {
    confirmKind.value = kind
    return confirmSheet.ask()
  },
  focusReply: () => void nextTick(() => commentsRef.value?.focus()),
  onDeleted: () => void router.push('/community'),
})

const approved = computed(() => post.value?.status === 'APPROVED')
const facts = computed(() => {
  const item = post.value
  if (!item) return []
  return [
    { term: '考试', value: item.examName },
    { term: '科目', value: item.subjectName },
    { term: '院校', value: item.schoolName },
    { term: '知识点', value: item.conceptName },
    { term: '关联课程', value: item.courseName },
  ].filter((fact) => fact.value)
})

function initial(name: string) {
  return (name || '?').charAt(0).toUpperCase()
}
</script>

<template>
  <div class="community-detail">
    <RouterLink class="back" to="/community">
      <ChevronLeft :size="18" aria-hidden="true" />
      返回社区
    </RouterLink>

    <section class="community-thread" aria-label="社区内容详情">
      <div v-if="loading" class="layout" aria-busy="true">
        <div class="main" aria-hidden="true">
          <span class="skeleton" style="width: 160px; height: 22px" />
          <span class="skeleton" style="width: 72%; height: 34px; margin-top: 16px" />
          <span class="skeleton" style="width: 220px; height: 14px; margin-top: 14px" />
          <span v-for="n in 4" :key="n" class="skeleton" style="height: 16px; margin-top: 14px" />
        </div>
        <span class="sr-only" role="status">正在读取内容</span>
      </div>

      <div v-else-if="loadError" class="state">
        <h1 class="state-title">内容没有加载出来</h1>
        <p class="state-text">{{ loadError }}</p>
        <LtButton variant="primary" @click="load">
          <template #icon><RotateCcw aria-hidden="true" /></template>
          重新加载
        </LtButton>
      </div>

      <div v-else-if="post" class="layout">
        <article class="main" aria-labelledby="post-title">
          <header class="post-head">
            <p class="tags">
              <span class="tag">{{ communityTypes[post.contentType] || post.contentType }}</span>
              <span class="tag" :data-status="post.status">{{ communityStatuses[post.status] || post.status }}</span>
            </p>
            <h1 id="post-title" class="post-title">{{ post.title }}</h1>
            <p class="byline">
              <span class="avatar" aria-hidden="true">{{ initial(post.authorName) }}</span>
              <span class="byline-name">{{ post.authorName }}</span>
              <time :datetime="post.createdAt">{{ formatTime(post.createdAt) }}</time>
            </p>
          </header>

          <p class="community-body post-body">{{ post.body }}</p>

          <section v-if="post.sourceNote" class="block">
            <h2 class="block-title">来源与使用许可</h2>
            <p class="community-body block-text">{{ post.sourceNote }}</p>
          </section>

          <section v-if="post.attachments.length" class="block">
            <h2 class="block-title">题库附件</h2>
            <ul class="files">
              <li v-for="file in post.attachments" :key="file.id" class="file">
                <FileText :size="18" class="file-icon" aria-hidden="true" />
                <span class="file-name">{{ file.name }}</span>
                <span class="file-size">{{ formatBytes(file.sizeBytes) }}</span>
                <LtButton size="sm" :disabled="downloading !== null" @click="download(file)">
                  <template #icon><Download aria-hidden="true" /></template>
                  {{ downloading === file.id ? '下载中…' : '下载' }}
                </LtButton>
              </li>
            </ul>
          </section>

          <p v-if="post.reviewNote" class="note">审核意见：{{ post.reviewNote }}</p>
          <details v-if="reviews.length" class="history">
            <summary>审核记录 · {{ reviews.length }}</summary>
            <p v-for="review in reviews" :key="review.id">
              {{ formatTime(review.createdAt) }} · {{ review.reviewerName }} ·
              {{ communityStatuses[review.decision] }}：{{ review.note }}
            </p>
          </details>

          <div class="post-actions">
            <button type="button" class="like" :aria-pressed="post.liked" :disabled="!approved || busy" @click="like()">
              <ThumbsUp :size="16" aria-hidden="true" />{{ post.liked ? '已赞同' : '赞同' }} · {{ post.likeCount }}
            </button>
            <LtButton v-if="canManage" variant="quiet" class="danger" :disabled="busy" @click="removePost">
              <template #icon><Trash2 aria-hidden="true" /></template>
              删除内容
            </LtButton>
          </div>
          <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>

          <section v-if="post.questionLinks.length" class="block">
            <h2 class="block-title">题目整理进度</h2>
            <p v-for="link in post.questionLinks" :key="link.submissionId" class="block-text">
              投稿 #{{ link.submissionId }} · {{ SUBMISSION_STATUS[link.status]
              }}<span v-if="link.questionId"> · 题目 #{{ link.questionId }}</span>
            </p>
          </section>

          <CommunityComments
            ref="commentsRef"
            v-model:reply="reply"
            v-model:reply-to="replyTo"
            :comment-count="post.commentCount"
            :comments="comments"
            :approved="approved"
            :busy="busy"
            :error="commentsError"
            :loading="commentsLoading"
            :page="commentPage"
            :total="commentTotal"
            @submit="submitReply"
            @retry="loadComments"
            @like="like"
            @answer="beginReply"
            @remove="removeComment"
            @page="pageComments"
          />
        </article>

        <aside v-if="facts.length" class="facts" aria-label="内容归属">
          <dl>
            <div v-for="fact in facts" :key="fact.term" class="fact">
              <dt>{{ fact.term }}</dt>
              <dd>{{ fact.value }}</dd>
            </div>
          </dl>
        </aside>
      </div>
    </section>

    <LtSheet v-model:open="confirmOpen" :title="CONFIRMS[confirmKind].title" :width="420">
      <p class="sheet-text">{{ CONFIRMS[confirmKind].text }}</p>
      <div class="sheet-actions">
        <LtButton @click="confirmSheet.answer(false)">取消</LtButton>
        <LtButton class="danger-fill" @click="confirmSheet.answer(true)">{{ CONFIRMS[confirmKind].action }}</LtButton>
      </div>
    </LtSheet>
  </div>
</template>

<style scoped>
.community-detail {
  --reading: 720px;
  --rail: 240px;

  width: min(100%, calc(var(--reading) + var(--rail) + var(--lt-space-9)));
  margin: 0 auto;
  padding: var(--lt-space-6) 0 var(--lt-space-10);
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: -6px;
  padding: 6px 10px 6px 4px;
  border-radius: var(--lt-radius-pill);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  text-decoration: none;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.back:hover {
  background: var(--lt-fill-3);
  color: var(--lt-label);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, var(--reading)) var(--rail);
  gap: var(--lt-space-9);
  align-items: start;
  margin-top: var(--lt-space-5);
}

.main {
  display: grid;
  min-width: 0;
}

.post-head {
  display: grid;
  gap: var(--lt-space-3);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  margin: 0;
}

.tag {
  padding: 3px 10px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.tag:is([data-status='PENDING'], [data-status='REJECTED']) {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}

.post-title {
  margin: 0;
  font-size: var(--lt-text-title1);
  line-height: var(--lt-leading-tight);
  letter-spacing: var(--lt-tracking-title);
  overflow-wrap: anywhere;
  text-wrap: balance;
}

.byline {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  margin: 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.byline-name {
  color: var(--lt-label-2);
  font-weight: 600;
}

.byline-name + time::before {
  content: '·';
  margin-right: var(--lt-space-2);
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
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.post-body {
  margin-top: var(--lt-space-7);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.block {
  margin-top: var(--lt-space-8);
}

.block-title {
  margin: 0 0 var(--lt-space-3);
  font-size: var(--lt-text-headline);
}

.block-text {
  margin: 0;
  color: var(--lt-label-2);
  line-height: 1.7;
}

.files {
  margin: 0;
  padding: var(--lt-space-2);
  list-style: none;
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.file {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  min-height: 52px;
  padding: var(--lt-space-2) var(--lt-space-2) var(--lt-space-2) var(--lt-space-3);
}

.file + .file {
  border-top: 0.5px solid var(--lt-separator);
}

.file-icon {
  flex: none;
  color: var(--lt-label-2);
}

.file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.note {
  margin: var(--lt-space-7) 0 0;
  padding: var(--lt-space-3) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
}

.history {
  margin-top: var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
}

.history summary {
  width: fit-content;
  color: var(--lt-accent);
  cursor: pointer;
}

.history p {
  margin: var(--lt-space-2) 0 0;
}

.post-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-8);
  padding-top: var(--lt-space-5);
  border-top: 0.5px solid var(--lt-separator);
}

.like {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: var(--lt-control-md);
  padding: 0 14px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
  font-size: var(--lt-text-subhead);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.like[aria-pressed='true'] {
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
}

.like:disabled {
  cursor: default;
  opacity: 0.5;
}

.post-actions .lt-button.danger {
  color: var(--lt-red-text);
}

.error {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-subhead);
}

.facts {
  position: sticky;
  top: calc(var(--shell-bar-height, 0px) + var(--lt-space-6));
  padding-top: var(--lt-space-2);
}

.facts dl {
  display: grid;
  gap: var(--lt-space-4);
  margin: 0;
  padding-left: var(--lt-space-5);
  border-left: 0.5px solid var(--lt-separator);
}

.fact dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.fact dd {
  margin: 2px 0 0;
  color: var(--lt-label);
  font-size: var(--lt-text-subhead);
  overflow-wrap: anywhere;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lt-space-3);
  max-width: var(--reading);
  padding: var(--lt-space-8) 0;
}

.state-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.state-text {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-label-2);
}

.sheet-text {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  line-height: 1.6;
}

.sheet-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-6);
}

.sheet-actions .lt-button.danger-fill {
  color: var(--lt-red-text);
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

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
