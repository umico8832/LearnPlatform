import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { AiUsageOverview } from '@/api/aiUsage'

echarts.use([
  BarChart,
  PieChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
])

export function useAiUsageCharts(overview: AiUsageOverview) {
  const trendChartRef = ref<HTMLElement>()
  const functionChartRef = ref<HTMLElement>()
  const modelChartRef = ref<HTMLElement>()

  let trendChart: echarts.ECharts | null = null
  let functionChart: echarts.ECharts | null = null
  let modelChart: echarts.ECharts | null = null

  function renderCharts() {
    trendChart = renderTrendChart(trendChartRef, trendChart)
    functionChart = renderDistributionChart(
      functionChartRef,
      functionChart,
      (overview.functionStats || []).map((item) => ({ name: item.functionType, value: item.count })),
    )
    modelChart = renderDistributionChart(
      modelChartRef,
      modelChart,
      (overview.modelStats || []).map((item) => ({ name: item.model, value: item.count })),
    )
  }

  function renderTrendChart(target: Ref<HTMLElement | undefined>, chart: echarts.ECharts | null) {
    if (!target.value) return chart
    const instance = chart || echarts.init(target.value)
    const trends = overview.dailyTrends || []
    instance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['成功', '失败', 'Tokens'], bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
      xAxis: {
        type: 'category',
        data: trends.map((item) => item.date.slice(5)),
        axisLabel: { rotate: trends.length > 15 ? 45 : 0 },
      },
      yAxis: [
        { type: 'value', name: '调用次数', position: 'left' },
        { type: 'value', name: 'Tokens', position: 'right' },
      ],
      series: [
        {
          name: '成功',
          type: 'bar',
          stack: 'calls',
          data: trends.map((item) => item.successCount),
          itemStyle: { color: '#67C23A' },
        },
        {
          name: '失败',
          type: 'bar',
          stack: 'calls',
          data: trends.map((item) => item.failedCount),
          itemStyle: { color: '#F56C6C' },
        },
        {
          name: 'Tokens',
          type: 'line',
          yAxisIndex: 1,
          data: trends.map((item) => item.totalTokens),
          itemStyle: { color: '#E6A23C' },
          smooth: true,
          lineStyle: { width: 2 },
        },
      ],
    })
    return instance
  }

  function renderDistributionChart(
    target: Ref<HTMLElement | undefined>,
    chart: echarts.ECharts | null,
    data: Array<{ name: string; value: number }>,
  ) {
    if (!target.value) return chart
    const instance = chart || echarts.init(target.value)
    instance.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: '5%', top: 'center', type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['35%', '50%'],
          avoidLabelOverlap: true,
          label: { show: false },
          data,
        },
      ],
    })
    return instance
  }

  function handleResize() {
    trendChart?.resize()
    functionChart?.resize()
    modelChart?.resize()
  }

  onMounted(() => window.addEventListener('resize', handleResize))
  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    trendChart?.dispose()
    functionChart?.dispose()
    modelChart?.dispose()
  })

  return { trendChartRef, functionChartRef, modelChartRef, renderCharts }
}
