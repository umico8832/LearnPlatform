import type {
  CodeListing,
  Frame,
  LabelView,
  NodeView,
  Predict,
  SceneModule,
  SceneSpec,
  Step,
  TagView,
  Trace,
  VarView,
} from '../../engine/types'

/**
 * Stack: is a pop sequence reachable when 1..n are pushed in order?
 * The classic 408 multiple-choice question, played on the learner's own
 * sequence. Greedy simulation: push until the target is on top, then it must
 * be the top, otherwise it is buried and the sequence is impossible.
 */

export interface StackSpec extends SceneSpec {
  scene: 'stack'
  op: 'check'
  /** Candidate pop sequence, a permutation of 1..n. */
  seq: number[]
}

interface State {
  n: number
  seq: number[]
  input: number[]
  stack: number[]
  /** Values popped so far, in order; out[k] sits in slot k. */
  out: number[]
  next: number
  k: number | null
  /** Set when the target is buried: which value was wanted. */
  stuck: number | null
  declared: Set<string>
}

interface Mark {
  line: number | null
  title: string
  note: string
  phase?: string
  predict?: Predict
  /** Value cell being moved or compared. */
  focus?: number
}

const P = 58
const SIDE_GAP = 98

const LISTING: CodeListing = {
  file: 'ValidPop.c',
  lines: [
    'bool ValidPop(int pop[], int n) {',
    '    SqStack S;  InitStack(S);',
    '    int next = 1;            // 下一个入栈的数',
    '    for (int k = 0; k < n; k++) {',
    '        while (next <= pop[k])',
    '            Push(S, next++);',
    '        if (GetTop(S) != pop[k])',
    '            return false;    // 被压在下面',
    '        Pop(S);',
    '    }',
    '    return true;',
    '}',
  ],
  vars: { next: 'orange', k: 'green', S: 'blue' },
}

const list = (values: number[]) => values.join(' ')

/* ---------------------------------------------------------------- layout */

function geometry(n: number) {
  const mouthY = -(n - 1) * P - P / 2 - 10
  const rowY = mouthY - 58
  return {
    mouthY,
    rowY,
    stackPos: (i: number) => ({ x: 0, y: -i * P }),
    inputPos: (j: number) => ({ x: -SIDE_GAP - j * P, y: rowY }),
    slotPos: (k: number) => ({ x: SIDE_GAP + k * P, y: rowY }),
  }
}

function layout(st: State, mark: Mark): Frame {
  const g = geometry(st.n)
  const nodes: NodeView[] = []
  const tags: TagView[] = []
  const top = st.stack[st.stack.length - 1]

  st.seq.forEach((value, k) => {
    const pos = g.slotPos(k)
    nodes.push({
      id: `slot${k}`,
      kind: 'cell',
      ...pos,
      text: String(value),
      tone: st.stuck !== null && k === st.k ? 'danger' : k === st.k ? 'accent' : 'muted',
    })
  })

  const place = (value: number): NodeView => {
    const inInput = st.input.indexOf(value)
    const inStack = st.stack.indexOf(value)
    const inOut = st.out.indexOf(value)
    const pos = inInput >= 0 ? g.inputPos(inInput) : inStack >= 0 ? g.stackPos(inStack) : g.slotPos(inOut)
    let tone: NodeView['tone']
    if (inOut >= 0) tone = 'success'
    else if (st.stuck !== null && value === st.stuck) tone = 'danger'
    else if (st.stuck !== null && value === top) tone = 'warning'
    else if (value === mark.focus) tone = 'accent'
    return { id: `v${value}`, kind: 'cell', ...pos, text: String(value), tone }
  }
  for (let v = 1; v <= st.n; v++) nodes.push(place(v))

  if (st.input.length)
    tags.push({ id: 'var:next', label: 'next', color: 'orange', target: `v${st.input[0]}`, side: 'below' })
  if (top !== undefined) tags.push({ id: 'var:top', label: '栈顶', color: 'blue', target: `v${top}`, side: 'right' })
  if (st.k !== null && st.k < st.n)
    tags.push({ id: 'var:k', label: 'k', color: 'green', target: `slot${st.k}`, side: 'below' })

  const inputCenter = -SIDE_GAP - ((st.n - 1) * P) / 2
  const slotCenter = SIDE_GAP + ((st.n - 1) * P) / 2
  const labels: LabelView[] = [
    { id: 'lbl-in', x: inputCenter, y: g.rowY - 44, text: '入栈顺序 →' },
    { id: 'lbl-out', x: slotCenter, y: g.rowY - 44, text: '要检查的出栈序列' },
    { id: 'lbl-bottom', x: 0, y: P / 2 + 26, text: '栈底' },
  ]
  return {
    nodes,
    edges: [],
    tags,
    labels,
    regions: [{ id: 'stack', x: -36, y: g.mouthY, w: 72, h: P / 2 + 12 - g.mouthY, style: 'well' }],
  }
}

function vars(st: State): VarView[] {
  const out: VarView[] = []
  if (st.declared.has('next'))
    out.push({ name: 'next', value: st.next > st.n ? `${st.next}（已全部入栈）` : String(st.next), color: 'orange' })
  if (st.declared.has('k')) {
    out.push({ name: 'k', value: st.k === null ? '?' : String(st.k), color: 'green' })
    out.push({ name: 'pop[k]', value: st.k !== null && st.k < st.n ? String(st.seq[st.k]) : '—' })
  }
  out.push({ name: 'S', value: st.stack.length ? `底 ${list(st.stack)} 顶` : '空', color: 'blue' })
  return out
}

function snapshot(st: State): State {
  return {
    ...st,
    seq: [...st.seq],
    input: [...st.input],
    stack: [...st.stack],
    out: [...st.out],
    declared: new Set(st.declared),
  }
}

/* -------------------------------------------------------------- simulate */

/** Ghost cells showing a hypothetical stack, bottom to top. */
function ghostStack(n: number, values: number[]): NodeView[] {
  const g = geometry(n)
  return values.map((v, i) => ({ id: `guess${v}`, kind: 'cell', ...g.stackPos(i), text: String(v) }))
}

function firstPushPredict(st: State, target: number): Predict {
  const below = Array.from({ length: target }, (_, i) => i + 1)
  if (target === 1) {
    return {
      id: 'stack-first-push',
      prompt: '要让 1 第一个出栈，弹出 1 前一刻栈里有什么？',
      options: [
        {
          id: 'right',
          text: '只有 1',
          correct: true,
          feedback: '对。1 是第一个入栈的，压进去就在栈顶，马上弹出即可。',
        },
        {
          id: 'all',
          text: `${list(Array.from({ length: st.n }, (_, i) => i + 1))}，全部压进去`,
          feedback: '多压的元素会盖在 1 上面，1 就不在栈顶了。只需要压到目标为止。',
          ghost: {
            nodes: ghostStack(
              st.n,
              Array.from({ length: st.n }, (_, i) => i + 1),
            ),
          },
        },
        {
          id: 'skip',
          text: '空的，1 直接从入栈序列出去',
          feedback: '每个元素都要先入栈才能出栈，不能从输入直接跳到输出。',
        },
      ],
      takeaway: '目标还没入栈时，就按顺序一直压，直到把目标压到栈顶。',
    }
  }
  return {
    id: 'stack-first-push',
    prompt: `要让 ${target} 第一个出栈。弹出 ${target} 前一刻，栈里从底到顶是？`,
    options: [
      {
        id: 'right',
        text: `${list(below)}（${target} 在栈顶）`,
        correct: true,
        feedback: `对。入栈顺序不能跳，${list(below.slice(0, -1))} 必须先压进去，垫在 ${target} 下面。`,
      },
      {
        id: 'only',
        text: `只有 ${target}`,
        feedback: `入栈顺序是固定的：${target} 之前的 ${list(below.slice(0, -1))} 必须先入栈，它们还没出栈，会垫在 ${target} 下面。`,
        ghost: { nodes: ghostStack(st.n, [target]) },
      },
      {
        id: 'reversed',
        text: `${list([...below].reverse())}（1 在栈顶）`,
        feedback: `后入栈的在上面。1 最先入栈，压在最底下；${target} 最后入栈，在栈顶。`,
        ghost: { nodes: ghostStack(st.n, [...below].reverse()) },
      },
    ],
    takeaway: '目标还没入栈时，就按顺序一直压，直到把目标压到栈顶。',
  }
}

function onTopPredict(target: number, lastOut: number | undefined): Predict {
  return {
    id: 'stack-already-pushed',
    prompt: `目标 ${target} 早已入栈。现在能直接让它出栈吗？`,
    options: [
      {
        id: 'right',
        text: '能，它正好在栈顶',
        correct: true,
        feedback:
          lastOut !== undefined ? `对。${lastOut} 刚刚弹出，露出来的正是 ${target}。` : `对。${target} 正在栈顶。`,
      },
      { id: 'again', text: '不能，要先再入栈一次', feedback: '已经入栈的元素就在栈里，不需要、也不能再入栈一次。' },
      {
        id: 'order',
        text: '不能，栈只能按入栈顺序出栈',
        feedback: '栈是后进先出：只要在栈顶就能出栈，和它什么时候入栈无关。',
      },
    ],
    takeaway: '目标已经在栈里时，不再入栈，只看它是不是栈顶。',
  }
}

function stuckPredict(st: State, target: number, top: number, slot: number): Predict {
  const g = geometry(st.n)
  return {
    id: 'stack-buried',
    prompt: `现在要出栈 ${target}，但栈顶是 ${top}。接下来会怎样？`,
    options: [
      {
        id: 'right',
        text: `不可能做到：${target} 被压在 ${top} 下面`,
        correct: true,
        feedback: `对。要拿到 ${target}，必须先弹出 ${top}，可序列里 ${top} 排在 ${target} 后面。所以这个出栈序列不合法。`,
      },
      {
        id: 'middle',
        text: `直接从栈里取出 ${target}`,
        feedback: `看虚线：这一步要从栈中间拿走 ${target}。栈只能操作栈顶，${target} 被 ${top} 压住，拿不到。`,
        ghost: { nodes: [{ id: 'grab', kind: 'cell', ...g.slotPos(slot), text: String(target) }] },
      },
      {
        id: 'back',
        text: `把 ${top} 放回入栈序列，再弹出 ${target}`,
        feedback: '入栈序列只能往前走，出栈的元素也不能退回去。',
      },
    ],
    takeaway: shortcut(st.seq),
  }
}

/** The 408 shortcut, illustrated with the sequence's own 312 pattern. */
function shortcut(seq: number[]): string {
  const rule = '速判技巧：一个数出栈后，排在它后面又比它小的数，必须按从大到小的顺序出栈。'
  for (let i = 0; i < seq.length; i++)
    for (let j = i + 1; j < seq.length; j++)
      for (let k = j + 1; k < seq.length; k++)
        if (seq[j] < seq[k] && seq[k] < seq[i])
          return `${rule}这里 ${seq[i]} 之后的 ${seq[j]}、${seq[k]} 是从小到大，所以不合法。`
  return rule
}

function simulate(seq: number[]): { steps: Step[]; valid: boolean } {
  const n = seq.length
  const st: State = {
    n,
    seq,
    input: Array.from({ length: n }, (_, i) => i + 1),
    stack: [],
    out: [],
    next: 1,
    k: null,
    stuck: null,
    declared: new Set(['next']),
  }
  const steps: Step[] = []
  const snap = (mark: Mark) => {
    const copy = snapshot(st)
    steps.push({
      frame: layout(copy, mark),
      line: mark.line,
      title: mark.title,
      note: mark.note,
      vars: vars(copy),
      predict: mark.predict,
      phase: mark.phase,
    })
  }

  snap({ line: 3, title: 'next = 1', note: `栈是空的。要检查的出栈序列是 ${list(seq)}，入栈顺序固定是 1 到 ${n}。` })
  st.declared.add('k')

  let askedFirst = false
  let askedTop = false
  for (let k = 0; k < n; k++) {
    const target = seq[k]
    const phase = `第 ${k + 1} 个出栈`
    st.k = k
    const pushed = target < st.next
    snap({
      line: 4,
      title: `k = ${k}，目标 pop[${k}] = ${target}`,
      note: pushed
        ? `目标 ${target} 早已入栈，不用再压，直接看它是不是栈顶。`
        : `目标 ${target} 还没入栈，要按顺序把 ${st.next === target ? target : `${st.next} 到 ${target}`} 压进去。`,
      phase,
    })

    while (st.next <= target) {
      const v = st.next
      st.input.shift()
      st.stack.push(v)
      st.next++
      const predict = !askedFirst ? firstPushPredict(st, target) : undefined
      askedFirst = true
      snap({
        line: 6,
        title: `Push(S, ${v})`,
        note: v < target ? `${v} 不是目标，但入栈不能跳号，先压进去垫在下面。` : `${v} 就是目标，压到栈顶。`,
        phase,
        focus: v,
        predict,
      })
    }

    const top = st.stack[st.stack.length - 1]
    if (top !== target) {
      st.stuck = target
      snap({
        line: 7,
        title: `GetTop(S) = ${top} ≠ ${target}`,
        note: `${target} 还在栈里，但被 ${top} 压着。栈只能从栈顶取，想拿到 ${target} 就得先弹出 ${top}，顺序就和序列对不上了。`,
        phase,
        predict: stuckPredict(st, target, top, k),
      })
      snap({
        line: 8,
        title: 'return false',
        note: `出栈序列 ${list(seq)} 不合法：卡在第 ${k + 1} 个数 ${target}。`,
        phase: '结论',
      })
      return { steps, valid: false }
    }

    const predict = pushed && !askedTop ? onTopPredict(target, st.out[st.out.length - 1]) : undefined
    if (predict) askedTop = true
    snap({
      line: 7,
      title: `GetTop(S) = ${top}，正是目标`,
      note: '栈顶就是要出栈的数，可以弹出。',
      phase,
      focus: top,
      predict,
    })

    st.stack.pop()
    st.out.push(target)
    snap({
      line: 9,
      title: `Pop(S) → ${target}`,
      note: `弹出 ${target}，对上出栈序列的第 ${k + 1} 个数。`,
      phase,
    })
  }

  st.k = n
  snap({ line: 11, title: 'return true', note: `每个数都在栈顶时被弹出，出栈序列 ${list(seq)} 合法。`, phase: '结论' })
  return { steps, valid: true }
}

/** Independent check: reachable pop sequences are exactly the 312-avoiding permutations. */
function avoids312(seq: number[]): boolean {
  for (let i = 0; i < seq.length; i++)
    for (let j = i + 1; j < seq.length; j++)
      for (let k = j + 1; k < seq.length; k++) if (seq[j] < seq[k] && seq[k] < seq[i]) return false
  return true
}

function isPermutation(values: number[]): string | null {
  const sorted = [...values].sort((a, b) => a - b)
  return sorted.every((v, i) => v === i + 1) ? null : `要用 1 到 ${values.length} 各一次`
}

export const stack: SceneModule = {
  id: 'stack',
  title: '栈',
  ops: [{ id: 'check', label: '判断出栈序列' }],
  params: [{ key: 'seq', label: '出栈序列', type: 'list', min: 3, max: 7, validate: isPermutation }],

  defaultSpec(): StackSpec {
    return { scene: 'stack', op: 'check', seq: [3, 2, 5, 1, 4] }
  },

  build(raw: SceneSpec): Trace {
    const spec = raw as StackSpec
    const { steps, valid } = simulate(spec.seq)
    const expected = avoids312(spec.seq)
    return {
      steps,
      code: LISTING,
      check: { ok: valid === expected, detail: valid ? '序列合法' : '序列不合法' },
    }
  },
}
