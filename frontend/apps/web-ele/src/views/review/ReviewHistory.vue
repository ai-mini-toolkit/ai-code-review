<script lang="ts" setup>
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Page } from '@vben/common-ui';
import { $t } from '@vben/locales';
import {
  ElButton,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElSpace,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import { useReviewStore } from '#/stores/review';
import type { ReviewTask, TaskStatus } from '#/types/review';

const router = useRouter();
const reviewStore = useReviewStore();

// 过滤后的审查列表（从store计算属性获取）
const filteredReviews = computed(() => reviewStore.filteredReviews);
const loading = computed(() => reviewStore.loading);
const error = computed(() => reviewStore.error);
const pagination = computed(() => reviewStore.pagination);

// 加载审查列表
async function fetchReviews() {
  await reviewStore.fetchReviews();
}

// 查看详情
function handleViewDetail(row: ReviewTask) {
  // 使用 resultId 作为详情页参数（而不是 taskId）
  if (row.resultId) {
    router.push({ name: 'ReviewDetail', params: { id: row.resultId } });
  } else {
    ElMessage.warning('该审查尚未完成，无结果详情');
  }
}

// 复制提交哈希
async function handleCopyCommitHash(commitHash: string) {
  try {
    await navigator.clipboard.writeText(commitHash);
    ElMessage.success($t('review.messages.commitHashCopied'));
  } catch (error) {
    ElMessage.error('复制失败');
  }
}

// 刷新列表
function handleRefresh() {
  fetchReviews();
}

// 重置过滤
function handleResetFilter() {
  reviewStore.resetFilters();
  fetchReviews();
}

// 修改过滤条件
function handleFilterChange(key: 'projectId' | 'status' | 'searchText', value: any) {
  reviewStore.setFilter(key, value);
  fetchReviews();
}

// 翻页
function handlePageChange(page: number) {
  reviewStore.setPage(page);
  fetchReviews();
}

// 改变每页大小
function handlePageSizeChange(pageSize: number) {
  reviewStore.setPageSize(pageSize);
  fetchReviews();
}

// 格式化时间
function formatDate(dateStr: string | undefined) {
  return dateStr ? new Date(dateStr).toLocaleString() : '-';
}

// 格式化处理耗时（毫秒 → 秒）
function formatProcessingTime(ms: number | undefined) {
  if (!ms) return '-';
  return `${(ms / 1000).toFixed(2)}s`;
}

// 状态标签颜色
function getStatusTagType(status: TaskStatus): '' | 'danger' | 'info' | 'success' | 'warning' {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'RUNNING':
      return '';
    case 'PENDING':
      return 'info';
    case 'FAILED':
      return 'danger';
    default:
      return 'info';
  }
}

// 类型标签颜色
function getTypeTagType(type: string): '' | 'danger' | 'info' | 'success' | 'warning' {
  switch (type) {
    case 'PR':
      return '';
    case 'MR':
      return 'success';
    case 'PUSH':
      return 'info';
    default:
      return 'info';
  }
}

// 阈值状态标签颜色（从 resultId 判断）
function getThresholdStatusTag(row: ReviewTask) {
  // 简化版：仅通过 status 判断
  if (row.status === 'COMPLETED') {
    return { text: $t('review.thresholdStatus.pass'), type: 'success' as const };
  }
  if (row.status === 'FAILED') {
    return { text: $t('review.thresholdStatus.blocked'), type: 'danger' as const };
  }
  return null;
}

onMounted(() => {
  fetchReviews();
});
</script>

<template>
  <Page :title="$t('review.history')">
    <!-- 搜索工具栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <ElInput
        :model-value="reviewStore.filters.projectId"
        type="number"
        :placeholder="$t('review.search.projectPlaceholder')"
        clearable
        class="!w-36"
        @update:model-value="(val) => handleFilterChange('projectId', val ? Number(val) : undefined)"
      />
      <ElInput
        :model-value="reviewStore.filters.searchText"
        :placeholder="$t('review.search.placeholder')"
        clearable
        class="!w-80"
        @update:model-value="(val) => handleFilterChange('searchText', val)"
      />
      <ElSelect
        :model-value="reviewStore.filters.status"
        :placeholder="$t('review.search.statusPlaceholder')"
        clearable
        class="!w-36"
        @update:model-value="(val) => handleFilterChange('status', val)"
      >
        <ElOption :label="$t('review.status.all')" value="all" />
        <ElOption :label="$t('review.status.PENDING')" value="PENDING" />
        <ElOption :label="$t('review.status.RUNNING')" value="RUNNING" />
        <ElOption :label="$t('review.status.COMPLETED')" value="COMPLETED" />
        <ElOption :label="$t('review.status.FAILED')" value="FAILED" />
      </ElSelect>
      <ElButton @click="handleResetFilter">
        {{ $t('common.reset') }}
      </ElButton>
      <ElButton @click="handleRefresh">
        {{ $t('review.actions.refresh') }}
      </ElButton>
      <div class="flex-1" />
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="mb-4 text-red-500">
      {{ error }}
    </div>

    <!-- 骨架屏加载 -->
    <ElSkeleton v-if="loading && filteredReviews.length === 0" :rows="8" animated />

    <!-- 审查任务表格 -->
    <ElTable
      v-else
      v-loading="loading"
      :data="filteredReviews"
      stripe
      border
      style="width: 100%"
    >
      <ElTableColumn
        prop="id"
        :label="$t('review.fields.taskId')"
        width="80"
        align="center"
      />
      <ElTableColumn
        prop="type"
        :label="$t('review.fields.type')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <ElTag :type="getTypeTagType(row.type)" size="small">
            {{ $t(`review.type.${row.type}`) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="status"
        :label="$t('review.fields.status')"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <ElTag :type="getStatusTagType(row.status)" size="small">
            {{ $t(`review.status.${row.status}`) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="branch"
        :label="$t('review.fields.branch')"
        min-width="140"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="commitHash"
        :label="$t('review.fields.commitHash')"
        width="120"
      >
        <template #default="{ row }">
          <span
            class="cursor-pointer text-blue-500 hover:underline"
            @click="handleCopyCommitHash(row.commitHash)"
          >
            {{ row.commitHash.substring(0, 8) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="author"
        :label="$t('review.fields.author')"
        width="120"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="prTitle"
        :label="$t('review.fields.prTitle')"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.prTitle || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="$t('review.fields.thresholdStatus')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <ElTag
            v-if="getThresholdStatusTag(row)"
            :type="getThresholdStatusTag(row)!.type"
            size="small"
          >
            {{ getThresholdStatusTag(row)!.text }}
          </ElTag>
          <span v-else>-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="createdAt"
        :label="$t('review.fields.createdAt')"
        width="180"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="$t('project.fields.operations')"
        width="140"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <ElSpace>
            <ElButton
              size="small"
              :disabled="!row.resultId"
              @click="handleViewDetail(row)"
            >
              {{ $t('review.actions.viewDetail') }}
            </ElButton>
          </ElSpace>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 分页 -->
    <div class="mt-4 flex justify-end">
      <ElPagination
        :current-page="pagination.page"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.totalItems"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>
  </Page>
</template>
