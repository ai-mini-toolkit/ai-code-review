/** Prompt 模板类别（六维度） */
export type TemplateCategory =
  | 'security'
  | 'performance'
  | 'quality'
  | 'style'
  | 'bug'
  | 'call-graph';

/** Prompt 模板 DTO */
export interface TemplateDTO {
  id: number;
  name: string;
  category: TemplateCategory;
  content: string;
  version: string;
  isDefault: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 创建 Prompt 模板请求 */
export interface CreateTemplateRequest {
  name: string;
  category: TemplateCategory;
  content: string;
  enabled?: boolean;
}

/** 更新 Prompt 模板请求 */
export interface UpdateTemplateRequest {
  name?: string;
  category?: TemplateCategory;
  content?: string;
  enabled?: boolean;
}

/** 模板预览请求 */
export interface TemplatePreviewRequest {
  templateContent: string;
  sampleData: PromptContext;
}

/** 模板预览响应 */
export interface TemplatePreviewResponse {
  renderedContent: string;
  variables: string[];
}

/** Prompt 上下文（模板变量） */
export interface PromptContext {
  fileName: string;
  filePath: string;
  language: string;
  codeSnippet: string;
  fullCode: string;
  lineStart: number;
  lineEnd: number;
  changedLines: string;
  addedLines: number;
  deletedLines: number;
  projectName: string;
  branch: string;
  author: string;
  commitMessage: string;
  dimension: string;
  previousIssues: string;
}
