import type { QuestionSource, UnderstandingOption } from '@/api/understanding'
import type { SceneSpec } from '@/viz'

/**
 * The homepage walks through one real, reviewed question instead of a made-up demo:
 * 2026 年 408 真题第 3 题. Stem and options come from V78, the misconception of option B
 * and both scene specs from V107, the variant's changed condition from V110. Keep them in
 * step with those migrations; the correct answer is deliberately not shown here, the same
 * gate the understanding page keeps until the learner answers right or asks for it.
 */
export const exampleSource: QuestionSource = {
  kind: 'OFFICIAL_EXAM',
  title: '2026 年 408 真题 第 3 题',
  url: 'https://csgraduates.com/study_methods/408quiz/2026/',
  verification: 'VERIFIED',
}

export const exampleStem =
  '已知二叉树 T 的中序遍历为 b, e, d, f, c, a, g，层序遍历为 a, b, g, c, d, e, f，则其后序遍历序列为（ ）。'

export const exampleOptions: UnderstandingOption[] = [
  { label: 'A', content: 'c, e, d, f, b, g, a' },
  { label: 'B', content: 'c, e, f, d, b, g, a' },
  { label: 'C', content: 'e, f, d, c, b, g, a' },
  { label: 'D', content: 'e, g, f, d, b, c, a' },
]

export const exampleChosen = 'B'

const exampleViz: SceneSpec = {
  scene: 'binary-tree',
  op: 'postorder',
  tree: ['a', 'b', 'g', null, 'c', null, null, 'd', null, 'e', 'f'],
  pauseAt: [{ id: 'tree-first-visit', guess: 'c' }],
}

export const exampleMisconception = {
  title: '把 c 放成了 b 的左孩子',
  explanation:
    '层序里 c 排在 b、g 之后，它确实是 b 的孩子，但在左边还是右边要看中序：中序 b, e, d, f, c 中 b 排在这一段最前面，说明 **b 没有左子树**，c 只能在 b 的右边，而且 d、e、f 都在 c 的左子树里。把 c 当成 b 左边的叶子，后序才会先输出 c。',
  vizTitle: '用本题的树走一遍后序，看谁先输出',
  viz: exampleViz,
}

export const exampleVariantCondition = '中序改为 c, b, e, d, f, a, g，层序不变，仍求后序遍历。'
