<template>
  <el-dialog v-model="importResultVisible" title="导入结果" width="500px" destroy-on-close>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="总行数">{{ importResult.totalRows }}</el-descriptions-item>
      <el-descriptions-item label="成功数">
        <el-tag type="success">{{ importResult.successCount }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="失败数">
        <el-tag :type="importResult.failCount > 0 ? 'danger' : 'success'">{{ importResult.failCount }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>
    <div v-if="importResult.errors.length > 0" class="import-errors">
      <p>错误详情：</p>
      <el-scrollbar max-height="200px">
        <p v-for="(error, index) in importResult.errors" :key="index" class="import-error-item">{{ error }}</p>
      </el-scrollbar>
    </div>
    <template #footer>
      <el-button type="primary" @click="importResultVisible = false">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="importDialogVisible" title="导入题目" width="540px" destroy-on-close>
    <el-tabs v-model="importTab">
      <el-tab-pane label="Excel 导入" name="excel">
        <el-upload
          ref="uploadRef"
          drag
          accept=".xlsx,.xls"
          :auto-upload="false"
          :limit="1"
          :on-change="onImportFileChange"
          :on-exceed="warnMultipleFiles"
        >
          <el-icon class="upload-icon"><Upload /></el-icon>
          <div>将 Excel 文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="upload-tip">
              仅支持 .xlsx / .xls 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadTemplate"
                >下载模板</a
              >
            </div>
          </template>
        </el-upload>
      </el-tab-pane>
      <el-tab-pane label="Markdown 导入" name="markdown">
        <el-upload
          ref="mdUploadRef"
          drag
          accept=".md,.markdown"
          :auto-upload="false"
          :limit="1"
          :on-change="onMdFileChange"
          :on-exceed="warnMultipleFiles"
        >
          <el-icon class="upload-icon"><Upload /></el-icon>
          <div>将 Markdown 文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="upload-tip">
              仅支持 .md / .markdown 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadMdTemplate"
                >下载模板</a
              >
            </div>
          </template>
        </el-upload>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="importDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="importLoading" @click="handleImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useQuestionImportExport } from '../useQuestionImportExport'

const props = defineProps<{
  filters: {
    questionType: string
    courseId: number | null
    difficulty: number | null
  }
}>()
const emit = defineEmits<{ imported: [] }>()

const {
  importDialogVisible,
  importResultVisible,
  importLoading,
  uploadRef,
  mdUploadRef,
  importTab,
  importResult,
  handleExport,
  handleDownloadTemplate,
  handleDownloadMdTemplate,
  onImportFileChange,
  onMdFileChange,
  handleImport,
} = useQuestionImportExport(props.filters, () => emit('imported'))

function openImport() {
  importDialogVisible.value = true
}

function warnMultipleFiles() {
  ElMessage.warning('只能上传一个文件')
}

defineExpose({
  openImport,
  exportQuestions: handleExport,
  downloadExcelTemplate: handleDownloadTemplate,
  downloadMarkdownTemplate: handleDownloadMdTemplate,
})
</script>

<style scoped>
.import-errors {
  margin-top: 12px;
}

.import-errors > p {
  margin-bottom: 8px;
  color: #f56c6c;
}

.import-error-item {
  margin: 4px 0;
  color: #606266;
  font-size: 13px;
}

.upload-icon {
  margin-bottom: 8px;
  color: #c0c4cc;
  font-size: 40px;
}

.upload-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.upload-tip a {
  color: #409eff;
}
</style>
