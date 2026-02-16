/**
 * 审查历史 Pinia Store
 * 管理审查任务列表、详情、过滤、分页状态
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import {
  getReviewResultApi,
  getReviewTaskApi,
  getReviewTasksApi,
} from '#/api/review';
import type {
  ReviewResult,
  ReviewTask,
  TaskStatus,
} from '#/types/review';

export const useReviewStore = defineStore('review', () => {
  // State
  const reviews = ref<ReviewTask[]>([]);
  const currentReview = ref<ReviewResult | null>(null);
  const currentTask = ref<ReviewTask | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const pagination = ref({
    page: 1,
    pageSize: 20,
    totalItems: 0,
    totalPages: 0,
  });
  const filters = ref({
    projectId: undefined as number | undefined,
    status: 'all' as TaskStatus | 'all',
    searchText: '',
  });

  // Getters
  const filteredReviews = computed(() => {
    // Server-side filtering now handles status and searchText
    // This getter just returns the server-filtered results
    return reviews.value;
  });

  const errorCount = computed(() =>
    currentReview.value?.summary.errorCount ?? 0
  );

  const warningCount = computed(() =>
    currentReview.value?.summary.warningCount ?? 0
  );

  const infoCount = computed(() =>
    currentReview.value?.summary.infoCount ?? 0
  );

  // Actions
  async function fetchReviews() {
    loading.value = true;
    error.value = null;
    try {
      const params = {
        projectId: filters.value.projectId,
        status: filters.value.status === 'all' ? undefined : filters.value.status,
        searchText: filters.value.searchText || undefined,
        page: pagination.value.page,
        pageSize: pagination.value.pageSize,
      };
      const response = await getReviewTasksApi(params);
      reviews.value = response.items;
      pagination.value = {
        page: response.page,
        pageSize: response.pageSize,
        totalItems: response.totalItems,
        totalPages: response.totalPages,
      };
    } catch (e: any) {
      error.value = e.message || '加载审查历史失败';
    } finally {
      loading.value = false;
    }
  }

  async function fetchReviewDetail(resultId: number) {
    loading.value = true;
    error.value = null;
    try {
      currentReview.value = await getReviewResultApi(resultId);
    } catch (e: any) {
      error.value = e.message || '加载审查详情失败';
    } finally {
      loading.value = false;
    }
  }

  async function fetchTask(taskId: number) {
    loading.value = true;
    error.value = null;
    try {
      currentTask.value = await getReviewTaskApi(taskId);
    } catch (e: any) {
      error.value = e.message || '加载任务详情失败';
    } finally {
      loading.value = false;
    }
  }

  function setFilter(key: keyof typeof filters.value, value: any) {
    filters.value[key] = value;
    pagination.value.page = 1; // 重置到第一页
  }

  function resetFilters() {
    filters.value = {
      projectId: undefined,
      status: 'all',
      searchText: '',
    };
    pagination.value.page = 1;
  }

  function setPage(page: number) {
    pagination.value.page = page;
  }

  function setPageSize(pageSize: number) {
    pagination.value.pageSize = pageSize;
    pagination.value.page = 1; // 重置到第一页
  }

  return {
    // State
    reviews,
    currentReview,
    currentTask,
    loading,
    error,
    pagination,
    filters,
    // Getters
    filteredReviews,
    errorCount,
    warningCount,
    infoCount,
    // Actions
    fetchReviews,
    fetchReviewDetail,
    fetchTask,
    setFilter,
    resetFilters,
    setPage,
    setPageSize,
  };
});
