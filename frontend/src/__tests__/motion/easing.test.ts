import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { springEasing, springs, type SpringPreset } from '@/motion'

const livingCss = readFileSync(resolve(process.cwd(), 'src/assets/styles/living.css'), 'utf8')

function points(easing: string): number[] {
  return easing
    .replace(/^linear\(|\)$/g, '')
    .split(',')
    .map((value) => Number(value.trim()))
}

function cssToken(name: string): string | undefined {
  const match = livingCss.match(new RegExp(`--${name}:\\s*([^;]+);`))
  return match?.[1].replace(/\s+/g, ' ').trim()
}

describe('弹簧生成的 CSS 缓动', () => {
  it('从 0 开始、以 1 结束，欠阻尼弹簧保留越过终点的部分', () => {
    const snappy = points(springEasing(springs.snappy).easing)
    expect(snappy[0]).toBe(0)
    expect(snappy.at(-1)).toBe(1)
    expect(Math.max(...snappy)).toBeGreaterThan(1)

    const fade = points(springEasing(springs.fade).easing)
    expect(Math.max(...fade)).toBeLessThanOrEqual(1)
  })

  it('时长覆盖弹簧静止所需时间，并对同一参数复用结果', () => {
    const layout = springEasing(springs.layout)
    expect(layout.duration).toBeGreaterThan(springs.layout.response * 1000)
    expect(springEasing({ ...springs.layout })).toBe(layout)
  })

  it.each<SpringPreset>(['snappy', 'layout', 'fade'])('living.css 中的 %s 缓动与弹簧预设一致', (preset) => {
    const expected = springEasing(springs[preset])
    expect(cssToken(`lt-ease-${preset}`), `重新生成 --lt-ease-${preset}`).toBe(expected.easing)
    expect(cssToken(`lt-dur-${preset}`)).toBe(`${expected.duration}ms`)
  })
})
