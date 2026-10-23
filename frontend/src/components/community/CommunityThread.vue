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
        <LpSkeleton v-if="commentsLoading" :rows="3" />
        <p v-else-if="!comments.length && !commentsError" class="community-muted">还没有回复。</p>
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
import { nextTick, ref } from 'vue'
import { ElMessageBox, type InputInstance } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { communityTypes, communityStatuses } from '@/api/community'
import { formatTime } from '@/utils/format'
import { formatBytes, useCommunityThread } from './useCommunityThread'

/** 管理端审核抽屉里的社区内容详情（旧基线）；学员端详情页用同一套逻辑另做新基线样式。 */
const props = defineProps<{ id: number }>()
const emit = defineEmits<{ deleted: []; changed: [] }>()
const user = useUserStore()
const replyInput = ref<InputInstance>()
const CONFIRMS = {
  post: ['正文和附件将不再可访问，已整理的题目投稿会保留。', '删除这条内容？'],
  comment: ['删除后不能恢复。', '删除这条回复？'],
} as const
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
  id: () => props.id,
  confirm: (kind) =>
    ElMessageBox.confirm(CONFIRMS[kind][0], CONFIRMS[kind][1], { type: 'warning' }).then(
      () => true,
      () => false,
    ),
  focusReply: () => void nextTick(() => replyInput.value?.focus()),
  onDeleted: () => emit('deleted'),
  onChanged: () => emit('changed'),
})
defineExpose({ reload: load })
</script>
