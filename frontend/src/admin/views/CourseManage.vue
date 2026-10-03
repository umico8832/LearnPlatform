<template>
  <div class="course-manage admin-page">
    <header class="admin-page-header">
      <div><h1>课程管理</h1></div>
      <div class="admin-header-actions">
        <el-button type="primary" :icon="Plus" :disabled="loading" @click="openDialog()">新增课程</el-button>
      </div>
    </header>

    <el-card shadow="never" class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="keyword"
            placeholder="搜索课程名称"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            :disabled="loading"
            @clear="fetchCourses"
            @keyup.enter="fetchCourses"
          />
          <el-button :icon="Search" :loading="loading" @click="fetchCourses">查询</el-button>
        </div>
        <span v-if="!listError && !loading" class="table-summary">共 {{ courses.length }} 门课程</span>
      </div>
      <LpStatePanel v-if="loading" state="loading" loading-label="正在读取课程" />
      <LpStatePanel
        v-else-if="listError"
        state="error"
        title="课程暂时无法读取"
        :description="listError"
        @retry="fetchCourses"
      />
      <template v-else>
        <el-table v-if="courses.length" :data="courses" stripe class="admin-data-table">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="课程名称" min-width="160" />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip
            ><template #default="{ row }">{{ (row as CourseVO).description || '-' }}</template></el-table-column
          >
          <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <el-table-column prop="status" label="状态" width="80" align="center"
            ><template #default="{ row }"
              ><el-tag :type="(row as CourseVO).status === 1 ? 'success' : 'info'" size="small">{{
                (row as CourseVO).status === 1 ? '启用' : '禁用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="180" fixed="right"
            ><template #default="{ row }"
              ><el-button
                type="primary"
                link
                size="small"
                :icon="Edit"
                :disabled="Boolean(deletingId)"
                @click="openDialog(row as CourseVO)"
                >编辑</el-button
              ><el-button
                type="primary"
                link
                size="small"
                :icon="Connection"
                :disabled="Boolean(deletingId)"
                @click="goToKP(row as CourseVO)"
                >知识点</el-button
              ><el-popconfirm
                title="确定删除该课程？"
                :disabled="Boolean(deletingId)"
                @confirm="handleDelete((row as CourseVO).id)"
                ><template #reference
                  ><el-button
                    type="danger"
                    link
                    size="small"
                    :icon="Delete"
                    :loading="deletingId === (row as CourseVO).id"
                    :disabled="Boolean(deletingId) && deletingId !== (row as CourseVO).id"
                    >删除</el-button
                  ></template
                ></el-popconfirm
              ></template
            ></el-table-column
          >
        </el-table>
        <LpEmptyState v-else title="暂无课程" description="可创建课程后再维护知识点。"
          ><template #actions
            ><el-button type="primary" :icon="Plus" @click="openDialog()">新增课程</el-button></template
          ></LpEmptyState
        >
      </template>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingCourse ? '编辑课程' : '新增课程'"
      width="500px"
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="resetDialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="submitting" @submit.prevent>
        <el-form-item label="课程名称" prop="name"
          ><el-input v-model="form.name" placeholder="请输入课程名称" maxlength="100" show-word-limit
        /></el-form-item>
        <el-form-item label="描述" prop="description"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入课程描述"
            maxlength="500"
            show-word-limit
        /></el-form-item>
        <el-form-item label="排序" prop="sortOrder"
          ><el-input-number v-model="form.sortOrder" :min="0" :max="9999"
        /></el-form-item>
        <p v-if="submitError" class="admin-inline-error" role="alert">{{ submitError }}</p>
      </el-form>
      <template #footer
        ><el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="submitting" @click="handleSubmit">{{
          editingCourse ? '更新' : '创建'
        }}</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import LpEmptyState from '@/components/ui/LpEmptyState.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createCourse, deleteCourse, getAdminCoursePage, updateCourse, type CourseVO } from '@/api/course'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(true)
const listError = ref('')
const keyword = ref('')
const dialogVisible = ref(false)
const editingCourse = ref<CourseVO | null>(null)
const submitting = ref(false)
const deletingId = ref<number | null>(null)
const submitError = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', sortOrder: 0 })
const rules: FormRules = { name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }] }
let alive = true
let listVersion = 0
let writeVersion = 0
const listCurrent = (version: number, session: number) =>
  alive && version === listVersion && session === getAuthSessionVersion()
const writeCurrent = (version: number, session: number) =>
  alive && version === writeVersion && session === getAuthSessionVersion()
async function fetchCourses() {
  const version = ++listVersion
  const session = getAuthSessionVersion()
  loading.value = true
  listError.value = ''
  try {
    const res = await getAdminCoursePage(
      { pageNum: 1, pageSize: 100, keyword: keyword.value || undefined },
      { errorDisplay: 'inline' },
    )
    if (!listCurrent(version, session)) return
    if (res.code === 0 && res.data) courses.value = res.data.records ?? []
    else listError.value = res.message || '请稍后重试。'
  } catch {
    if (listCurrent(version, session)) listError.value = '请检查网络后重试。'
  } finally {
    if (listCurrent(version, session)) loading.value = false
  }
}
function openDialog(course?: CourseVO) {
  if (submitting.value || deletingId.value) return
  editingCourse.value = course ?? null
  form.name = course?.name ?? ''
  form.description = course?.description ?? ''
  form.sortOrder = course?.sortOrder ?? 0
  submitError.value = ''
  dialogVisible.value = true
}
function resetDialog() {
  if (submitting.value) return
  editingCourse.value = null
  submitError.value = ''
}
async function handleSubmit() {
  if (submitting.value || !formRef.value) return
  const version = ++writeVersion
  const session = getAuthSessionVersion()
  submitting.value = true
  submitError.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!writeCurrent(version, session) || !valid) {
    if (writeCurrent(version, session)) submitting.value = false
    return
  }
  const id = editingCourse.value?.id
  try {
    const res = id
      ? await updateCourse(id, { ...form }, { errorDisplay: 'inline' })
      : await createCourse({ ...form }, { errorDisplay: 'inline' })
    if (!writeCurrent(version, session)) return
    if (res.code !== 0) {
      submitError.value = res.message || '保存失败，请重试。'
      return
    }
    dialogVisible.value = false
    ElMessage.success(id ? '更新成功' : '创建成功')
    void fetchCourses()
  } catch {
    if (writeCurrent(version, session)) submitError.value = '保存失败，请重试。'
  } finally {
    if (writeCurrent(version, session)) submitting.value = false
  }
}
async function handleDelete(id: number) {
  if (deletingId.value) return
  const version = ++writeVersion
  const session = getAuthSessionVersion()
  deletingId.value = id
  try {
    const res = await deleteCourse(id, { errorDisplay: 'inline' })
    if (!writeCurrent(version, session)) return
    if (res.code !== 0) {
      listError.value = res.message || '删除失败，请重试。'
      return
    }
    ElMessage.success('删除成功')
    void fetchCourses()
  } catch {
    if (writeCurrent(version, session)) listError.value = '删除失败，请重试。'
  } finally {
    if (writeCurrent(version, session)) deletingId.value = null
  }
}
function goToKP(course: CourseVO) {
  router.push({ name: 'AdminKPManage', query: { courseId: course.id, courseName: course.name } })
}
const stopSession = onAuthSessionChange(() => {
  listVersion++
  writeVersion++
  courses.value = []
  dialogVisible.value = false
  submitting.value = false
  deletingId.value = null
})
onMounted(() => void fetchCourses())
onBeforeUnmount(() => {
  alive = false
  listVersion++
  writeVersion++
  stopSession()
})
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.admin-inline-error {
  margin: 0;
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
</style>
