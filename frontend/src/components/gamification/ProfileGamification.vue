<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getAchievements, getLearningHeatmap, type Achievement, type HeatmapDay } from '@/api/gamification'
import { useGamificationStore } from '@/stores/gamification'
import { calendarDays, calendarRange, learningDate } from '@/utils/learningCalendar'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

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

const resetForSession = () => {
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
const unsubscribe = onAuthSessionChange(resetForSession)

onUnmounted(() => {
  alive = false
  unsubscribe()
})

async function load() {
  if (loading.value) return
  const session = getAuthSessionVersion()
  loading.value = true
  detailError.value = ''
  await store.load()
  if (!alive || !store.summary || session !== getAuthSessionVersion()) {
    loading.value = false
    return
  }
  goal.value = store.summary.dailyGoal
  const range = calendarRange(learningDate(new Date(), store.summary.zoneId))
  try {
    const [achievementResponse, heatmapResponse] = await Promise.all([
      getAchievements(),
      getLearningHeatmap(range.from, range.to),
    ])
    if (!alive || session !== getAuthSessionVersion()) return
    achievements.value = achievementResponse.data
    heatmap.value = calendarDays(range.from, range.to, heatmapResponse.data)
    loaded.value = true
  } catch {
    if (alive && session === getAuthSessionVersion()) detailError.value = '成就与学习日历暂时无法加载'
  } finally {
    if (alive && session === getAuthSessionVersion()) loading.value = false
  }
}

async function saveGoal() {
  if (!Number.isInteger(goal.value) || goal.value < 1 || goal.value > 200 || saving.value) return
  const session = getAuthSessionVersion()
  saving.value = true
  goalError.value = ''
  try {
    await store.saveGoal(goal.value)
  } catch {
    if (alive && session === getAuthSessionVersion()) goalError.value = '目标保存失败，请重试'
  } finally {
    if (alive && session === getAuthSessionVersion()) saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section
    id="learning"
    class="profile-gamification"
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

    <LpStatePanel
      v-if="detailError || store.error"
      state="error"
      title="学习记录暂时无法加载"
      :description="detailError || store.error"
      retry-label="重新加载"
      :retrying="loading"
      @retry="load"
    />
    <LpStatePanel v-else-if="loading && !store.summary" state="loading" loading-label="正在读取学习记录" />
    <template v-if="store.summary">
      <div class="profile-gamification__overview">
        <div>
          <strong>Lv.{{ store.summary.level }}</strong>
          <span>{{ store.summary.levelTitle }}</span>
        </div>
        <div>
          <strong>{{ store.summary.streakDays }}</strong>
          <span>连续学习天</span>
        </div>
        <div>
          <strong>{{ store.summary.todayAnsweredCount }}/{{ store.summary.dailyGoal }}</strong>
          <span>今日已作答</span>
        </div>
      </div>

      <form data-testid="gamification-daily-goal" @submit.prevent="saveGoal">
        <label>
          每日目标
          <input v-model.number="goal" type="number" min="1" max="200" :disabled="saving || loading" />
        </label>
        <button type="submit" :disabled="saving || loading">保存目标</button>
      </form>
      <p v-if="goalError" class="goal-error" role="status">
        {{ goalError }}
        <button type="button" :disabled="saving || loading" @click="saveGoal">重试</button>
      </p>

      <LpSkeleton v-if="loading" :rows="3" label="正在读取学习日历" />
      <template v-else-if="loaded">
        <section data-testid="gamification-heatmap">
          <h3>学习日历</h3>
          <div class="profile-gamification__heatmap" aria-label="学习热力日历">
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
          <p class="calendar-caption">
            {{ heatmap[0]?.date }} — {{ heatmap.at(-1)?.date }} · 按 {{ store.summary.zoneId }} 自然日记录
          </p>
          <p v-if="selectedDay" class="calendar-caption" role="status">
            {{ selectedDay.date }} · {{ selectedDay.answeredCount }} 题 · +{{ selectedDay.earnedXp }} 经验
          </p>
          <p v-else-if="!heatmap.some((day) => day.answeredCount > 0)" class="calendar-caption">
            完成一次作答，点亮第一个学习日。
          </p>
        </section>

        <details data-testid="gamification-achievements" class="profile-gamification__achievements">
          <summary>成就 · 已解锁 {{ unlockedCount }} / {{ achievements.length }}</summary>
          <ul>
            <li v-for="item in achievements" :key="item.code" :class="{ locked: !item.unlocked }">
              <strong>{{ item.name }}</strong>
              <span>{{ item.description }}</span>
              <small>{{ item.unlocked ? '已解锁' : `${item.progress} / ${item.target}` }}</small>
              <progress :value="item.progress" :max="item.target" :aria-label="`${item.name}进度`" />
            </li>
          </ul>
        </details>
      </template>
    </template>
  </section>
</template>

<style scoped>
.profile-gamification {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
}
header {
  display: flex;
  justify-content: space-between;
  gap: var(--lp-space-4);
}
p,
h2,
h3 {
  margin: 0;
}
header p {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
h2 {
  font-size: var(--lp-text-2xl);
}
header > span {
  color: var(--lp-text-secondary);
  font-variant-numeric: tabular-nums;
}
.profile-gamification__overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lp-space-3);
}
.profile-gamification__overview div {
  display: grid;
  gap: var(--lp-space-1);
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
}
.profile-gamification__overview strong {
  font-size: var(--lp-text-xl);
  font-variant-numeric: tabular-nums;
}
.profile-gamification__overview span,
.profile-gamification li span {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
form {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}
input {
  width: 72px;
  padding: var(--lp-space-2);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
}
button {
  padding: var(--lp-space-2) var(--lp-space-3);
  color: var(--lp-on-primary);
  background: var(--lp-primary);
  border: 0;
  border-radius: var(--lp-radius-sm);
  cursor: pointer;
}
button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
.profile-gamification__heatmap {
  display: grid;
  grid-template-rows: repeat(7, var(--lp-space-4));
  grid-auto-flow: column;
  grid-auto-columns: var(--lp-space-4);
  gap: var(--lp-space-1);
  justify-content: start;
  margin-top: var(--lp-space-3);
}
.profile-gamification__heatmap button {
  width: var(--lp-space-4);
  height: var(--lp-space-4);
  padding: 0;
  background: var(--lp-surface-inset);
  border-radius: var(--lp-radius-xs);
}
.profile-gamification__heatmap button[data-active='true'] {
  background: var(--lp-primary);
}
.profile-gamification__heatmap button[aria-pressed='true'] {
  box-shadow: var(--lp-shadow-focus);
}
.calendar-caption {
  margin-top: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.goal-error {
  margin: calc(var(--lp-space-2) * -1) 0 0;
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
.goal-error button {
  margin-left: var(--lp-space-2);
  padding: 0;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  background: transparent;
}
.profile-gamification__achievements summary {
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
  cursor: pointer;
}
.profile-gamification__achievements ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-2);
  padding: 0;
  margin: var(--lp-space-3) 0 0;
  list-style: none;
}
.profile-gamification__achievements li {
  display: grid;
  gap: var(--lp-space-1);
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
}
.profile-gamification__achievements li:not(.locked) {
  background: var(--lp-primary-soft);
}
.profile-gamification li small {
  color: var(--lp-text-secondary);
}
.profile-gamification progress {
  width: 100%;
  height: 5px;
  accent-color: var(--lp-primary);
}
.profile-gamification button:focus-visible,
.profile-gamification input:focus-visible,
.profile-gamification summary:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}
</style>
