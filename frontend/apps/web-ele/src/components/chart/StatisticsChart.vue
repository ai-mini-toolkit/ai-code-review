<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { $t } from '@vben/locales';
import { ElEmpty, ElSkeleton } from 'element-plus';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

import type { ComponentCategory, ComponentSeverity, StatisticsData } from '#/types/review';
import { CATEGORY_COLORS, SEVERITY_COLORS } from '#/constants/review';

// Register only needed ECharts components (tree-shaking)
echarts.use([
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent,
  CanvasRenderer,
]);

type ChartType = 'severity-pie' | 'category-bar' | 'trend-line';

interface Props {
  chartType: ChartType;
  data: StatisticsData;
  height?: string;
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  height: '320px',
  title: undefined,
});


const chartRef = ref<HTMLElement | null>(null);
const chartInstance = shallowRef<echarts.ECharts | null>(null);
const loading = ref(false);

function buildSeverityPieOption(): echarts.EChartsOption {
  const dist = props.data.severityDistribution;
  const data = (Object.keys(dist) as ComponentSeverity[])
    .filter((k) => dist[k] > 0)
    .map((k) => ({
      name: $t(`review.severity.${k.toLowerCase()}`),
      value: dist[k],
      itemStyle: { color: SEVERITY_COLORS[k] },
    }));

  return {
    title: props.title
      ? { text: props.title, left: 'center', textStyle: { fontSize: 14 } }
      : undefined,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'horizontal',
      bottom: 10,
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderWidth: 2, borderColor: '#fff' },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        data,
      },
    ],
  };
}

function buildCategoryBarOption(): echarts.EChartsOption {
  const dist = props.data.categoryDistribution;
  const categories = Object.keys(dist) as ComponentCategory[];
  const values = categories.map((k) => dist[k]);
  const colors = categories.map((k) => CATEGORY_COLORS[k]);

  return {
    title: props.title
      ? { text: props.title, left: 'center', textStyle: { fontSize: 14 } }
      : undefined,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'category',
      data: categories.map((k) => k.replace('_', ' ')),
      axisLabel: { fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      name: $t('review.chart.issueCount'),
    },
    series: [
      {
        type: 'bar',
        data: values.map((v, i) => ({
          value: v,
          itemStyle: { color: colors[i] },
        })),
        barMaxWidth: 48,
        label: { show: true, position: 'top', fontSize: 11 },
      },
    ],
  };
}

function buildTrendLineOption(): echarts.EChartsOption {
  const trend = props.data.trendData ?? [];
  const severities: ComponentSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  const dates = trend.map((d) => d.date);

  return {
    title: props.title
      ? { text: props.title, left: 'center', textStyle: { fontSize: 14 } }
      : undefined,
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    dataZoom: [{ type: 'inside' }],
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { rotate: 30, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      name: $t('review.chart.issueCount'),
    },
    series: severities.map((sev) => ({
      name: $t(`review.severity.${sev.toLowerCase()}`),
      type: 'line',
      smooth: true,
      data: trend.map((d) => d.counts[sev] ?? 0),
      lineStyle: { color: SEVERITY_COLORS[sev] },
      itemStyle: { color: SEVERITY_COLORS[sev] },
    })),
  };
}

function getOption(): echarts.EChartsOption {
  switch (props.chartType) {
    case 'severity-pie':
      return buildSeverityPieOption();
    case 'category-bar':
      return buildCategoryBarOption();
    case 'trend-line':
      return buildTrendLineOption();
  }
}

function renderChart() {
  if (!chartRef.value) return;

  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value);
  }

  chartInstance.value.setOption(getOption(), { notMerge: true });
}

// Resize observer
let resizeObserver: ResizeObserver | null = null;

onMounted(async () => {
  loading.value = true;
  await nextTick();
  renderChart();
  loading.value = false;

  resizeObserver = new ResizeObserver(() => {
    chartInstance.value?.resize();
  });
  if (chartRef.value) {
    resizeObserver.observe(chartRef.value);
  }
});

watch(
  () => [props.data, props.chartType],
  () => {
    renderChart();
  },
  { deep: true },
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chartInstance.value?.dispose();
  chartInstance.value = null;
});

const hasData = computed(() => {
  if (props.chartType === 'severity-pie') {
    return Object.values(props.data.severityDistribution).some((v) => v > 0);
  }
  if (props.chartType === 'category-bar') {
    return Object.values(props.data.categoryDistribution).some((v) => v > 0);
  }
  if (props.chartType === 'trend-line') {
    return (props.data.trendData?.length ?? 0) > 0;
  }
  return false;
});
</script>

<template>
  <div class="statistics-chart" :style="{ height }">
    <ElSkeleton v-if="loading" :rows="4" animated style="padding: 16px" />
    <ElEmpty
      v-else-if="!hasData"
      :description="$t('review.chart.noData')"
      style="height: 100%; display: flex; flex-direction: column; justify-content: center"
    />
    <div
      v-else
      ref="chartRef"
      class="statistics-chart__canvas"
      :style="{ width: '100%', height: '100%' }"
    />
  </div>
</template>

<style scoped>
.statistics-chart {
  width: 100%;
  position: relative;
}

.statistics-chart__canvas {
  width: 100%;
  height: 100%;
}
</style>
