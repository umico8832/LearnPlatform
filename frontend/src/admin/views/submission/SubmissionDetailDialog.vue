<template>
  <el-dialog v-model="visible" title="投稿详情" width="700px">
    <el-descriptions v-if="submission" :column="2" border>
      <el-descriptions-item label="ID">{{ submission.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="submissionStatusTag(submission.status)">{{ submissionStatusLabel(submission.status) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="投稿人">{{ submission.nickname || submission.username }}</el-descriptions-item>
      <el-descriptions-item label="投稿时间">{{ formatSubmissionTime(submission.createTime) }}</el-descriptions-item>
      <el-descriptions-item label="课程">{{ submission.courseName }}</el-descriptions-item>
      <el-descriptions-item label="题型">{{ questionTypeLabel(submission.questionType) }}</el-descriptions-item>
      <el-descriptions-item label="难度">
        <el-rate v-model="submission.difficulty" disabled :max="5" />
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.source" label="来源">{{ submission.source }}</el-descriptions-item>
      <el-descriptions-item label="题干内容" :span="2">
        <div class="detail-content">{{ submission.content }}</div>
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.optionsJson" label="选项" :span="2">
        <div v-for="(option, index) in parseSubmissionOptions(submission.optionsJson)" :key="index">
          <strong>{{ option.label || String.fromCharCode(65 + index) }}.</strong> {{ option.content }}
          <el-tag v-if="option.isCorrect" type="success" size="small" class="correct-tag">正确</el-tag>
        </div>
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.correctAnswer" label="参考答案" :span="2">
        {{ submission.correctAnswer }}
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.analysis" label="解析" :span="2">
        <div class="detail-content">{{ submission.analysis }}</div>
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.tags" label="标签">{{ submission.tags }}</el-descriptions-item>
      <el-descriptions-item v-if="submission.knowledgePointIds" label="知识点IDs">
        {{ submission.knowledgePointIds }}
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.reviewComment" label="审核意见" :span="2">
        <el-text type="info">{{ submission.reviewComment }}</el-text>
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.reviewedByName" label="审核人">
        {{ submission.reviewedByName }}
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.reviewedTime" label="审核时间">
        {{ formatSubmissionTime(submission.reviewedTime) }}
      </el-descriptions-item>
      <el-descriptions-item v-if="submission.importedQuestionId" label="入库题目ID">
        <el-button type="primary" link @click="goToQuestion(submission.importedQuestionId)">
          #{{ submission.importedQuestionId }}
        </el-button>
      </el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { QuestionSubmissionVO } from '@/api/submission'
import {
  formatSubmissionTime,
  parseSubmissionOptions,
  questionTypeLabel,
  submissionStatusLabel,
  submissionStatusTag,
} from './submissionPresentation'

const router = useRouter()
const visible = ref(false)
const submission = ref<QuestionSubmissionVO | null>(null)

function open(value: QuestionSubmissionVO) {
  submission.value = value
  visible.value = true
}

function goToQuestion(questionId: number) {
  visible.value = false
  void router.push({ name: 'AdminQuestionManage', query: { highlight: questionId } })
}

defineExpose({ open })
</script>

<style scoped>
.detail-content {
  white-space: pre-wrap;
  line-height: 1.6;
}

.correct-tag {
  margin-left: 4px;
}
</style>
