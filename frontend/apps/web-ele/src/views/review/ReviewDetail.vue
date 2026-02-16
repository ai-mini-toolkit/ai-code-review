<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Page } from '@vben/common-ui';
import { $t } from '@vben/locales';
import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElProgress,
  ElRow,
  ElSelect,
  ElOption,
  ElTag,
  ElTabs,
  ElTabPane,
} from 'element-plus';
import { useReviewStore } from '#/stores/review';
import { getReviewIssuesApi, getReviewTaskApi } from '#/api/review';
import type { ReviewIssue, ReviewTask, IssueSeverity, IssueCategory } from '#/types/review';

const route = useRoute();
const router = useRouter();
const reviewStore = useReviewStore();

const resultId = Number(route.params.id);
const currentReview = computed(() => reviewStore.currentReview);
const loading = computed(() => reviewStore.loading);
const error = computed(() => reviewStore.error);

// 问题列表状态
const issues = ref<ReviewIssue[]>([]);
const issuesLoading = ref(false);
const filterSeverity = ref<IssueSeverity | 'all'>('all');
const filterCategory = ref<IssueCategory | 'all'>('all');

// 任务详情（用于显示基本信息）
const taskDetail = ref<ReviewTask | null>(null);

// 过滤后的问题列表
const filteredIssues = computed(() => {
  let result = issues.value;

  if (filterSeverity.value !== 'all') {
    result = result.filter(i => i.severity === filterSeverity.value);
  }

  if (filterCategory.value !== 'all') {
    result = result.filter(i => i.category === filterCategory.value);
  }

  return result;
});

// 按严重性分组的问题
const issuesBySeverity = computed(() => {
  const groups: Record<IssueSeverity, ReviewIssue[]> = {
    Critical: [],
    High: [],
    Medium: [],
    Low: [],
    Info: [],
  };

  filteredIssues.value.forEach(issue => {
    groups[issue.severity].push(issue);
  });

  return groups;
});

// 加载审查详情
async function fetchReviewDetail() {
  await reviewStore.fetchReviewDetail(resultId);

  // 同时加载关联的任务详情
  if (currentReview.value) {
    try {
      taskDetail.value = await getReviewTaskApi(currentReview.value.taskId);
    } catch (e) {
      console.error('Failed to load task detail:', e);
    }
  }
}

// 加载问题列表
async function fetchIssues() {
  issuesLoading.value = true;
  try {
    issues.value = await getReviewIssuesApi(resultId);
  } catch (e: any) {
    ElMessage.error(e.message || $t('review.messages.loadDetailFailed'));
  } finally {
    issuesLoading.value = false;
  }
}

// 返回列表
function handleBack() {
  router.push({ name: 'ReviewHistory' });
}

// 导出报告
function handleExportReport() {
  ElMessage.info('报告导出功能开发中');
}

// 格式化时间
function formatDate(dateStr: string | undefined) {
  return dateStr ? new Date(dateStr).toLocaleString() : '-';
}

// 格式化处理耗时
function formatProcessingTime(ms: number) {
  return `${(ms / 1000).toFixed(2)}s`;
}

// 严重性徽章颜色
function getSeverityTagType(severity: IssueSeverity): '' | 'danger' | 'info' | 'success' | 'warning' {
  switch (severity) {
    case 'Critical':
      return 'danger';
    case 'High':
      return 'danger';
    case 'Medium':
      return 'warning';
    case 'Low':
      return 'info';
    case 'Info':
      return 'info';
    default:
      return 'info';
  }
}

// 阈值状态标签
function getThresholdStatusTag() {
  const status = currentReview.value?.summary.thresholdStatus;
  if (!status) return null;

  const configs = {
    pass: { text: $t('review.thresholdStatus.pass'), type: 'success' as const, icon: '✅' },
    blocked: { text: $t('review.thresholdStatus.blocked'), type: 'danger' as const, icon: '❌' },
    warning: { text: $t('review.thresholdStatus.warning'), type: 'warning' as const, icon: '⚠️' },
  };

  return configs[status];
}

// 分数颜色
function getScoreColor(score: number): 'success' | 'exception' | 'warning' | '' {
  if (score >= 80) return 'success';
  if (score >= 60) return 'warning';
  return 'exception';
}

onMounted(() => {
  fetchReviewDetail();
  fetchIssues();
});
</script>

<template>
  <Page :title="$t('review.detail')">
    <div v-if="error" class="mb-4 text-red-500">
      {{ error }}
    </div>

    <div v-if="!loading && currentReview" class="review-detail">
      <!-- 顶部操作栏 -->
      <div class="mb-4 flex items-center justify-between">
        <ElButton @click="handleBack">
          ← {{ $t('common.back') }}
        </ElButton>
        <ElButton type="primary" @click="handleExportReport">
          {{ $t('review.actions.exportReport') }}
        </ElButton>
      </div>

      <!-- 审查摘要卡片 -->
      <ElCard v-loading="loading" class="mb-4">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-lg font-bold">{{ $t('review.summary.title') }}</span>
            <ElTag v-if="getThresholdStatusTag()" :type="getThresholdStatusTag()!.type" size="large">
              {{ getThresholdStatusTag()!.icon }} {{ getThresholdStatusTag()!.text }}
            </ElTag>
          </div>
        </template>

        <ElRow :gutter="24">
          <ElCol :xs="24" :sm="12" :md="8">
            <div class="mb-4">
              <div class="text-sm text-gray-500 mb-2">{{ $t('review.fields.score') }}</div>
              <ElProgress
                :percentage="currentReview.summary.score"
                :color="getScoreColor(currentReview.summary.score)"
                :stroke-width="20"
              />
              <div class="mt-2 text-center text-2xl font-bold">
                {{ currentReview.summary.score }} / 100
              </div>
            </div>
          </ElCol>
          <ElCol :xs="24" :sm="12" :md="16">
            <ElDescriptions :column="2" border size="small">
              <ElDescriptionsItem :label="$t('review.fields.totalFiles')">
                {{ currentReview.summary.totalFiles }}
              </ElDescriptionsItem>
              <ElDescriptionsItem :label="$t('review.fields.totalLines')">
                {{ currentReview.summary.totalLines }}
              </ElDescriptionsItem>
              <ElDescriptionsItem :label="$t('review.fields.totalIssues')">
                {{ currentReview.summary.totalIssues }}
              </ElDescriptionsItem>
              <ElDescriptionsItem :label="$t('review.fields.processingTime')">
                {{ formatProcessingTime(currentReview.summary.processingTimeMs) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem :label="$t('review.summary.errorCount')">
                <ElTag type="danger" size="small">{{ currentReview.summary.errorCount }}</ElTag>
              </ElDescriptionsItem>
              <ElDescriptionsItem :label="$t('review.summary.warningCount')">
                <ElTag type="warning" size="small">{{ currentReview.summary.warningCount }}</ElTag>
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElCol>
        </ElRow>

        <!-- 任务基本信息 -->
        <div v-if="taskDetail" class="mt-4 border-t pt-4">
          <ElDescriptions :column="3" size="small">
            <ElDescriptionsItem :label="$t('review.fields.branch')">
              {{ taskDetail.branch }}
            </ElDescriptionsItem>
            <ElDescriptionsItem :label="$t('review.fields.commitHash')">
              {{ taskDetail.commitHash.substring(0, 8) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem :label="$t('review.fields.author')">
              {{ taskDetail.author }}
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="taskDetail.prTitle" :label="$t('review.fields.prTitle')" :span="3">
              {{ taskDetail.prTitle }}
            </ElDescriptionsItem>
            <ElDescriptionsItem :label="$t('review.fields.createdAt')">
              {{ formatDate(taskDetail.createdAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem :label="$t('review.fields.completedAt')">
              {{ formatDate(taskDetail.completedAt) }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </div>
      </ElCard>

      <!-- 问题列表 -->
      <ElCard v-loading="issuesLoading">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-lg font-bold">{{ $t('review.summary.issues') }}</span>
            <div class="flex gap-2">
              <ElSelect
                v-model="filterSeverity"
                :placeholder="$t('review.filters.severity')"
                class="!w-36"
                size="small"
              >
                <ElOption :label="$t('review.filters.allSeverities')" value="all" />
                <ElOption :label="$t('review.severity.Critical')" value="Critical" />
                <ElOption :label="$t('review.severity.High')" value="High" />
                <ElOption :label="$t('review.severity.Medium')" value="Medium" />
                <ElOption :label="$t('review.severity.Low')" value="Low" />
                <ElOption :label="$t('review.severity.Info')" value="Info" />
              </ElSelect>
              <ElSelect
                v-model="filterCategory"
                :placeholder="$t('review.filters.category')"
                class="!w-36"
                size="small"
              >
                <ElOption :label="$t('review.filters.allCategories')" value="all" />
                <ElOption :label="$t('review.category.security')" value="security" />
                <ElOption :label="$t('review.category.performance')" value="performance" />
                <ElOption :label="$t('review.category.quality')" value="quality" />
                <ElOption :label="$t('review.category.style')" value="style" />
                <ElOption :label="$t('review.category.bug')" value="bug" />
                <ElOption :label="$t('review.category.best_practice')" value="best_practice" />
              </ElSelect>
            </div>
          </div>
        </template>

        <ElTabs v-if="filteredIssues.length > 0">
          <ElTabPane :label="`${$t('review.filters.allSeverities')} (${filteredIssues.length})`">
            <div class="issues-list space-y-3">
              <div
                v-for="(issue, index) in filteredIssues"
                :key="index"
                class="issue-card p-4 border rounded-lg hover:shadow-md transition-shadow"
              >
                <div class="flex items-start justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <ElTag :type="getSeverityTagType(issue.severity)" size="small">
                      {{ $t(`review.severity.${issue.severity}`) }}
                    </ElTag>
                    <ElTag type="info" size="small">
                      {{ $t(`review.category.${issue.category}`) }}
                    </ElTag>
                    <span class="font-semibold">{{ issue.title }}</span>
                  </div>
                  <span class="text-sm text-gray-500">{{ issue.filePath }}:{{ issue.lineNumber }}</span>
                </div>

                <div class="mb-2 text-sm text-gray-700">
                  {{ issue.description }}
                </div>

                <div v-if="issue.codeSnippet" class="mb-2 p-2 bg-gray-50 rounded border">
                  <pre class="text-xs overflow-x-auto"><code>{{ issue.codeSnippet }}</code></pre>
                </div>

                <div v-if="issue.fixSuggestion" class="text-sm">
                  <span class="font-semibold text-green-600">{{ $t('review.issue.fixSuggestion') }}:</span>
                  <span class="ml-2">{{ issue.fixSuggestion }}</span>
                </div>
              </div>
            </div>
          </ElTabPane>

          <!-- 按严重性分组的标签页 -->
          <ElTabPane
            v-for="severity in ['Critical', 'High', 'Medium', 'Low', 'Info']"
            :key="severity"
            :label="`${$t(`review.severity.${severity}`)} (${issuesBySeverity[severity as IssueSeverity].length})`"
          >
            <div v-if="issuesBySeverity[severity as IssueSeverity].length > 0" class="issues-list space-y-3">
              <div
                v-for="(issue, index) in issuesBySeverity[severity as IssueSeverity]"
                :key="index"
                class="issue-card p-4 border rounded-lg hover:shadow-md transition-shadow"
              >
                <div class="flex items-start justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <ElTag type="info" size="small">
                      {{ $t(`review.category.${issue.category}`) }}
                    </ElTag>
                    <span class="font-semibold">{{ issue.title }}</span>
                  </div>
                  <span class="text-sm text-gray-500">{{ issue.filePath }}:{{ issue.lineNumber }}</span>
                </div>

                <div class="mb-2 text-sm text-gray-700">
                  {{ issue.description }}
                </div>

                <div v-if="issue.codeSnippet" class="mb-2 p-2 bg-gray-50 rounded border">
                  <pre class="text-xs overflow-x-auto"><code>{{ issue.codeSnippet }}</code></pre>
                </div>

                <div v-if="issue.fixSuggestion" class="text-sm">
                  <span class="font-semibold text-green-600">{{ $t('review.issue.fixSuggestion') }}:</span>
                  <span class="ml-2">{{ issue.fixSuggestion }}</span>
                </div>
              </div>
            </div>
            <ElEmpty v-else :description="$t('review.empty.noIssues')" />
          </ElTabPane>
        </ElTabs>

        <ElEmpty v-else :description="$t('review.empty.noIssues')" />
      </ElCard>
    </div>

    <ElEmpty v-else-if="!loading" :description="$t('review.messages.noResults')" />
  </Page>
</template>

<style scoped lang="scss">
.review-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.issues-list {
  max-height: 800px;
  overflow-y: auto;
}

.issue-card {
  background: white;

  &:hover {
    border-color: #409eff;
  }
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
