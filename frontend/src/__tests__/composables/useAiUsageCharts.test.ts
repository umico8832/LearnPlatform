import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AiUsageOverview } from '@/api/aiUsage'

const chartApi = vi.hoisted(() => ({
  dispose: vi.fn(),
  init: vi.fn(),
  resize: vi.fn(),
  setOption: vi.fn(),
  use: vi.fn(),
}))

vi.mock('echarts/core', () => ({
  init: chartApi.init,
  use: chartApi.use,
}))
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {}, PieChart: {} }))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  LegendComponent: {},
  TitleComponent: {},
  TooltipComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

import { useAiUsageCharts } from '@/composables/useAiUsageCharts'

const populated: AiUsageOverview = {
  totalCalls: 1,
  successCalls: 1,
  failedCalls: 0,
  successRate: 100,
  totalTokens: 20,
  avgDuration: 10,
  todayCalls: 1,
  todayTokens: 20,
  totalCostUsd: null,
  todayCostUsd: null,
  dailyTrends: [
    { date: '2026-10-02', totalCount: 1, successCount: 1, failedCount: 0, totalTokens: 20, totalCostUsd: null },
  ],
  functionStats: [
    {
      functionType: 'tutor_agent',
      count: 1,
      successCount: 1,
      failedCount: 0,
      totalTokens: 20,
      totalCostUsd: null,
      avgDuration: 10,
    },
  ],
  modelStats: [{ model: 'e2e-deterministic', count: 1, totalTokens: 20, totalCostUsd: null, avgDuration: 10 }],
  topUsers: [],
  recentFailures: [],
}
const empty: AiUsageOverview = { ...populated, dailyTrends: [], functionStats: [], modelStats: [] }

const wrappers: ReturnType<typeof mount>[] = []
function render(initial: AiUsageOverview) {
  const overview = ref(initial)
  let charts!: ReturnType<typeof useAiUsageCharts>
  const wrapper = mount(
    defineComponent({
      setup() {
        charts = useAiUsageCharts(overview)
        return { overview, ...charts }
      },
      template: `
        <div>
          <div v-if="overview.dailyTrends.length" ref="trendChartRef"></div>
          <div v-if="overview.functionStats.length" ref="functionChartRef"></div>
          <div v-if="overview.modelStats.length" ref="modelChartRef"></div>
        </div>`,
    }),
  )
  wrappers.push(wrapper)
  return { charts, overview, wrapper }
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.clearAllMocks()
})

describe('useAiUsageCharts', () => {
  it('does not mount empty charts, initializes them when data arrives, and disposes them when it clears', async () => {
    chartApi.init.mockImplementation(() => ({
      dispose: chartApi.dispose,
      resize: chartApi.resize,
      setOption: chartApi.setOption,
    }))
    const { charts, overview } = render(empty)
    await nextTick()

    charts.renderCharts()
    expect(chartApi.init).not.toHaveBeenCalled()

    overview.value = populated
    await nextTick()
    charts.renderCharts()
    expect(chartApi.init).toHaveBeenCalledTimes(3)

    overview.value = empty
    await nextTick()
    charts.renderCharts()

    expect(chartApi.dispose).toHaveBeenCalledTimes(3)
    expect(chartApi.init).toHaveBeenCalledTimes(3)
  })

  it('disposes each mounted chart when its component unmounts', async () => {
    chartApi.init.mockImplementation(() => ({
      dispose: chartApi.dispose,
      resize: chartApi.resize,
      setOption: chartApi.setOption,
    }))
    const { charts, wrapper } = render(populated)
    await nextTick()
    charts.renderCharts()

    wrapper.unmount()
    expect(chartApi.dispose).toHaveBeenCalledTimes(3)
  })
})
