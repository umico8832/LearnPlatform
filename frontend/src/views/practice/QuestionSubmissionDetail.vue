<script setup lang="ts">
import LtButton from '@/components/living/LtButton.vue'
import LtSheet from '@/components/living/LtSheet.vue'
import type { QuestionSubmissionVO } from '@/api/submission'
import QuestionProse from '@/views/question/QuestionProse.vue'
import { questionTypeLabel, difficultyLabel, recordTime } from './practiceLibraryPresentation'
import { submissionOptions, submissionStatus } from './questionSubmissionForm'
defineProps<{ detail: QuestionSubmissionVO | null }>()
const open = defineModel<boolean>({ default: false })
</script>
<template>
  <LtSheet v-model:open="open" title="投稿详情" :width="720">
    <article v-if="detail" class="submission-detail">
      <div class="detail-meta">
        <span class="status" :data-status="detail.status">{{ submissionStatus(detail.status) }}</span
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
        <QuestionProse class="reading-content" :content="detail.content" />
      </section>
      <ul v-if="submissionOptions(detail.optionsJson).length" class="detail-options">
        <li
          v-for="(option, index) in submissionOptions(detail.optionsJson)"
          :key="index"
          :data-correct="option.isCorrect"
        >
          <strong>{{ option.label }}</strong
          ><span>{{ option.content }}</span
          ><small v-if="option.isCorrect">正确答案</small>
        </li>
      </ul>
      <section v-if="detail.correctAnswer">
        <h3>参考答案</h3>
        <QuestionProse class="reading-content" :content="detail.correctAnswer" />
      </section>
      <section v-if="detail.analysis">
        <h3>解析</h3>
        <QuestionProse class="reading-content" :content="detail.analysis" />
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
    <div class="actions"><LtButton @click="open = false">关闭</LtButton></div>
  </LtSheet>
</template>
<style scoped>
.submission-detail {
  display: grid;
  gap: var(--lt-space-5);
}
.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.status {
  padding: calc(var(--lt-space-1) * 0.75) calc(var(--lt-space-2) + var(--lt-space-1) / 4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.status[data-status='2'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.status:is([data-status='1'], [data-status='3']) {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.detail-time {
  margin: calc(-1 * var(--lt-space-3)) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
h3 {
  margin: 0 0 var(--lt-space-3);
  font-size: var(--lt-text-footnote);
}
.reading-content,
.review-note p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.review-note {
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
}
.review-note span {
  display: block;
  margin-top: var(--lt-space-3);
  color: var(--lt-orange-text);
  font-size: var(--lt-text-caption);
}
.detail-options {
  display: grid;
  gap: var(--lt-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.detail-options li {
  display: flex;
  gap: var(--lt-space-3);
  align-items: baseline;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  line-height: var(--lt-leading-ui);
}
.detail-options li[data-correct='true'] {
  background: var(--lt-green-soft);
}
.detail-options strong {
  color: var(--lt-label-2);
}
.detail-options span {
  flex: 1;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.detail-options small {
  color: var(--lt-green-text);
  white-space: nowrap;
}
.detail-footnotes {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--lt-space-3);
  margin: 0;
  padding-top: var(--lt-space-4);
  border-top: 1px solid var(--lt-separator);
  font-size: var(--lt-text-footnote);
}
dt {
  color: var(--lt-label-3);
}
dd {
  margin: 0;
  color: var(--lt-label-2);
  overflow-wrap: anywhere;
}
.actions {
  display: flex;
  justify-content: end;
  margin-top: var(--lt-space-5);
}
</style>
