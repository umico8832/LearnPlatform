import { onBeforeUnmount, onMounted, ref } from 'vue'
import { init, use, type ECharts } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { AdminStatisticsOverview } from '@/api/statistics'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export function useDashboardCharts() {
  const activityChartRef = ref<HTMLElement | null>(null)
  const questionChartRef = ref<HTMLElement | null>(null)
  let activityChart: ECharts | null = null
  let questionChart: ECharts | null = null
  function disposeCharts() {
    activityChart?.dispose()
    questionChart?.dispose()
    activityChart = questionChart = null
  }
  function renderCharts(stats: AdminStatisticsOverview, reducedMotion: boolean) {
    disposeCharts()
    const styles = getComputedStyle(document.documentElement)
    const token = (name: string) => styles.getPropertyValue(`--lp-${name}`).trim()
    const primary = token('primary'),
      secondary = token('text-secondary'),
      border = token('border')
    const base = {
      animation: !reducedMotion,
      animationDuration: 180,
      animationDurationUpdate: 180,
      textStyle: { fontFamily: token('font-sans'), color: secondary },
    }
    if (activityChartRef.value) {
      activityChart = init(activityChartRef.value)
      activityChart.setOption({
        ...base,
        tooltip: { trigger: 'axis' },
        legend: { data: ['刷题次数', '活跃用户'], right: 0, top: 0, textStyle: { color: secondary } },
        grid: { left: 36, right: 16, top: 45, bottom: 28 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: stats.dailyActivity.map((item) => item.date.substring(5)),
          axisLine: { lineStyle: { color: border } },
          axisLabel: { color: secondary },
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          splitLine: { lineStyle: { color: border, type: 'dashed' } },
          axisLabel: { color: secondary },
        },
        series: [
          {
            name: '刷题次数',
            type: 'line',
            symbol: 'circle',
            symbolSize: 6,
            data: stats.dailyActivity.map((item) => item.practiceCount),
            lineStyle: { width: 2, color: primary },
            itemStyle: { color: primary },
          },
          {
            name: '活跃用户',
            type: 'line',
            symbol: 'rect',
            symbolSize: 6,
            data: stats.dailyActivity.map((item) => item.activeUsers),
            lineStyle: { width: 2, type: 'dashed', color: token('success') },
            itemStyle: { color: token('success') },
          },
        ],
      })
    }
    if (questionChartRef.value) {
      questionChart = init(questionChartRef.value)
      questionChart.setOption({
        ...base,
        tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 道（{d}%）' },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: secondary } },
        color: [primary, token('success'), token('warning'), token('text-muted'), token('danger')],
        series: [
          {
            type: 'pie',
            radius: ['48%', '72%'],
            center: ['50%', '42%'],
            label: { show: false },
            data: Object.entries(stats.questionTypeDistribution).map(([name, value]) => ({ name, value })),
          },
        ],
      })
    }
  }
  const resize = () => {
    activityChart?.resize()
    questionChart?.resize()
  }
  onMounted(() => window.addEventListener('resize', resize))
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    disposeCharts()
  })
  return { activityChartRef, questionChartRef, renderCharts, disposeCharts }
}
