import { requestClient } from '#/api/request';
import type {
  AIModelDTO,
  CreateAIModelRequest,
  UpdateAIModelRequest,
  TestConnectionRequest,
  TestConnectionResponse,
} from '#/types/aiModel';

export namespace AIModelApi {
  export type {
    AIModelDTO,
    CreateAIModelRequest,
    UpdateAIModelRequest,
    TestConnectionRequest,
    TestConnectionResponse,
  };
}

/** 获取 AI 模型列表 */
export async function getAIModelsApi() {
  return requestClient.get<AIModelDTO[]>('/api/v1/ai-models');
}

/** 获取单个 AI 模型 */
export async function getAIModelApi(id: number) {
  return requestClient.get<AIModelDTO>(`/api/v1/ai-models/${id}`);
}

/** 创建 AI 模型 */
export async function createAIModelApi(data: CreateAIModelRequest) {
  return requestClient.post<AIModelDTO>('/api/v1/ai-models', data);
}

/** 更新 AI 模型 */
export async function updateAIModelApi(id: number, data: UpdateAIModelRequest) {
  return requestClient.put<AIModelDTO>(`/api/v1/ai-models/${id}`, data);
}

/** 删除 AI 模型 */
export async function deleteAIModelApi(id: number) {
  return requestClient.delete(`/api/v1/ai-models/${id}`);
}

/** 测试 AI 模型连接 */
export async function testConnectionApi(data: TestConnectionRequest) {
  return requestClient.post<TestConnectionResponse>(
    '/api/v1/ai-models/test',
    data,
  );
}
