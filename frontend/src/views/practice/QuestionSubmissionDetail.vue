<script setup lang="ts">
import type { QuestionSubmissionVO } from '@/api/submission'
import { questionTypeLabel, difficultyLabel, recordTime } from './practiceLibraryPresentation'
import { submissionOptions, submissionStatus } from './questionSubmissionForm'

defineProps<{ detail: QuestionSubmissionVO | null }>()
const open = defineModel<boolean>({ default: false })
</script>

<template>
  <el-dialog v-model="open" title="投稿详情" width="720px">
    <article v-if="detail" class="submission-detail">
      <div class="detail-meta">
        <el-tag type="info">{{ submissionStatus(detail.status) }}</el-tag
        ><span>{{ detail.courseName }}</span
        ><span>{{ questionTypeLabel(detail.questionType) }}</span
        ><span>{{ difficultyLabel(detail.difficulty) }}</span>
      </div>
      <p class="detail-time">投稿于 {{ recordTime(detail.createTime) }}</p>
      <section v-if="detail.reviewComment || detail.reviewedTime" class="review-note">
        <h3>审核反馈</h3>
        <p v-if="detail.reviewComment">{{ detail.reviewComment }}</p>
        <span v-if="detail.reviewedTime"
          >{{ recordTime(detail.reviewedTime)
          }}<template v-if="detail.reviewedByName"> · {{ detail.reviewedByName }}</template></span
        >
      </section>
      <section>
        <h3>题干</h3>
        <p class="reading-content">{{ detail.content }}</p>
      </section>
      <ul v-if="submissionOptions(detail.optionsJson).length" class="detail-options">
        <li v-for="(option, index) in submissionOptions(detail.optionsJson)" :key="index">
          <strong>{{ option.label }}</strong
          ><span>{{ option.content }}</span
          ><small v-if="option.isCorrect">正确答案</small>
        </li>
      </ul>
      <section v-if="detail.correctAnswer">
        <h3>参考答案</h3>
        <p class="reading-content">{{ detail.correctAnswer }}</p>
      </section>
      <section v-if="detail.analysis">
        <h3>解析</h3>
        <p class="reading-content">{{ detail.analysis }}</p>
      </section>
      <dl v-if="detail.tags || detail.source" class="detail-footnotes">
        <template v-if="detail.tags"
          ><dt>标签</dt>
          <dd>{{ detail.tags }}</dd></template
        ><template v-if="detail.source"
          ><dt>来源</dt>
          <dd>{{ detail.source }}</dd></template
        >
      </dl>
    </article>
    <template #footer><el-button @click="open = false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.submission-detail {
  display: grid;
  gap: var(--lp-space-5);
}
.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.detail-time {
  margin: calc(-1 * var(--lp-space-3)) 0 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.submission-detail h3 {
  margin: 0 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-medium);
}
.reading-content,
.review-note p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}
.review-note {
  padding: var(--lp-space-4);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
}
.review-note span {
  display: block;
  margin-top: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.detail-options {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--lp-space-3);
}
.detail-options li {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  line-height: var(--lp-leading-body);
}
.detail-options strong {
  color: var(--lp-text-secondary);
}
.detail-options span {
  flex: 1;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  min-width: 0;
}
.detail-options small {
  color: var(--lp-success);
  flex-shrink: 0;
}
.detail-footnotes {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--lp-space-3);
  margin: 0;
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
  font-size: var(--lp-text-sm);
}
.detail-footnotes dt {
  color: var(--lp-text-muted);
}
.detail-footnotes dd {
  margin: 0;
  color: var(--lp-text-secondary);
  overflow-wrap: anywhere;
}
</style>
