<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 阶段测评设置弹窗：选择课程整体或单个已审查知识点范围。
 * 行为契约与 CourseOverviewView 保持一致（E2E 依赖弹窗标题与按钮文案）。
 */
const props = withDefaults(
  defineProps<{
    visible: boolean
    starting: boolean
    knowledgePoints: { id: number; title: string }[]
  }>(),
  { visible: false, starting: false },
)

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'start', knowledgePointId: number): void
}>()

/** el-dialog 使用 v-model 需要可写代理。 */
const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const knowledgePointId = ref(0)

watch(
  () => props.visible,
  (value) => {
    if (value) knowledgePointId.value = 0
  },
)

function handleStart() {
  emit('start', knowledgePointId.value)
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="开始阶段测评" width="min(480px, 94vw)" :close-on-click-modal="false">
    <p class="assessment-setup-note">
      默认从整门课程选题；也可以限定在单个已审查知识点内，只从该知识点关联的可见已发布客观题选题。
    </p>
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="知识点范围">
        <el-select v-model="knowledgePointId" placeholder="选择知识点范围" class="assessment-scope-select">
          <el-option :value="0" label="课程整体测评" />
          <el-option v-for="item in knowledgePoints" :key="item.id" :value="item.id" :label="item.title" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="starting" @click="handleStart">开始测评</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.assessment-setup-note {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.assessment-scope-select {
  width: 100%;
}
</style>
