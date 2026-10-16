import type {
  CodeListing,
  EdgeView,
  Frame,
  GhostLayer,
  NodeView,
  Predict,
  SceneModule,
  SceneSpec,
  Step,
  TagView,
  Trace,
  VarColor,
  VarView,
} from '../../engine/types'

/**
 * Singly linked list with a head node (带头结点), the 408 convention.
 * The simulator mutates a tiny memory model and snapshots it after every
 * statement; layout turns each snapshot into stage primitives.
 */

type Op = 'insert' | 'delete' | 'reverse'

export interface LinkedListSpec extends SceneSpec {
  scene: 'linked-list'
  op: Op
  values: number[]
  /** Position for insert/delete, 1-based like the textbook. */
  i?: number
  /** Value to insert. */
  e?: number
}

interface MemNode {
  id: string
  value: number | null
  next: string | null
  head?: boolean
  /** Fresh malloc: contents are garbage until written. */
  dataUnset?: boolean
  ptrUnset?: boolean
  freed?: boolean
}

interface State {
  nodes: Map<string, MemNode>
  slot: Map<string, number>
  aside: Set<string>
  ptr: Map<string, string | null>
  int: Map<string, number | null>
  /** Where a NULL variable parks relative to the list. */
  park: Map<string, 'before' | 'after'>
  /** Nodes already in the finished part (reverse). */
  done: Set<string>
  tone: Map<string, NodeView['tone']>
  /** Watch-panel text for variables whose target no longer exists. */
  display: Map<string, string>
  /** Variables currently in scope, shown in the watch panel. */
  declared: Set<string>
}

interface Mark {
  line: number | null
  title: string
  note: string
  phase?: string
  predict?: Predict
  /** Pointer field just written: its edge is highlighted. */
  wrote?: string
  /** Field just read. */
  read?: { id: string; field: 'data' | 'ptr' }
}

const PITCH = 150
const ASIDE_Y = -118
const HEAD = 'h'

const COLORS: Record<string, VarColor> = {
  L: 'graphite',
  p: 'blue',
  s: 'green',
  q: 'pink',
  prev: 'indigo',
  cur: 'blue',
  nxt: 'orange',
}

/* -------------------------------------------------------------- listings */

const LISTINGS: Record<Op, CodeListing> = {
  insert: {
    file: 'ListInsert.c',
    lines: [
      'bool ListInsert(LinkList L, int i, int e) {',
      '    if (i < 1) return false;',
      '    LNode *p = L;  int j = 0;',
      '    while (p != NULL && j < i - 1) {',
      '        p = p->next;  j++;',
      '    }',
      '    if (p == NULL) return false;  // i 不合法',
      '    LNode *s = (LNode *)malloc(sizeof(LNode));',
      '    if (s == NULL) return false;',
      '    s->data = e;',
      '    s->next = p->next;',
      '    p->next = s;',
      '    return true;',
      '}',
    ],
    vars: { L: 'graphite', p: 'blue', s: 'green' },
  },
  delete: {
    file: 'ListDelete.c',
    lines: [
      'bool ListDelete(LinkList L, int i, int *e) {',
      '    if (i < 1) return false;',
      '    LNode *p = L;  int j = 0;',
      '    while (p->next != NULL && j < i - 1) {',
      '        p = p->next;  j++;',
      '    }',
      '    if (p->next == NULL) return false;',
      '    LNode *q = p->next;  // q 指向被删结点',
      '    *e = q->data;',
      '    p->next = q->next;   // 让 q 断开',
      '    free(q);',
      '    return true;',
      '}',
    ],
    vars: { L: 'graphite', p: 'blue', q: 'pink' },
  },
  reverse: {
    file: 'Reverse.c',
    lines: [
      'void Reverse(LinkList L) {',
      '    LNode *prev = NULL, *cur = L->next, *nxt;',
      '    while (cur != NULL) {',
      '        nxt = cur->next;   // 先记住后继',
      '        cur->next = prev;  // 指针掉头',
      '        prev = cur;        // prev 前进一步',
      '        cur = nxt;         // cur 前进一步',
      '    }',
      '    L->next = prev;        // 接上新的首结点',
      '}',
    ],
    vars: { L: 'graphite', prev: 'indigo', cur: 'blue', nxt: 'orange' },
  },
}

/* ---------------------------------------------------------------- memory */

function createState(values: number[]): State {
  const nodes = new Map<string, MemNode>()
  const slot = new Map<string, number>()
  nodes.set(HEAD, { id: HEAD, value: null, next: values.length ? 'n1' : null, head: true })
  slot.set(HEAD, 0)
  values.forEach((value, k) => {
    const id = `n${k + 1}`
    nodes.set(id, { id, value, next: k + 1 < values.length ? `n${k + 2}` : null })
    slot.set(id, k + 1)
  })
  return {
    nodes,
    slot,
    aside: new Set(),
    ptr: new Map([['L', HEAD]]),
    int: new Map(),
    park: new Map(),
    done: new Set(),
    tone: new Map(),
    display: new Map(),
    declared: new Set(['L']),
  }
}

function clone(state: State): State {
  return {
    nodes: new Map([...state.nodes].map(([k, v]) => [k, { ...v }])),
    slot: new Map(state.slot),
    aside: new Set(state.aside),
    ptr: new Map(state.ptr),
    int: new Map(state.int),
    park: new Map(state.park),
    done: new Set(state.done),
    tone: new Map(state.tone),
    display: new Map(state.display),
    declared: new Set(state.declared),
  }
}

function node(state: State, id: string | null | undefined): MemNode {
  const found = id ? state.nodes.get(id) : undefined
  if (!found) throw new Error(`dangling node ${id}`)
  return found
}

/** Values reached from the head node; guards against cycles. */
function traverse(state: State): number[] {
  const out: number[] = []
  const seen = new Set<string>()
  let id = state.nodes.get(HEAD)?.next ?? null
  while (id && !seen.has(id)) {
    seen.add(id)
    const n = node(state, id)
    if (n.value !== null) out.push(n.value)
    id = n.next
  }
  return out
}

/** Re-slot nodes in list order, dropping freed ones. */
function tidy(state: State): void {
  for (const [id, n] of state.nodes) if (n.freed) state.nodes.delete(id)
  state.aside.clear()
  state.tone.clear()
  state.done.clear()
  let k = 0
  let id: string | null = HEAD
  const seen = new Set<string>()
  while (id && !seen.has(id)) {
    seen.add(id)
    state.slot.set(id, k++)
    id = node(state, id).next
  }
}

function label(state: State, id: string | null | undefined): string {
  if (!id) return 'NULL'
  const n = node(state, id)
  if (n.head) return '头结点'
  if (n.freed) return '已释放'
  if (n.dataUnset) return '新结点'
  return String(n.value)
}

/* ---------------------------------------------------------------- layout */

function layout(state: State, mark: Mark, showIndex: boolean): Frame {
  const nodes: NodeView[] = []
  const edges: EdgeView[] = []
  const tags: TagView[] = []

  const positions = new Map<string, number>()
  if (showIndex) {
    let k = 0
    let id: string | null = HEAD
    const seen = new Set<string>()
    while (id && !seen.has(id)) {
      seen.add(id)
      if (!state.aside.has(id)) positions.set(id, k)
      k++
      id = node(state, id).next
    }
  }

  const mainSlots = [...state.nodes.keys()].filter((id) => !state.aside.has(id)).map((id) => state.slot.get(id) ?? 0)
  const lastSlot = Math.max(0, ...mainSlots)

  for (const n of state.nodes.values()) {
    const slot = state.slot.get(n.id) ?? 0
    const aside = state.aside.has(n.id)
    const index = positions.get(n.id)
    let tone = state.tone.get(n.id) ?? (state.done.has(n.id) ? 'success' : undefined)
    if (mark.wrote === n.id || mark.read?.id === n.id) tone = tone ?? 'accent'
    nodes.push({
      id: n.id,
      kind: 'record',
      x: slot * PITCH,
      y: aside ? ASIDE_Y : 0,
      text: n.freed ? '已释放' : n.head ? '' : n.dataUnset ? '?' : String(n.value),
      sub: n.head ? '头结点' : index ? `第 ${index} 个` : undefined,
      head: n.head,
      tone: n.freed ? 'muted' : tone,
      ptrMark: n.freed ? undefined : n.ptrUnset ? 'unset' : n.next === null ? 'null' : undefined,
      focus: mark.wrote === n.id ? 'ptr' : mark.read?.id === n.id ? mark.read.field : undefined,
    })
    if (n.next && !n.freed && !n.ptrUnset) {
      const to = node(state, n.next)
      const toSlot = state.slot.get(to.id) ?? 0
      const bothMain = !aside && !state.aside.has(to.id)
      const clearPath = bothMain && toSlot > slot && !mainSlots.some((s) => s > slot && s < toSlot)
      edges.push({
        id: `${n.id}.next`,
        from: n.id,
        port: 'ptr',
        to: to.id,
        tone: mark.wrote === n.id ? 'accent' : undefined,
        route: clearPath ? 'straight' : 'auto',
      })
    }
  }

  for (const [name, target] of state.ptr) {
    const targetNode = target ? state.nodes.get(target) : undefined
    const side = target && state.aside.has(target) ? 'above' : 'below'
    const parkBefore = state.park.get(name) === 'before'
    tags.push({
      id: `var:${name}`,
      label: name,
      color: COLORS[name] ?? 'graphite',
      target: targetNode ? target : null,
      side,
      park: { x: parkBefore ? -PITCH * 0.62 : (lastSlot + 0.62) * PITCH, y: 32 },
    })
  }

  return { nodes, edges, tags }
}

function vars(state: State, order: string[]): VarView[] {
  return order
    .filter((name) => state.declared.has(name))
    .map((name) => {
      const shown = state.display.get(name)
      if (shown) return { name, value: shown, color: COLORS[name] }
      if (state.ptr.has(name)) {
        const target = state.ptr.get(name)
        return { name, value: target ? `→ ${label(state, target)}` : 'NULL', color: COLORS[name] }
      }
      const value = state.int.get(name)
      return { name, value: value === null || value === undefined ? '?' : String(value) }
    })
}

class Recorder {
  readonly steps: Step[] = []
  constructor(
    private readonly state: State,
    private readonly order: string[],
    private readonly showIndex: boolean,
  ) {}

  snap(mark: Mark): void {
    const snapshot = clone(this.state)
    this.steps.push({
      frame: layout(snapshot, mark, this.showIndex),
      line: mark.line,
      title: mark.title,
      note: mark.note,
      vars: vars(snapshot, this.order),
      predict: mark.predict,
      phase: mark.phase,
    })
  }
}

const seq = (values: (number | string)[]) => (values.length ? values.join(' → ') : '空表')

/* --------------------------------------------------------------- reverse */

function simulateReverse(values: number[]): { steps: Step[]; result: number[] } {
  const st = createState(values)
  const rec = new Recorder(st, ['L', 'prev', 'cur', 'nxt'], false)
  const ids = values.map((_, k) => `n${k + 1}`)
  const name = (k: number) => String(values[k])

  for (const name of ['prev', 'cur', 'nxt']) st.declared.add(name)
  st.ptr.set('prev', null)
  st.park.set('prev', 'before')
  st.ptr.set('cur', node(st, HEAD).next)
  st.park.set('nxt', 'after')
  st.park.set('cur', 'after')
  rec.snap({
    line: 2,
    title: 'prev = NULL, cur = L->next',
    note: values.length
      ? `prev 指向已经逆置好的部分，目前为空；cur 指向还没处理的第一个结点 ${name(0)}。`
      : '这是一个空表，cur 一开始就是 NULL。',
  })

  ids.forEach((id, k) => {
    const phase = `第 ${k + 1} 轮`
    const nextId = node(st, id).next
    st.ptr.set('nxt', nextId)
    rec.snap({
      line: 4,
      title: 'nxt = cur->next',
      note: nextId
        ? `先用 nxt 记住 ${name(k)} 的后继 ${name(k + 1)}。下一行会改写 cur->next，之后只能靠 nxt 找到剩下的链。`
        : `${name(k)} 是最后一个结点，它的后继是 NULL，nxt 也就成了 NULL。`,
      phase,
      read: { id, field: 'ptr' },
    })

    const prevId = st.ptr.get('prev') ?? null
    node(st, id).next = prevId
    let predict: Predict | undefined
    if (k === 0) {
      predict = {
        id: 'reverse-first-flip',
        prompt: `执行 cur->next = prev 之后，结点 ${name(0)} 的 next 指向哪里？`,
        options: [
          {
            id: 'null',
            text: 'NULL（∧）',
            correct: true,
            feedback: `对。prev 的初值是 NULL，所以 ${name(0)} 会成为新链表的尾结点。`,
          },
          {
            id: 'old',
            text: values.length > 1 ? `结点 ${values[1]}，仍是原来的后继` : `结点 ${name(0)} 自己`,
            feedback:
              values.length > 1
                ? '赋值会覆盖 cur->next 原来的值。原来的后继已经提前存进 nxt。'
                : 'prev 的初值是 NULL，不是 cur；这个结点不会指向自己。',
          },
          {
            id: 'head',
            text: '头结点',
            feedback: `prev 的初值是 NULL，不是 L。要是让 ${name(0)} 指回头结点，逆置后的链表尾部会绕回开头，形成环。`,
            ghost: { edges: [{ id: `${id}.next`, from: id, port: 'ptr', to: HEAD }] },
          },
        ],
        takeaway: 'cur->next = prev 是真正“掉头”的一步；prev 始终指向已经逆置好的那一段的开头。',
      }
    } else if (k === 1 && values.length >= 3) {
      predict = {
        id: 'reverse-why-nxt',
        prompt: '如果删掉上一行 nxt = cur->next，直接执行 cur->next = prev，会怎样？',
        options: [
          {
            id: 'lost',
            text: `${name(2)} 及之后的结点再也找不到了`,
            correct: true,
            feedback: `对。cur->next 是通往 ${name(2)} 的唯一指针，覆盖之前必须先存进 nxt。`,
          },
          {
            id: 'kept',
            text: '没有影响，cur->next 的旧值会自动保留',
            feedback: 'C 的赋值不会保留旧值。cur->next 一旦改写，原来的地址就丢了。',
          },
          {
            id: 'prev',
            text: '会丢失 prev',
            feedback: `prev 不受这一行影响。真正丢失的是 cur 原来的后继，也就是还没处理的 ${name(2)} 及之后的部分。`,
          },
        ],
        takeaway: '改写指针前，先把会被覆盖的地址存起来。这是所有链表操作的共同套路。',
      }
    }
    rec.snap({
      line: 5,
      title: 'cur->next = prev',
      note:
        k === 0
          ? values.length > 1
            ? `${name(0)} 的 next 改为 NULL。从头结点只能到达 ${name(0)}；后面的结点暂时只能靠 nxt 找到。`
            : `${name(0)} 的 next 改为 NULL。从头结点仍能到达这个唯一的数据结点。`
          : `${name(k)} 的指针掉头，指向 ${name(k - 1)}。从 ${name(k)} 往回走，已经能一路走到 ${name(0)}。`,
      phase,
      wrote: id,
      predict,
    })

    st.ptr.set('prev', id)
    st.done.add(id)
    rec.snap({
      line: 6,
      title: 'prev = cur',
      note: `prev 跟到 ${name(k)}。逆置好的部分现在是 ${seq(values.slice(0, k + 1).reverse())}。`,
      phase,
    })

    st.ptr.set('cur', nextId)
    rec.snap({
      line: 7,
      title: 'cur = nxt',
      note: nextId ? `cur 前进到 ${name(k + 1)}，继续处理剩下的部分。` : 'cur 变成 NULL，没有剩下的结点了。',
      phase,
    })
  })

  const lastId = ids[ids.length - 1] ?? null
  rec.snap({
    line: 3,
    title: 'cur == NULL，循环结束',
    note: values.length
      ? `每个结点的指针都已掉头，prev 指向 ${name(values.length - 1)}；头结点仍只能到达原首结点 ${name(0)}。`
      : '循环一次也没有执行。',
  })

  node(st, HEAD).next = st.ptr.get('prev') ?? null
  rec.snap({
    line: 9,
    title: 'L->next = prev',
    note: values.length
      ? `头结点改为指向 ${name(values.length - 1)}，从头结点又能到达整条逆置后的链表。`
      : '头结点仍指向 NULL。',
    wrote: HEAD,
    predict:
      values.length >= 2
        ? {
            id: 'reverse-who-points-last',
            prompt: '循环结束后，若省略最后的 L->next = prev，从头结点出发能访问到哪些数据结点？',
            options: [
              {
                id: 'old-first',
                text: `只有原首结点 ${name(0)}`,
                correct: true,
                feedback: `对。L->next 仍指向 ${name(0)}，而 ${name(0)}->next 已改为 NULL。`,
              },
              {
                id: 'all',
                text: '整条逆置后的链表',
                feedback: `逆置好的链在 prev 下面；L->next 还指着 ${name(0)}，不会随 prev 自动改变。`,
                ghost: { edges: [{ id: 'h.next', from: HEAD, port: 'ptr', to: lastId! }] },
              },
              {
                id: 'none',
                text: '一个数据结点也访问不到',
                feedback: `L->next 没有变成 NULL，它还指向原首结点 ${name(0)}。`,
              },
            ],
            takeaway: '改变数据结点的 next 不会自动改变 L->next；最后要把头结点接到 prev。',
          }
        : undefined,
  })

  for (const name of ['prev', 'cur', 'nxt']) {
    st.ptr.delete(name)
    st.declared.delete(name)
  }
  tidy(st)
  rec.snap({
    line: null,
    title: '按链表顺序重新排列',
    note: `从头结点出发：${seq(traverse(st))}。结点本身从没移动过，变的只是指针。`,
  })

  return { steps: rec.steps, result: traverse(st) }
}

/* ---------------------------------------------------------------- insert */

function walkToPredecessor(st: State, rec: Recorder, i: number, guard: 'p' | 'p->next'): boolean {
  st.declared.add('p').add('j')
  st.ptr.set('p', HEAD)
  st.int.set('j', 0)
  rec.snap({
    line: 3,
    title: 'p = L; j = 0',
    note: 'p 从头结点出发，j 记录 p 是第几个结点（头结点算第 0 个）。',
    phase: '查找前驱',
  })
  let j = 0
  while (j < i - 1) {
    const current = node(st, st.ptr.get('p'))
    const blocked = guard === 'p' ? false : current.next === null
    if (blocked) break
    const nextId = current.next
    if (!nextId) {
      st.ptr.set('p', null)
      j++
      st.int.set('j', j)
      rec.snap({ line: 5, title: 'p = p->next; j++', note: 'p 走出了表尾，变成 NULL。', phase: '查找前驱' })
      return false
    }
    st.ptr.set('p', nextId)
    j++
    st.int.set('j', j)
    rec.snap({
      line: 5,
      title: 'p = p->next; j++',
      note: `p 走到第 ${j} 个结点 ${label(st, nextId)}，j = ${j}。`,
      phase: '查找前驱',
    })
  }
  return j === i - 1
}

function simulateInsert(values: number[], i: number, e: number): { steps: Step[]; result: number[] } {
  const st = createState(values)
  const rec = new Recorder(st, ['L', 'p', 's', 'i', 'j', 'e'], true)
  st.declared.add('i').add('e')
  st.int.set('i', i)
  st.int.set('e', e)

  if (i < 1) {
    rec.snap({
      line: 2,
      title: 'if (i < 1) return false',
      note: `i = ${i}，位序必须从 1 开始，插入失败。`,
      phase: '结束',
    })
    return { steps: rec.steps, result: traverse(st) }
  }
  rec.snap({
    line: 2,
    title: 'if (i < 1) return false',
    note: `i = ${i}，满足 i ≥ 1，继续查找前驱。`,
    phase: '查找前驱',
  })

  const found = walkToPredecessor(st, rec, i, 'p')
  const pId = st.ptr.get('p') ?? null
  if (!found || !pId) {
    rec.snap({
      line: 7,
      title: 'return false',
      note: `表中只有 ${values.length} 个结点，位置 ${i} 不合法。`,
      phase: '结束',
    })
    return { steps: rec.steps, result: traverse(st) }
  }
  rec.snap({
    line: 4,
    title: `j == i - 1，循环结束`,
    note: `p 停在第 ${i - 1} 个结点${i === 1 ? '（头结点）' : ` ${label(st, pId)}`}。新结点要接在它后面。`,
    phase: '查找前驱',
  })

  const p = node(st, pId)
  const succId = p.next
  const succ = succId ? label(st, succId) : null
  const s: MemNode = { id: 's', value: e, next: null, dataUnset: true, ptrUnset: true }
  st.nodes.set('s', s)
  st.slot.set('s', (st.slot.get(pId) ?? 0) + 0.5)
  st.aside.add('s')
  st.tone.set('s', 'success')
  st.declared.add('s')
  st.ptr.set('s', 's')
  rec.snap({
    line: 8,
    title: 's = malloc(sizeof(LNode))',
    note: '申请新结点 s；数据域和指针域尚未赋值，图中先画成问号。',
    phase: '插入',
  })
  rec.snap({
    line: 9,
    title: 'if (s == NULL) return false',
    note: 's 不为 NULL，内存分配成功，可以写入新结点。',
    phase: '插入',
  })

  s.dataUnset = false
  rec.snap({
    line: 10,
    title: 's->data = e',
    note: `把 ${e} 写进新结点的数据域。`,
    phase: '插入',
    read: { id: 's', field: 'data' },
  })

  const tail = succId === null
  const swapGhost: GhostLayer = {
    edges: [
      { id: `${pId}.next`, from: pId, port: 'ptr', to: 's' },
      { id: 's.next', from: 's', port: 'ptr', to: 's' },
    ],
  }
  s.next = succId
  s.ptrUnset = false
  rec.snap({
    line: 11,
    title: 's->next = p->next',
    note: tail
      ? 'p 原来就是表尾，s->next 得到 NULL。'
      : `先让 s 指向 p 的后继 ${succ}。此刻 ${succ} 同时被两个指针指着，链没有断。`,
    phase: '插入',
    wrote: 's',
    predict: {
      id: 'insert-order',
      prompt: '新结点已准备好。s->next = p->next 和 p->next = s 这两句能交换顺序吗？',
      options: [
        {
          id: 'no',
          text: tail ? '不能，先改 p->next 会让 s 指向自己' : '不能，先改 p->next 会丢掉原来的后继',
          correct: true,
          feedback: tail
            ? '对。先执行 p->next = s，再执行 s->next = p->next，s 就指向了自己，遍历会陷入死循环。'
            : `对。先改 p->next，下一句会让 s 指向自己；原后继 ${succ} 也无法从头结点到达。`,
        },
        {
          id: 'yes',
          text: '能，两句互不影响',
          feedback: tail
            ? '看虚线：先改 p->next，s->next = p->next 就成了 s->next = s，形成自环。'
            : `看虚线：先执行 p->next = s，再执行 s->next = p->next，s 就指向自己；从头结点也到不了 ${succ}。`,
          ghost: swapGhost,
        },
        {
          id: 'only-tail',
          text: '只有 p 是表尾时不能',
          feedback: '与 p 在哪里无关。先改 p->next 后，s->next 读到的就是 s，自环在任何插入位置都会出现。',
          ghost: swapGhost,
        },
      ],
      takeaway: '先连后断：新结点先接住后继，再让前驱指向新结点。',
    },
  })

  const options = insertOrderOptions(values, i, e)
  p.next = 's'
  rec.snap({
    line: 12,
    title: 'p->next = s',
    note: `再把 p 的指针改到 s。s 正式接进链表，成为第 ${i} 个结点。`,
    phase: '插入',
    wrote: pId,
    predict: {
      id: 'insert-result',
      prompt: '执行 p->next = s 之后，从头结点出发依次经过哪些结点？（用 s 标出新结点）',
      options,
      takeaway: `“插在第 i 个位置”意味着插入后 ${e} 成为第 i 个结点，所以要找的是第 i-1 个结点。`,
    },
  })

  tidy(st)
  rec.snap({
    line: 13,
    title: 'return true',
    note: `插入完成：${seq(traverse(st))}。${i <= values.length ? '后面结点的位序都加 1。' : ''}`,
    phase: '结束',
  })
  return { steps: rec.steps, result: traverse(st) }
}

function insertOrderOptions(values: number[], i: number, e: number) {
  const at = (pos: number) => seq([...values.slice(0, pos - 1), `s(${e})`, ...values.slice(pos - 1)])
  const n = values.length
  const options = [
    {
      id: 'right',
      text: at(i),
      correct: true,
      feedback: `对。s 接在${i === 1 ? '头结点' : `原第 ${i - 1} 个结点`}之后，成为第 ${i} 个结点。`,
    },
  ]
  const used = new Set([at(i)])
  const candidates = [
    {
      id: 'after',
      pos: i + 1,
      feedback: `这把 s 插在原第 ${i} 个结点之后，结果成了第 ${i + 1} 个；应在${i === 1 ? '头结点' : `原第 ${i - 1} 个结点`}之后插入。`,
    },
    {
      id: 'before',
      pos: i - 1,
      feedback: `这比正确位置靠前了一位。j 从 0（头结点）开始数，循环结束时 p 是第 ${i - 1} 个结点，s 接在它后面。`,
    },
    {
      id: 'front',
      pos: 1,
      feedback: `只有 i = 1 时 s 才在最前面。这里 p 停在第 ${i - 1} 个结点，s 接在它后面。`,
    },
    { id: 'end', pos: n + 1, feedback: 's->next 接住了 p 原来的后继，s 后面还连着原来的结点，并不在表尾。' },
  ]
  for (const c of candidates) {
    if (options.length >= 3) break
    if (c.pos < 1 || c.pos > n + 1) continue
    const text = at(c.pos)
    if (used.has(text)) continue
    used.add(text)
    options.push({ id: c.id, text, correct: false, feedback: c.feedback })
  }
  return options.sort((a, b) => a.text.localeCompare(b.text))
}

/* ---------------------------------------------------------------- delete */

function simulateDelete(values: number[], i: number): { steps: Step[]; result: number[]; removed: number | null } {
  const st = createState(values)
  const rec = new Recorder(st, ['L', 'p', 'q', 'i', 'j', '*e'], true)
  st.declared.add('i').add('*e')
  st.int.set('i', i)
  st.int.set('*e', null)

  if (i < 1) {
    rec.snap({
      line: 2,
      title: 'if (i < 1) return false',
      note: `i = ${i}，位序必须从 1 开始，删除失败。`,
      phase: '结束',
    })
    return { steps: rec.steps, result: traverse(st), removed: null }
  }
  rec.snap({
    line: 2,
    title: 'if (i < 1) return false',
    note: `i = ${i}，满足 i ≥ 1，继续查找前驱。`,
    phase: '查找前驱',
  })

  const found = walkToPredecessor(st, rec, i, 'p->next')
  const pId = st.ptr.get('p') ?? null
  const p = pId ? node(st, pId) : null
  if (!found || !p || p.next === null) {
    rec.snap({ line: 7, title: 'return false', note: `第 ${i} 个结点不存在，删除失败。`, phase: '结束' })
    return { steps: rec.steps, result: traverse(st), removed: null }
  }
  rec.snap({
    line: 4,
    title: 'j == i - 1，循环结束',
    note: `p 停在第 ${i - 1} 个结点${i === 1 ? '（头结点）' : ` ${label(st, pId)}`}，它的后继就是要删的结点。`,
    phase: '查找前驱',
  })

  const qId = p.next
  const q = node(st, qId)
  st.declared.add('q')
  st.ptr.set('q', qId)
  rec.snap({
    line: 8,
    title: 'q = p->next',
    note: `q 指向被删结点 ${q.value}。先用 q 记住它，后面释放内存还要用到。`,
    phase: '删除',
  })

  st.int.set('*e', q.value)
  rec.snap({
    line: 9,
    title: '*e = q->data',
    note: `把被删值 ${q.value} 写入 *e，带回给调用者。`,
    phase: '删除',
    read: { id: qId, field: 'data' },
  })

  const tail = q.next === null
  const after = q.next ? label(st, q.next) : 'NULL'
  p.next = q.next
  st.aside.add(qId)
  st.tone.set(qId, 'danger')
  rec.snap({
    line: 10,
    title: 'p->next = q->next',
    note: tail
      ? `p 的指针改成 NULL，${q.value} 被摘下，p 成为新的表尾。`
      : `p 直接跨过 q，指向 ${after}。${q.value} 被摘下，但它还占着内存。`,
    phase: '删除',
    wrote: pId!,
    predict: {
      id: 'delete-relink',
      prompt: '要把 q 从链上摘下，p->next 应该改成什么？',
      options: tail
        ? [
            {
              id: 'right',
              text: 'q->next（也就是 NULL）',
              correct: true,
              feedback: 'q 是表尾，它的后继是 NULL，所以 p 成为新的表尾。',
            },
            { id: 'q', text: 'q', feedback: 'p->next 本来就是 q，什么也没改变，q 仍在链上。' },
            {
              id: 'self',
              text: 'p',
              feedback: '看虚线：p->next = p 会让 p 指向自己，遍历时陷入死循环。',
              ghost: { edges: [{ id: `${pId}.next`, from: pId!, port: 'ptr', to: pId! }] },
            },
          ]
        : [
            { id: 'right', text: 'q->next', correct: true, feedback: `对。p 直接跨过 q，指向 q 的后继 ${after}。` },
            {
              id: 'null',
              text: 'NULL',
              feedback: `改成 NULL 会让 ${after} 及之后的结点无法从头结点到达。`,
              ghost: {
                nodes: [
                  {
                    id: 'guess-cut',
                    kind: 'record',
                    x: (st.slot.get(pId!) ?? 0) * PITCH,
                    y: 0,
                    text: p.head ? '' : String(p.value),
                    head: p.head,
                    ptrMark: 'null',
                  },
                ],
              },
            },
            { id: 'q', text: 'q', feedback: 'p->next 本来就是 q，什么也没改变，q 仍在链上。' },
          ],
      takeaway: '删除只需要改前驱的一个指针：p->next = q->next。',
    },
  })

  // Nodes after q, for the "free releases the whole tail" misconception.
  const tailIds: string[] = []
  for (let id = q.next; id; id = node(st, id).next) tailIds.push(id)

  q.freed = true
  st.display.set('q', '悬空指针（不可使用）')
  rec.snap({
    line: 11,
    title: 'free(q)',
    note: `释放被删结点（值为 ${q.value}）；q 已失效，成为悬空指针，不能再使用。`,
    phase: '删除',
    predict: {
      id: 'delete-dangling',
      prompt: 'q 已从链上摘下。执行 free(q) 后，哪些结点会被释放？',
      options: [
        {
          id: 'only-q',
          text: `只释放 q 指向的结点（${q.value}）`,
          correct: true,
          feedback: '对。free(q) 只释放 q 所指的结点；p->next 已绕过它，其余结点仍在链上。',
        },
        q.next
          ? {
              id: 'all',
              text: '从 q 开始的后续结点都被释放',
              feedback: 'free(q) 只释放一个结点，不会沿 next 逐个释放；后继仍由 p->next 接着。',
              ghost: {
                nodes: tailIds.map((id) => ({
                  id: `guess-freed-${id}`,
                  kind: 'record' as const,
                  x: (st.slot.get(id) ?? 0) * PITCH,
                  y: 0,
                  text: label(st, id),
                  tone: 'danger' as const,
                })),
              },
            }
          : {
              id: 'predecessor',
              text: 'q 和前驱 p 都被释放',
              feedback: 'free(q) 只释放 q 指向的结点；前驱 p 仍在链上，已成为新表尾。',
              ghost: {
                nodes: [
                  {
                    id: 'guess-freed-p',
                    kind: 'record',
                    x: (st.slot.get(pId!) ?? 0) * PITCH,
                    y: 0,
                    text: p.head ? '' : String(p.value),
                    head: p.head,
                    tone: 'danger',
                  },
                ],
              },
            },
        {
          id: 'still',
          text: `${q.value} 只是脱链，内存仍可使用`,
          feedback: '脱链发生在上一行；free(q) 会归还这一个结点的内存，之后不能再通过 q 访问。',
        },
      ],
      takeaway: '先改 p->next 保住后继，再 free(q) 释放被删结点；此后 q 是悬空指针。',
    },
  })

  st.ptr.delete('q')
  tidy(st)
  rec.snap({
    line: 12,
    title: 'return true',
    note: `删除完成：${seq(traverse(st))}。${tail ? '' : '后面结点的位序都减 1。'}`,
    phase: '结束',
  })
  return { steps: rec.steps, result: traverse(st), removed: q.value }
}

/* ---------------------------------------------------------------- module */

const OPS: { id: Op; label: string }[] = [
  { id: 'insert', label: '插入' },
  { id: 'delete', label: '删除' },
  { id: 'reverse', label: '原地逆置' },
]

function same(a: number[], b: number[]): boolean {
  return a.length === b.length && a.every((v, k) => v === b[k])
}

export const linkedList: SceneModule = {
  id: 'linked-list',
  title: '单链表',
  ops: OPS,
  params: [
    { key: 'values', label: '链表', type: 'list', min: 1, max: 7 },
    {
      key: 'i',
      label: '位置 i',
      type: 'int',
      min: 1,
      max: (spec) => (spec.values as number[]).length + (spec.op === 'insert' ? 1 : 0),
      ops: ['insert', 'delete'],
    },
    { key: 'e', label: '新值 e', type: 'int', min: 0, max: 99, ops: ['insert'] },
  ],

  defaultSpec(op = 'reverse'): LinkedListSpec {
    return { scene: 'linked-list', op: op as Op, values: [3, 7, 1, 9, 4], i: 3, e: 5 }
  },

  build(raw: SceneSpec): Trace {
    const spec = raw as LinkedListSpec
    const values = spec.values
    const code = LISTINGS[spec.op]
    if (spec.op === 'reverse') {
      const { steps, result } = simulateReverse(values)
      const expected = [...values].reverse()
      return { steps, code, check: { ok: same(result, expected), detail: `结果 ${seq(result)}` } }
    }
    if (spec.op === 'insert') {
      const i = spec.i ?? 1
      const e = spec.e ?? 0
      const { steps, result } = simulateInsert(values, i, e)
      const valid = i >= 1 && i <= values.length + 1
      const expected = valid ? [...values.slice(0, i - 1), e, ...values.slice(i - 1)] : values
      return { steps, code, check: { ok: same(result, expected), detail: `结果 ${seq(result)}` } }
    }
    const i = spec.i ?? 1
    const { steps, result, removed } = simulateDelete(values, i)
    const valid = i >= 1 && i <= values.length
    const expected = valid ? values.filter((_, k) => k !== i - 1) : values
    const ok = same(result, expected) && (!valid || removed === values[i - 1])
    return { steps, code, check: { ok, detail: `结果 ${seq(result)}` } }
  },
}
