/** AI 模型提供商类型 */
export type ProviderType = 'OPENAI' | 'ANTHROPIC' | 'CUSTOM_OPENAPI';

/** AI 模型配置 DTO */
export interface AIModelDTO {
  id: number;
  name: string;
  providerType: ProviderType;
  modelName: string;
  apiEndpoint?: string;
  apiKeyConfigured: boolean;
  enabled: boolean;
  timeoutSeconds: number;
  maxTokens?: number;
  temperature?: number;
  createdAt: string;
  updatedAt: string;
}

/** 创建 AI 模型请求 */
export interface CreateAIModelRequest {
  name: string;
  providerType: ProviderType;
  modelName: string;
  apiEndpoint?: string;
  apiKey: string;
  enabled?: boolean;
  timeoutSeconds?: number;
  maxTokens?: number;
  temperature?: number;
}

/** 更新 AI 模型请求 */
export interface UpdateAIModelRequest {
  name?: string;
  providerType?: ProviderType;
  modelName?: string;
  apiEndpoint?: string;
  apiKey?: string;
  enabled?: boolean;
  timeoutSeconds?: number;
  maxTokens?: number;
  temperature?: number;
}

/** 测试连接请求 */
export interface TestConnectionRequest {
  providerType: ProviderType;
  modelName: string;
  apiEndpoint?: string;
  apiKey: string;
  timeoutSeconds?: number;
}

/** 测试连接响应 */
export interface TestConnectionResponse {
  success: boolean;
  message: string;
  responseTimeMs?: number;
  errorDetails?: string;
}
