<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElEmpty, ElSkeleton } from 'element-plus';
import mermaid from 'mermaid';

interface Props {
  /** Mermaid syntax string, e.g. "graph LR\n A --> B" */
  mermaidSyntax: string;
  /** Chart title for accessibility */
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: undefined,
});

const emit = defineEmits<{
  nodeClick: [nodeId: string];
}>();

const { t } = useI18n();

const containerRef = ref<HTMLElement | null>(null);
const renderedSvg = ref('');
const loading = ref(false);
const error = ref('');

let initialized = false;

async function initMermaid() {
  if (initialized) return;
  initialized = true;
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'loose', // Allow click events
    flowchart: {
      useMaxWidth: true,
      htmlLabels: true,
    },
  });
}

async function renderChart() {
  if (!props.mermaidSyntax?.trim()) {
    renderedSvg.value = '';
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    await initMermaid();
    const uniqueId = `mermaid-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const { svg } = await mermaid.render(uniqueId, props.mermaidSyntax);
    renderedSvg.value = svg;

    // Attach click listeners after DOM updates
    await nextTick();
    attachNodeClickListeners();
  } catch (err: any) {
    error.value = err?.message ?? t('review.callGraph.renderError');
    renderedSvg.value = '';
  } finally {
    loading.value = false;
  }
}

function attachNodeClickListeners() {
  if (!containerRef.value) return;
  const nodes = containerRef.value.querySelectorAll('.node, .nodeLabel');
  nodes.forEach((node) => {
    node.addEventListener('click', (e) => {
      const target = e.currentTarget as HTMLElement;
      const nodeId = target.getAttribute('id') ?? target.textContent?.trim() ?? '';
      emit('nodeClick', nodeId);
    });
  });
}

onMounted(async () => {
  await renderChart();
});

watch(() => props.mermaidSyntax, async () => {
  await renderChart();
});

onUnmounted(() => {
  // Cleanup references
  renderedSvg.value = '';
});
</script>

<template>
  <div class="call-graph-chart">
    <!-- Loading -->
    <ElSkeleton v-if="loading" :rows="6" animated />

    <!-- Error -->
    <div v-else-if="error" class="call-graph-chart__error">
      <ElEmpty :description="error" />
    </div>

    <!-- Empty -->
    <ElEmpty
      v-else-if="!mermaidSyntax?.trim()"
      :description="t('review.callGraph.noData')"
    />

    <!-- Rendered SVG -->
    <div
      v-else
      ref="containerRef"
      class="call-graph-chart__container"
      role="img"
      :aria-label="title"
      v-html="renderedSvg"
    />
  </div>
</template>

<style scoped>
.call-graph-chart {
  width: 100%;
  min-height: 200px;
}

.call-graph-chart__container {
  width: 100%;
  overflow: auto;
}

.call-graph-chart__container :deep(svg) {
  max-width: 100%;
  height: auto;
}

/* Highlight changed nodes */
.call-graph-chart__container :deep(.node.changed rect),
.call-graph-chart__container :deep(.node.changed circle) {
  stroke: #f56c6c;
  stroke-width: 3px;
}

/* Node hover cursor */
.call-graph-chart__container :deep(.node) {
  cursor: pointer;
}

.call-graph-chart__container :deep(.node:hover rect),
.call-graph-chart__container :deep(.node:hover circle) {
  filter: brightness(0.9);
}

.call-graph-chart__error {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
</style>
