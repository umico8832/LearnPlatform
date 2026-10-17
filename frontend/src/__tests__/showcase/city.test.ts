import { describe, expect, it } from 'vitest'
import { buildCity, search, type City } from '@/views/home/showcase/network/city'

// Bellman-Ford relaxes all edges; it shares neither Dijkstra's selection nor adjacency traversal.
function reference(city: City, start: number) {
  const distances = city.junctions.map(() => Infinity)
  distances[start] = 0
  for (let pass = 1; pass < distances.length; pass++) {
    let changed = false
    for (const { a, b, length } of city.streets) {
      if (distances[a] + length < distances[b]) {
        distances[b] = distances[a] + length
        changed = true
      }
      if (distances[b] + length < distances[a]) {
        distances[a] = distances[b] + length
        changed = true
      }
    }
    if (!changed) break
  }
  return distances
}

describe('showcase shortest routes', () => {
  it.each([1, 7, 11, 89])('matches independent shortest distances for city seed %i', (seed) => {
    const city = buildCity(seed)
    for (const start of [0, 71, city.junctions.length - 1]) {
      const expected = reference(city, start)
      const target = city.junctions.length - 1 - start
      const route = search(city, start, target)
      route.distance.forEach((distance, i) => expect(distance).toBeCloseTo(expected[i], 10))
      expect(route.path[0]).toBe(start)
      expect(route.path.at(-1)).toBe(target)
      expect(new Set(route.path).size).toBe(route.path.length)
      let length = 0
      route.pathStreets.forEach((edge, i) => {
        const street = city.streets[edge]
        expect([street.a, street.b].sort()).toEqual([route.path[i], route.path[i + 1]].sort())
        length += street.length
      })
      expect(length).toBeCloseTo(expected[target], 10)
    }
  })

  it('handles start equal to target and alternative routes', () => {
    const city: City = {
      junctions: [
        { x: 0, z: 0 },
        { x: 1, z: 0 },
        { x: 2, z: 0 },
      ],
      streets: [
        { a: 0, b: 2, length: 9 },
        { a: 0, b: 1, length: 1 },
        { a: 1, b: 2, length: 2 },
      ],
      adjacent: [
        [0, 1],
        [1, 2],
        [0, 2],
      ],
    }
    expect(search(city, 0, 2).path).toEqual([0, 1, 2])
    expect(search(city, 0, 0).path).toEqual([0])
    expect(search(city, 0, 0).pathStreets).toEqual([])
  })
})
