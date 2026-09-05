<template>
  <div class="user-manage admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">ACCESS CONTROL</p>
        <h2>用户管理</h2>
        <p class="admin-page-description">统一维护账号状态、角色权限与 AI 日配额，所有配额调整都会留下审计记录。</p>
      </div>
      <div class="admin-header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in statCards" :key="item.label" shadow="never" class="admin-summary-card">
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
        <span class="table-summary">当前筛选 {{ total }} 位用户</span>
      </div>

      <!-- 用户表格 -->
      <div v-if="selectedUsers.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy"
          >已选择 <strong>{{ selectedUsers.length }}</strong> 位用户</span
        >
        <div class="admin-bulk-actions">
          <el-button size="small" :icon="CircleCheck" @click="handleBulkStatus(1)">批量启用</el-button>
          <el-button size="small" :icon="CircleClose" @click="handleBulkStatus(0)">批量禁用</el-button>
          <el-button size="small" @click="clearUserSelection">清空选择</el-button>
        </div>
      </div>

      <el-table
        ref="userTableRef"
        :data="users"
        v-loading="loading"
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
              <el-button type="primary" link size="small" :icon="UserFilled" @click="openRoleDialog(row as AdminUserVO)"
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

      <!-- 分页 -->
      <div class="admin-pagination">
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
import { ElMessageBox } from 'element-plus'
import type { AdminUserVO } from '@/api/adminUser'
import UserMaintenanceDialogs from './user/UserMaintenanceDialogs.vue'
import { useAdminUserList } from './user/useAdminUserList'

const {
  users,
  userTableRef,
  selectedUsers,
  loading,
  keyword,
  filterRole,
  filterStatus,
  currentPage,
  pageSize,
  total,
  userStats,
  activationRate,
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
    note: '平台注册账号',
    icon: User,
    className: 'summary-total',
  },
  {
    label: '已启用',
    value: userStats.active,
    note: `${activationRate.value}% 账号可登录`,
    icon: CircleCheck,
    className: 'summary-active',
  },
  {
    label: '已禁用',
    value: userStats.disabled,
    note: '需管理员重新启用',
    icon: CircleClose,
    className: 'summary-disabled',
  },
  {
    label: '管理员',
    value: userStats.admins,
    note: '拥有后台权限',
    icon: UserFilled,
    className: 'summary-admin',
  },
])

const handleUserRowCommand = async (command: string, user: AdminUserVO) => {
  if (command === 'reset') {
    userDialogs.value?.openResetPassword(user)
    return
  }
  if (command === 'quota') {
    void userDialogs.value?.openQuota(user)
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该用户？此操作不可恢复。', '删除用户', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      await handleDelete(user.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
  }
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
  font-size: 13px;
}
</style>
