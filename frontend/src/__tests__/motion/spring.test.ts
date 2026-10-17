import { describe, expect, it } from 'vitest'
import { Spring, springs, stepSpring } from '@/motion'

function run(spring: Spring, seconds: number, dt: number) {
  for (let t = 0; t < seconds; t += dt) spring.step(dt)
}

describe('弹簧核心', () => {
  it('解析解与帧率无关：60 帧与 240 帧走到同一时刻的位置一致', () => {
    const a = new Spring(0, springs.pointer)
    const b = new Spring(0, springs.pointer)
    a.set(100)
    b.set(100)
    for (let i = 0; i < 12; i++) a.step(1 / 60)
    for (let i = 0; i < 48; i++) b.step(1 / 240)
    expect(a.value).toBeCloseTo(b.value, 6)
    expect(a.velocity).toBeCloseTo(b.velocity, 4)
  })

  it('欠阻尼预设会越过目标再回弹，临界阻尼不会越过', () => {
    let x = -1
    let v = 0
    let peak = -Infinity
    for (let i = 0; i < 120; i++) {
      ;[x, v] = stepSpring(x, v, springs.pointer, 1 / 120)
      peak = Math.max(peak, x)
    }
    expect(peak).toBeGreaterThan(0)

    x = -1
    v = 0
    peak = -Infinity
    for (let i = 0; i < 240; i++) {
      ;[x, v] = stepSpring(x, v, springs.fade, 1 / 120)
      peak = Math.max(peak, x)
    }
    expect(peak).toBeLessThanOrEqual(1e-9)
  })

  it('中途改目标时保留速度，从当前位置继续而不是从头开始', () => {
    const spring = new Spring(0, springs.layout)
    spring.set(100)
    run(spring, 0.15, 1 / 60)
    const position = spring.value
    const velocity = spring.velocity
    expect(velocity).toBeGreaterThan(0)

    spring.set(-50)
    expect(spring.value).toBe(position)
    expect(spring.velocity).toBe(velocity)
    spring.step(1 / 60)
    expect(spring.value).toBeGreaterThan(position)
  })

  it('静止后吸附到目标并报告停止', () => {
    const spring = new Spring(0, springs.snappy)
    spring.set(1)
    run(spring, 3, 1 / 60)
    expect(spring.settled).toBe(true)
    expect(spring.step(1 / 60)).toBe(false)
    expect(spring.value).toBe(1)
    expect(spring.velocity).toBe(0)
  })

  it('jump 立即到达且清零速度，kick 叠加冲量', () => {
    const spring = new Spring(0)
    spring.set(10)
    spring.step(1 / 60)
    spring.jump(4)
    expect(spring.value).toBe(4)
    expect(spring.target).toBe(4)
    expect(spring.velocity).toBe(0)
    spring.kick(-300)
    expect(spring.velocity).toBe(-300)
    expect(spring.step(1 / 60)).toBe(true)
  })
})
