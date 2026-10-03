<template>
  <div class="user-manage admin-page">
    <header class="admin-page-header">
      <div>
        <h1>用户管理</h1>
      </div>
      <div class="admin-header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
      </div>
    </header>

    <LpStatePanel
      :state="statsLoading ? 'loading' : statsError ? 'error' : statsLoaded ? 'ready' : 'loading'"
      title="用户统计暂时无法读取"
      :description="statsError"
      loading-label="正在读取用户统计"
      @retry="fetchStats"
    >
      <section class="admin-summary-grid">
        <el-card v-for="item in statCards" :key="item.label" shadow="never" class="admin-summary-card">
          <span class="admin-summary-icon" :class="item.className">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <div class="admin-summary-copy">
            <p class="admin-summary-label">{{ item.label }}</p>
            <div class="admin-summary-value">{{ item.value }}</div>
          </div>
        </el-card>
      </section>
    </LpStatePanel>

    <el-card shadow="never" class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名/昵称"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @clear="fetchUsers"
            @keyup.enter="fetchUsers"
          />
          <el-select v-model="filterRole" placeholder="角色" clearable style="width: 130px" @change="fetchUsers">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="fetchUsers">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button :icon="Search" @click="fetchUsers">查询</el-button>
        </div>
        <span v-if="hasLoaded && !listError" class="table-summary">当前筛选 {{ total }} 位用户</span>
      </div>

      <div v-if="selectedUsers.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy"
          >已选择 <strong>{{ selectedUsers.length }}</strong> 位用户</span
        >
        <div class="admin-bulk-actions">
          <el-button size="small" :icon="CircleCheck" :disabled="actionPending" @click="handleBulkStatus(1)"
            >批量启用</el-button
          >
          <el-button size="small" :icon="CircleClose" :disabled="actionPending" @click="handleBulkStatus(0)"
            >批量禁用</el-button
          >
          <el-button size="small" @click="clearUserSelection">清空选择</el-button>
        </div>
      </div>

      <LpStatePanel v-if="loading" state="loading" loading-label="正在读取用户" />
      <LpStatePanel
        v-else-if="listError"
        state="error"
        title="用户暂时无法读取"
        :description="listError"
        @retry="fetchUsers"
      />
      <template v-else>
        <p v-if="actionError" class="admin-inline-error" role="alert">{{ actionError }}</p>
        <el-table
          ref="userTableRef"
          :data="users"
          stripe
          class="admin-data-table"
          @selection-change="handleUserSelectionChange"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="nickname" label="昵称" min-width="120">
            <template #default="{ row }">
              {{ (row as AdminUserVO).nickname || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="(row as AdminUserVO).role === 'ADMIN' ? 'danger' : 'info'" size="small">
                {{ (row as AdminUserVO).role === 'ADMIN' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="(row as AdminUserVO).status === 1 ? 'success' : 'warning'" size="small">
                {{ (row as AdminUserVO).status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="AI 日配额" width="130" align="center">
            <template #default="{ row }">
              <span v-if="(row as AdminUserVO).aiDailyQuota == null">继承全局</span>
              <el-tag v-else-if="(row as AdminUserVO).aiDailyQuota === 0" type="success" size="small">不限次数</el-tag>
              <span v-else>{{ (row as AdminUserVO).aiDailyQuota }} 次</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="170" />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <div class="admin-row-actions">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :icon="UserFilled"
                  @click="openRoleDialog(row as AdminUserVO)"
                  >改角色</el-button
                >
                <el-button
                  :type="(row as AdminUserVO).status === 1 ? 'warning' : 'success'"
                  link
                  size="small"
                  :icon="SwitchButton"
                  @click="toggleStatus(row as AdminUserVO)"
                >
                  {{ (row as AdminUserVO).status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-dropdown
                  trigger="click"
                  @command="(command) => handleUserRowCommand(command as string, row as AdminUserVO)"
                >
                  <el-button link size="small" :icon="MoreFilled">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="reset" :icon="Key">重置密码</el-dropdown-item>
                      <el-dropdown-item command="quota" :icon="Cpu">AI 配额</el-dropdown-item>
                      <el-dropdown-item command="delete" :icon="Delete" class="danger-dropdown-item"
                        >删除用户</el-dropdown-item
                      >
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty class="admin-table-empty" description="没有匹配的用户">
              <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
            </el-empty>
          </template>
        </el-table>
      </template>

      <!-- 分页 -->
      <div v-if="hasLoaded && !listError" class="admin-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchUsers"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>

    <UserMaintenanceDialogs ref="userDialogs" @refresh="handleDialogRefresh" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CircleClose,
  CircleCheck,
  Cpu,
  Delete,
  Key,
  MoreFilled,
  Plus,
  Search,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
import type { AdminUserVO } from '@/api/adminUser'
import UserMaintenanceDialogs from './user/UserMaintenanceDialogs.vue'
import { useAdminUserList } from './user/useAdminUserList'

const {
  users,
  userTableRef,
  selectedUsers,
  loading,
  listError,
  statsError,
  statsLoading,
  statsLoaded,
  hasLoaded,
  actionPending,
  actionError,
  keyword,
  filterRole,
  filterStatus,
  currentPage,
  pageSize,
  total,
  userStats,
  fetchUsers,
  fetchStats,
  handleUserSelectionChange,
  clearUserSelection,
  toggleStatus,
  handleBulkStatus,
  handleDelete,
} = useAdminUserList()
const userDialogs = ref<InstanceType<typeof UserMaintenanceDialogs>>()

const statCards = computed(() => [
  {
    label: '用户总数',
    value: userStats.total,
    icon: User,
    className: 'summary-total',
  },
  {
    label: '已启用',
    value: userStats.active,
    icon: CircleCheck,
    className: 'summary-active',
  },
  {
    label: '已禁用',
    value: userStats.disabled,
    icon: CircleClose,
    className: 'summary-disabled',
  },
  {
    label: '管理员',
    value: userStats.admins,
    icon: UserFilled,
    className: 'summary-admin',
  },
])

const handleUserRowCommand = async (command: string, user: AdminUserVO) => {
  if (actionPending.value) return
  if (command === 'reset') return userDialogs.value?.openResetPassword(user)
  if (command === 'quota') return userDialogs.value?.openQuota(user)
  if (command === 'delete') return handleDelete(user.id)
}

function openCreateDialog() {
  userDialogs.value?.openCreate()
}

function openRoleDialog(user: AdminUserVO) {
  userDialogs.value?.openRole(user)
}

function handleDialogRefresh(statsChanged: boolean) {
  void fetchUsers()
  if (statsChanged) void fetchStats()
}
</script>

<style scoped>
.summary-active {
  color: var(--lp-success);
  background: #eef8f2;
}

.summary-disabled {
  color: var(--lp-warning);
  background: #fff7e6;
}

.summary-admin {
  color: var(--lp-danger);
  background: #fff1f0;
}

.table-summary {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.admin-inline-error {
  margin: 0 0 var(--lp-space-3);
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
</style>
