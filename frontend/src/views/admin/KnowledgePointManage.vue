<template>
  <div class="kp-manage">
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="router.push('/admin/courses')">返回</el-button>
        <h2>{{ courseName }} - 知识点管理</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增知识点</el-button>
    </div>

    <el-card shadow="never">
      <div v-loading="loading">
        <el-tree
          v-if="treeData.length > 0"
          :data="treeData"
          :props="{ children: 'children', label: 'name' }"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          draggable
          :allow-drop="allowDrop"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="node-left">
                <el-icon v-if="data.children && data.children.length > 0" color="#409eff">
                  <Folder />
                </el-icon>
                <el-icon v-else color="#67c23a"><Document /></el-icon>
                <span class="node-name">{{ data.name }}</span>
                <span v-if="data.description" class="node-desc">{{ data.description }}</span>
              </div>
              <div class="node-right">
                <el-tag v-if="data.children && data.children.length > 0" size="small" type="info">
                  {{ data.children.length }} 子项
                </el-tag>
                <el-button type="primary" link size="small" @click.stop="openDialog(undefined, data.id)">
                  添加子知识点
                </el-button>
                <el-button type="primary" link size="small" @click.stop="openDialog(data)">
                  编辑
                </el-button>
                <el-popconfirm title="删除知识点将同时删除其子知识点，确定？" @confirm="handleDelete(data.id)">
                  <template #reference>
                    <el-button type="danger" link size="small" @click.stop>删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </template>
        </el-tree>

        <el-empty v-else-if="!loading" description="暂无知识点">
          <el-button type="primary" @click="openDialog()">新增知识点</el-button>
        </el-empty>
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingKP ? '编辑知识点' : '新增知识点'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item label="知识点名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识点名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识点描述"
            maxlength="500"
          />
        </el-form-item>
        <el-form-item label="父知识点" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="treeOptions"
            :props="{ children: 'children', label: 'name' }"
            node-key="id"
            placeholder="无（顶级知识点）"
            clearable
            check-strictly
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingKP ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Plus, Folder, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getKnowledgeTree,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
  type KnowledgePointVO,
  type KnowledgePointForm,
} from '@/api/knowledgePoint'

const route = useRoute()
const router = useRouter()

const courseId = computed(() => Number(route.query.courseId))
const courseName = computed(() => (route.query.courseName as string) || '课程')

const treeData = ref<KnowledgePointVO[]>([])
const loading = ref(false)

// 弹窗相关
const dialogVisible = ref(false)
const editingKP = ref<KnowledgePointVO | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const presetParentId = ref<number | undefined>(undefined)

const form = reactive<KnowledgePointForm>({
  name: '',
  description: '',
  parentId: undefined,
  sortOrder: 0,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }],
}

/** 生成树选择器选项，排除当前编辑的节点及其子节点 */
const treeOptions = computed(() => {
  if (!editingKP.value) return treeData.value
  // 排除自身及其子节点
  function filterSelf(nodes: KnowledgePointVO[]): KnowledgePointVO[] {
    return nodes
      .filter((n) => n.id !== editingKP.value!.id)
      .map((n) => ({
        ...n,
        children: n.children ? filterSelf(n.children) : [],
      }))
  }
  return filterSelf(treeData.value)
})

/** 不允许拖拽到叶子节点内部 */
function allowDrop(_draggingNode: any, _dropNode: any, type: string) {
  if (type === 'inner') {
    return true
  }
  return true
}

async function fetchTree() {
  loading.value = true
  try {
    const res = await getKnowledgeTree(courseId.value)
    treeData.value = res.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function openDialog(kp?: KnowledgePointVO, parentId?: number) {
  editingKP.value = kp || null
  presetParentId.value = parentId
  form.name = kp?.name || ''
  form.description = kp?.description || ''
  form.parentId = kp?.parentId || parentId || undefined
  form.sortOrder = kp?.sortOrder || 0
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingKP.value) {
      await updateKnowledgePoint(editingKP.value.id, {
        name: form.name,
        description: form.description,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('更新成功')
    } else {
      await createKnowledgePoint({
        courseId: courseId.value,
        parentId: form.parentId || 0,
        name: form.name,
        description: form.description,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchTree()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteKnowledgePoint(id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch {
    // 错误已在拦截器中处理
  }
}

onMounted(() => {
  if (!courseId.value) {
    ElMessage.warning('请从课程管理页面进入')
    router.push('/admin/courses')
    return
  }
  fetchTree()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header .header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 14px;
}

.node-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-name {
  color: #303133;
  font-weight: 500;
}

.node-desc {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 8px;
}
</style>
