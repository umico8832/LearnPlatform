<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'

/**
 * Learner primary navigation. One highlight sits behind the current section and
 * glides to the next one on navigation, so the bar shows where you moved from.
 * Each section also owns the sub-pages reached from it (wrong questions belong to review).
 */
const ITEMS = [
  { to: '/my-courses', label: '我的课程', match: ['/my-courses'] },
  { to: '/courses', label: '课程库', match: ['/courses'] },
  { to: '/practice', label: '练习', match: ['/practice', '/questions', '/favorites'] },
  { to: '/review', label: '复习', match: ['/review', '/wrong-questions'] },
  { to: '/exams', label: '考试', match: ['/exams'] },
  { to: '/community', label: '社区', match: ['/community', '/submit'] },
]

const route = useRoute()
const list = ref<HTMLElement | null>(null)
const links = new Map<string, HTMLElement>()
const x = useSpring(0, springs.layout)
const w = useSpring(0, springs.layout)
const shown = ref(false)
let placed = false
let observer: ResizeObserver | undefined

const activeTo = computed(
  () =>
    ITEMS.find((item) => item.match.some((prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`)))?.to,
)

function bind(to: string, el: unknown) {
  const node = (el as { $el?: unknown } | null)?.$el ?? el
  if (node instanceof HTMLElement) links.set(to, node)
  else links.delete(to)
}

async function place() {
  await nextTick()
  const link = activeTo.value ? links.get(activeTo.value) : undefined
  if (!link) {
    shown.value = false
    placed = false
    return
  }
  if (placed && shown.value) {
    x.set(link.offsetLeft)
    w.set(link.offsetWidth)
  } else {
    x.jump(link.offsetLeft)
    w.jump(link.offsetWidth)
    placed = true
  }
  shown.value = true
}

watch(activeTo, () => void place())

onMounted(() => {
  void place()
  if (typeof ResizeObserver !== 'undefined' && list.value) {
    observer = new ResizeObserver(() => {
      const link = activeTo.value ? links.get(activeTo.value) : undefined
      if (!link) return
      x.jump(link.offsetLeft)
      w.jump(link.offsetWidth)
    })
    observer.observe(list.value)
  }
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <nav class="shell-nav" aria-label="主导航">
    <div ref="list" class="list">
      <span
        class="highlight"
        :data-shown="shown || undefined"
        aria-hidden="true"
        :style="{ transform: `translate3d(${x.value.value}px, 0, 0)`, width: `${w.value.value}px` }"
      />
      <RouterLink
        v-for="item in ITEMS"
        :key="item.to"
        :ref="(el) => bind(item.to, el)"
        :to="item.to"
        class="item"
        :class="{ 'is-active': activeTo === item.to }"
        :aria-current="activeTo === item.to ? 'page' : undefined"
      >
        {{ item.label }}
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.list {
  position: relative;
  display: flex;
  gap: 2px;
}

.highlight {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  opacity: 0;
  transition: opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.highlight[data-shown] {
  opacity: 1;
}

.item {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--lt-radius-pill);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  text-decoration: none;
  white-space: nowrap;
  transition:
    color var(--lt-dur-fade) var(--lt-ease-fade),
    background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.item:hover {
  color: var(--lt-label);
}

.item:not(.is-active):hover {
  background: var(--lt-fill-3);
}

/* 当前项只换颜色不加粗：字宽不变，高亮测量与相邻项位置才稳定。 */
.item.is-active {
  color: var(--lt-label);
}
</style>
