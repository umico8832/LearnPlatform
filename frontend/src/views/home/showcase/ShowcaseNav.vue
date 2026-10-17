<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronDown } from '@lucide/vue'
import { LtLinkButton } from '@/components/living'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import { isAuthenticated } from '@/utils/auth'
import { links, menus } from './showcaseContent'

/**
 * Homepage bar. At the top of the page it is part of the canvas; once the visitor
 * scrolls it gathers into a floating glass bar and stays. The two menus share one panel
 * that reshapes and slides between them instead of closing and reopening.
 */
const panelId = `${useId()}-menu`
const condensed = ref(false)
const openId = ref<string | null>(null)
const lastIndex = ref(0)
const bar = ref<HTMLElement | null>(null)
const triggers = new Map<string, HTMLButtonElement>()
const panes = new Map<string, HTMLElement>()
const x = useSpring(0, springs.layout)
const w = useSpring(0, springs.layout)
const h = useSpring(0, springs.layout)
let closeTimer = 0
let placed = false

const start = computed(() =>
  isAuthenticated() ? { to: '/my-courses', text: 'Open my courses' } : { to: '/register', text: 'Get started' },
)
const openIndex = computed(() => menus.findIndex((menu) => menu.id === openId.value))

function bindTrigger(id: string, el: unknown) {
  if (el instanceof HTMLButtonElement) triggers.set(id, el)
  else triggers.delete(id)
}

function bindPane(id: string, el: unknown) {
  if (el instanceof HTMLElement) panes.set(id, el)
  else panes.delete(id)
}

/** Fit the panel to the open pane and centre it under its trigger. */
async function place() {
  await nextTick()
  const id = openId.value
  const pane = id ? panes.get(id) : null
  const trigger = id ? triggers.get(id) : null
  if (!pane || !trigger || !bar.value) return
  const width = pane.offsetWidth
  const height = pane.offsetHeight
  const barRect = bar.value.getBoundingClientRect()
  const triggerRect = trigger.getBoundingClientRect()
  const centre = triggerRect.left + triggerRect.width / 2 - barRect.left
  const left = Math.min(Math.max(centre - width / 2, 0), barRect.width - width)
  if (!placed) {
    x.jump(left)
    w.jump(width)
    h.jump(height)
    placed = true
  } else {
    x.set(left)
    w.set(width)
    h.set(height)
  }
}

function open(id: string) {
  window.clearTimeout(closeTimer)
  if (openId.value === id) return
  if (openId.value) lastIndex.value = openIndex.value
  else placed = false
  openId.value = id
  void place()
}

function close() {
  openId.value = null
}

function closeSoon() {
  window.clearTimeout(closeTimer)
  closeTimer = window.setTimeout(close, 140)
}

function toggle(id: string) {
  if (openId.value === id) close()
  else open(id)
}

/** Keyboard: open with Arrow Down and move into the pane. */
async function enter(id: string) {
  open(id)
  await nextTick()
  panes.get(id)?.querySelector<HTMLElement>('a')?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !openId.value) return
  const id = openId.value
  close()
  triggers.get(id)?.focus()
}

function onPointerDown(event: PointerEvent) {
  if (openId.value && bar.value && !bar.value.contains(event.target as Node)) close()
}

function onScroll() {
  condensed.value = window.scrollY > 24
}

function paneState(index: number) {
  if (index === openIndex.value) return 'active'
  return index < openIndex.value ? 'before' : 'after'
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onPointerDown)
})

onBeforeUnmount(() => {
  window.clearTimeout(closeTimer)
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onPointerDown)
})
</script>

<template>
  <header class="showcase-nav" :data-condensed="condensed || undefined">
    <div ref="bar" class="bar" @pointerleave="closeSoon" @pointerenter="openId && open(openId)">
      <RouterLink class="brand" to="/">LearnPlatform</RouterLink>

      <nav class="primary" aria-label="Main">
        <button
          v-for="menu in menus"
          :key="menu.id"
          :ref="(el) => bindTrigger(menu.id, el)"
          type="button"
          class="item"
          :aria-expanded="openId === menu.id"
          :aria-controls="panelId"
          @pointerenter="open(menu.id)"
          @click="toggle(menu.id)"
          @keydown.down.prevent="enter(menu.id)"
        >
          {{ menu.label }}
          <ChevronDown class="chevron" :size="14" aria-hidden="true" />
        </button>
        <RouterLink v-for="link in links" :key="link.to" class="item" :to="link.to" @pointerenter="closeSoon">
          {{ link.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <RouterLink v-if="!isAuthenticated()" class="item" to="/login">Sign in</RouterLink>
        <LtLinkButton variant="primary" size="sm" :to="start.to">{{ start.text }}</LtLinkButton>
      </div>

      <div
        :id="panelId"
        class="panel"
        :data-open="openId ? '' : undefined"
        :style="{
          transform: `translate3d(${x.value.value}px, 0, 0)`,
          width: `${w.value.value}px`,
          height: `${h.value.value}px`,
        }"
        @pointerenter="open(openId ?? menus[0].id)"
      >
        <div
          v-for="(menu, index) in menus"
          :key="menu.id"
          :ref="(el) => bindPane(menu.id, el)"
          class="pane"
          :data-cols="menu.items.length > 3 ? 2 : 1"
          :data-state="paneState(index)"
          :inert="openId !== menu.id"
          :aria-label="menu.label"
          role="group"
        >
          <RouterLink v-for="item in menu.items" :key="item.title" class="entry" :to="item.to" @click="close">
            <span class="entry-icon"><component :is="item.icon" :size="18" aria-hidden="true" /></span>
            <span class="entry-text">
              <span class="entry-title">{{ item.title }}</span>
              <span class="entry-note">{{ item.text }}</span>
            </span>
          </RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.showcase-nav {
  position: fixed;
  z-index: 20;
  top: 0;
  right: 0;
  left: 0;
  padding: 0 var(--lt-space-4);
  pointer-events: none;
  transition: padding-top var(--lt-dur-layout) var(--lt-ease-layout);
}

.showcase-nav[data-condensed] {
  padding-top: 12px;
}

.bar {
  position: relative;
  display: flex;
  align-items: center;
  max-width: 1320px;
  height: 84px;
  margin: 0 auto;
  padding: 0 var(--lt-space-9);
  border-radius: 28px;
  background: transparent;
  box-shadow: 0 0 0 0.5px transparent;
  pointer-events: auto;
  transition:
    max-width var(--lt-dur-layout) var(--lt-ease-layout),
    height var(--lt-dur-layout) var(--lt-ease-layout),
    padding var(--lt-dur-layout) var(--lt-ease-layout),
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    backdrop-filter var(--lt-dur-fade) var(--lt-ease-fade);
}

[data-condensed] .bar {
  max-width: 1120px;
  height: 56px;
  padding: 0 10px 0 24px;
  background: var(--lt-glass);
  box-shadow:
    0 0 0 0.5px var(--lt-hairline),
    0 10px 36px light-dark(rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.45));
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  backdrop-filter: saturate(180%) blur(20px);
}

.brand {
  color: var(--lt-label);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
  text-decoration: none;
  transition: font-size var(--lt-dur-layout) var(--lt-ease-layout);
}

[data-condensed] .brand {
  font-size: var(--lt-text-headline);
}

.primary {
  display: flex;
  gap: 2px;
  margin: 0 auto;
}

.item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: transparent;
  color: var(--lt-label-2);
  font: inherit;
  font-size: var(--lt-text-subhead);
  text-decoration: none;
  cursor: pointer;
  transition:
    color var(--lt-dur-fade) var(--lt-ease-fade),
    background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.item:hover,
.item[aria-expanded='true'] {
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

.chevron {
  transition: transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.item[aria-expanded='true'] .chevron {
  transform: rotate(180deg);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
}

.brand:focus-visible,
.item:focus-visible,
.entry:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}

.panel {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  overflow: hidden;
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-float);
  opacity: 0;
  visibility: hidden;
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    visibility 0s linear var(--lt-dur-fade);
}

.panel[data-open] {
  opacity: 1;
  visibility: visible;
  transition: opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.pane {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  gap: 2px;
  width: max-content;
  padding: 10px;
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-layout) var(--lt-ease-layout);
}

.pane[data-cols='2'] {
  grid-template-columns: repeat(2, 292px);
}

.pane[data-cols='1'] {
  grid-template-columns: 300px;
}

.pane[data-state='before'] {
  opacity: 0;
  transform: translateX(-28px);
}

.pane[data-state='after'] {
  opacity: 0;
  transform: translateX(28px);
}

.entry {
  display: flex;
  gap: var(--lt-space-3);
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: var(--lt-radius-md);
  color: inherit;
  text-decoration: none;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.entry:hover {
  background: var(--lt-fill-3);
}

.entry-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
}

.entry-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.entry-title {
  color: var(--lt-label);
  font-size: var(--lt-text-subhead);
  font-weight: 600;
}

.entry-note {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
</style>
