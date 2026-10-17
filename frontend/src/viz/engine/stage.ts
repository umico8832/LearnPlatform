import { Spring, Spring2, springs, wake, unregister } from '@/motion'
import type { EdgeRoute, EdgeView, Frame, GhostLayer, LabelView, NodeView, RegionView, TagView } from './types'

/**
 * Generic stage renderer. Knows nothing about algorithms: it receives
 * declarative frames, diffs them by id, and springs every primitive.
 * Nodes, tags and labels are DOM (crisp text, CSS styling); edges are SVG
 * paths recomputed every tick from live node positions.
 */

export const NODE_SIZE = {
  record: { w: 100, h: 48, ptr: 40 },
  cell: { w: 48, h: 48, ptr: 0 },
  circle: { w: 48, h: 48, ptr: 0 },
} as const

const SVG_NS = 'http://www.w3.org/2000/svg'
const ARROW = 8
const HOP_THRESHOLD = 110

type Side = 'left' | 'top' | 'bottom' | 'origin'

interface Point {
  x: number
  y: number
}

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className: string,
  parent?: Element,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  node.className = className
  parent?.appendChild(node)
  return node
}

function svg<K extends keyof SVGElementTagNameMap>(
  tag: K,
  className: string,
  parent?: Element,
): SVGElementTagNameMap[K] {
  const node = document.createElementNS(SVG_NS, tag)
  node.setAttribute('class', className)
  parent?.appendChild(node)
  return node
}

function size(view: NodeView) {
  return NODE_SIZE[view.kind]
}

/* ---------------------------------------------------------------- nodes */

class NodeItem {
  view: NodeView
  readonly el: HTMLDivElement
  readonly pos: Spring2
  readonly scale = new Spring(0.55, springs.pointer)
  readonly opacity = new Spring(0, springs.fade)
  alive = true
  private dataEl: HTMLDivElement
  private ptrEl: HTMLDivElement | null = null
  private subEl: HTMLDivElement

  constructor(view: NodeView, layer: HTMLElement, ghost = false, origin?: Point) {
    this.view = view
    this.el = el('div', `vz-node vz-${view.kind}${ghost ? ' is-ghost' : ''}`, layer)
    this.dataEl = el('div', 'vz-field vz-data', this.el)
    if (view.kind === 'record') {
      this.ptrEl = el('div', 'vz-field vz-ptr', this.el)
      el('span', 'vz-ptr-mark vz-ptr-null', this.ptrEl).textContent = '∧'
      el('span', 'vz-ptr-mark vz-ptr-unset', this.ptrEl).textContent = '?'
      el('span', 'vz-ptr-dot', this.ptrEl)
    }
    this.subEl = el('div', 'vz-sub', this.el)
    if (origin) {
      // Fly out of another node: start on top of it, already mostly visible.
      this.pos = new Spring2(origin.x, origin.y, springs.layout)
      this.scale.jump(0.9)
      this.opacity.jump(0.6)
      const dx = view.x - origin.x
      if (Math.abs(dx) > HOP_THRESHOLD) this.pos.y.kick(-Math.min((Math.abs(dx) - HOP_THRESHOLD + 60) * 2.2, 1100))
    } else {
      this.pos = new Spring2(view.x, view.y + 14, springs.layout)
    }
    this.update(view)
  }

  update(view: NodeView): void {
    const previous = this.view
    this.view = view
    if (this.alive && Math.abs(view.x - previous.x) > HOP_THRESHOLD) {
      // Long horizontal moves travel in an arc so crossing nodes don't overlap.
      const direction = view.x > previous.x ? -1 : 1
      this.pos.y.kick(direction * Math.min((Math.abs(view.x - previous.x) - HOP_THRESHOLD + 40) * 2.4, 1300))
    }
    this.alive = true
    this.pos.set(view.x, view.y)
    this.scale.set(1)
    this.opacity.set(1)
    this.dataEl.textContent = view.text
    this.subEl.textContent = view.sub ?? ''
    this.el.dataset.tone = view.tone ?? 'default'
    this.el.dataset.focus = view.focus ?? ''
    this.el.classList.toggle('is-head', Boolean(view.head))
    if (this.ptrEl) this.ptrEl.dataset.mark = view.ptrMark ?? ''
  }

  exit(): void {
    this.alive = false
    this.scale.set(0.6)
    this.opacity.set(0)
  }

  step(dt: number): boolean {
    const a = this.pos.step(dt)
    const b = this.scale.step(dt)
    const c = this.opacity.step(dt)
    return a || b || c
  }

  paint(): void {
    const { w, h } = size(this.view)
    const o = Math.max(0, Math.min(1, this.opacity.value))
    this.el.style.transform = `translate(${this.pos.x.value - w / 2}px, ${this.pos.y.value - h / 2}px) scale(${this.scale.value})`
    this.el.style.opacity = String(o)
    this.el.style.filter = o < 0.98 ? `blur(${(1 - o) * 6}px)` : ''
  }

  /** Where an outgoing pointer starts. */
  port(name: string | undefined): Point {
    const { w, ptr } = size(this.view)
    const x = this.pos.x.value
    const y = this.pos.y.value
    if (this.view.kind === 'record') return { x: x + w / 2 - ptr / 2, y }
    if (this.view.kind === 'circle') {
      const r = w / 2
      if (name === 'left') return { x: x - r * 0.62, y: y + r * 0.78 }
      if (name === 'right') return { x: x + r * 0.62, y: y + r * 0.78 }
      return { x, y: y + r }
    }
    return { x: x + w / 2, y }
  }

  anchor(side: Side, from: Point): Point {
    const { w, h } = size(this.view)
    const x = this.pos.x.value
    const y = this.pos.y.value
    if (this.view.kind === 'circle') {
      // Land on the circle boundary, facing the source.
      const dx = from.x - x
      const dy = from.y - y
      const len = Math.hypot(dx, dy) || 1
      return { x: x + (dx / len) * (w / 2 + 1), y: y + (dy / len) * (w / 2 + 1) }
    }
    // Land on the seam between fields, clear of the caption above the data field.
    if (side === 'top') return { x: x + w * 0.1, y: y - h / 2 - 2 }
    if (side === 'bottom') return { x: x + w * 0.1, y: y + h / 2 + 2 }
    return { x: x - w / 2 - 2, y }
  }

  get bottom(): number {
    return this.pos.y.value + size(this.view).h / 2
  }

  get top(): number {
    return this.pos.y.value - size(this.view).h / 2
  }
}

/* ---------------------------------------------------------------- edges */

interface Routing {
  route: Exclude<EdgeRoute, 'auto'> | 'cross' | 'self'
  side: Side
  height: number
}

class EdgeItem {
  view: EdgeView
  readonly group: SVGGElement
  private line: SVGPathElement
  private head: SVGPathElement
  /** Tip offset from the live anchor; springs to zero. */
  readonly tip = new Spring2(0, 0, springs.pointer)
  /** Control vectors relative to the start and tip. */
  readonly c1 = new Spring2(0, 0, springs.bend)
  readonly c2 = new Spring2(0, 0, springs.bend)
  readonly opacity = new Spring(0, springs.fade)
  alive = true
  routing: Routing = { route: 'straight', side: 'left', height: 0 }
  private key = ''
  private lastTip: Point | null = null
  private lastStart: Point | null = null

  constructor(view: EdgeView, layer: SVGElement, ghost: boolean) {
    this.view = view
    this.group = svg('g', `vz-edge${ghost ? ' is-ghost' : ''}`, layer)
    this.line = svg('path', 'vz-edge-line', this.group)
    this.head = svg('path', 'vz-edge-head', this.group)
  }

  update(view: EdgeView, routing: Routing): void {
    this.view = view
    this.routing = routing
    this.alive = true
    this.opacity.set(1)
    this.group.dataset.tone = view.tone ?? 'default'
    this.group.classList.toggle('is-line', view.style === 'line')
  }

  exit(): void {
    this.alive = false
    this.routing = { route: 'straight', side: 'origin', height: 0 }
  }

  /** Recompute geometry against live node positions and advance springs. */
  step(dt: number, nodes: Map<string, NodeItem>): boolean {
    const source = nodes.get(this.view.from)
    const target = nodes.get(this.view.to)
    if (!source) {
      this.alive = false
      this.opacity.set(0)
      return this.opacity.step(dt)
    }
    const start = source.port(this.view.port)
    const side: Side = this.alive && target ? this.routing.side : 'origin'
    const anchor = side === 'origin' || !target ? start : target.anchor(side, start)
    const key = `${this.view.to}:${side}`

    if (key !== this.key) {
      // Retarget: keep the tip where it is and let it spring to the new anchor.
      const from = this.lastTip ?? start
      this.tip.x.value = from.x - anchor.x
      this.tip.y.value = from.y - anchor.y
      this.tip.set(0, 0)
      this.key = key
    }

    const [t1, t2] = this.controls(start, anchor)
    this.c1.set(t1.x, t1.y)
    this.c2.set(t2.x, t2.y)
    const moving = [this.tip.step(dt), this.c1.step(dt), this.c2.step(dt)].some(Boolean)

    if (!this.alive) {
      // Fade only once the pointer has mostly retracted.
      const remaining = Math.hypot(this.tip.x.value, this.tip.y.value)
      if (remaining < 18) this.opacity.set(0)
    }
    const fading = this.opacity.step(dt)

    this.lastStart = start
    this.lastTip = { x: anchor.x + this.tip.x.value, y: anchor.y + this.tip.y.value }
    return moving || fading
  }

  private controls(start: Point, anchor: Point): [Point, Point] {
    const dx = anchor.x - start.x
    const dy = anchor.y - start.y
    const { route, height } = this.routing
    switch (route) {
      case 'over':
        return [
          { x: Math.sign(dx) * Math.min(Math.abs(dx) * 0.12, 24), y: -height },
          { x: 0, y: -height * 0.8 },
        ]
      case 'under':
        return [
          { x: Math.sign(dx) * Math.min(Math.abs(dx) * 0.12, 24), y: height },
          { x: 0, y: height * 0.8 },
        ]
      case 'self':
        return [
          { x: 46, y: -86 },
          { x: -12, y: -70 },
        ]
      case 'cross': {
        const reach = Math.max(30, Math.abs(dy) * 0.45)
        const out = { x: Math.min(Math.max(dx * 0.3, 18), 60), y: dy * 0.2 }
        if (this.routing.side === 'top') return [out, { x: 0, y: -reach }]
        if (this.routing.side === 'bottom') return [out, { x: 0, y: reach }]
        return [out, { x: -Math.max(30, Math.abs(dx) * 0.35), y: 0 }]
      }
      default:
        return [
          { x: dx * 0.35, y: dy * 0.35 },
          { x: -dx * 0.35, y: -dy * 0.35 },
        ]
    }
  }

  paint(): void {
    const start = this.lastStart
    const tip = this.lastTip
    if (!start || !tip) return
    const p1 = { x: start.x + this.c1.x.value, y: start.y + this.c1.y.value }
    const p2 = { x: tip.x + this.c2.x.value, y: tip.y + this.c2.y.value }
    let dirX = tip.x - p2.x
    let dirY = tip.y - p2.y
    let len = Math.hypot(dirX, dirY)
    if (len < 1) {
      dirX = tip.x - start.x
      dirY = tip.y - start.y
      len = Math.hypot(dirX, dirY) || 1
    }
    const ux = dirX / len
    const uy = dirY / len
    const span = Math.hypot(tip.x - start.x, tip.y - start.y)
    const arrow = this.view.style === 'line' ? 0 : Math.min(ARROW, span * 0.5)
    const end = { x: tip.x - ux * arrow * 0.7, y: tip.y - uy * arrow * 0.7 }
    this.line.setAttribute('d', `M${start.x},${start.y} C${p1.x},${p1.y} ${p2.x},${p2.y} ${end.x},${end.y}`)
    if (arrow > 0.5) {
      const bx = tip.x - ux * arrow
      const by = tip.y - uy * arrow
      const w = arrow * 0.62
      this.head.setAttribute('d', `M${tip.x},${tip.y} L${bx - uy * w},${by + ux * w} L${bx + uy * w},${by - ux * w} Z`)
    } else {
      this.head.setAttribute('d', '')
    }
    this.group.style.opacity = String(Math.max(0, Math.min(1, this.opacity.value)))
  }
}

/* ----------------------------------------------------------------- tags */

const TAG_GAP = 6

function tagWidth(view: TagView): number {
  const text = [...view.label].reduce((sum, ch) => sum + (ch.charCodeAt(0) > 0x2e80 ? 12.5 : 7.4), 0)
  return text + 18 + (view.target === null ? 34 : 0)
}

class TagItem {
  view: TagView
  readonly el: HTMLDivElement
  readonly pos: Spring2
  readonly scale = new Spring(0.6, springs.pointer)
  readonly opacity = new Spring(0, springs.fade)
  alive = true
  private labelEl: HTMLSpanElement

  constructor(view: TagView, layer: HTMLElement, ghost: boolean, origin: Point) {
    this.view = view
    this.el = el('div', `vz-tag${ghost ? ' is-ghost' : ''}`, layer)
    const inner = el('div', 'vz-tag-inner', this.el)
    el('span', 'vz-tag-stem', inner)
    const pill = el('span', 'vz-tag-pill', inner)
    this.labelEl = el('span', 'vz-tag-label', pill)
    el('span', 'vz-tag-null', pill).textContent = 'NULL'
    this.pos = new Spring2(origin.x, origin.y + 12, { response: 0.5, damping: 0.74 })
    this.update(view)
  }

  update(view: TagView): void {
    this.view = view
    this.alive = true
    this.scale.set(1)
    this.opacity.set(1)
    this.labelEl.textContent = view.label
    this.el.dataset.color = view.color
    this.el.dataset.side = view.side ?? 'below'
    this.el.classList.toggle('is-null', view.target === null)
  }

  exit(): void {
    this.alive = false
    this.scale.set(0.6)
    this.opacity.set(0)
  }

  step(dt: number): boolean {
    const a = this.pos.step(dt)
    const b = this.scale.step(dt)
    const c = this.opacity.step(dt)
    return a || b || c
  }

  paint(): void {
    const o = Math.max(0, Math.min(1, this.opacity.value))
    this.el.style.transform = `translate(${this.pos.x.value}px, ${this.pos.y.value}px) scale(${this.scale.value})`
    this.el.style.opacity = String(o)
  }
}

/* --------------------------------------------------------------- labels */

class LabelItem {
  view: LabelView
  readonly el: HTMLDivElement
  readonly pos: Spring2
  readonly opacity = new Spring(0, springs.fade)
  alive = true

  constructor(view: LabelView, layer: HTMLElement) {
    this.view = view
    this.el = el('div', 'vz-label', layer)
    this.pos = new Spring2(view.x, view.y, springs.layout)
    this.update(view)
  }

  update(view: LabelView): void {
    this.view = view
    this.alive = true
    this.pos.set(view.x, view.y)
    this.opacity.set(1)
    this.el.textContent = view.text
    this.el.dataset.align = view.align ?? 'center'
    this.el.dataset.tone = view.tone ?? 'default'
  }

  exit(): void {
    this.alive = false
    this.opacity.set(0)
  }

  step(dt: number): boolean {
    const a = this.pos.step(dt)
    const b = this.opacity.step(dt)
    return a || b
  }

  paint(): void {
    this.el.style.transform = `translate(${this.pos.x.value}px, ${this.pos.y.value}px)`
    this.el.style.opacity = String(Math.max(0, Math.min(1, this.opacity.value)))
  }
}

class RegionItem {
  view: RegionView
  readonly el: HTMLDivElement
  readonly box: [Spring, Spring, Spring, Spring]
  readonly opacity = new Spring(0, springs.fade)
  alive = true
  private labelEl: HTMLDivElement

  constructor(view: RegionView, layer: HTMLElement) {
    this.view = view
    this.el = el('div', 'vz-region', layer)
    this.labelEl = el('div', 'vz-region-label', this.el)
    this.box = [view.x, view.y, view.w, view.h].map((v) => new Spring(v, springs.layout)) as RegionItem['box']
    this.update(view)
  }

  update(view: RegionView): void {
    this.view = view
    this.alive = true
    ;[view.x, view.y, view.w, view.h].forEach((v, i) => this.box[i].set(v))
    this.opacity.set(1)
    this.labelEl.textContent = view.label ?? ''
    this.el.dataset.style = view.style ?? 'well'
  }

  exit(): void {
    this.alive = false
    this.opacity.set(0)
  }

  step(dt: number): boolean {
    return [...this.box.map((s) => s.step(dt)), this.opacity.step(dt)].some(Boolean)
  }

  paint(): void {
    const [x, y, w, h] = this.box.map((s) => s.value)
    this.el.style.transform = `translate(${x}px, ${y}px)`
    this.el.style.width = `${w}px`
    this.el.style.height = `${h}px`
    this.el.style.opacity = String(Math.max(0, Math.min(1, this.opacity.value)))
  }
}

/* ---------------------------------------------------------------- stage */

interface Bounds {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export interface Insets {
  top: number
  right: number
  bottom: number
  left: number
}

export class Stage {
  private readonly host: HTMLElement
  private readonly world: HTMLDivElement
  private readonly regionLayer: HTMLDivElement
  private readonly edgeLayer: SVGSVGElement
  private readonly nodeLayer: HTMLDivElement
  private readonly tagLayer: HTMLDivElement
  private readonly labelLayer: HTMLDivElement
  private readonly nodes = new Map<string, NodeItem>()
  private readonly edges = new Map<string, EdgeItem>()
  private readonly tags = new Map<string, TagItem>()
  private readonly labels = new Map<string, LabelItem>()
  private readonly regions = new Map<string, RegionItem>()
  private readonly camera = {
    scale: new Spring(1, springs.camera),
    x: new Spring(0, springs.camera),
    y: new Spring(0, springs.camera),
  }
  private bounds: Bounds = { minX: 0, minY: 0, maxX: 1, maxY: 1 }
  private insets: Insets = { top: 0, right: 0, bottom: 0, left: 0 }
  private readonly resizeObserver: ResizeObserver
  private firstFrame = true
  private readonly tick = (dt: number) => this.advance(dt)

  constructor(host: HTMLElement) {
    this.host = host
    host.classList.add('vz-stage')
    this.world = el('div', 'vz-world', host)
    this.regionLayer = el('div', 'vz-layer', this.world)
    this.edgeLayer = svg('svg', 'vz-edges', this.world)
    this.nodeLayer = el('div', 'vz-layer', this.world)
    this.tagLayer = el('div', 'vz-layer', this.world)
    this.labelLayer = el('div', 'vz-layer', this.world)
    this.resizeObserver = new ResizeObserver(() => this.frameCamera(false))
    this.resizeObserver.observe(host)
  }

  destroy(): void {
    unregister(this.tick)
    this.resizeObserver.disconnect()
    this.host.replaceChildren()
  }

  setInsets(insets: Partial<Insets>): void {
    this.insets = { ...this.insets, ...insets }
    this.frameCamera(false)
  }

  render(frame: Frame, ghost?: GhostLayer | null): void {
    this.syncNodes(frame.nodes, ghost?.nodes ?? [])
    this.syncEdges(frame.edges, ghost?.edges ?? [])
    this.syncTags(frame.tags, ghost?.tags ?? [])
    this.syncSimple(this.labels, frame.labels ?? [], (v) => new LabelItem(v, this.labelLayer))
    this.syncSimple(this.regions, frame.regions ?? [], (v) => new RegionItem(v, this.regionLayer))
    this.bounds = this.measure(frame, ghost)
    this.frameCamera(this.firstFrame)
    this.firstFrame = false
    wake(this.tick)
  }

  private syncNodes(views: NodeView[], ghosts: NodeView[]): void {
    const seen = new Set<string>()
    const apply = (source: NodeView, ghost: boolean) => {
      const view = ghost ? { ...source, id: `ghost:${source.id}` } : source
      seen.add(view.id)
      const item = this.nodes.get(view.id)
      if (item) {
        item.update(view)
        return
      }
      const from = view.enterFrom ? this.nodes.get(view.enterFrom) : undefined
      const origin = from?.alive ? { x: from.pos.x.value, y: from.pos.y.value } : undefined
      this.nodes.set(view.id, new NodeItem(view, this.nodeLayer, ghost, origin))
    }
    views.forEach((v) => apply(v, false))
    ghosts.forEach((v) => apply(v, true))
    for (const [id, item] of this.nodes) if (!seen.has(id) && item.alive) item.exit()
  }

  private syncEdges(views: EdgeView[], ghosts: EdgeView[]): void {
    const seen = new Set<string>()
    const apply = (view: EdgeView, ghost: boolean) => {
      const id = ghost ? `ghost:${view.id}` : view.id
      seen.add(id)
      const routing = this.route(view)
      let item = this.edges.get(id)
      if (!item) {
        item = new EdgeItem({ ...view, id }, this.edgeLayer, ghost)
        this.edges.set(id, item)
      }
      item.update({ ...view, id }, routing)
    }
    views.forEach((v) => apply(v, false))
    ghosts.forEach((v) => apply({ ...v, tone: v.tone ?? 'warning' }, true))
    for (const [id, item] of this.edges) if (!seen.has(id) && item.alive) item.exit()
  }

  /** Decide a route from the target layout, so it stays stable mid-animation. */
  private route(view: EdgeView): Routing {
    const source = this.nodes.get(view.from)?.view
    const target = this.nodes.get(view.to)?.view
    if (!source || !target) return { route: 'straight', side: 'left', height: 0 }
    if (view.from === view.to) return { route: 'self', side: 'top', height: 0 }
    if (source.kind === 'circle' || target.kind === 'circle') return { route: 'straight', side: 'left', height: 0 }
    const { w } = size(source)
    const dx = target.x - source.x
    const dy = target.y - source.y
    const arc = Math.min(40 + Math.abs(dx) * 0.16, 170)
    const forced = view.route && view.route !== 'auto' ? view.route : null
    if (forced === 'over') return { route: 'over', side: 'top', height: arc }
    if (forced === 'under') return { route: 'under', side: 'bottom', height: arc }
    if (forced === 'straight') return { route: 'straight', side: 'left', height: 0 }
    if (Math.abs(dy) < 12) {
      if (dx > 0 && dx < w * 1.9) return { route: 'straight', side: 'left', height: 0 }
      return { route: 'over', side: 'top', height: arc }
    }
    if (Math.abs(dx) > w * 0.75 && dx > 0) return { route: 'cross', side: 'left', height: 0 }
    return { route: 'cross', side: dy > 0 ? 'top' : 'bottom', height: 0 }
  }

  private syncTags(views: TagView[], ghosts: TagView[]): void {
    const seen = new Set<string>()
    const all = [...views.map((v) => ({ view: v, ghost: false })), ...ghosts.map((v) => ({ view: v, ghost: true }))]
    // Tags sharing a node (or a parking spot) line up side by side.
    const groups = new Map<string, { view: TagView; ghost: boolean }[]>()
    for (const entry of all) {
      const v = entry.view
      const key = v.target ? `${v.target}:${v.side ?? 'below'}` : `park:${v.park?.x ?? 0}:${v.park?.y ?? 0}`
      groups.set(key, [...(groups.get(key) ?? []), entry])
    }
    for (const group of groups.values()) {
      // Above/below: side by side. Left/right: stacked vertically.
      const side = group[0].view.side
      const vertical = side === 'left' || side === 'right'
      const widths = group.map((g) => (vertical ? 22 : tagWidth(g.view)))
      const total = widths.reduce((a, b) => a + b, 0) + TAG_GAP * (group.length - 1)
      let cursor = -total / 2
      group.forEach(({ view, ghost }, i) => {
        const id = ghost ? `ghost:${view.id}` : view.id
        seen.add(id)
        const offset = cursor + widths[i] / 2
        cursor += widths[i] + TAG_GAP
        const anchor = this.tagAnchor(view)
        let item = this.tags.get(id)
        if (!item) {
          item = new TagItem(view, this.tagLayer, ghost, anchor)
          this.tags.set(id, item)
        }
        item.update(view)
        if (vertical) item.pos.set(anchor.x, anchor.y + offset)
        else item.pos.set(anchor.x + offset, anchor.y)
      })
    }
    for (const [id, item] of this.tags) if (!seen.has(id) && item.alive) item.exit()
  }

  private tagAnchor(view: TagView): Point {
    const node = view.target ? this.nodes.get(view.target)?.view : undefined
    if (!node) return view.park ?? { x: 0, y: 60 }
    const { h, w, ptr } = size(node)
    if (view.side === 'left') return { x: node.x - w / 2 - 6, y: node.y }
    if (view.side === 'right') return { x: node.x + w / 2 + 6, y: node.y }
    // Point at the data field of records so the stem reads as "this node".
    const x = node.kind === 'record' ? node.x - w / 2 + (w - ptr) / 2 : node.x
    return view.side === 'above' ? { x, y: node.y - h / 2 - 8 } : { x, y: node.y + h / 2 + 8 }
  }

  private syncSimple<V extends { id: string }, I extends { alive: boolean; update(v: V): void; exit(): void }>(
    map: Map<string, I>,
    views: V[],
    create: (v: V) => I,
  ): void {
    const seen = new Set<string>()
    for (const view of views) {
      seen.add(view.id)
      const item = map.get(view.id)
      if (item) item.update(view)
      else map.set(view.id, create(view))
    }
    for (const [id, item] of map) if (!seen.has(id) && item.alive) item.exit()
  }

  private measure(frame: Frame, ghost?: GhostLayer | null): Bounds {
    const b: Bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
    const add = (x: number, y: number) => {
      b.minX = Math.min(b.minX, x)
      b.maxX = Math.max(b.maxX, x)
      b.minY = Math.min(b.minY, y)
      b.maxY = Math.max(b.maxY, y)
    }
    for (const n of [...frame.nodes, ...(ghost?.nodes ?? [])]) {
      const { w, h } = size(n)
      add(n.x - w / 2, n.y - h / 2 - (n.sub ? 18 : 0))
      add(n.x + w / 2, n.y + h / 2)
    }
    for (const e of [...frame.edges, ...(ghost?.edges ?? [])]) {
      const r = this.route(e)
      const s = this.nodes.get(e.from)?.view
      if (!s) continue
      if (r.route === 'over') add(s.x, s.y - size(s).h / 2 - r.height * 0.75)
      if (r.route === 'under') add(s.x, s.y + size(s).h / 2 + r.height * 0.75)
      if (r.route === 'self') add(s.x, s.y - 90)
    }
    for (const t of [...frame.tags, ...(ghost?.tags ?? [])]) {
      const a = this.tagAnchor(t)
      const width = tagWidth(t) + 8
      if (t.side === 'left' || t.side === 'right') {
        add(a.x + (t.side === 'left' ? -width : width), a.y - 14)
        add(a.x, a.y + 14)
        continue
      }
      const dir = t.side === 'above' ? -1 : 1
      add(a.x - width / 2, a.y)
      add(a.x + width / 2, a.y + dir * 34)
    }
    for (const l of frame.labels ?? []) {
      const half = l.text.length * 7
      add(l.x - half, l.y - 12)
      add(l.x + half, l.y + 12)
    }
    for (const r of frame.regions ?? []) {
      add(r.x, r.y)
      add(r.x + r.w, r.y + r.h)
    }
    if (!Number.isFinite(b.minX)) return { minX: 0, minY: 0, maxX: 1, maxY: 1 }
    return b
  }

  private frameCamera(instant: boolean): void {
    const width = this.host.clientWidth
    const height = this.host.clientHeight
    if (!width || !height) return
    const pad = 40
    const availW = width - this.insets.left - this.insets.right - pad * 2
    const availH = height - this.insets.top - this.insets.bottom - pad * 2
    const bw = Math.max(this.bounds.maxX - this.bounds.minX, 1)
    const bh = Math.max(this.bounds.maxY - this.bounds.minY, 1)
    const scale = Math.max(0.35, Math.min(availW / bw, availH / bh, 1.2))
    const cx = (this.bounds.minX + this.bounds.maxX) / 2
    const cy = (this.bounds.minY + this.bounds.maxY) / 2
    const screenX = this.insets.left + pad + availW / 2
    const screenY = this.insets.top + pad + availH / 2
    const tx = screenX - cx * scale
    const ty = screenY - cy * scale
    if (instant) {
      this.camera.scale.jump(scale)
      this.camera.x.jump(tx)
      this.camera.y.jump(ty)
    } else {
      this.camera.scale.set(scale)
      this.camera.x.set(tx)
      this.camera.y.set(ty)
    }
    wake(this.tick)
  }

  private advance(dt: number): boolean {
    let busy = false
    for (const s of Object.values(this.camera)) if (s.step(dt)) busy = true
    for (const [id, item] of this.nodes) {
      if (item.step(dt)) busy = true
      item.paint()
      if (!item.alive && item.opacity.settled && item.opacity.value <= 0.01) {
        item.el.remove()
        this.nodes.delete(id)
      }
    }
    for (const [id, item] of this.edges) {
      if (item.step(dt, this.nodes)) busy = true
      item.paint()
      if (!item.alive && item.opacity.settled && item.opacity.value <= 0.01) {
        item.group.remove()
        this.edges.delete(id)
      }
    }
    for (const map of [this.tags, this.labels, this.regions] as Map<string, TagItem | LabelItem | RegionItem>[]) {
      for (const [id, item] of map) {
        if (item.step(dt)) busy = true
        item.paint()
        if (!item.alive && item.opacity.settled && item.opacity.value <= 0.01) {
          item.el.remove()
          map.delete(id)
        }
      }
    }
    const s = this.camera.scale.value
    this.world.style.transform = `translate(${this.camera.x.value}px, ${this.camera.y.value}px) scale(${s})`
    return busy
  }
}
