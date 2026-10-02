import { computed, ref, type ComputedRef } from 'vue'
import type { UploadFile } from 'element-plus'
import type { PrivateExamImportRequest } from '@/api/exam'

export const PLACEHOLDER = `## 1. 单选题\n**题干**: 栈遵循哪种访问顺序？\n**选项**:\n- A. 先进先出\n- B. 先进后出\n**答案**: B\n**解析**: 栈遵循 LIFO。\n**分值**: 2`

export function initialPrivateExamCourseId(defaultCourseId?: number) {
  return Number.isFinite(defaultCourseId) && (defaultCourseId || 0) > 0 ? defaultCourseId || 0 : 0
}

export function usePrivateExamImportForm(defaultCourseId: ComputedRef<number>) {
  const sourceFile = ref<File | null>(null)
  const validationError = ref('')
  const emptyImportForm = (): PrivateExamImportRequest => ({
    title: '',
    courseId: defaultCourseId.value,
    duration: 60,
    sourceName: '',
    sourceFormat: 'MARKDOWN',
    content: '',
  })
  const importForm = ref<PrivateExamImportRequest>(emptyImportForm())
  const isFileImport = computed(() => ['PDF', 'DOCX'].includes(importForm.value.sourceFormat))
  const fileAccept = computed(() =>
    importForm.value.sourceFormat === 'PDF'
      ? 'application/pdf,.pdf'
      : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx',
  )
  const selectSourceFile = (uploadFile: UploadFile) => {
    sourceFile.value = uploadFile.raw || null
    importForm.value.sourceName = uploadFile.name
    importForm.value.content = ''
  }
  const removeSourceFile = () => {
    sourceFile.value = null
    importForm.value.sourceName = ''
  }
  const changeSourceFormat = () => {
    sourceFile.value = null
    importForm.value.sourceName = ''
    importForm.value.content = ''
  }
  const valid = () => {
    validationError.value = !importForm.value.title.trim()
      ? '请填写试卷标题。'
      : !importForm.value.courseId
        ? '请选择所属课程。'
        : isFileImport.value && !sourceFile.value
          ? '请选择需要导入的文件。'
          : !isFileImport.value && !importForm.value.sourceName.trim()
            ? '请填写原始资料名称。'
            : !isFileImport.value && !importForm.value.content.trim()
              ? '请填写原始内容。'
              : ''
    return !validationError.value
  }
  const reset = () => {
    validationError.value = ''
    sourceFile.value = null
    importForm.value = emptyImportForm()
  }
  return {
    sourceFile,
    importForm,
    isFileImport,
    fileAccept,
    selectSourceFile,
    removeSourceFile,
    changeSourceFormat,
    valid,
    validationError,
    reset,
  }
}
