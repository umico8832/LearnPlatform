<template>
  <article class="variant-card" :class="resultClass">
    <header class="variant-card__header">
      <div>
        <span class="variant-card__eyebrow">TRANSFER CHECK</span>
        <h3>独立作答 · 变式检验</h3>
      </div>
      <div class="variant-card__meta">
        <el-tag effect="plain">单选题</el-tag>
        <el-tag type="warning" effect="plain">难度 {{ question.difficulty }}/5</el-tag>
      </div>
    </header>

    <div class="variant-card__question">
      <MarkdownRenderer :content="question.questionContent" />
    </div>

    <el-radio-group v-model="selectedAnswer" class="variant-options" :disabled="Boolean(training.answered)">
      <el-radio v-for="option in question.options" :key="option.label" :value="option.label" class="variant-option">
        <span class="variant-option__label">{{ option.label }}</span>
        <span class="variant-option__content">{{ option.content }}</span>
      </el-radio>
    </el-radio-group>

    <div v-if="!training.answered" class="variant-card__actions">
      <p>答案提交后立即判分，并保留首次结果用于学习效果统计。</p>
      <el-button type="primary" :loading="submitting" :disabled="!selectedAnswer" @click="submitAnswer">
        提交答案并判分
      </el-button>
    </div>

    <section v-else class="variant-result" :class="training.correct ? 'is-correct' : 'is-wrong'">
      <div class="variant-result__headline">
        <span>{{ training.correct ? '✓' : '!' }}</span>
        <div>
          <strong>{{ training.correct ? '回答正确，迁移成功' : '这次未答对，先看清差异' }}</strong>
          <p>你的答案：{{ training.userAnswer || '-' }} · 正确答案：{{ training.correctAnswer || '-' }}</p>
        </div>
      </div>
      <div v-if="training.analysis" class="variant-result__analysis">
        <span>解析</span>
        <MarkdownRenderer :content="training.analysis" />
      </div>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { errorMessage } from '@/utils/errors'
import { ElMessage } from 'element-plus'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { submitVariantAnswer, type AiVariantQuestion, type AiVariantTrainingStatus } from '@/api/ai'

const props = defineProps<{
  questionId: number
  question: AiVariantQuestion
  training: {
    answered?: boolean
    correct?: boolean | null
    userAnswer?: string | null
    correctAnswer?: string | null
    analysis?: string | null
  }
}>()

const emit = defineEmits<{
  answered: [training: AiVariantTrainingStatus]
}>()

const selectedAnswer = ref(props.training.userAnswer || '')
const submitting = ref(false)
const resultClass = computed(() =>
  props.training.answered ? (props.training.correct ? 'has-correct-result' : 'has-wrong-result') : '',
)

watch(
  () => props.training.userAnswer,
  (value) => {
    if (value) selectedAnswer.value = value
  },
)

async function submitAnswer() {
  if (!selectedAnswer.value || submitting.value) return
  submitting.value = true
  try {
    const response = await submitVariantAnswer(props.questionId, selectedAnswer.value)
    emit('answered', response.data)
    ElMessage.success(response.data.correct ? '回答正确' : '已完成判分，看看解析再巩固一次')
  } catch (error) {
    ElMessage.error(errorMessage(error, '提交答案失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.variant-card {
  --variant-accent: #256b8f;
  position: relative;
  overflow: hidden;
  padding: 22px;
  border: 1px solid #cdddea;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgb(240 248 252 / 92%), rgb(255 255 255 / 96%) 48%),
    repeating-linear-gradient(90deg, transparent 0 31px, rgb(37 107 143 / 4%) 31px 32px);
  box-shadow: 0 12px 28px rgb(42 77 102 / 8%);
}

.variant-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--variant-accent);
  content: '';
}

.variant-card.has-correct-result {
  --variant-accent: #2b8a57;
}
.variant-card.has-wrong-result {
  --variant-accent: #c26b36;
}

.variant-card__header,
.variant-card__actions,
.variant-result__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.variant-card__eyebrow {
  color: var(--variant-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.variant-card h3 {
  margin: 5px 0 0;
  color: #1c3444;
  font-size: 18px;
}

.variant-card__meta {
  display: flex;
  gap: 6px;
}

.variant-card__question {
  margin: 20px 0 14px;
  color: #203646;
  font-size: 15px;
  line-height: 1.7;
}

.variant-options {
  display: grid;
  gap: 10px;
  width: 100%;
}

.variant-option {
  box-sizing: border-box;
  width: 100%;
  height: auto;
  min-height: 48px;
  margin: 0;
  padding: 11px 14px;
  border: 1px solid #d8e3eb;
  border-radius: 10px;
  background: rgb(255 255 255 / 82%);
  transition:
    border-color 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.variant-option:hover {
  border-color: #8eb4ca;
  box-shadow: 0 7px 18px rgb(42 77 102 / 8%);
  transform: translateY(-1px);
}

.variant-option :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #334b5b;
  white-space: normal;
}

.variant-option__label {
  display: inline-grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 7px;
  background: #e8f1f6;
  color: var(--variant-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 800;
}

.variant-card__actions {
  align-items: flex-end;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #cbdbe5;
}

.variant-card__actions p {
  max-width: 520px;
  margin: 0;
  color: #6f8190;
  font-size: 12px;
  line-height: 1.55;
}

.variant-result {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #d5e7dc;
  border-radius: 11px;
  background: #f2faf5;
}

.variant-result.is-wrong {
  border-color: #ead9cd;
  background: #fff8f2;
}

.variant-result__headline {
  justify-content: flex-start;
}

.variant-result__headline > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 50%;
  background: var(--variant-accent);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
}

.variant-result__headline strong {
  color: #244536;
}
.variant-result.is-wrong .variant-result__headline strong {
  color: #754329;
}
.variant-result__headline p {
  margin: 4px 0 0;
  color: #6f7e75;
  font-size: 12px;
}

.variant-result__analysis {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgb(64 111 80 / 14%);
}

.variant-result__analysis > span {
  color: var(--variant-accent);
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .variant-card {
    padding: 18px 15px;
  }
  .variant-card__header,
  .variant-card__actions {
    align-items: stretch;
    flex-direction: column;
  }
  .variant-card__meta {
    align-self: flex-start;
  }
  .variant-card__actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
