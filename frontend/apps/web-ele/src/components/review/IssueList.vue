<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconifyIcon } from '@vben/icons';
import {
  ElButton,
  ElCard,
  ElCol,
  ElEmpty,
  ElOption,
  ElRow,
  ElSelect,
  ElTag,
  ElText,
} from 'element-plus';

import type { ComponentCategory, ComponentIssue, ComponentSeverity } from '#/types/review';
import {
  CATEGORY_COLORS,
  SEVERITY_ORDER,
} from '#/constants/review';
import SeverityBadge from './SeverityBadge.vue';

interface Props {
  issues: ComponentIssue[];
  showFilters?: boolean;
  maxHeight?: string;
}

const props = withDefaults(defineProps<Props>(), {
  showFilters: true,
  maxHeight: undefined,
});

const emit = defineEmits<{
  lineClick: [file: string, line: number];
}>();

const { t } = useI18n();

// Filter state
const filterSeverity = ref<ComponentSeverity | 'all'>('all');
const filterCategory = ref<ComponentCategory | 'all'>('all');

// Expand state per issue (by index)
const expandedIssues = ref<Set<number>>(new Set());

const severityOptions: Array<{ label: string; value: ComponentSeverity | 'all' }> = [
  { label: t('review.filters.allSeverities'), value: 'all' },
  { label: t('review.severity.critical'), value: 'CRITICAL' },
  { label: t('review.severity.high'), value: 'HIGH' },
  { label: t('review.severity.medium'), value: 'MEDIUM' },
  { label: t('review.severity.low'), value: 'LOW' },
  { label: t('review.severity.info'), value: 'INFO' },
];

const categoryOptions: Array<{ label: string; value: ComponentCategory | 'all' }> = [
  { label: t('review.filters.allCategories'), value: 'all' },
  { label: t('review.category.security'), value: 'SECURITY' },
  { label: t('review.category.performance'), value: 'PERFORMANCE' },
  { label: t('review.category.quality'), value: 'QUALITY' },
  { label: t('review.category.style'), value: 'STYLE' },
  { label: t('review.category.bug'), value: 'BUG' },
  { label: t('review.category.bestPractice'), value: 'BEST_PRACTICE' },
];

const filteredIssues = computed(() => {
  let result = [...props.issues];

  if (filterSeverity.value !== 'all') {
    result = result.filter((i) => i.severity === filterSeverity.value);
  }

  if (filterCategory.value !== 'all') {
    result = result.filter((i) => i.category === filterCategory.value);
  }

  // Sort by severity (CRITICAL first), then by file/line
  result.sort((a, b) => {
    const severityDiff = SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity];
    if (severityDiff !== 0) return severityDiff;
    const fileDiff = a.file.localeCompare(b.file);
    if (fileDiff !== 0) return fileDiff;
    return a.line - b.line;
  });

  return result;
});

function toggleExpand(index: number) {
  if (expandedIssues.value.has(index)) {
    expandedIssues.value.delete(index);
  } else {
    expandedIssues.value.add(index);
  }
}

function isExpanded(index: number) {
  return expandedIssues.value.has(index);
}

function onLineClick(file: string, line: number) {
  emit('lineClick', file, line);
}

function getCategoryColor(category: ComponentCategory): string {
  return CATEGORY_COLORS[category] || '#909399';
}

function resetFilters() {
  filterSeverity.value = 'all';
  filterCategory.value = 'all';
}

function getShortFileName(filePath: string): string {
  const parts = filePath.replace(/\\/g, '/').split('/');
  // Show last 2 path segments
  return parts.slice(-2).join('/');
}
</script>

<template>
  <div class="issue-list">
    <!-- Filters -->
    <div v-if="showFilters" class="issue-list__filters">
      <ElRow :gutter="12" align="middle">
        <ElCol :xs="24" :sm="8" :md="6">
          <ElSelect
            v-model="filterSeverity"
            :placeholder="t('review.filters.severity')"
            size="small"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in severityOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElCol>
        <ElCol :xs="24" :sm="8" :md="6">
          <ElSelect
            v-model="filterCategory"
            :placeholder="t('review.filters.category')"
            size="small"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in categoryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElCol>
        <ElCol :xs="24" :sm="8" :md="4">
          <ElButton size="small" @click="resetFilters">
            {{ t('review.actions.resetFilters') }}
          </ElButton>
        </ElCol>
        <ElCol :xs="24" :sm="24" :md="8" class="issue-list__count">
          <ElText type="info" size="small">
            {{ t('review.summary.issuesFound', { count: filteredIssues.length }) }}
          </ElText>
        </ElCol>
      </ElRow>
    </div>

    <!-- Issue Cards -->
    <div
      class="issue-list__body"
      :style="maxHeight ? { maxHeight, overflowY: 'auto' } : {}"
    >
      <ElEmpty
        v-if="filteredIssues.length === 0"
        :description="t('review.empty.noIssues')"
      />

      <ElCard
        v-for="(issue, index) in filteredIssues"
        :key="index"
        class="issue-card"
        :class="`issue-card--${issue.severity.toLowerCase()}`"
        shadow="hover"
      >
        <!-- Card Header -->
        <div class="issue-card__header">
          <div class="issue-card__badges">
            <SeverityBadge :severity="issue.severity" size="small" />
            <ElTag
              size="small"
              :color="getCategoryColor(issue.category)"
              style="color: #fff; border: none; margin-left: 6px"
            >
              {{ issue.category.replace('_', ' ') }}
            </ElTag>
          </div>
          <div class="issue-card__location">
            <ElButton
              link
              type="primary"
              size="small"
              class="issue-card__file-link"
              @click="onLineClick(issue.file, issue.line)"
            >
              <IconifyIcon icon="lucide:file-code" class="mr-1" style="width: 0.85em; height: 0.85em" />
              {{ getShortFileName(issue.file) }}:{{ issue.line }}
            </ElButton>
          </div>
        </div>

        <!-- Issue Title & Short Description -->
        <div class="issue-card__title">{{ issue.message }}</div>

        <!-- Expandable Detail Section -->
        <template v-if="isExpanded(index)">
          <!-- Code snippet preview -->
          <div v-if="issue.codeSnippet" class="issue-card__snippet">
            <pre class="issue-card__code">{{ issue.codeSnippet }}</pre>
          </div>

          <!-- Fix Suggestion -->
          <div v-if="issue.suggestion" class="issue-card__suggestion">
            <div class="issue-card__suggestion-label">
              <IconifyIcon icon="lucide:lightbulb" class="mr-1" style="color: #e6a23c; width: 1em; height: 1em" />
              {{ t('review.chart.fixSuggestion') }}
            </div>
            <div class="issue-card__suggestion-text">{{ issue.suggestion }}</div>
          </div>
        </template>

        <!-- Expand / Collapse Toggle -->
        <div
          v-if="issue.codeSnippet || issue.suggestion"
          class="issue-card__toggle"
        >
          <ElButton link size="small" @click="toggleExpand(index)">
            <IconifyIcon
              :icon="isExpanded(index) ? 'lucide:chevron-up' : 'lucide:chevron-down'"
              class="mr-1"
              style="width: 0.85em; height: 0.85em"
            />
            {{ isExpanded(index) ? t('review.chart.collapse') : t('review.chart.expand') }}
          </ElButton>
        </div>
      </ElCard>
    </div>
  </div>
</template>

<style scoped>
.issue-list__filters {
  margin-bottom: 16px;
}

.issue-list__count {
  text-align: right;
}

.issue-card {
  margin-bottom: 12px;
  border-left: 4px solid transparent;
}

.issue-card--critical {
  border-left-color: #f56c6c;
}

.issue-card--high {
  border-left-color: #e6a23c;
}

.issue-card--medium {
  border-left-color: #ffd21e;
}

.issue-card--low {
  border-left-color: #409eff;
}

.issue-card--info {
  border-left-color: #909399;
}

.issue-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.issue-card__badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.issue-card__location {
  display: flex;
  align-items: center;
}

.issue-card__file-link {
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
}

.issue-card__title {
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
  line-height: 1.5;
}

.issue-card__snippet {
  background: var(--el-fill-color-darker, #f5f7fa);
  border-radius: 4px;
  padding: 10px 12px;
  margin-top: 8px;
  overflow-x: auto;
}

.issue-card__code {
  margin: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre;
  color: var(--el-text-color-primary);
}

.issue-card__suggestion {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--el-color-warning-light-9, #fdf6ec);
  border-radius: 4px;
}

.issue-card__suggestion-label {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
}

.issue-card__suggestion-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.issue-card__toggle {
  margin-top: 8px;
  text-align: right;
}

@media (max-width: 768px) {
  .issue-list__count {
    text-align: left;
    margin-top: 8px;
  }
}
</style>
