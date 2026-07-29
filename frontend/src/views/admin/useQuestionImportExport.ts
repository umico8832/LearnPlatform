import { reactive, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import {
  downloadMarkdownTemplate,
  downloadTemplate,
  exportQuestions,
  importQuestions,
  importQuestionsMarkdown,
  type QuestionImportResult,
} from '@/api/question'

interface QuestionExportFilters {
  questionType: string
  courseId: number | null
  difficulty: number | null
}

function downloadBlob(data: BlobPart, type: string, filename: string) {
  const blob = new Blob([data], { type })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

export function useQuestionImportExport(filters: QuestionExportFilters, onImported: () => void | Promise<void>) {
  const importDialogVisible = ref(false)
  const importResultVisible = ref(false)
  const importLoading = ref(false)
  const importFile = ref<File | null>(null)
  const uploadRef: Ref<UploadInstance | undefined> = ref()
  const mdUploadRef: Ref<UploadInstance | undefined> = ref()
  const importTab = ref<'excel' | 'markdown'>('excel')
  const importResult = reactive<QuestionImportResult>({
    totalRows: 0,
    successCount: 0,
    failCount: 0,
    errors: [],
  })

  async function handleExport() {
    try {
      const response = await exportQuestions({
        questionType: filters.questionType || undefined,
        courseId: filters.courseId || undefined,
        difficulty: filters.difficulty || undefined,
      })
      downloadBlob(response.data, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', '题目导出.xlsx')
      ElMessage.success('导出成功')
    } catch {
      ElMessage.error('导出失败')
    }
  }

  async function handleDownloadTemplate() {
    try {
      const response = await downloadTemplate()
      downloadBlob(
        response.data,
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        '题目导入模板.xlsx',
      )
    } catch {
      ElMessage.error('模板下载失败')
    }
  }

  async function handleDownloadMdTemplate() {
    try {
      const response = await downloadMarkdownTemplate()
      downloadBlob(response.data, 'text/markdown; charset=utf-8', '题目导入模板.md')
    } catch {
      ElMessage.error('模板下载失败')
    }
  }

  function onImportFileChange(file: UploadFile) {
    importFile.value = file.raw || null
  }

  const onMdFileChange = onImportFileChange

  async function handleImport() {
    if (!importFile.value) {
      ElMessage.warning('请先选择文件')
      return
    }
    importLoading.value = true
    try {
      const response =
        importTab.value === 'markdown'
          ? await importQuestionsMarkdown(importFile.value)
          : await importQuestions(importFile.value)
      Object.assign(importResult, response.data)
      importDialogVisible.value = false
      importResultVisible.value = true
      importFile.value = null
      uploadRef.value?.clearFiles()
      mdUploadRef.value?.clearFiles()
      if (response.data.successCount > 0) {
        await onImported()
      }
    } catch {
      ElMessage.error('导入失败')
    } finally {
      importLoading.value = false
    }
  }

  return {
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
  }
}
