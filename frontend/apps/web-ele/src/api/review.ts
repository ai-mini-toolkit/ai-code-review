/**
 * 审查历史 API 服务
 */
import { requestClient } from './request';
import type {
  IssueQueryParams,
  PaginatedResponse,
  ReviewIssue,
  ReviewResult,
  ReviewTask,
  TaskQueryParams,
} from '#/types/review';

/**
 * 获取审查任务列表（带分页）
 * @param params 查询参数
 * @returns 分页的审查任务列表
 */
export async function getReviewTasksApi(params?: TaskQueryParams) {
  return requestClient.get<PaginatedResponse<ReviewTask>>('/api/v1/tasks', {
    params,
  });
}

/**
 * 获取审查结果详情
 * @param resultId 审查结果 ID
 * @returns 审查结果详情
 */
export async function getReviewResultApi(resultId: number) {
  return requestClient.get<ReviewResult>(`/api/v1/results/${resultId}`);
}

/**
 * 获取审查问题列表
 * @param resultId 审查结果 ID
 * @param params 查询参数（按严重性或类别过滤）
 * @returns 审查问题列表
 */
export async function getReviewIssuesApi(
  resultId: number,
  params?: IssueQueryParams,
) {
  return requestClient.get<ReviewIssue[]>(
    `/api/v1/results/${resultId}/issues`,
    { params },
  );
}

/**
 * 获取审查任务详情
 * @param taskId 审查任务 ID
 * @returns 审查任务详情
 */
export async function getReviewTaskApi(taskId: number) {
  return requestClient.get<ReviewTask>(`/api/v1/tasks/${taskId}`);
}
