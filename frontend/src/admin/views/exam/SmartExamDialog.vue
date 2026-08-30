<template>
  <el-dialog v-model="visible" title="智能组卷" width="750px" destroy-on-close>
    <template v-if="!preview">
      <el-form :model="form" label-width="100px">
        <el-form-item label="课程">
          <el-select v-model="form.courseId" placeholder="全部课程" clearable style="width: 100%">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目数量">
          <el-slider v-model="form.questionCount" :min="5" :max="50" :step="5" show-stops show-input />
        </el-form-item>
        <el-form-item label="难度模式">
          <el-radio-group v-model="form.difficultyMode">
            <el-radio-button value="ADAPTIVE">自适应</el-radio-button>
            <el-radio-button value="BALANCED">均衡</el-radio-button>
            <el-radio-button value="EASY">偏基础</el-radio-button>
            <el-radio-button value="HARD">偏进阶</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="考试时长">
          <el-input-number v-model="form.duration" :min="10" :max="300" :step="10" /> 分钟
        </el-form-item>
        <el-form-item label="包含错题">
          <el-switch v-model="form.includeWrongQuestions" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="试卷标题">
          <el-input v-model="form.title" placeholder="留空则自动生成" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon style="margin-top: 8px">
        <template #title>智能组卷将根据知识点覆盖度和难度分布自动选题，自适应模式会参考历史答题表现</template>
      </el-alert>
    </template>

    <template v-else>
      <div class="smart-preview">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="试卷名称">{{ preview.title }}</el-descriptions-item>
          <el-descriptions-item label="题目数量">{{ preview.questionCount }} 道</el-descriptions-item>
          <el-descriptions-item label="总分">{{ preview.totalScore }} 分</el-descriptions-item>
          <el-descriptions-item label="考试时长">{{ preview.duration }} 分钟</el-descriptions-item>
        </el-descriptions>

        <el-alert :title="preview.recommendation" type="success" :closable="false" show-icon style="margin: 12px 0" />

        <el-row :gutter="16" style="margin-bottom: 12px">
          <el-col :span="12">
            <div class="preview-card">
              <h4>知识点覆盖</h4>
              <div v-for="(count, name) in preview.knowledgePointDistribution" :key="name" class="dist-item">
                <span class="dist-label">{{ name }}</span>
                <el-progress
                  :percentage="Math.round((count / preview.questionCount) * 100)"
                  :stroke-width="14"
                  :text-inside="true"
                />
              </div>
              <el-empty
                v-if="Object.keys(preview.knowledgePointDistribution).length === 0"
                description="无知识点数据"
                :image-size="40"
              />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="preview-card">
              <h4>难度分布</h4>
              <div v-for="(count, level) in preview.difficultyDistribution" :key="level" class="dist-item">
                <span class="dist-label">{{ level }}</span>
                <el-progress
                  :percentage="Math.round((count / preview.questionCount) * 100)"
                  :stroke-width="14"
                  :text-inside="true"
                  :color="difficultyColor(level as string)"
                />
              </div>
              <el-empty
                v-if="Object.keys(preview.difficultyDistribution).length === 0"
                description="无难度数据"
                :image-size="40"
              />
            </div>
          </el-col>
        </el-row>

        <p class="preview-desc">{{ preview.description }}</p>
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="!preview" type="primary" :loading="loading" @click="generatePreview">生成预览</el-button>
      <template v-else>
        <el-button @click="preview = null">返回调整</el-button>
        <el-button type="primary" :loading="loading" @click="confirmCreate">确认创建</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { smartExamCreate, smartExamPreview, type SmartExamPreview, type SmartExamRequest } from '@/api/exam'
import { errorMessage } from '@/utils/errors'

defineProps<{ courses: Array<{ id: number; name: string }> }>()
const emit = defineEmits<{ created: [] }>()

const visible = ref(false)
const loading = ref(false)
const preview = ref<SmartExamPreview | null>(null)
const form = ref<SmartExamRequest>(initialForm())

function initialForm(): SmartExamRequest {
  return {
    courseId: undefined,
    questionCount: 20,
    difficultyMode: 'ADAPTIVE',
    includeWrongQuestions: true,
    title: '',
    duration: 60,
  }
}

function open() {
  preview.value = null
  form.value = initialForm()
  visible.value = true
}

async function generatePreview() {
  loading.value = true
  try {
    const response = await smartExamPreview(form.value)
    if (response.code === 0 && response.data) {
      preview.value = response.data
    } else {
      ElMessage.error(response.message || '智能组卷预览失败')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '智能组卷预览失败，请确保题库中有足够题目'))
  } finally {
    loading.value = false
  }
}

async function confirmCreate() {
  if (!preview.value) return
  loading.value = true
  try {
    const response = await smartExamCreate(preview.value)
    if (response.code === 0) {
      ElMessage.success(`智能试卷「${response.data?.title}」已创建，共 ${response.data?.questionCount} 题`)
      visible.value = false
      preview.value = null
      emit('created')
    } else {
      ElMessage.error(response.message || '创建失败')
    }
  } catch {
    ElMessage.error('创建失败')
  } finally {
    loading.value = false
  }
}

function difficultyColor(level: string): string {
  if (level.includes('★★★★★')) return '#f56c6c'
  if (level.includes('★★★★')) return '#e6a23c'
  if (level.includes('★★★')) return '#409eff'
  if (level.includes('★★')) return '#67c23a'
  return '#909399'
}

defineExpose({ open })
</script>

<style scoped>
.preview-card {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.preview-card h4 {
  margin: 0 0 12px;
  color: #303133;
  font-size: 14px;
}
.dist-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.dist-label {
  min-width: 60px;
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}
.dist-item .el-progress {
  flex: 1;
}
.preview-desc {
  margin: 8px 0 0;
  color: #909399;
  font-size: 13px;
}
</style>
