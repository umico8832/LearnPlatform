<template>
  <div class="course-manage">
    <div class="page-header">
      <h2>课程管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增课程</el-button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索课程名称"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @clear="fetchCourses"
          @keyup.enter="fetchCourses"
        />
      </div>

      <el-table :data="courses" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as CourseVO).description || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as CourseVO).status === 1 ? 'success' : 'info'" size="small">
              {{ (row as CourseVO).status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row as CourseVO)">编辑</el-button>
            <el-button type="primary" link size="small" @click="goToKP(row as CourseVO)">知识点</el-button>
            <el-popconfirm title="确定删除该课程？" @confirm="handleDelete((row as CourseVO).id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingCourse ? '编辑课程' : '新增课程'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        @submit.prevent
      >
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入课程描述"
            maxlength="500"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingCourse ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getCoursePage, createCourse, updateCourse, deleteCourse, type CourseVO } from '@/api/course'

const router = useRouter()

const courses = ref<CourseVO[]>([])
const loading = ref(false)
const keyword = ref('')

// 弹窗相关
const dialogVisible = ref(false)
const editingCourse = ref<CourseVO | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  sortOrder: 0,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
}

async function fetchCourses() {
  loading.value = true
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100, keyword: keyword.value || undefined })
    courses.value = res.data.records
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function openDialog(course?: CourseVO) {
  editingCourse.value = course || null
  form.name = course?.name || ''
  form.description = course?.description || ''
  form.sortOrder = course?.sortOrder || 0
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingCourse.value) {
      await updateCourse(editingCourse.value.id, {
        name: form.name,
        description: form.description,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('更新成功')
    } else {
      await createCourse({
        name: form.name,
        description: form.description,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchCourses()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteCourse(id)
    ElMessage.success('删除成功')
    fetchCourses()
  } catch {
    // 错误已在拦截器中处理
  }
}

function goToKP(course: CourseVO) {
  router.push(`/admin/knowledge-points?courseId=${course.id}&courseName=${encodeURIComponent(course.name)}`)
}

onMounted(() => {
  fetchCourses()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.toolbar {
  margin-bottom: 16px;
}
</style>