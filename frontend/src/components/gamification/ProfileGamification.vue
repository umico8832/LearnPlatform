<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getAchievements, getLearningHeatmap, type Achievement, type HeatmapDay } from '@/api/gamification'
import { useGamificationStore } from '@/stores/gamification'
import { calendarDays, calendarRange, learningDate } from '@/utils/learningCalendar'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
const store = useGamificationStore()
const achievements = ref<Achievement[]>([])
const heatmap = ref<HeatmapDay[]>([])
const goal = ref(store.summary?.dailyGoal ?? 0)
const saving = ref(false)
const detailError = ref('')
const goalError = ref('')
const loaded = ref(false)
const loading = ref(false)
const selectedDay = ref<HeatmapDay>()
const unlockedCount = computed(() => achievements.value.filter((item) => item.unlocked).length)
let alive = true
let requestGeneration = 0
function reset() {
  achievements.value = []
  heatmap.value = []
  selectedDay.value = undefined
  loaded.value = false
  detailError.value = ''
  goalError.value = ''
  goal.value = 0
  saving.value = false
  loading.value = false
}
function current(generation: number, session: number) {
  return alive && generation === requestGeneration && session === getAuthSessionVersion() && isAuthenticated()
}
async function load() {
  if (loading.value) return
  const generation = ++requestGeneration
  const session = getAuthSessionVersion()
  loading.value = true
  detailError.value = ''
  await store.load()
  if (!current(generation, session)) return
  if (!store.summary) {
    loading.value = false
    return
  }
  goal.value = store.summary.dailyGoal
  const range = calendarRange(learningDate(new Date(), store.summary.zoneId))
  try {
    const [a, h] = await Promise.all([
      getAchievements({ errorDisplay: 'inline' }),
      getLearningHeatmap(range.from, range.to, { errorDisplay: 'inline' }),
    ])
    if (!current(generation, session)) return
    achievements.value = a.data
    heatmap.value = calendarDays(range.from, range.to, h.data)
    loaded.value = true
  } catch {
    if (current(generation, session)) detailError.value = '学习日历与成就暂时无法加载'
  } finally {
    if (current(generation, session)) loading.value = false
  }
}
async function saveGoal() {
  if (!Number.isInteger(goal.value) || goal.value < 1 || goal.value > 200 || saving.value) return
  const generation = requestGeneration
  const session = getAuthSessionVersion()
  saving.value = true
  goalError.value = ''
  try {
    await store.saveGoal(goal.value)
  } catch {
    if (current(generation, session)) goalError.value = '目标保存失败，请重试'
  } finally {
    if (current(generation, session)) saving.value = false
  }
}
const unsubscribe = onAuthSessionChange(() => {
  requestGeneration++
  reset()
  if (isAuthenticated()) void load()
})
onMounted(() => void load())
onUnmounted(() => {
  alive = false
  unsubscribe()
})
</script>
<template>
  <section
    id="learning"
    class="profile-gamification lt"
    data-appearance="dark"
    data-testid="gamification-profile"
    aria-labelledby="learning-title"
  >
    <header>
      <div>
        <p>学习投入记录</p>
        <h2 id="learning-title">学习进度</h2>
      </div>
      <span v-if="store.summary">累计 {{ store.summary.totalXp }} 经验</span>
    </header>
    <div v-if="!store.summary && (detailError || store.error)" class="state error" role="alert">
      <h3>学习记录暂时无法加载</h3>
      <p>{{ detailError || store.error }}</p>
      <button type="button" :disabled="loading" @click="load">重新加载</button>
    </div>
    <div v-else-if="loading && !store.summary" class="state" role="status">正在读取学习记录…</div>
    <template v-if="store.summary"
      ><p v-if="store.error" class="partial" role="alert">
        概览正在显示最近一次成功读取的数据。{{ store.error }}
        <button type="button" :disabled="loading" @click="load">重新加载</button>
      </p>
      <div class="overview">
        <div>
          <strong>Lv.{{ store.summary.level }}</strong
          ><span>{{ store.summary.levelTitle }}</span>
        </div>
        <div>
          <strong>{{ store.summary.streakDays }}</strong
          ><span>连续学习天</span>
        </div>
        <div>
          <strong>{{ store.summary.todayAnsweredCount }}/{{ store.summary.dailyGoal }}</strong
          ><span>今日已作答</span>
        </div>
      </div>
      <form data-testid="gamification-daily-goal" @submit.prevent="saveGoal">
        <label
          >每日目标 <input v-model.number="goal" type="number" min="1" max="200" :disabled="saving || loading" /></label
        ><button type="submit" :disabled="saving || loading">保存目标</button>
      </form>
      <p v-if="goalError" class="goal-error" role="alert">
        {{ goalError }} <button type="button" :disabled="saving || loading" @click="saveGoal">重试</button>
      </p>
      <div v-if="loading" class="calendar-skeleton" aria-busy="true">
        <span v-for="n in 21" :key="n" /><span class="lt-sr-only" role="status">正在读取学习日历</span>
      </div>
      <p v-else-if="detailError" class="partial" role="alert">
        {{ detailError }} <button type="button" @click="load">重新加载</button>
      </p>
      <template v-else-if="loaded"
        ><section data-testid="gamification-heatmap">
          <h3>学习日历</h3>
          <div class="heatmap" aria-label="学习热力日历">
            <button
              v-for="day in heatmap"
              :key="day.date"
              :data-active="day.earnedXp > 0"
              type="button"
              :aria-pressed="selectedDay?.date === day.date"
              :aria-label="`${day.date}：${day.answeredCount} 题，${day.earnedXp} 经验`"
              :title="`${day.date}：${day.earnedXp} 经验`"
              @click="selectedDay = day"
            />
          </div>
          <p class="caption">
            {{ heatmap[0]?.date }} — {{ heatmap.at(-1)?.date }} · 按 {{ store.summary.zoneId }} 自然日记录
          </p>
          <p v-if="selectedDay" class="caption" role="status">
            {{ selectedDay.date }} · {{ selectedDay.answeredCount }} 题 · +{{ selectedDay.earnedXp }} 经验
          </p>
          <p v-else-if="!heatmap.some((day) => day.answeredCount > 0)" class="caption">
            完成一次作答，点亮第一个学习日。
          </p>
        </section>
        <details data-testid="gamification-achievements" class="achievements">
          <summary>成就 · 已解锁 {{ unlockedCount }} / {{ achievements.length }}</summary>
          <ul>
            <li v-for="item in achievements" :key="item.code" :class="{ locked: !item.unlocked }">
              <strong>{{ item.name }}</strong
              ><span>{{ item.description }}</span
              ><small>{{ item.unlocked ? '已解锁' : `${item.progress} / ${item.target}` }}</small
              ><progress :value="item.progress" :max="item.target" :aria-label="`${item.name}进度`" />
            </li>
          </ul></details></template
    ></template>
  </section>
</template>
<style scoped>
.profile-gamification {
  display: grid;
  gap: var(--lt-space-4);
  padding: var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
header {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-4);
}
p,
h2,
h3 {
  margin: 0;
}
header p,
header > span,
.caption,
.overview span,
.achievements li span,
.achievements li small {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
h2 {
  font-size: var(--lt-text-title2);
}
header > span,
.overview strong {
  font-variant-numeric: tabular-nums;
}
.state,
.partial,
.goal-error {
  color: var(--lt-red-text);
}
.state {
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
}
.state.error {
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
.state h3 {
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
}
.state p {
  margin-bottom: var(--lt-space-3);
}
.partial,
.goal-error {
  font-size: var(--lt-text-footnote);
}
.overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lt-space-3);
}
.overview div,
.achievements li {
  display: grid;
  gap: var(--lt-space-1);
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
.overview strong {
  font-size: var(--lt-text-title3);
}
form {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
}
input {
  width: calc(var(--lt-space-10) + var(--lt-space-6));
  padding: var(--lt-space-2);
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 1px var(--lt-separator);
  color: var(--lt-label);
}
button {
  padding: var(--lt-space-2) var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font: inherit;
  cursor: pointer;
}
button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
.partial button,
.goal-error button {
  padding: 0;
  background: none;
  color: inherit;
  text-decoration: underline;
}
.heatmap {
  display: grid;
  grid-template-rows: repeat(7, var(--lt-space-4));
  grid-auto-flow: column;
  grid-auto-columns: var(--lt-space-4);
  gap: var(--lt-space-1);
  justify-content: start;
  margin-top: var(--lt-space-3);
}
.heatmap button {
  width: var(--lt-space-4);
  height: var(--lt-space-4);
  padding: 0;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
}
.heatmap button[data-active='true'] {
  background: var(--lt-accent);
}
.heatmap button[aria-pressed='true'] {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.caption {
  margin-top: var(--lt-space-3);
}
.achievements summary {
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  cursor: pointer;
}
.achievements ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-2);
  padding: 0;
  margin: var(--lt-space-3) 0 0;
  list-style: none;
}
.achievements li:not(.locked) {
  background: var(--lt-accent-soft);
}
progress {
  width: 100%;
  height: var(--lt-space-1);
  accent-color: var(--lt-accent);
}
.calendar-skeleton {
  display: grid;
  grid-template-columns: repeat(7, var(--lt-space-4));
  gap: var(--lt-space-2);
}
.calendar-skeleton span:not(.lt-sr-only) {
  height: var(--lt-space-4);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
}
.profile-gamification :focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
</style>
