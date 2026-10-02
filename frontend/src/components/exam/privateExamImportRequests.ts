import {
  confirmPrivateExamDocx,
  confirmPrivateExamImport,
  confirmPrivateExamPdf,
  createPrivateExamDocxDraft,
  createPrivateExamDraft,
  createPrivateExamPdfDraft,
  previewPrivateExamDocx,
  previewPrivateExamImport,
  previewPrivateExamPdf,
} from '@/api/exam'
import type { PrivateExamImportPreview, PrivateExamImportRequest } from '@/api/exam'

interface FileMetadata {
  title: string
  courseId: number
  duration: number
}
const inlineErrors = { errorDisplay: 'inline' } as const

export function previewPrivateExamSource(form: PrivateExamImportRequest, sourceFile: File | null) {
  const metadata = fileMetadata(form)
  if (form.sourceFormat === 'PDF' && sourceFile) return previewPrivateExamPdf(metadata, sourceFile, inlineErrors)
  if (form.sourceFormat === 'DOCX' && sourceFile) return previewPrivateExamDocx(metadata, sourceFile, inlineErrors)
  return previewPrivateExamImport(form, inlineErrors)
}

export function confirmPrivateExamSource(
  form: PrivateExamImportRequest,
  preview: PrivateExamImportPreview,
  sourceFile: File | null,
) {
  const metadata = { ...fileMetadata(form), expectedContentHash: preview.contentHash, confirmed: true as const }
  if (form.sourceFormat === 'PDF' && sourceFile) return confirmPrivateExamPdf(metadata, sourceFile, inlineErrors)
  if (form.sourceFormat === 'DOCX' && sourceFile) return confirmPrivateExamDocx(metadata, sourceFile, inlineErrors)
  return confirmPrivateExamImport({ ...form, expectedContentHash: preview.contentHash, confirmed: true }, inlineErrors)
}

export function createPrivateExamAnswerDraft(
  form: PrivateExamImportRequest,
  preview: PrivateExamImportPreview,
  sourceFile: File | null,
) {
  const metadata = { ...fileMetadata(form), expectedContentHash: preview.contentHash }
  if (form.sourceFormat === 'PDF' && sourceFile) return createPrivateExamPdfDraft(metadata, sourceFile, inlineErrors)
  if (form.sourceFormat === 'DOCX' && sourceFile) return createPrivateExamDocxDraft(metadata, sourceFile, inlineErrors)
  return createPrivateExamDraft({ ...form, expectedContentHash: preview.contentHash }, inlineErrors)
}

function fileMetadata(form: PrivateExamImportRequest): FileMetadata {
  return { title: form.title, courseId: form.courseId, duration: form.duration }
}
