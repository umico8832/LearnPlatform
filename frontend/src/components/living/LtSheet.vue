<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { X } from '@lucide/vue'
import { ignoreAnimationCancellation, springAnimate, springs } from '@/motion'
import LtIconButton from './LtIconButton.vue'

/**
 * Modal sheet on the native <dialog>: the browser handles the top layer,
 * inert background and Escape. It opens out of its trigger and shrinks back
 * toward it on close; focus returns to the trigger.
 */
const props = withDefaults(
  defineProps<{
    title: string
    /** Element the sheet grows out of and returns focus to. */
    origin?: HTMLElement | null
    width?: number
  }>(),
  { origin: null, width: 580 },
)

const open = defineModel<boolean>('open', { required: true })

const titleId = useId()
const dialog = ref<HTMLDialogElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const scrim = ref<HTMLElement | null>(null)
let returnFocus: HTMLElement | null = null
let running: Animation[] = []

function stopRunning() {
  for (const animation of running) animation.cancel()
  running = []
}

function track(...animations: (Animation | null)[]) {
  running = animations.filter((animation): animation is Animation => animation !== null)
  for (const animation of running) void ignoreAnimationCancellation(animation)
}

function aimAtOrigin() {
  if (!panel.value) return
  const origin = props.origin
  if (!origin) {
    panel.value.style.transformOrigin = ''
    return
  }
  const from = origin.getBoundingClientRect()
  const to = panel.value.getBoundingClientRect()
  panel.value.style.transformOrigin = `${from.left + from.width / 2 - to.left}px ${from.top + from.height / 2 - to.top}px`
}

function show() {
  const el = dialog.value
  if (!el || !panel.value || !scrim.value) return
  stopRunning()
  if (!el.open) {
    returnFocus = props.origin ?? (document.activeElement as HTMLElement | null)
    el.showModal()
  }
  aimAtOrigin()
  track(
    springAnimate(
      panel.value,
      [
        { transform: 'scale(0.12)', opacity: 0, filter: 'blur(10px)' },
        { transform: 'scale(1)', opacity: 1, filter: 'blur(0)' },
      ],
      springs.layout,
    ),
    springAnimate(scrim.value, [{ opacity: 0 }, { opacity: 1 }], springs.fade),
  )
}

function hide() {
  const el = dialog.value
  if (!el?.open || !panel.value || !scrim.value) return
  stopRunning()
  const finish = () => {
    stopRunning()
    el.close()
    returnFocus?.focus({ preventScroll: true })
    returnFocus = null
  }
  // A short exit keeps the page interactive again quickly.
  const exit = { duration: 300, fill: 'forwards' as const }
  const panelExit = springAnimate(
    panel.value,
    [
      { transform: 'scale(1)', opacity: 1 },
      { transform: 'scale(0.9)', opacity: 0 },
    ],
    springs.fade,
    exit,
  )
  track(panelExit, springAnimate(scrim.value, [{ opacity: 1 }, { opacity: 0 }], springs.fade, exit))
  if (panelExit) panelExit.onfinish = finish
  else finish()
}

function onCancel(event: Event) {
  event.preventDefault()
  open.value = false
}

watch(open, (value) => (value ? show() : hide()), { flush: 'post' })

onMounted(() => {
  if (open.value) show()
})

onBeforeUnmount(() => {
  stopRunning()
  if (dialog.value?.open) dialog.value.close()
})
</script>

<template>
  <dialog ref="dialog" class="lt-sheet" :aria-labelledby="titleId" @cancel="onCancel">
    <div ref="scrim" class="lt-sheet-scrim" @click="open = false" />
    <section ref="panel" class="lt-sheet-panel" :style="{ width: `min(${width}px, calc(100vw - 48px))` }">
      <header class="lt-sheet-head">
        <h2 :id="titleId" class="lt-sheet-title">{{ title }}</h2>
        <LtIconButton label="关闭" @click="open = false">
          <X :stroke-width="2" aria-hidden="true" />
        </LtIconButton>
      </header>
      <div class="lt-sheet-body">
        <slot />
      </div>
    </section>
  </dialog>
</template>

<style scoped>
.lt-sheet {
  position: fixed;
  inset: 0;
  width: 100vw;
  max-width: none;
  height: 100vh;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lt-label);
  overflow: hidden;
}

.lt-sheet[open] {
  display: grid;
  place-items: center;
}

.lt-sheet::backdrop {
  background: transparent;
}

.lt-sheet-scrim {
  position: absolute;
  inset: 0;
  background: var(--lt-scrim);
  backdrop-filter: blur(8px);
}

.lt-sheet-panel {
  position: relative;
  max-height: 82vh;
  overflow: auto;
  overscroll-behavior: contain;
  padding: var(--lt-space-5) var(--lt-space-6) var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-float);
  backdrop-filter: blur(40px) saturate(180%);
}

.lt-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-right: -8px;
}

.lt-sheet-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.lt-sheet-body {
  margin-top: var(--lt-space-2);
}
</style>
