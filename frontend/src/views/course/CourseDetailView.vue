<template>
  <div class="course-detail">
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
        <h2>{{ course?.name || '课程详情' }}</h2>
      </div>
    </div>

    <!-- 课程基本信息 -->
    <el-card v-if="course" class="info-card" shadow="never">
      <div class="course-info">
        <div class="info-icon">
          <el-icon :size="40" color="#409eff"><Reading /></el-icon>
        </div>
        <div class="info-content">
          <h3>{{ course.name }}</h3>
          <p>{{ course.description || '暂无描述' }}</p>
        </div>
      </div>
    </el-card>

    <!-- 知识点树 -->
    <el-card class="tree-card" shadow="never">
      <template #header>
        <div class="tree-header">
          <span>知识点树</span>
          <el-tag type="info" size="small">{{ totalKP }} 个知识点</el-tag>
        </div>
      </template>

      <div v-loading="loading">
        <el-tree
          v-if="treeData.length > 0"
          :data="treeData"
          :props="{ children: 'children', label: 'name' }"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="node-left">
                <el-icon v-if="data.children && data.children.length > 0" color="#409eff">
                  <Folder />
                </el-icon>
                <el-icon v-else color="#67c23a"><Document /></el-icon>
                <span class="node-name">{{ data.name }}</span>
              </div>
              <div class="node-right">
                <span v-if="data.description" class="node-desc">{{ data.description }}</span>
                <el-tag v-if="data.children && data.children.length > 0" size="small" type="info">
                  {{ data.children.length }} 子项
                </el-tag>
              </div>
            </div>
          </template>
        </el-tree>

        <el-empty v-else-if="!loading" description="暂无知识点" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Reading, Folder, Document } from '@element-plus/icons-vue'
import { getCourseById, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

const route = useRoute()
const router = useRouter()

const course = ref<CourseVO | null>(null)
const treeData = ref<KnowledgePointVO[]>([])
const loading = ref(false)

const courseId = computed(() => Number(route.params.id))

/** 递归统计知识点总数 */
function countNodes(nodes: KnowledgePointVO[]): number {
  let count = 0
  for (const node of nodes) {
    count++
    if (node.children && node.children.length > 0) {
      count += countNodes(node.children)
    }
  }
  return count
}

const totalKP = computed(() => countNodes(treeData.value))

async function fetchDetail() {
  loading.value = true
  try {
    const [courseRes, treeRes] = await Promise.all([
      getCourseById(courseId.value),
      getKnowledgeTree(courseId.value),
    ])
    course.value = courseRes.data.data
    treeData.value = treeRes.data.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.page-header {
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

.info-card {
  margin-bottom: 20px;
}

.course-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info-icon {
  width: 64px;
  height: 64px;
  background: #f0f7ff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info-content h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: #303133;
}

.info-content p {
  margin: 0;
  font-size: 14px;
  color: #606266;
}

.tree-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
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
}

.node-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 8px;
}

.node-desc {
  font-size: 12px;
  color: #909399;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
