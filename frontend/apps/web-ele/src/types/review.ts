/**
 * 审查相关 TypeScript 类型定义
 * 对应后端 ReviewTask, ReviewResult, ReviewIssue DTO
 */

/** 任务状态 */
export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/** 问题严重性 */
export type IssueSeverity = 'Critical' | 'High' | 'Medium' | 'Low' | 'Info';

/** 问题类别 */
export type IssueCategory =
  | 'security'
  | 'performance'
  | 'quality'
  | 'style'
  | 'bug'
  | 'best_practice';

/** 审查任务（来自 review_task 表） */
export interface ReviewTask {
  id: number; // taskId
  type: string; // "PR" | "MR" | "PUSH"
  priority: number; // 0-100
  status: TaskStatus;
  projectId: number;
  repoUrl: string;
  branch: string;
  commitHash: string;
  author: string;
  prNumber?: number;
  prTitle?: string;
  prDescription?: string;
  createdAt: string; // ISO 8601
  completedAt?: string;
  resultId?: number; // 关联 review_result 表
  retryCount?: number;
  maxRetries?: number;
}

/** 审查结果（来自 review_result 表） */
export interface ReviewResult {
  id: number;
  taskId: number;
  projectId: number;
  summary: ReviewSummary; // JSONB 字段
  createdAt: string;
}

/** 审查摘要（存储在 review_result.summary JSONB 字段） */
export interface ReviewSummary {
  totalFiles: number;
  totalLines: number;
  totalIssues: number;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  score: number; // 0-100
  thresholdStatus: 'pass' | 'blocked' | 'warning';
  dimensions: DimensionResult[];
  callGraph?: CallGraphData;
  processingTimeMs: number;
}

/** 六维度审查结果 */
export interface DimensionResult {
  dimension: string; // "security" | "performance" | "quality" | "style" | "bug" | "call-graph"
  score: number;
  issues: ReviewIssue[];
}

/** 审查问题 */
export interface ReviewIssue {
  severity: IssueSeverity;
  category: IssueCategory;
  title: string;
  description: string;
  filePath: string;
  lineNumber: number;
  codeSnippet: string;
  fixSuggestion: string;
  referenceLinks?: string[];
}

/** 调用链路图数据 */
export interface CallGraphData {
  nodes: CallGraphNode[];
  edges: CallGraphEdge[];
}

export interface CallGraphNode {
  id: string;
  label: string;
  type: 'class' | 'method';
  isChanged: boolean;
}

export interface CallGraphEdge {
  from: string;
  to: string;
  label?: string;
}

/** 分页响应（后端统一格式） */
export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

/** 审查任务查询参数 */
export interface TaskQueryParams {
  projectId?: number;
  status?: TaskStatus;
  page?: number;
  pageSize?: number;
  searchText?: string;
}

/** 审查问题查询参数 */
export interface IssueQueryParams {
  severity?: IssueSeverity;
  category?: IssueCategory;
}
