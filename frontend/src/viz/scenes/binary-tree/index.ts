import type {
  CodeListing,
  EdgeView,
  Frame,
  NodeView,
  Predict,
  PredictOption,
  SceneModule,
  SceneSpec,
  Step,
  TagView,
  Trace,
  VarView,
} from '../../engine/types'

/**
 * Binary tree traversals (pre / in / post order) with the recursion made
 * visible: every call flies a frame out of its tree node into the call stack,
 * every visit flies a copy into the output row, and NULL calls show where the
 * recursion bottoms out.
 */

type Op = 'preorder' | 'inorder' | 'postorder'
type Part = 'visit' | 'left' | 'right'

export interface BinaryTreeSpec extends SceneSpec {
  scene: 'binary-tree'
  op: Op
  /** Level order with null for an empty child (LeetCode style). */
  tree: (string | null)[]
}

interface TreeNode {
  label: string
  left: string | null
  right: string | null
  parent: string | null
  side: 'left' | 'right' | null
  depth: number
}

interface Tree {
  root: string
  nodes: Map<string, TreeNode>
  order: string[]
  inIndex: Map<string, number>
  maxDepth: number
}

interface Frame0 {
  id: string
  node: string | null
  /** Tree node that made this call; NULL frames fly out of it. */
  caller: string | null
  side: 'left' | 'right' | null
}

interface State {
  frames: Frame0[]
  visited: string[]
}

interface Mark {
  line: number | null
  title: string
  note: string
  predict?: Predict
}

const SPEC: Record<Op, { fn: string; name: string; parts: Part[] }> = {
  preorder: { fn: 'PreOrder', name: '先序', parts: ['visit', 'left', 'right'] },
  inorder: { fn: 'InOrder', name: '中序', parts: ['left', 'visit', 'right'] },
  postorder: { fn: 'PostOrder', name: '后序', parts: ['left', 'right', 'visit'] },
}

const COMMENT: Record<Part, string> = { visit: '// 访问根', left: '// 左子树', right: '// 右子树' }

function listing(op: Op): CodeListing {
  const { fn, parts } = SPEC[op]
  const body = parts.map((part) => {
    const stmt = part === 'visit' ? 'visit(T);' : `${fn}(T->${part === 'left' ? 'lchild' : 'rchild'});`
    return `    ${stmt.padEnd(22)}${COMMENT[part]}`
  })
  return {
    file: `${fn}.c`,
    lines: [`void ${fn}(BiTree T) {`, '    if (T == NULL) return;', ...body, '}'],
    vars: { T: 'blue' },
  }
}

/** 1-based line of each part in the listing. */
function lineOf(op: Op, part: Part): number {
  return SPEC[op].parts.indexOf(part) + 3
}

const SIDE_NAME = { left: '左', right: '右' } as const
const X_GAP = 64
const Y_GAP = 86
const CELL = 56

/* ----------------------------------------------------------------- parse */

export function parseTree(text: string): { value?: (string | null)[]; error?: string } {
  const tokens = text
    .trim()
    .split(/[\s,，、]+/)
    .filter(Boolean)
    .map((t) => (/^(#|null|NULL|∅)$/.test(t) ? null : t))
  if (!tokens.length || tokens[0] === null) return { error: '根结点不能为空' }
  if (tokens.some((t) => t !== null && !/^[A-Za-z0-9]{1,2}$/.test(t))) return { error: '结点名用 1–2 个字母或数字' }
  const labels = tokens.filter((t): t is string => t !== null)
  if (new Set(labels).size !== labels.length) return { error: '结点名不能重复' }
  if (labels.length > 12) return { error: '最多 12 个结点' }
  try {
    const tree = buildTree(tokens)
    if (tree.maxDepth > 5) return { error: '树高最多 6 层' }
  } catch (err) {
    return { error: (err as Error).message }
  }
  while (tokens.length && tokens[tokens.length - 1] === null) tokens.pop()
  return { value: tokens }
}

function formatTree(value: unknown): string {
  return ((value as (string | null)[]) ?? []).map((t) => t ?? '#').join(' ')
}

function buildTree(tokens: (string | null)[]): Tree {
  const nodes = new Map<string, TreeNode>()
  const root = tokens[0]!
  nodes.set(root, { label: root, left: null, right: null, parent: null, side: null, depth: 0 })
  const queue = [root]
  let i = 1
  while (queue.length && i < tokens.length) {
    const parent = nodes.get(queue.shift()!)!
    for (const side of ['left', 'right'] as const) {
      if (i >= tokens.length) break
      const label = tokens[i++]
      if (label === null) continue
      nodes.set(label, { label, left: null, right: null, parent: parent.label, side, depth: parent.depth + 1 })
      parent[side] = label
      queue.push(label)
    }
  }
  if (tokens.slice(i).some((t) => t !== null)) throw new Error('空结点下面不能再接结点')

  const order: string[] = []
  const walk = (id: string | null) => {
    if (!id) return
    const n = nodes.get(id)!
    walk(n.left)
    order.push(id)
    walk(n.right)
  }
  walk(root)
  return {
    root,
    nodes,
    order,
    inIndex: new Map(order.map((id, k) => [id, k])),
    maxDepth: Math.max(...[...nodes.values()].map((n) => n.depth)),
  }
}

/* ---------------------------------------------------------------- layout */

function geometry(tree: Tree) {
  const maxX = (tree.order.length - 1) * X_GAP
  const stackX = maxX + 150
  const stackBase = tree.maxDepth * Y_GAP + 10
  const rowY = (tree.maxDepth + 1) * Y_GAP + 20
  return {
    nodePos: (id: string) => {
      const n = tree.nodes.get(id)!
      return { x: tree.inIndex.get(id)! * X_GAP, y: n.depth * Y_GAP }
    },
    framePos: (i: number) => ({ x: stackX, y: stackBase - i * CELL }),
    outPos: (k: number) => ({ x: k * CELL, y: rowY }),
    stackX,
    stackBase,
    rowY,
    /** Deepest possible call stack: the longest path plus one NULL call. */
    stackTop: stackBase - (tree.maxDepth + 1) * CELL,
  }
}

function layout(tree: Tree, st: State, _mark: Mark): Frame {
  const g = geometry(tree)
  const top = st.frames[st.frames.length - 1]
  const current = top?.node ?? null
  const nodes: NodeView[] = []
  const edges: EdgeView[] = []
  const tags: TagView[] = []

  for (const n of tree.nodes.values()) {
    const visited = st.visited.includes(n.label)
    nodes.push({
      id: `t:${n.label}`,
      kind: 'circle',
      ...g.nodePos(n.label),
      text: n.label,
      tone: current === n.label ? 'accent' : visited ? 'success' : undefined,
    })
    if (n.parent)
      edges.push({ id: `e:${n.label}`, from: `t:${n.parent}`, port: n.side!, to: `t:${n.label}`, style: 'line' })
  }

  st.frames.forEach((f, i) => {
    nodes.push({
      id: f.id,
      kind: 'cell',
      ...g.framePos(i),
      text: f.node ?? '∅',
      tone: f.node === null ? 'muted' : i === st.frames.length - 1 ? 'accent' : undefined,
      enterFrom: `t:${f.node ?? f.caller}`,
    })
  })

  st.visited.forEach((label, k) => {
    nodes.push({
      id: `o:${label}`,
      kind: 'cell',
      ...g.outPos(k),
      text: label,
      tone: 'success',
      enterFrom: `t:${label}`,
    })
  })

  if (top) {
    if (top.node) {
      tags.push({ id: 'var:T', label: 'T', color: 'blue', target: `t:${top.node}`, side: 'left' })
    } else if (top.caller) {
      const p = g.nodePos(top.caller)
      tags.push({
        id: 'var:T',
        label: 'T',
        color: 'blue',
        target: null,
        side: 'below',
        park: { x: p.x + (top.side === 'left' ? -30 : 30), y: p.y + Y_GAP * 0.62 },
      })
    }
  }

  return {
    nodes,
    edges,
    tags,
    labels: [
      { id: 'lbl-stack', x: g.stackX, y: g.stackTop - 44, text: '调用栈' },
      { id: 'lbl-base', x: g.stackX, y: g.stackBase + 50, text: '栈底' },
      { id: 'lbl-out', x: -30, y: g.rowY, text: '访问序列', align: 'end' },
    ],
    regions: [
      {
        id: 'stack',
        x: g.stackX - 36,
        y: g.stackTop - 22,
        w: 72,
        h: g.stackBase - g.stackTop + 22 + 36,
        style: 'well',
      },
    ],
  }
}

function vars(st: State): VarView[] {
  const top = st.frames[st.frames.length - 1]
  return [
    { name: 'T', value: !top ? '—' : top.node ? `→ ${top.node}` : 'NULL', color: 'blue' },
    { name: '调用深度', value: String(st.frames.length) },
    { name: '已访问', value: st.visited.length ? st.visited.join(' ') : '—' },
  ]
}

/* -------------------------------------------------------------- simulate */

function traversal(tree: Tree, op: Op): string[] {
  const out: string[] = []
  const walk = (id: string | null) => {
    if (!id) return
    const n = tree.nodes.get(id)!
    for (const part of SPEC[op].parts) {
      if (part === 'visit') out.push(id)
      else walk(n[part])
    }
  }
  walk(tree.root)
  return out
}

/** Independent check: explicit-stack traversal, no recursion. */
function iterative(tree: Tree, op: Op): string[] {
  const out: string[] = []
  const stack: { id: string; stage: number }[] = [{ id: tree.root, stage: 0 }]
  while (stack.length) {
    const top = stack[stack.length - 1]
    const part = SPEC[op].parts[top.stage++]
    if (part === undefined) {
      stack.pop()
      continue
    }
    if (part === 'visit') out.push(top.id)
    else {
      const child = tree.nodes.get(top.id)![part]
      if (child) stack.push({ id: child, stage: 0 })
    }
  }
  return out
}

function firstVisitPredict(tree: Tree, op: Op): Predict {
  const answer = traversal(tree, op)[0]
  const firsts: Record<Op, string> = {
    preorder: traversal(tree, 'preorder')[0],
    inorder: traversal(tree, 'inorder')[0],
    postorder: traversal(tree, 'postorder')[0],
  }
  const rules: Record<Op, string> = {
    preorder: '先序一进入结点就访问，所以根第一个输出。',
    inorder: `中序要先走完左子树：从根一路向左，走到没有左孩子的 ${firsts.inorder}，它第一个输出。`,
    postorder: `后序要等左右子树都处理完：一路向下找到第一个叶子 ${firsts.postorder}，它第一个输出。`,
  }
  const why: Record<Op, string> = {
    preorder: '这是先序的第一个：先序进入结点就访问。',
    inorder: '这是中序的第一个：一路向左走到底就输出。',
    postorder: '这是后序的第一个：要等子树处理完才输出。',
  }
  const options: PredictOption[] = [{ id: answer, text: answer, correct: true, feedback: `对。${rules[op]}` }]
  for (const other of ['preorder', 'inorder', 'postorder'] as Op[]) {
    const label = firsts[other]
    if (options.some((o) => o.id === label)) continue
    options.push({ id: label, text: label, feedback: `${why[other]}${SPEC[op].name}不是这样：${rules[op]}` })
  }
  for (const label of tree.order) {
    if (options.length >= 3) break
    if (options.some((o) => o.id === label)) continue
    options.push({ id: label, text: label, feedback: `${label} 不会第一个输出。${rules[op]}` })
  }
  const g = geometry(tree)
  for (const o of options) o.ghost = { nodes: [{ id: 'guess-first', kind: 'cell', ...g.outPos(0), text: o.text }] }
  return {
    id: 'tree-first-visit',
    prompt: `${SPEC[op].name}遍历第一个输出的结点是？`,
    options: options.slice(0, 3),
    takeaway: `${SPEC[op].name}的口诀：${op === 'preorder' ? '根 左 右' : op === 'inorder' ? '左 根 右' : '左 右 根'}。“根”在哪个位置，就在什么时候访问它。`,
  }
}

function nullPredict(tree: Tree, op: Op, caller: string, side: 'left' | 'right'): Predict {
  const isRoot = caller === tree.root
  return {
    id: 'tree-null-call',
    prompt: `${caller} 的${SIDE_NAME[side]}孩子为空，${SPEC[op].fn}(NULL) 接下来会怎样？`,
    options: [
      {
        id: 'return',
        text: `第 2 行直接返回，回到 ${caller} 继续`,
        correct: true,
        feedback: `对。T == NULL 时立刻返回，${caller} 接着执行自己的下一行。`,
      },
      {
        id: 'crash',
        text: '访问了空指针，程序出错',
        feedback: '第 2 行先判断 T == NULL，空树直接返回，根本不会去读 T->lchild。这一行就是递归的出口。',
      },
      isRoot
        ? { id: 'end', text: '整个遍历结束', feedback: `返回只结束这一次空调用，回到 ${caller} 继续执行，遍历还没完。` }
        : {
            id: 'root',
            text: `回到根 ${tree.root} 重新开始`,
            feedback: `返回只回到直接调用者 ${caller}，不会跳回根。`,
          },
    ],
    takeaway: '每条递归路径最终都停在 T == NULL。没有这一行，递归就停不下来。',
  }
}

function returnPredict(tree: Tree, id: string): Predict {
  const n = tree.nodes.get(id)!
  const parent = n.parent!
  const sibling = tree.nodes.get(parent)![n.side === 'left' ? 'right' : 'left']
  const guess = (label: string) => ({
    tags: [{ id: 'guess-T', label: 'T', color: 'blue' as const, target: `t:${label}`, side: 'left' as const }],
  })
  const options: PredictOption[] = [
    {
      id: 'parent',
      text: `回到 ${parent}，接着执行它的下一行`,
      correct: true,
      feedback: `对。${id} 是 ${parent} 调用的，调用栈里 ${parent} 就在 ${id} 下面一层。`,
      ghost: guess(parent),
    },
  ]
  if (parent !== tree.root) {
    options.push({
      id: 'root',
      text: `回到根 ${tree.root}`,
      feedback: `返回只回到直接调用者。${id} 是 ${parent} 调用的，所以先回到 ${parent}。`,
      ghost: guess(tree.root),
    })
  }
  if (sibling) {
    options.push({
      id: 'sibling',
      text: `跳到兄弟 ${sibling}`,
      feedback: `兄弟之间没有调用关系。${sibling} 由 ${parent} 调用，必须先回到 ${parent}。`,
      ghost: guess(sibling),
    })
  }
  if (options.length < 3) {
    options.push({ id: 'end', text: '遍历结束', feedback: `${parent} 还没执行完，回到它之后还要继续。` })
  }
  return {
    id: 'tree-return',
    prompt: `${id} 的左右子树都处理完了。返回之后，回到哪里？`,
    options: options.slice(0, 3),
    takeaway: '调用栈记住了“回到哪里”：栈顶弹出后，下面那一层就是要继续执行的地方。',
  }
}

/** Return step that makes the best "where do we go back to" question. */
function pickReturn(tree: Tree, op: Op): string {
  const order = traversal(tree, 'postorder')
  const deep = order.filter((id) => tree.nodes.get(id)!.depth >= 2)
  void op
  return (
    deep.find((id) => tree.nodes.get(id)!.side === 'right') ??
    deep[0] ??
    order.find((id) => tree.nodes.get(id)!.depth >= 1) ??
    ''
  )
}

function simulate(tree: Tree, op: Op): { steps: Step[]; output: string[] } {
  const { fn, parts } = SPEC[op]
  const st: State = { frames: [], visited: [] }
  const steps: Step[] = []
  const path = () => st.frames.map((f) => f.node ?? '∅').join(' › ')
  const snap = (mark: Mark) => {
    const copy: State = { frames: [...st.frames], visited: [...st.visited] }
    steps.push({
      frame: layout(tree, copy, mark),
      line: mark.line,
      title: mark.title,
      note: mark.note,
      vars: vars(copy),
      predict: mark.predict,
      phase: copy.frames.length ? path() : undefined,
    })
  }

  let nullCount = 0
  let askedFirst = false
  let askedNull = false
  const returnAt = pickReturn(tree, op)

  const call = (id: string | null, caller: string | null, side: 'left' | 'right' | null, line: number) => {
    if (id === null) {
      st.frames.push({ id: `f:null${nullCount++}`, node: null, caller, side })
      snap({
        line: 2,
        title: `${fn}(NULL)`,
        note: `${caller} 的${SIDE_NAME[side!]}孩子为空。第 2 行发现 T == NULL，立刻返回。`,
        predict: askedNull ? undefined : nullPredict(tree, op, caller!, side!),
      })
      askedNull = true
      st.frames.pop()
      return
    }
    st.frames.push({ id: `f:${id}`, node: id, caller, side })
    snap({
      line,
      title: `${fn}(${id})`,
      note: caller
        ? `${caller} 执行到第 ${line} 行，调用 ${fn}(${id})。调用栈多了一层。`
        : `从根 ${id} 开始，调用 ${fn}(${id})。`,
    })
    const n = tree.nodes.get(id)!
    for (const part of parts) {
      if (part === 'visit') {
        st.visited.push(id)
        snap({
          line: lineOf(op, 'visit'),
          title: `visit(${id})`,
          note: `访问 ${id}。访问序列：${st.visited.join(' ')}`,
          predict: askedFirst ? undefined : firstVisitPredict(tree, op),
        })
        askedFirst = true
      } else {
        call(n[part], id, part, lineOf(op, part))
      }
    }
    st.frames.pop()
    snap({
      line: parts.length + 3,
      title: `${fn}(${id}) 返回`,
      note: caller
        ? `${id} 的左右子树都处理完了，返回 ${caller}，从它刚才停下的那一行往下走。`
        : `根 ${id} 的调用也结束了，遍历完成。${SPEC[op].name}序列：${st.visited.join(' ')}`,
      predict: id === returnAt ? returnPredict(tree, id) : undefined,
    })
  }

  call(tree.root, null, null, 1)
  return { steps, output: st.visited }
}

/* ---------------------------------------------------------------- module */

export const binaryTree: SceneModule = {
  id: 'binary-tree',
  title: '二叉树',
  ops: [
    { id: 'preorder', label: '先序' },
    { id: 'inorder', label: '中序' },
    { id: 'postorder', label: '后序' },
  ],
  params: [
    {
      key: 'tree',
      label: '层序',
      type: 'text',
      size: 16,
      hint: '按层序输入，# 表示空，例如 A B C D E # F',
      parse: parseTree,
      format: formatTree,
    },
  ],

  defaultSpec(op = 'inorder'): BinaryTreeSpec {
    return { scene: 'binary-tree', op: op as Op, tree: ['A', 'B', 'C', 'D', 'E', null, 'F'] }
  },

  build(raw: SceneSpec): Trace {
    const spec = raw as BinaryTreeSpec
    const tree = buildTree(spec.tree)
    const { steps, output } = simulate(tree, spec.op)
    const expected = iterative(tree, spec.op)
    const ok = output.length === expected.length && output.every((v, i) => v === expected[i])
    return { steps, code: listing(spec.op), check: { ok, detail: `${SPEC[spec.op].name}序列 ${output.join(' ')}` } }
  },
}
