<template>
  <div class="kp-manage admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">KNOWLEDGE MAP</p>
        <h2>知识点管理</h2>
        <p class="admin-page-description">维护课程「{{ courseName }}」的知识结构，父子层级会影响题目归类、诊断和复习路径。</p>
      </div>
      <div class="admin-header-actions">
        <el-button :icon="ArrowLeft" @click="router.push('/admin/courses')">返回课程</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增知识点</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in knowledgeStats" :key="item.label" shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon" :class="item.className">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">{{ item.label }}</p>
          <div class="admin-summary-value">{{ item.value }}</div>
          <div class="admin-summary-note">{{ item.note }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card knowledge-tree-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="keyword"
            placeholder="搜索知识点名称/描述"
            :prefix-icon="Search"
            clearable
            style="width: 260px"
          />
          <el-button :icon="Refresh" @click="fetchTree">刷新</el-button>
        </div>
        <span class="table-summary">当前显示 {{ visibleNodeCount }} / {{ totalNodeCount }} 个知识点</span>
      </div>

      <div v-loading="loading">
        <el-tree
          v-if="filteredTreeData.length > 0"
          :data="filteredTreeData"
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
                <el-icon v-if="data.children && data.children.length > 0" class="node-icon is-folder">
                  <Folder />
                </el-icon>
                <el-icon v-else class="node-icon is-document"><Document /></el-icon>
                <div class="node-copy">
                  <span class="node-name">{{ data.name }}</span>
                  <span v-if="data.description" class="node-desc">{{ data.description }}</span>
                </div>
              </div>
              <div class="node-right">
                <el-tag v-if="data.children && data.children.length > 0" size="small" type="info">
                  {{ data.children.length }} 子项
                </el-tag>
                <el-button type="primary" link size="small" :icon="Plus" @click.stop="openDialog(undefined, data.id)">
                  添加子知识点
                </el-button>
                <el-button type="primary" link size="small" :icon="Edit" @click.stop="openDialog(data)">
                  编辑
                </el-button>
                <el-popconfirm title="删除知识点将同时删除其子知识点，确定？" @confirm="handleDelete(data.id)">
                  <template #reference>
                    <el-button type="danger" link size="small" :icon="Delete" @click.stop>删除</el-button>
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
import {
  ArrowLeft,
  Collection,
  Delete,
  Document,
  Edit,
  Files,
  Folder,
  FolderOpened,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue'
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
const keyword = ref('')

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

const totalNodeCount = computed(() => countNodes(treeData.value))
const rootNodeCount = computed(() => treeData.value.length)
const leafNodeCount = computed(() => countLeaves(treeData.value))
const maxTreeDepth = computed(() => getMaxDepth(treeData.value))
const filteredTreeData = computed(() => filterTree(treeData.value, keyword.value.trim()))
const visibleNodeCount = computed(() => countNodes(filteredTreeData.value))

const knowledgeStats = computed(() => [
  {
    label: '知识点总数',
    value: totalNodeCount.value,
    note: '当前课程结构规模',
    icon: Collection,
    className: 'is-primary',
  },
  {
    label: '顶级节点',
    value: rootNodeCount.value,
    note: '课程主干目录',
    icon: FolderOpened,
    className: 'is-success',
  },
  {
    label: '叶子节点',
    value: leafNodeCount.value,
    note: '适合绑定题目',
    icon: Document,
    className: 'is-warning',
  },
  {
    label: '最大层级',
    value: maxTreeDepth.value,
    note: '层级过深需拆分',
    icon: Files,
    className: 'is-info',
  },
])

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

function countNodes(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countNodes(node.children || []), 0)
}

function countLeaves(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => {
    const children = node.children || []
    return sum + (children.length ? countLeaves(children) : 1)
  }, 0)
}

function getMaxDepth(nodes: KnowledgePointVO[]): number {
  if (!nodes.length) return 0
  return Math.max(...nodes.map((node) => 1 + getMaxDepth(node.children || [])))
}

function filterTree(nodes: KnowledgePointVO[], searchText: string): KnowledgePointVO[] {
  if (!searchText) return nodes
  const loweredKeyword = searchText.toLowerCase()
  return nodes.reduce<KnowledgePointVO[]>((result, node) => {
    const children = filterTree(node.children || [], searchText)
    const matched =
      node.name.toLowerCase().includes(loweredKeyword) ||
      (node.description || '').toLowerCase().includes(loweredKeyword)
    if (matched || children.length > 0) {
      result.push({ ...node, children })
    }
    return result
  }, [])
}

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
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.admin-summary-icon.is-primary {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

.admin-summary-icon.is-success {
  color: var(--lp-success);
  background: #e9f8ef;
}

.admin-summary-icon.is-warning {
  color: var(--lp-warning);
  background: #fff7df;
}

.admin-summary-icon.is-info {
  color: var(--lp-text-secondary);
  background: #eef3f8;
}

.knowledge-tree-card :deep(.el-tree) {
  --el-tree-node-hover-bg-color: #f6f8fb;
  color: var(--lp-text-primary);
}

.knowledge-tree-card :deep(.el-tree-node__content) {
  min-height: 48px;
  height: auto;
  border-radius: 8px;
  margin: 2px 0;
  padding-right: 8px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 6px 0;
  font-size: 14px;
  min-width: 0;
}

.node-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.node-icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.node-icon.is-folder {
  color: var(--lp-primary);
}

.node-icon.is-document {
  color: var(--lp-success);
}

.node-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.node-name {
  color: var(--lp-text-primary);
  font-weight: 500;
}

.node-desc {
  font-size: 12px;
  color: var(--lp-text-muted);
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 8px;
  flex: 0 0 auto;
}

@media (max-width: 768px) {
  .tree-node {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .node-right {
    flex-wrap: wrap;
    margin-left: 28px;
    margin-right: 0;
  }

  .node-desc {
    max-width: min(520px, calc(100vw - 140px));
  }
}
</style>
