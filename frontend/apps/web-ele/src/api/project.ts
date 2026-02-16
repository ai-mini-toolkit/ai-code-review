import { requestClient } from '#/api/request';

export namespace ProjectApi {
  export interface ProjectDTO {
    id: number;
    name: string;
    description: string | null;
    enabled: boolean;
    gitPlatform: string;
    repoUrl: string;
    webhookSecretConfigured: boolean;
    createdAt: string;
    updatedAt: string;
  }

  export interface CreateProjectRequest {
    name: string;
    description?: string;
    enabled?: boolean;
    gitPlatform: string;
    repoUrl: string;
    webhookSecret: string;
  }

  export interface UpdateProjectRequest {
    name?: string;
    description?: string;
    enabled?: boolean;
    gitPlatform?: string;
    repoUrl?: string;
    webhookSecret?: string;
  }
}

/** 获取项目列表 */
export async function getProjectsApi(enabled?: boolean) {
  const params = enabled !== undefined ? { enabled } : {};
  return requestClient.get<ProjectApi.ProjectDTO[]>('/api/v1/projects', {
    params,
  });
}

/** 获取单个项目 */
export async function getProjectApi(id: number) {
  return requestClient.get<ProjectApi.ProjectDTO>(`/api/v1/projects/${id}`);
}

/** 创建项目 */
export async function createProjectApi(
  data: ProjectApi.CreateProjectRequest,
) {
  return requestClient.post<ProjectApi.ProjectDTO>('/api/v1/projects', data);
}

/** 更新项目 */
export async function updateProjectApi(
  id: number,
  data: ProjectApi.UpdateProjectRequest,
) {
  return requestClient.put<ProjectApi.ProjectDTO>(
    `/api/v1/projects/${id}`,
    data,
  );
}

/** 删除项目 */
export async function deleteProjectApi(id: number) {
  return requestClient.delete(`/api/v1/projects/${id}`);
}
