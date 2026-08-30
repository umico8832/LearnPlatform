import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { acknowledge, getEffect, getOverview, getReport, renderCharts, success } = vi.hoisted(() => ({
  acknowledge: vi.fn(),
  getEffect: vi.fn(),
  getOverview: vi.fn(),
  getReport: vi.fn(),
  renderCharts: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/api/aiUsage', () => ({
  acknowledgeAiUsageAlert: (...args: unknown[]) => acknowledge(...args),
  getAiLearningEffect: (...args: unknown[]) => getEffect(...args),
  getAiUsageOverview: (...args: unknown[]) => getOverview(...args),
  getAiUsageReport: (...args: unknown[]) => getReport(...args),
}))

vi.mock('@/composables/useAiUsageCharts', () => ({
  useAiUsageCharts: () => ({
    trendChartRef: { value: undefined },
    functionChartRef: { value: undefined },
    modelChartRef: { value: undefined },
    renderCharts,
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success, error: vi.fn() } }
})

import AiUsageView from '@/admin/views/AiUsageView.vue'

describe('AiUsageView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    acknowledge.mockResolvedValue({ data: { id: 7 } })
    getOverview.mockResolvedValue({
      data: {
        totalCalls: 12,
        successCalls: 10,
        failedCalls: 2,
        successRate: 83.3,
        totalTokens: 2500,
        avgDuration: 300,
        todayCalls: 3,
        todayTokens: 600,
        totalCostUsd: 0.04,
        todayCostUsd: 0.01,
        functionStats: [],
        modelStats: [],
        dailyTrends: [],
        topUsers: [],
        recentFailures: [],
      },
    })
    getReport.mockResolvedValue({
      data: {
        days: 30,
        changes: {
          callsPercent: 5,
          tokensPercent: 3,
          costPercent: 2,
          failureRatePointChange: 0,
          avgDurationPercent: -1,
        },
        alerts: [{ id: 7, type: 'FAILURE_RATE', level: 'WARNING', message: '失败率上升', status: 'OPEN' }],
      },
    })
    getEffect.mockResolvedValue({
      data: {
        days: 30,
        periodStart: '2026-08-01',
        periodEnd: '2026-08-30',
        conclusionLevel: 'INSUFFICIENT_DATA',
        crossQuestionConclusionLevel: 'INSUFFICIENT_DATA',
      },
    })
  })

  it('并发加载三类数据并传给领域组件', async () => {
    const wrapper = shallowMount(AiUsageView)
    await flushPromises()

    expect(getOverview).toHaveBeenCalledWith(30)
    expect(getReport).toHaveBeenCalledWith(30)
    expect(getEffect).toHaveBeenCalledWith(30)
    expect(renderCharts).toHaveBeenCalledOnce()
    expect(wrapper.findComponent({ name: 'AiUsageReportPanel' }).props('report')).toMatchObject({ days: 30 })
    expect(wrapper.findComponent({ name: 'AiLearningEffectPanel' }).props('effect')).toMatchObject({
      periodStart: '2026-08-01',
    })
    expect(wrapper.findComponent({ name: 'AiUsageDetails' }).props('overview')).toMatchObject({ totalCalls: 12 })
  })

  it('领域组件确认提醒后重新加载数据', async () => {
    const wrapper = shallowMount(AiUsageView)
    await flushPromises()

    wrapper.findComponent({ name: 'AiUsageReportPanel' }).vm.$emit('acknowledge', 7)
    await flushPromises()

    expect(acknowledge).toHaveBeenCalledWith(7)
    expect(success).toHaveBeenCalledWith('已确认该提醒')
    expect(getOverview).toHaveBeenCalledTimes(2)
    expect(getReport).toHaveBeenCalledTimes(2)
    expect(getEffect).toHaveBeenCalledTimes(2)
  })
})
