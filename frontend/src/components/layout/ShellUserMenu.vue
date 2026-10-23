<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, ChartLine, LogOut, PenLine, Settings2, UserRound } from '@lucide/vue'
import { useUserStore } from '@/stores/user'

/**
 * Account disclosure in the learner bar: a list of links that opens under the
 * avatar. Escape or a click elsewhere closes it and focus returns to the avatar.
 * Administrators also find the admin entry and the AI usage alerts here.
 */
const props = defineProps<{ alertCount?: number }>()
const emit = defineEmits<{ alerts: [] }>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const panelId = `${useId()}-account`
const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)

const user = computed(() => userStore.userInfo)
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const name = computed(() => user.value?.nickname || user.value?.username || '用户')
const initial = computed(() => name.value.charAt(0).toUpperCase())
const hasAlerts = computed(() => isAdmin.value && (props.alertCount ?? 0) > 0)

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    panel.value?.querySelector<HTMLElement>('a, button')?.focus()
  }
}

function close(returnFocus = false) {
  if (!open.value) return
  open.value = false
  if (returnFocus) trigger.value?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) close(true)
}

function onPointerDown(event: PointerEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) close()
}

function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (next && root.value && !root.value.contains(next)) close()
}

function openAlerts() {
  close()
  emit('alerts')
}

function logout() {
  close()
  userStore.clearLoginInfo()
  void router.push('/login')
}

watch(
  () => route.fullPath,
  () => close(),
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onPointerDown)
})
</script>

<template>
  <div ref="root" class="account" @focusout="onFocusOut">
    <button
      ref="trigger"
      type="button"
      class="avatar"
      :aria-label="hasAlerts ? `用户菜单，有 ${alertCount} 条运营提醒` : '用户菜单'"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="toggle"
    >
      <img v-if="user?.avatar" :src="user.avatar" alt="" class="avatar-image" />
      <span v-else aria-hidden="true">{{ initial }}</span>
      <span v-if="hasAlerts" class="avatar-dot" aria-hidden="true" />
    </button>

    <div :id="panelId" ref="panel" class="panel" :data-open="open || undefined" :inert="!open">
      <div class="who">
        <strong class="who-name">{{ name }}</strong>
        <span class="who-role">{{ isAdmin ? '管理员' : '学习者' }}</span>
      </div>
      <ul class="entries">
        <li>
          <RouterLink class="entry" to="/profile" @click="close()">
            <UserRound :size="16" aria-hidden="true" />个人中心
          </RouterLink>
        </li>
        <li>
          <RouterLink class="entry" to="/learning-diagnosis" @click="close()">
            <ChartLine :size="16" aria-hidden="true" />学习诊断
          </RouterLink>
        </li>
        <li>
          <RouterLink class="entry" to="/submit" @click="close()">
            <PenLine :size="16" aria-hidden="true" />题目投稿
          </RouterLink>
        </li>
        <template v-if="isAdmin">
          <li class="rule" role="separator" />
          <li>
            <button type="button" class="entry" @click="openAlerts">
              <Bell :size="16" aria-hidden="true" />运营提醒
              <span v-if="alertCount" class="count">{{ alertCount }}</span>
            </button>
          </li>
          <li>
            <a class="entry" href="/admin/"> <Settings2 :size="16" aria-hidden="true" />进入管理系统 </a>
          </li>
        </template>
        <li class="rule" role="separator" />
        <li>
          <button type="button" class="entry" @click="logout"><LogOut :size="16" aria-hidden="true" />退出登录</button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.account {
  position: relative;
}

.avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: var(--lt-control-md);
  height: var(--lt-control-md);
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.avatar:hover,
.avatar[aria-expanded='true'] {
  background: var(--lt-fill-hover);
}

.avatar-image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 9px;
  height: 9px;
  border: 2px solid var(--lt-canvas);
  border-radius: 50%;
  background: var(--lt-orange);
}

.panel {
  position: absolute;
  z-index: 1;
  top: calc(100% + 10px);
  right: 0;
  width: 232px;
  padding: 6px;
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-float);
  -webkit-backdrop-filter: blur(40px) saturate(180%);
  backdrop-filter: blur(40px) saturate(180%);
  opacity: 0;
  visibility: hidden;
  transform: scale(0.96);
  transform-origin: top right;
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy),
    visibility 0s linear var(--lt-dur-fade);
}

.panel[data-open] {
  opacity: 1;
  visibility: visible;
  transform: scale(1);
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.who {
  display: grid;
  gap: 2px;
  padding: 10px 12px 8px;
}

.who-name {
  overflow: hidden;
  font-size: var(--lt-text-subhead);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.who-role {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.entries {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rule {
  height: 0.5px;
  margin: 6px 10px;
  background: var(--lt-separator);
}

.entry {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: transparent;
  color: var(--lt-label);
  font: inherit;
  font-size: var(--lt-text-subhead);
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.entry svg {
  flex: none;
  color: var(--lt-label-2);
}

.entry:hover {
  background: var(--lt-fill-3);
}

.count {
  margin-left: auto;
  padding: 0 7px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-caption);
  font-variant-numeric: tabular-nums;
  line-height: 20px;
}
</style>
