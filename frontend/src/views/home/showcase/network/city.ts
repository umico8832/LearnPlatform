/**
 * A city street grid on a ground plane, and the shortest-path search over it.
 * The search is real Dijkstra: the light the stage draws reaches each junction exactly at
 * that junction's shortest distance from the start, so the picture is the algorithm.
 */

export interface Junction {
  /** Across the plane, centred on 0. */
  x: number
  /** Into the distance: 0 nearest the viewer. */
  z: number
}

export interface Street {
  a: number
  b: number
  length: number
}

export interface City {
  junctions: Junction[]
  streets: Street[]
  /** Street indices touching each junction. */
  adjacent: number[][]
}

export interface Route {
  start: number
  target: number
  /** Shortest distance from the start to every junction. */
  distance: number[]
  /** When the search first sees each junction: the distance of the neighbour that reaches it first. */
  seen: number[]
  /** Junctions from start to target. */
  path: number[]
  /** Streets along the path, in order. */
  pathStreets: number[]
  /** Distance at which the whole city has been reached. */
  reach: number
}

const COLS = 26
const ROWS = 14
export const CITY_WIDTH = 5.2
export const CITY_DEPTH = 3.4

function mulberry32(seed: number) {
  let a = seed
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function connected(count: number, streets: Street[], skip: number): boolean {
  const links: number[][] = Array.from({ length: count }, () => [])
  streets.forEach((street, i) => {
    if (i === skip) return
    links[street.a].push(street.b)
    links[street.b].push(street.a)
  })
  const seen = new Set([0])
  const stack = [0]
  while (stack.length) {
    for (const next of links[stack.pop()!]) {
      if (!seen.has(next)) {
        seen.add(next)
        stack.push(next)
      }
    }
  }
  return seen.size === count
}

export function buildCity(seed = 11): City {
  const random = mulberry32(seed)
  const cellX = CITY_WIDTH / (COLS - 1)
  const cellZ = CITY_DEPTH / (ROWS - 1)
  const junctions: Junction[] = []
  for (let row = 0; row < ROWS; row += 1) {
    for (let col = 0; col < COLS; col += 1) {
      junctions.push({
        x: -CITY_WIDTH / 2 + col * cellX + (random() - 0.5) * cellX * 0.36,
        z: row * cellZ + (random() - 0.5) * cellZ * 0.3,
      })
    }
  }
  const id = (row: number, col: number) => row * COLS + col
  let streets: Street[] = []
  const link = (a: number, b: number) =>
    streets.push({ a, b, length: Math.hypot(junctions[a].x - junctions[b].x, junctions[a].z - junctions[b].z) })
  for (let row = 0; row < ROWS; row += 1) {
    for (let col = 0; col < COLS; col += 1) {
      if (col + 1 < COLS) link(id(row, col), id(row, col + 1))
      if (row + 1 < ROWS) link(id(row, col), id(row + 1, col))
      // A few diagonal boulevards cut across the blocks.
      if (row + 1 < ROWS && col + 1 < COLS && random() < 0.08) {
        if (random() < 0.5) link(id(row, col), id(row + 1, col + 1))
        else link(id(row, col + 1), id(row + 1, col))
      }
    }
  }
  // Every fourth street is an avenue that runs unbroken; side streets go missing so
  // routes have to go around blocks. Every junction stays reachable.
  const isAvenue = (street: Street) => {
    const ra = Math.floor(street.a / COLS)
    const ca = street.a % COLS
    const rb = Math.floor(street.b / COLS)
    const cb = street.b % COLS
    return (ra === rb && ra % 4 === 1) || (ca === cb && ca % 4 === 2)
  }
  for (let i = streets.length - 1; i >= 0; i -= 1) {
    if (!isAvenue(streets[i]) && random() < 0.3 && connected(junctions.length, streets, i)) {
      streets = streets.filter((_, j) => j !== i)
    }
  }
  const adjacent: number[][] = junctions.map(() => [])
  streets.forEach((street, i) => {
    adjacent[street.a].push(i)
    adjacent[street.b].push(i)
  })
  return { junctions, streets, adjacent }
}

export function search(city: City, start: number, target: number): Route {
  const { junctions, streets, adjacent } = city
  const distance = junctions.map(() => Infinity)
  const seen = junctions.map(() => Infinity)
  const via = junctions.map(() => -1)
  const done = junctions.map(() => false)
  distance[start] = 0
  seen[start] = 0
  for (let round = 0; round < junctions.length; round += 1) {
    let node = -1
    for (let i = 0; i < junctions.length; i += 1) {
      if (!done[i] && (node < 0 || distance[i] < distance[node])) node = i
    }
    if (node < 0 || distance[node] === Infinity) break
    done[node] = true
    for (const s of adjacent[node]) {
      const street = streets[s]
      const other = street.a === node ? street.b : street.a
      if (seen[other] === Infinity) seen[other] = distance[node]
      const next = distance[node] + street.length
      if (next < distance[other]) {
        distance[other] = next
        via[other] = s
      }
    }
  }
  const pathStreets: number[] = []
  const path = [target]
  for (let node = target; node !== start && via[node] >= 0;) {
    const street = streets[via[node]]
    pathStreets.unshift(via[node])
    node = street.a === node ? street.b : street.a
    path.unshift(node)
  }
  return { start, target, distance, seen, path, pathStreets, reach: Math.max(...distance) }
}

/** Junction nearest to a point on the plane. */
export function nearestJunction(city: City, x: number, z: number): number {
  let best = 0
  let bestDistance = Infinity
  city.junctions.forEach((junction, i) => {
    const d = Math.hypot(junction.x - x, junction.z - z)
    if (d < bestDistance) {
      best = i
      bestDistance = d
    }
  })
  return best
}
