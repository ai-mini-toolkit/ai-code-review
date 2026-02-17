import { requestClient } from '#/api/request';
import type {
  CreateTemplateRequest,
  TemplateDTO,
  TemplatePreviewRequest,
  TemplatePreviewResponse,
  UpdateTemplateRequest,
} from '#/types/template';

export namespace TemplateApi {
  export type {
    TemplateDTO,
    CreateTemplateRequest,
    UpdateTemplateRequest,
    TemplatePreviewRequest,
    TemplatePreviewResponse,
  };
}

/** 获取 Prompt 模板列表 */
export async function getTemplatesApi(params?: { category?: string }) {
  return requestClient.get<TemplateDTO[]>('/api/v1/templates', { params });
}

/** 获取单个 Prompt 模板 */
export async function getTemplateApi(id: number) {
  return requestClient.get<TemplateDTO>(`/api/v1/templates/${id}`);
}

/** 创建 Prompt 模板 */
export async function createTemplateApi(data: CreateTemplateRequest) {
  return requestClient.post<TemplateDTO>('/api/v1/templates', data);
}

/** 更新 Prompt 模板 */
export async function updateTemplateApi(
  id: number,
  data: UpdateTemplateRequest,
) {
  return requestClient.put<TemplateDTO>(`/api/v1/templates/${id}`, data);
}

/** 删除 Prompt 模板 */
export async function deleteTemplateApi(id: number) {
  return requestClient.delete(`/api/v1/templates/${id}`);
}

/** 恢复默认模板 */
export async function restoreDefaultTemplateApi(id: number) {
  return requestClient.post<TemplateDTO>(
    `/api/v1/templates/${id}/restore-default`,
  );
}

/** 预览模板渲染 */
export async function previewTemplateApi(data: TemplatePreviewRequest) {
  return requestClient.post<TemplatePreviewResponse>(
    '/api/v1/templates/preview',
    data,
  );
}
