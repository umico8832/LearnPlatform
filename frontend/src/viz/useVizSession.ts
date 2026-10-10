import { computed, onScopeDispose, ref, shallowRef, watch, type ComputedRef, type Ref, type ShallowRef } from 'vue'
import { Player, type Answer, type PlayerState } from './engine/player'
import type { GhostLayer, Predict, SceneModule, SceneSpec, Step, Trace } from './engine/types'
import { findScene } from './scenes'
import { applyPauseAt, normalizeSpec, sameStory } from './spec'

export interface Checkpoint {
  /** Step the question guards. */
  index: number
  predict: Predict
}

export type CheckpointState = 'open' | 'asking' | 'right' | 'retried'

export interface VizSession {
  status: Ref<'loading' | 'ready' | 'error'>
  error: Ref<string>
  module: ShallowRef<SceneModule | null>
  /** The spec actually simulated, after clamping. */
  spec: ShallowRef<SceneSpec | null>
  trace: ShallowRef<Trace | null>
  state: ShallowRef<PlayerState>
  answers: ShallowRef<ReadonlyMap<number, Answer>>
  /** Hypothetical layer drawn while a prediction is open. */
  ghost: ShallowRef<GhostLayer | null>
  step: ComputedRef<Step | null>
  checkpoints: ComputedRef<Checkpoint[]>
  player: Player
  checkpointState(index: number): CheckpointState
  /** Draw (or clear) a hypothetical layer for the open prediction. */
  showGhost(layer: GhostLayer | null): void
  retry(): void
}

/**
 * One playable visualization: loads the scene module for `source`, runs its
 * deterministic simulator and drives a Player. Parameter edits keep the
 * playhead; a new scene or operation starts from the first step.
 */
export function useVizSession(source: () => SceneSpec): VizSession {
  const player = new Player()
  const status = ref<'loading' | 'ready' | 'error'>('loading')
  const error = ref('')
  const module = shallowRef<SceneModule | null>(null)
  const spec = shallowRef<SceneSpec | null>(null)
  const trace = shallowRef<Trace | null>(null)
  const state = shallowRef<PlayerState>(player.snapshot())
  const answers = shallowRef<ReadonlyMap<number, Answer>>(new Map())
  const ghost = shallowRef<GhostLayer | null>(null)
  let generation = 0

  player.subscribe((snapshot) => {
    state.value = snapshot
    answers.value = new Map(player.answers)
    if (snapshot.asking === null) ghost.value = null
  })

  function build(loaded: SceneModule, requested: SceneSpec) {
    const next = normalizeSpec(loaded, requested)
    const keepIndex = sameStory(spec.value, next)
    try {
      const built = applyPauseAt(loaded.build(next), next.pauseAt)
      spec.value = next
      trace.value = built
      ghost.value = null
      player.load(built, keepIndex)
      status.value = 'ready'
      if (import.meta.env.DEV && !built.check.ok)
        console.warn(`[viz] ${loaded.id} 模拟器自检失败：${built.check.detail}`)
    } catch (cause) {
      status.value = 'error'
      error.value = cause instanceof Error ? cause.message : '无法生成这段动画'
    }
  }

  async function load(requested: SceneSpec) {
    const current = module.value
    if (current && current.id === requested.scene) {
      build(current, requested)
      return
    }
    const entry = findScene(requested.scene)
    const ticket = ++generation
    status.value = 'loading'
    if (!entry) {
      status.value = 'error'
      error.value = `没有名为“${requested.scene}”的可视化`
      return
    }
    try {
      const loaded = await entry.load()
      if (ticket !== generation) return
      module.value = loaded
      build(loaded, requested)
    } catch {
      if (ticket !== generation) return
      status.value = 'error'
      error.value = '可视化加载失败，请检查网络后重试'
    }
  }

  watch(
    () => JSON.stringify(source()),
    () => void load(source()),
    { immediate: true },
  )

  onScopeDispose(() => {
    generation++
    player.destroy()
  })

  const step = computed(() => trace.value?.steps[state.value.index] ?? null)
  const checkpoints = computed(() =>
    (trace.value?.steps ?? []).flatMap((item, index) => (item.predict ? [{ index, predict: item.predict }] : [])),
  )

  return {
    status,
    error,
    module,
    spec,
    trace,
    state,
    answers,
    ghost,
    step,
    checkpoints,
    player,
    checkpointState(index) {
      if (state.value.asking === index) return 'asking'
      const answer = answers.value.get(index)
      if (!answer?.solved) return 'open'
      return answer.firstTry ? 'right' : 'retried'
    },
    showGhost(layer) {
      ghost.value = state.value.asking === null ? null : layer
    },
    retry() {
      module.value = null
      void load(source())
    },
  }
}
