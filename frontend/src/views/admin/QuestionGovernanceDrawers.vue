<template>
  <el-drawer v-model="duplicateOpen" title="疑似重复题目" size="560px" destroy-on-close>
    <div class="governance-stack">
      <div class="summary">
        <span>按当前课程与题型筛选检测</span>
        <strong>{{ duplicateGroups.length }}</strong>
        <span>组疑似重复</span>
      </div>
      <el-empty v-if="duplicateGroups.length === 0" description="当前筛选下暂未发现疑似重复题目" />
      <div v-else class="governance-stack">
        <el-card
          v-for="group in duplicateGroups"
          :key="group.questions.map((question) => question.id).join('-')"
          shadow="never"
        >
          <div class="card-head">
            <el-tag :type="group.matchType === 'EXACT' ? 'danger' : 'warning'" size="small">
              {{ group.matchType === 'EXACT' ? '精确重复' : '高相似' }}
            </el-tag>
            <span>相似度 {{ group.similarityScore }}%</span>
          </div>
          <p class="representative">{{ group.representativeContent }}</p>
          <div class="governance-stack">
            <div v-for="question in group.questions" :key="question.id" class="question-item">
              <div class="card-head">
                <strong>#{{ question.id }}</strong>
                <span>{{ question.courseName || '未命名课程' }} · {{ questionTypeLabel(question.questionType) }}</span>
              </div>
              <p>{{ question.content }}</p>
              <div class="actions">
                <el-button size="small" text type="primary" :icon="Edit" @click="$emit('edit', question)"
                  >编辑</el-button
                >
                <el-button size="small" text type="primary" :icon="RefreshRight" @click="$emit('review', question)">
                  复审
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </el-drawer>

  <el-drawer v-model="correctionOpen" title="题目纠错记录" size="560px" destroy-on-close>
    <div class="governance-stack">
      <div class="toolbar">
        <el-select
          :model-value="correctionStatus"
          placeholder="处理状态"
          clearable
          style="width: 150px"
          @update:model-value="$emit('update:correctionStatus', $event)"
          @change="$emit('refreshCorrections')"
        >
          <el-option label="待处理" value="OPEN" />
          <el-option label="已处理" value="RESOLVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button :icon="RefreshRight" :loading="correctionLoading" @click="$emit('refreshCorrections')"
          >刷新</el-button
        >
      </div>
      <div v-loading="correctionLoading" class="governance-stack">
        <el-empty v-if="correctionReports.length === 0 && !correctionLoading" description="暂无题目纠错记录" />
        <el-card v-for="report in correctionReports" :key="report.id" shadow="never">
          <div class="card-head">
            <div>
              <strong>#{{ report.questionId }}</strong>
              <span>{{ reportTypeLabel(report.reportType) }} · {{ report.reporterName || '用户' }}</span>
            </div>
            <el-tag size="small" :type="correctionStatusTag(report.status)">
              {{ correctionStatusLabel(report.status) }}
            </el-tag>
          </div>
          <p class="representative">{{ report.questionContent || '题目内容不可用' }}</p>
          <p class="description">{{ report.description }}</p>
          <div v-if="report.handlerComment" class="muted">
            {{ report.handlerName || '管理员' }}：{{ report.handlerComment }}
          </div>
          <div class="card-head">
            <span>{{ report.createTime }}</span>
            <div v-if="report.status === 'OPEN'">
              <el-button size="small" text type="primary" @click="$emit('processCorrection', report, 'RESOLVED')">
                标记已处理
              </el-button>
              <el-button size="small" text type="danger" @click="$emit('processCorrection', report, 'REJECTED')">
                驳回
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
      <div class="pagination">
        <el-pagination
          :current-page="correctionPageNum"
          :page-size="correctionPageSize"
          :total="correctionTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @update:current-page="$emit('update:correctionPageNum', $event)"
          @update:page-size="$emit('update:correctionPageSize', $event)"
          @current-change="$emit('refreshCorrections')"
          @size-change="$emit('refreshCorrections')"
        />
      </div>
    </div>
  </el-drawer>

  <el-drawer v-model="versionOpen" title="题目版本记录" size="620px" destroy-on-close>
    <div class="governance-stack">
      <div class="summary">
        <span v-if="versionQuestion">#{{ versionQuestion.id }} · {{ versionQuestion.content }}</span>
        <el-button :icon="RefreshRight" :loading="versionLoading" @click="$emit('refreshVersions')">刷新</el-button>
      </div>
      <div v-loading="versionLoading">
        <el-empty v-if="questionVersions.length === 0 && !versionLoading" description="暂无版本记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="version in questionVersions"
            :key="version.id"
            :timestamp="version.createTime"
            placement="top"
          >
            <el-card shadow="never">
              <div class="card-head">
                <div>
                  <strong>v{{ version.versionNo }}</strong>
                  <span>{{ changeTypeLabel(version.changeType) }} · {{ version.operatorName || '系统' }}</span>
                </div>
                <el-tag size="small" :type="changeTypeTag(version.changeType)">
                  {{ changeTypeLabel(version.changeType) }}
                </el-tag>
              </div>
              <p v-if="version.changeSummary" class="muted">{{ version.changeSummary }}</p>
              <el-collapse>
                <el-collapse-item v-if="version.snapshotBefore" title="变更前快照" name="before">
                  <pre>{{ formatQuestionSnapshot(version.snapshotBefore) }}</pre>
                </el-collapse-item>
                <el-collapse-item v-if="version.snapshotAfter" title="变更后快照" name="after">
                  <pre>{{ formatQuestionSnapshot(version.snapshotAfter) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Edit, RefreshRight } from '@element-plus/icons-vue'
import type {
  QuestionCorrectionReportVO,
  QuestionDuplicateGroupVO,
  QuestionVersionVO,
  QuestionVO,
} from '@/api/question'
import {
  changeTypeLabel,
  changeTypeTag,
  correctionStatusLabel,
  correctionStatusTag,
  formatQuestionSnapshot,
  questionTypeLabel,
  reportTypeLabel,
} from './questionManagePresentation'

const props = defineProps<{
  duplicateVisible: boolean
  duplicateGroups: QuestionDuplicateGroupVO[]
  correctionVisible: boolean
  correctionLoading: boolean
  correctionReports: QuestionCorrectionReportVO[]
  correctionStatus: string
  correctionPageNum: number
  correctionPageSize: number
  correctionTotal: number
  versionVisible: boolean
  versionLoading: boolean
  versionQuestion: QuestionVO | null
  questionVersions: QuestionVersionVO[]
}>()

const emit = defineEmits<{
  'update:duplicateVisible': [value: boolean]
  'update:correctionVisible': [value: boolean]
  'update:correctionStatus': [value: string]
  'update:correctionPageNum': [value: number]
  'update:correctionPageSize': [value: number]
  'update:versionVisible': [value: boolean]
  edit: [question: QuestionVO]
  review: [question: QuestionVO]
  refreshCorrections: []
  processCorrection: [report: QuestionCorrectionReportVO, status: 'RESOLVED' | 'REJECTED']
  refreshVersions: []
}>()

const duplicateOpen = computed({
  get: () => props.duplicateVisible,
  set: (value) => emit('update:duplicateVisible', value),
})
const correctionOpen = computed({
  get: () => props.correctionVisible,
  set: (value) => emit('update:correctionVisible', value),
})
const versionOpen = computed({
  get: () => props.versionVisible,
  set: (value) => emit('update:versionVisible', value),
})
</script>

<style scoped>
.governance-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.summary,
.toolbar,
.card-head,
.actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}
.summary {
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  color: var(--lp-text-muted);
  padding: 12px 14px;
}
.summary strong {
  color: var(--lp-text-primary);
  font-size: 20px;
}
.card-head span,
.muted {
  color: var(--lp-text-muted);
  font-size: 12px;
}
.representative {
  color: var(--lp-text-primary);
  font-weight: 600;
  line-height: 1.6;
}
.question-item,
.description {
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  padding: 10px;
}
.description {
  color: var(--lp-text-regular);
  line-height: 1.6;
}
.pagination {
  display: flex;
  justify-content: flex-end;
}
pre {
  background: #101418;
  border-radius: 8px;
  color: #d6e2ee;
  font:
    12px/1.5 ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    monospace;
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 720px) {
  .summary,
  .toolbar,
  .card-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
