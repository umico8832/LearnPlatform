<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { buildCalendar, dayLabel, DAYS, FOLLOWED, FOLLOWED_NEXT, gapLabel, level, monthStarts } from './calendar'

/**
 * "Remember": ten months of review days, read like a contribution calendar. It fills in
 * once, left to right like time passing, then holds still. The left is busy and the
 * right is quiet because every answer you get right pushes that question's next review
 * further out; one question is picked out in blue, its jumps drawn and named above the
 * calendar. Pointing at a day says what happened on it.
 */
const CELL = 22
const PITCH = 28
/** Delay per week while the calendar fills in. */
const SWEEP = 28
/** Room above the calendar for the arcs and their labels. */
const LIFT = 132

const days = buildCalendar().map((count, i) => ({
  i,
  count,
  x: Math.floor(i / 7) * PITCH,
  y: (i % 7) * PITCH,
  level: level(count),
  followed: FOLLOWED.includes(i),
}))
const width = Math.ceil(DAYS / 7) * PITCH - (PITCH - CELL)
const height = 7 * PITCH - (PITCH - CELL)
const delay = (i: number) => `${Math.floor(i / 7) * SWEEP}ms`
const months = monthStarts().map((month) => ({ ...month, x: month.week * PITCH }))

/** The followed question's jumps, as arcs leaping along the top edge of the calendar. */
const arcs = FOLLOWED.slice(1).map((to, k) => {
  const from = FOLLOWED[k]
  const x1 = days[from].x + CELL / 2
  const x2 = days[to].x + CELL / 2
  const top = LIFT - Math.min(28 + (to - from) * 0.75, 112)
  const peak = LIFT + (top - LIFT) * 0.75
  // The two shortest jumps sit side by side; their labels lean apart so they don't touch.
  const lean = k === 0 ? -7 : k === 1 ? 7 : 0
  // Drawn from take-off to landing at the speed the calendar fills, so the tip rides the sweep.
  const span = Math.floor(to / 7) - Math.floor(from / 7)
  return {
    d: `M ${x1} ${LIFT} C ${x1} ${top}, ${x2} ${top}, ${x2} ${LIFT}`,
    at: to,
    label: gapLabel(to - from),
    lx: (x1 + x2) / 2 + lean,
    ly: peak - 9,
    style: { transitionDelay: delay(from), transitionDuration: `${Math.max(1, span) * SWEEP}ms` },
    labelStyle: { transitionDelay: delay(to) },
  }
})

/* ------------------------------------------------------------- tooltip */

interface Tip {
  x: number
  y: number
  title: string
  text?: string
}

const tip = ref<Tip | null>(null)
const tipShown = ref(false)

function describe(i: number): Omit<Tip, 'x' | 'y'> {
  const date = dayLabel(i)
  const k = FOLLOWED.indexOf(i)
  if (k >= 0) {
    const next = (FOLLOWED[k + 1] ?? i + FOLLOWED_NEXT) - i
    return {
      title: k === 0 ? `First answer · ${date}` : `Review ${k} · ${date}`,
      text: `Right on your own. Back in ${gapLabel(next)}.`,
    }
  }
  const count = days[i].count
  return { title: count ? `${count} review${count > 1 ? 's' : ''} · ${date}` : `No reviews · ${date}` }
}

function onHover(event: PointerEvent) {
  const cell = (event.target as HTMLElement).closest<HTMLElement>('.day')
  if (!cell) {
    tipShown.value = false
    return
  }
  const day = days[Number(cell.dataset.i)]
  tip.value = { x: day.x + CELL / 2, y: day.y + LIFT, ...describe(day.i) }
  tipShown.value = true
}

/* ------------------------------------------------------------ entrance */

const root = ref<HTMLElement | null>(null)
const lit = ref(false)
let observer: IntersectionObserver | null = null

onMounted(() => {
  observer = new IntersectionObserver(
    ([entry]) => {
      if (!entry.isIntersecting) return
      observer?.disconnect()
      lit.value = true
    },
    { threshold: 0.45 },
  )
  if (root.value) observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section
    id="remember"
    ref="root"
    class="remember lt"
    data-appearance="dark"
    :data-lit="lit || undefined"
    aria-labelledby="remember-title"
  >
    <div class="inner">
      <header class="intro">
        <h2 id="remember-title" class="title">Back right before<br /><em>you forget.</em></h2>
        <p class="line">Every answer you get right pushes the next review further out.</p>
      </header>

      <div class="chart" :style="{ width: `${width}px` }">
        <figure
          class="year"
          :style="{ height: `${height + LIFT}px` }"
          aria-label="Ten months of review days, busy at first and quieter later. One question comes back after 1 week, then 2 weeks, 4 weeks, 7 weeks, 2½ months and 4 months."
          @pointerover="onHover"
          @pointerleave="tipShown = false"
        >
          <svg class="arcs" :width="width" :height="height + LIFT" aria-hidden="true">
            <path v-for="arc in arcs" :key="arc.at" class="arc" :d="arc.d" pathLength="1" :style="arc.style" />
            <text
              v-for="arc in arcs"
              :key="`label-${arc.at}`"
              class="gap"
              :x="arc.lx"
              :y="arc.ly"
              text-anchor="middle"
              :style="arc.labelStyle"
            >
              {{ arc.label }}
            </text>
          </svg>
          <span
            v-for="day in days"
            :key="day.i"
            class="day"
            :data-i="day.i"
            :data-level="day.level"
            :data-followed="day.followed || undefined"
            :style="{ left: `${day.x}px`, top: `${day.y + LIFT}px`, transitionDelay: delay(day.i) }"
          />
          <div
            v-if="tip"
            class="tip"
            :data-shown="tipShown || undefined"
            :style="{ left: `${tip.x}px`, top: `${tip.y}px` }"
            aria-hidden="true"
          >
            <strong>{{ tip.title }}</strong>
            <span v-if="tip.text">{{ tip.text }}</span>
          </div>
        </figure>

        <div class="months" aria-hidden="true">
          <span v-for="month in months" :key="month.week" :style="{ left: `${month.x}px` }">{{ month.label }}</span>
        </div>

        <div class="legend" aria-hidden="true">
          <span>Less</span>
          <span v-for="n in 5" :key="n" class="swatch" :data-level="n - 1" />
          <span>More</span>
          <span class="swatch" data-followed />
          <span>One question</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* One screen, like the sections above; the content centres below the floating bar. */
.remember {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100vh;
  padding-top: 68px;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.inner {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 var(--lt-space-9);
}

.intro {
  margin-bottom: 40px;
  text-align: center;
}

.title {
  margin: 0;
  font-family: var(--lt-font-display);
  font-size: 80px;
  font-weight: 400;
  letter-spacing: -0.02em;
  line-height: 1;
}

.title em {
  color: var(--lt-label-2);
  font-style: italic;
}

.line {
  margin: var(--lt-space-5) 0 0;
  color: var(--lt-label-2);
  font-size: 19px;
  letter-spacing: -0.01em;
  line-height: 1.5;
}

.chart {
  margin: 0 auto;
}

.year {
  position: relative;
  margin: 0;
}

.arcs {
  position: absolute;
  inset: 0;
  overflow: visible;
}

/* Each jump draws itself from take-off to landing as the calendar fills. */
.arc {
  fill: none;
  stroke: var(--lt-accent);
  stroke-width: 1.5;
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  opacity: 0.85;
  filter: drop-shadow(0 0 4px color-mix(in oklab, var(--lt-accent) 70%, transparent));
  transition: stroke-dashoffset 700ms linear;
}

.remember[data-lit] .arc {
  stroke-dashoffset: 0;
}

/* How long it waited: named at the top of each jump once it has landed. */
.gap {
  fill: var(--lt-label-2);
  font: 500 12px var(--lt-font);
  letter-spacing: 0.01em;
  opacity: 0;
  transition: opacity 500ms var(--lt-ease-fade);
}

.remember[data-lit] .gap {
  opacity: 1;
}

.day,
.swatch {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: color-mix(in oklab, var(--lt-label) 5%, transparent);
}

.day {
  position: absolute;
  z-index: 1;
  transition:
    background-color 500ms var(--lt-ease-fade),
    box-shadow 500ms var(--lt-ease-fade),
    scale var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.day:hover {
  z-index: 2;
  scale: 1.15;
}

/* Reviews that day, shaded like a contribution calendar in greys so the followed question stands out. */
.remember[data-lit] .day[data-level='1'],
.swatch[data-level='1'] {
  background: color-mix(in oklab, var(--lt-label) 16%, transparent);
}

.remember[data-lit] .day[data-level='2'],
.swatch[data-level='2'] {
  background: color-mix(in oklab, var(--lt-label) 28%, transparent);
}

.remember[data-lit] .day[data-level='3'],
.swatch[data-level='3'] {
  background: color-mix(in oklab, var(--lt-label) 42%, transparent);
}

.remember[data-lit] .day[data-level='4'],
.swatch[data-level='4'] {
  background: color-mix(in oklab, var(--lt-label) 58%, transparent);
}

.remember[data-lit] .day[data-followed],
.swatch[data-followed] {
  background: var(--lt-accent);
  box-shadow:
    0 0 0 1px color-mix(in oklab, white 35%, var(--lt-accent)),
    0 0 18px color-mix(in oklab, var(--lt-accent) 70%, transparent);
}

.months {
  position: relative;
  height: 16px;
  margin-top: 14px;
}

.months span,
.legend {
  color: var(--lt-label-3);
  font-size: 12px;
  letter-spacing: 0.01em;
}

.months span {
  position: absolute;
  top: 0;
}

.legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 22px;
}

.legend .swatch {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.legend .swatch[data-followed] {
  margin-left: 18px;
  box-shadow: 0 0 10px color-mix(in oklab, var(--lt-accent) 70%, transparent);
}

/* GitHub-style: a small raised bubble over the day, springing in and out. */
.tip {
  position: absolute;
  z-index: 3;
  display: grid;
  gap: 2px;
  padding: 8px 12px;
  border-radius: var(--lt-radius-md);
  /* The raised colour is translucent; laid over the canvas so the arcs don't show through. */
  background: linear-gradient(var(--lt-surface-raised), var(--lt-surface-raised)), var(--lt-canvas);
  box-shadow:
    0 0 0 0.5px var(--lt-separator),
    0 -4px 16px rgba(0, 0, 0, 0.4);
  color: var(--lt-label);
  font-size: 12.5px;
  line-height: 1.35;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  translate: -50% calc(-100% - 10px);
  scale: 0.94;
  transform-origin: 50% 100%;
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    scale var(--lt-dur-snappy) var(--lt-ease-snappy),
    left var(--lt-dur-snappy) var(--lt-ease-snappy),
    top var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.tip[data-shown] {
  opacity: 1;
  scale: 1;
}

.tip strong {
  font-weight: 600;
}

.tip span {
  color: var(--lt-label-2);
}

@media (prefers-reduced-motion: reduce) {
  .arc,
  .gap,
  .day,
  .tip {
    transition: none;
  }
}
</style>
