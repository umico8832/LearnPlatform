<template>
  <div class="ca-viewer">
    <!-- 控制栏 -->
    <div class="ca-controls">
      <el-button-group size="small">
        <el-button @click="prevStep" :disabled="currentStep <= 0">
          <el-icon><DArrowLeft /></el-icon> 上一步
        </el-button>
        <el-button @click="togglePlay" :type="playing ? 'warning' : 'primary'">
          <el-icon v-if="playing"><VideoPause /></el-icon>
          <el-icon v-else><VideoPlay /></el-icon>
          {{ playing ? '暂停' : '播放' }}
        </el-button>
        <el-button @click="nextStep" :disabled="currentStep >= element.steps.length - 1">
          下一步 <el-icon><DArrowRight /></el-icon>
        </el-button>
      </el-button-group>
      <span class="ca-step-info">
        步骤 {{ currentStep + 1 }} / {{ element.steps.length }}
      </span>
      <div class="ca-speed">
        <span class="ca-speed-label">速度：</span>
        <el-select v-model="speedMs" size="small" style="width: 90px;">
          <el-option :value="500" label="快" />
          <el-option :value="1000" label="正常" />
          <el-option :value="2000" label="慢" />
        </el-select>
      </div>
    </div>

    <!-- 进度条 -->
    <div class="ca-progress-bar">
      <div
        class="ca-progress-fill"
        :style="{ width: ((currentStep + 1) / element.steps.length * 100) + '%' }"
      />
    </div>

    <!-- 主体：代码 + 变量面板 -->
    <div class="ca-body">
      <!-- 代码区域 -->
      <div class="ca-code-panel">
        <div v-if="element.language" class="ca-lang-tag">{{ element.language }}</div>
        <pre class="ca-code"><code><template v-for="(line, li) in codeLines" :key="li"><span
          :class="{
            'ca-line': true,
            'ca-line--active': isLineActive(li + 1),
            'ca-line--dim': !isLineActive(li + 1),
          }"
        ><span class="ca-line-num">{{ li + 1 }}</span>{{ line }}
</span></template></code></pre>
      </div>

      <!-- 变量面板 -->
      <div class="ca-var-panel">
        <div class="ca-var-title">📋 变量状态</div>
        <div v-if="currentStepData.variables.length === 0" class="ca-var-empty">
          暂无变量
        </div>
        <div v-else class="ca-var-list">
          <div
            v-for="(v, vi) in currentStepData.variables"
            :key="vi"
            class="ca-var-item"
            :class="{ 'ca-var-item--changed': v.changed }"
          >
            <span class="ca-var-name">{{ v.name }}</span>
            <span class="ca-var-eq">=</span>
            <span class="ca-var-value">{{ v.value }}</span>
            <span v-if="v.changed" class="ca-var-badge">changed</span>
          </div>
        </div>

        <!-- 控制台输出 -->
        <div v-if="currentStepData.output" class="ca-output">
          <div class="ca-output-title">🖨️ 输出</div>
          <pre class="ca-output-text">{{ currentStepData.output }}</pre>
        </div>
      </div>
    </div>

    <!-- 步骤描述 -->
    <div class="ca-description">
      <span class="ca-desc-icon">💬</span>
      {{ currentStepData.description }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { DArrowLeft, DArrowRight, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import type { CodeAnimationElement } from '@/api/ai'

const props = defineProps<{
  element: CodeAnimationElement
}>()

const currentStep = ref(0)
const playing = ref(false)
const speedMs = ref(1000)
let timer: ReturnType<typeof setTimeout> | null = null

const codeLines = computed(() => props.element.code.split('\n'))

const currentStepData = computed(() => props.element.steps[currentStep.value])

function isLineActive(lineNum: number): boolean {
  const step = currentStepData.value
  return lineNum >= step.lineStart && lineNum <= step.lineEnd
}

function nextStep() {
  if (currentStep.value < props.element.steps.length - 1) {
    currentStep.value++
  } else {
    playing.value = false
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function togglePlay() {
  playing.value = !playing.value
}

watch(playing, (val) => {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  if (val) {
    scheduleNext()
  }
})

watch(speedMs, () => {
  if (playing.value) {
    if (timer) clearTimeout(timer)
    scheduleNext()
  }
})

function scheduleNext() {
  timer = setTimeout(() => {
    if (!playing.value) return
    if (currentStep.value < props.element.steps.length - 1) {
      currentStep.value++
      scheduleNext()
    } else {
      playing.value = false
    }
  }, speedMs.value)
}

watch(() => props.element, () => {
  currentStep.value = 0
  playing.value = false
})

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<script lang="ts">
export default { name: 'CodeAnimationViewer' }
</script>

<style scoped>
.ca-viewer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 控制栏 */
.ca-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.ca-step-info {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}

.ca-speed {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.ca-speed-label {
  font-size: 12px;
  color: #909399;
}

/* 进度条 */
.ca-progress-bar {
  height: 4px;
  background: #e4e7ed;
  border-radius: 2px;
  overflow: hidden;
}

.ca-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border-radius: 2px;
  transition: width 0.3s ease;
}

/* 主体 */
.ca-body {
  display: flex;
  gap: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
}

/* 代码面板 */
.ca-code-panel {
  flex: 1;
  min-width: 0;
  position: relative;
  overflow-x: auto;
}

.ca-lang-tag {
  position: absolute;
  top: 4px;
  right: 8px;
  font-size: 10px;
  color: #909399;
  background: rgba(255, 255, 255, 0.08);
  padding: 1px 6px;
  border-radius: 3px;
  text-transform: uppercase;
}

.ca-code {
  margin: 0;
  padding: 12px 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.7;
  color: #d4d4d4;
  overflow-x: auto;
}

.ca-code code {
  display: block;
}

.ca-line {
  display: block;
  padding: 0 12px 0 0;
  white-space: pre;
  transition: background 0.2s ease;
}

.ca-line--active {
  background: rgba(64, 158, 255, 0.2);
  border-left: 3px solid #409eff;
  padding-left: 9px;
}

.ca-line--dim {
  padding-left: 12px;
  opacity: 0.5;
}

.ca-line-num {
  display: inline-block;
  width: 32px;
  text-align: right;
  margin-right: 12px;
  color: #636d83;
  user-select: none;
  font-size: 11px;
}

/* 变量面板 */
.ca-var-panel {
  width: 220px;
  flex-shrink: 0;
  background: #252526;
  border-left: 1px solid #3c3c3c;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ca-var-title {
  font-size: 12px;
  font-weight: 600;
  color: #cccccc;
  padding-bottom: 4px;
  border-bottom: 1px solid #3c3c3c;
}

.ca-var-empty {
  font-size: 12px;
  color: #636d83;
  padding: 4px 0;
}

.ca-var-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ca-var-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  transition: background 0.2s ease;
}

.ca-var-item--changed {
  background: rgba(255, 152, 0, 0.15);
}

.ca-var-name {
  color: #9cdcfe;
}

.ca-var-eq {
  color: #d4d4d4;
}

.ca-var-value {
  color: #ce9178;
}

.ca-var-badge {
  font-size: 9px;
  color: #fff;
  background: #e6a23c;
  padding: 0 4px;
  border-radius: 3px;
  margin-left: auto;
  font-family: sans-serif;
}

/* 输出区域 */
.ca-output {
  margin-top: 4px;
  border-top: 1px solid #3c3c3c;
  padding-top: 6px;
}

.ca-output-title {
  font-size: 11px;
  color: #cccccc;
  margin-bottom: 4px;
}

.ca-output-text {
  margin: 0;
  font-size: 11px;
  color: #6a9955;
  font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 步骤描述 */
.ca-description {
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #303133;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  line-height: 1.5;
}

.ca-desc-icon {
  flex-shrink: 0;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .ca-body {
    flex-direction: column;
  }
  .ca-var-panel {
    width: 100%;
    border-left: none;
    border-top: 1px solid #3c3c3c;
  }
  .ca-speed {
    margin-left: 0;
  }
}
</style>