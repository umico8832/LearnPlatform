import { CITY_DEPTH, CITY_WIDTH, type City, type Route } from './city'

/**
 * Projection and drawing for the city. Everything here is a pure function of the scene
 * at a moment in time, so the stage can repaint any frame, including after a theme switch.
 */

export interface Point {
  x: number
  y: number
  /** Size relative to the nearest row, for line widths and dot radii. */
  s: number
  /** 1 near the viewer, fading towards the horizon. */
  fog: number
}

export interface Palette {
  base: number[]
  label: number[]
  accent: number[]
  green: number[]
}

export interface Scene {
  city: City
  points: Point[]
  route: Route | null
  preview: Route | null
  hovered: number
  t: number
  appearFrom: number
  waveFrom: number
  palette: Palette
  /** The fixed starting junction, marked even before any search. */
  start: number
}

/** Seconds the light takes to cover the whole city, and to trace the route back. */
export const SPREAD = 2.8
export const TRACE = 1
const PULSE_RIDE = 1.6
/** Seconds after the target is found when the route has been traced and ridden, and nothing moves. */
export const STILL_AFTER = TRACE + 0.3 + PULSE_RIDE
const CAMERA_HEIGHT = 1.4

/**
 * A camera at a fixed height looks down at `pitch` radians below the horizon. The nearest
 * row is pinned just above the bottom edge and spans a little more than the width, so the
 * city always runs off both sides; the canvas mask fades it out at the edges.
 */
export function project(city: City, width: number, height: number, pitch: number): Point[] {
  const sin = Math.sin(pitch)
  const cos = Math.cos(pitch)
  const back = CAMERA_HEIGHT / Math.tan(pitch) - CITY_DEPTH * 0.35
  const depthOf = (z: number) => CAMERA_HEIGHT * sin + (z + back) * cos
  const nearDepth = depthOf(0)
  const farDepth = depthOf(CITY_DEPTH)
  const scale = (width * 0.78) / (CITY_WIDTH / 2 / nearDepth)
  const rise = (z: number) => (-CAMERA_HEIGHT * cos + (z + back) * sin) / depthOf(z)
  const base = height * 0.97
  const nearRise = rise(0)
  return city.junctions.map(({ x, z }) => {
    const depth = depthOf(z)
    const far = (depth - nearDepth) / (farDepth - nearDepth)
    return {
      x: width / 2 + (x / depth) * scale,
      y: base - (rise(z) - nearRise) * scale,
      s: nearDepth / depth,
      fog: 0.12 + 0.88 * (1 - far) ** 1.2,
    }
  })
}

const clamp01 = (v: number) => Math.max(0, Math.min(1, v))
const rgba = (c: number[], a: number) => `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${clamp01(a)})`

function line(ctx: CanvasRenderingContext2D, a: Point, b: Point, part = 1) {
  ctx.beginPath()
  ctx.moveTo(a.x, a.y)
  ctx.lineTo(a.x + (b.x - a.x) * part, a.y + (b.y - a.y) * part)
  ctx.stroke()
}

function dot(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.beginPath()
  ctx.arc(x, y, Math.max(r, 0), 0, Math.PI * 2)
  ctx.fill()
}

function ring(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.beginPath()
  ctx.arc(x, y, Math.max(r, 0), 0, Math.PI * 2)
  ctx.stroke()
}

/** Distances are shown as whole numbers: ten to a unit of the plane. */
export const units = (distance: number) => Math.round(distance * 10)

function label(ctx: CanvasRenderingContext2D, p: Point, text: string, size: number, color: string) {
  ctx.font = `600 ${size}px ui-rounded, 'SF Pro Rounded', system-ui, sans-serif`
  ctx.fillStyle = color
  ctx.fillText(text, p.x, p.y - 7 * p.s - 3)
}

/** Distance the search front has travelled at time t. Like Dijkstra, it stops once the destination is settled. */
export function frontAt(scene: Scene): number {
  const { route, t, waveFrom } = scene
  if (!route) return 0
  return Math.min(Math.max(t - waveFrom, 0) * (route.reach / SPREAD), route.distance[route.target])
}

/** Seconds since the front reached the target; negative before. */
export function foundFor(scene: Scene): number {
  const { route, t, waveFrom } = scene
  if (!route) return -1
  return t - waveFrom - route.distance[route.target] / (route.reach / SPREAD)
}

function routeLengths(city: City, route: Route) {
  return route.pathStreets.map((s) => city.streets[s].length)
}

export function paint(ctx: CanvasRenderingContext2D, scene: Scene) {
  const { city, points, route, preview, hovered, t, appearFrom, palette } = scene
  const { base, label: ink, accent, green } = palette
  const speed = route ? route.reach / SPREAD : 1
  const front = frontAt(scene)
  const found = foundFor(scene)
  const settle = 1 - 0.7 * clamp01(found / 1.4)
  const shown = (node: number) => clamp01((t - appearFrom - 1.1 * (city.junctions[node].z / CITY_DEPTH)) / 0.5)
  const d = route?.distance
  ctx.lineCap = 'round'
  ctx.textAlign = 'center'

  // Streets, then the light running along them.
  city.streets.forEach((street) => {
    const p = points[street.a]
    const q = points[street.b]
    const fade = Math.min(p.fog * shown(street.a), q.fog * shown(street.b))
    if (fade <= 0.01) return
    const s = (p.s + q.s) / 2
    ctx.globalCompositeOperation = 'source-over'
    ctx.strokeStyle = rgba(base, 0.2 * fade)
    ctx.lineWidth = 1.1 * s
    line(ctx, p, q)
    if (!d) return
    const fromA = clamp01((front - d[street.a]) / street.length)
    const fromB = clamp01((front - d[street.b]) / street.length)
    if (fromA + fromB <= 0) return
    const age = front - (Math.min(d[street.a], d[street.b]) + street.length)
    const bright = (0.3 + 0.7 * Math.exp(-Math.max(age, 0) / (route!.reach * 0.1))) * settle * fade
    ctx.globalCompositeOperation = 'lighter'
    const parts: [Point, Point, number][] =
      fromA + fromB >= 1
        ? [[p, q, 1]]
        : [
            [p, q, fromA],
            [q, p, fromB],
          ]
    for (const [from, to, part] of parts) {
      if (part <= 0) continue
      ctx.strokeStyle = rgba(accent, 0.15 * bright)
      ctx.lineWidth = 7 * s
      line(ctx, from, to, part)
      ctx.strokeStyle = rgba(accent, 0.9 * bright)
      ctx.lineWidth = 1.6 * s
      line(ctx, from, to, part)
      if (part < 1) {
        const x = from.x + (to.x - from.x) * part
        const y = from.y + (to.y - from.y) * part
        ctx.fillStyle = rgba(accent, 0.3 * settle * fade)
        dot(ctx, x, y, 7 * s)
        ctx.fillStyle = rgba(ink, 0.95 * settle * fade)
        dot(ctx, x, y, 1.8 * s)
      }
    }
  })

  // Junctions: waiting ones breathe while the search runs, settled ones flash and show their distance once.
  city.junctions.forEach((_, node) => {
    const p = points[node]
    const fade = p.fog * shown(node)
    if (fade <= 0.01) return
    ctx.globalCompositeOperation = 'source-over'
    // Seconds since this junction was settled, on the clock, so a stopped search does not freeze its flashes.
    const since = d && d[node] <= front ? t - scene.waveFrom - d[node] / speed : -1
    ctx.fillStyle = since >= 0 ? rgba(accent, (0.5 + 0.5 * settle) * fade) : rgba(base, 0.4 * fade)
    dot(ctx, p.x, p.y, (since >= 0 ? 2.4 : 1.7) * p.s)
    if (!route || !d) return
    const waiting = front >= route.seen[node] && since < 0
    ctx.globalCompositeOperation = 'lighter'
    // Once the target is found the search is over, so the junctions still waiting fade away.
    const searching = 1 - clamp01(found / 0.6)
    if (waiting && searching > 0) {
      ctx.strokeStyle = rgba(accent, (0.35 + 0.25 * Math.sin(t * 7 + node)) * settle * searching * fade)
      ctx.lineWidth = 1.2 * p.s
      ring(ctx, p.x, p.y, (5.5 + 1.2 * Math.sin(t * 7 + node)) * p.s)
    } else if (since >= 0 && since < 0.7) {
      ctx.strokeStyle = rgba(accent, (1 - since / 0.7) * 0.55 * fade)
      ctx.lineWidth = 1.3 * p.s
      ring(ctx, p.x, p.y, (3 + since * 24) * p.s)
    }
    if (since >= 0 && since < 0.9 && p.fog > 0.6 && node !== route.start) {
      const alpha = since < 0.12 ? since / 0.12 : 1 - (since - 0.12) / 0.78
      ctx.globalCompositeOperation = 'source-over'
      label(ctx, p, String(units(d[node])), Math.round(9 + 2 * p.s), rgba(ink, 0.75 * alpha * fade))
    }
  })

  // A hovered junction previews its route as a dashed line before anyone commits to it.
  if (preview && hovered >= 0) {
    ctx.globalCompositeOperation = 'source-over'
    ctx.setLineDash([4, 6])
    ctx.strokeStyle = rgba(green, 0.6)
    preview.pathStreets.forEach((_, i) => {
      const p = points[preview.path[i]]
      const q = points[preview.path[i + 1]]
      ctx.lineWidth = 2 * ((p.s + q.s) / 2)
      line(ctx, p, q)
    })
    ctx.setLineDash([])
    const h = points[hovered]
    ctx.lineWidth = 1.5
    ring(ctx, h.x, h.y, 9 * h.s)
    label(ctx, h, String(units(preview.distance[hovered])), Math.round(11 + 2 * h.s), rgba(green, 0.9))
  }

  if (route) paintRoute(ctx, scene, found)

  // Start and destination.
  const markers: [number, number[], boolean][] = [[scene.start, accent, true]]
  if (route) markers.push([route.target, green, found > 0])
  for (const [node, color, reached] of markers) {
    if (node < 0) continue
    const p = points[node]
    const fade = shown(node)
    ctx.globalCompositeOperation = 'source-over'
    ctx.fillStyle = rgba(color, (reached ? 1 : 0.35) * fade)
    dot(ctx, p.x, p.y, 4.6 * p.s)
    ctx.strokeStyle = rgba(color, 0.9 * fade)
    ctx.lineWidth = 2 * p.s
    ring(ctx, p.x, p.y, 10 * p.s)
  }
  ctx.globalCompositeOperation = 'source-over'
}

/** The route is traced back from the destination along the recorded predecessors; then a pulse rides it once. */
function paintRoute(ctx: CanvasRenderingContext2D, scene: Scene, found: number) {
  const { city, points, route, palette } = scene
  if (!route || found <= 0) return
  const { green, label: ink } = palette
  const lengths = routeLengths(city, route)
  const total = lengths.reduce((sum, n) => sum + n, 0)
  if (total <= 0) return
  let budget = clamp01(found / TRACE) * total
  for (let i = route.pathStreets.length - 1; i >= 0 && budget > 0; i -= 1) {
    const p = points[route.path[i + 1]]
    const q = points[route.path[i]]
    const part = Math.min(budget / lengths[i], 1)
    budget -= lengths[i]
    const s = (p.s + q.s) / 2
    ctx.globalCompositeOperation = 'lighter'
    ctx.strokeStyle = rgba(green, 0.2)
    ctx.lineWidth = 12 * s
    line(ctx, p, q, part)
    ctx.globalCompositeOperation = 'source-over'
    ctx.strokeStyle = rgba(green, 1)
    ctx.lineWidth = 3 * s
    line(ctx, p, q, part)
    ctx.fillStyle = rgba(green, 1)
    dot(ctx, p.x, p.y, 3.6 * p.s)
  }
  if (found > TRACE) {
    const target = points[route.target]
    const alpha = clamp01((found - TRACE) / 0.3)
    label(ctx, target, String(units(route.distance[route.target])), Math.round(13 + 3 * target.s), rgba(ink, alpha))
  }
  const after = found - TRACE - 0.3
  const phase = after > 0 ? after / PULSE_RIDE : 2
  if (phase > 1) return
  let at = phase * total
  for (let i = 0; i < route.pathStreets.length; i += 1) {
    if (at > lengths[i]) {
      at -= lengths[i]
      continue
    }
    const p = points[route.path[i]]
    const q = points[route.path[i + 1]]
    const x = p.x + ((q.x - p.x) * at) / lengths[i]
    const y = p.y + ((q.y - p.y) * at) / lengths[i]
    const ends = Math.sin(Math.PI * phase)
    ctx.globalCompositeOperation = 'lighter'
    ctx.fillStyle = rgba(green, 0.35 * ends)
    dot(ctx, x, y, 13 * p.s)
    ctx.fillStyle = rgba(ink, ends)
    dot(ctx, x, y, 3.2 * p.s)
    break
  }
}
