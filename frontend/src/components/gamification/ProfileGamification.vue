<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getAchievements, getLearningHeatmap, type Achievement, type HeatmapDay } from '@/api/gamification'
import { useGamificationStore } from '@/stores/gamification'
import { calendarDays, calendarRange, learningDate } from '@/utils/learningCalendar'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

const store = useGamificationStore()
const achievements = ref<Achievement[]>([])
const heatmap = ref<HeatmapDay[]>([])
const goal = ref(0)
const saving = ref(false)
const error = ref('')
const loaded = ref(false)
const loading = ref(false)
const selectedDay = ref<HeatmapDay>()
let alive = true
const unsubscribe = onAuthSessionChange(() => {
  achievements.value = []
  heatmap.value = []
  selectedDay.value = undefined
  loaded.value = false
  error.value = ''
  goal.value = 0
  saving.value = false
  loading.value = false
})
onUnmounted(() => {
  alive = false
  unsubscribe()
})
async function load() {
  if (loading.value) return
  const session = getAuthSessionVersion()
  loading.value = true
  error.value = ''
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
    if (alive && session === getAuthSessionVersion()) error.value = '成就与学习日历暂时无法加载'
  } finally {
    if (alive && session === getAuthSessionVersion()) loading.value = false
  }
}
async function saveGoal() {
  if (!Number.isInteger(goal.value) || goal.value < 1 || goal.value > 200 || saving.value) return
  const session = getAuthSessionVersion()
  saving.value = true
  error.value = ''
  try {
    await store.saveGoal(goal.value)
  } catch {
    if (alive && session === getAuthSessionVersion()) error.value = '目标保存失败，请重试'
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
    <div v-if="error || store.error" class="profile-gamification__error" role="status">
      <p>{{ error || store.error }}</p>
      <button type="button" @click="load">重新加载</button>
    </div>
    <LpSkeleton v-if="loading" :rows="3" />
    <template v-if="store.summary">
      <div class="profile-gamification__overview">
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
        <label>每日目标 <input v-model.number="goal" type="number" min="1" max="200" /></label
        ><button type="submit" :disabled="saving">保存目标</button>
      </form>
      <section v-if="loaded" data-testid="gamification-achievements">
        <h3>成就</h3>
        <ul>
          <li v-for="item in achievements" :key="item.code" :class="{ locked: !item.unlocked }">
            <strong>{{ item.name }}</strong
            ><span>{{ item.description }}</span>
            <small>{{ item.unlocked ? '已解锁' : `${item.progress} / ${item.target}` }}</small>
            <progress :value="item.progress" :max="item.target" :aria-label="`${item.name}进度`" />
          </li>
        </ul>
      </section>
      <section v-if="loaded" data-testid="gamification-heatmap">
        <h3>学习热力</h3>
        <div class="profile-gamification__heatmap">
          <button
            v-for="day in heatmap"
            :key="day.date"
            :data-active="day.earnedXp > 0"
            type="button"
            :aria-label="`${day.date}：${day.answeredCount} 题，${day.earnedXp} 经验`"
            @click="selectedDay = day"
            :title="`${day.date}：${day.earnedXp} 经验`"
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
  color: var(--lp-reward-xp);
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
}
ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-2);
  padding: 0;
  list-style: none;
}
li {
  display: grid;
  gap: var(--lp-space-1);
  padding: var(--lp-space-3);
  background: var(--lp-reward-achievement-soft);
  border-radius: var(--lp-radius-sm);
}
li.locked {
  background: var(--lp-surface-soft);
}
.profile-gamification__heatmap {
  display: grid;
  grid-template-rows: repeat(7, 16px);
  grid-auto-flow: column;
  grid-auto-columns: 16px;
  gap: var(--lp-space-1);
}
.profile-gamification__heatmap button {
  aspect-ratio: 1;
  background: var(--lp-surface-inset);
  border-radius: 2px;
}
.profile-gamification__heatmap button[data-active='true'] {
  background: var(--lp-reward-xp);
}
.profile-gamification__error {
  color: var(--lp-danger);
}
</style>

<style scoped>
.profile-gamification li small {
  color: var(--lp-text-secondary);
}
.profile-gamification progress {
  width: 100%;
  height: 5px;
  accent-color: var(--lp-reward-achievement);
}
.profile-gamification__heatmap {
  display: grid;
  grid-template-rows: repeat(7, 16px);
  grid-auto-flow: column;
  grid-auto-columns: 16px;
  justify-content: start;
}
.profile-gamification__heatmap button {
  padding: 0;
  width: 16px;
  height: 16px;
  cursor: pointer;
}
.calendar-caption {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  margin-top: var(--lp-space-3);
}
.profile-gamification button:focus-visible,
.profile-gamification input:focus-visible {
  outline: 2px solid var(--lp-primary);
  outline-offset: 2px;
}
</style>
