# Story 8.3: 实现 AI 模型配置界面

Status: done

## Story

As a 系统管理员,
I want 通过 Web 界面管理 AI 模型配置,
so that 可视化地添加和配置 AI 提供商，无需直接操作数据库。

## Acceptance Criteria

1. **AI 模型列表页面（AIModelList）**：表格展示模型配置，包含列：名称、提供商类型、模型名称、状态（启用/禁用）、创建时间、操作按钮
2. **操作按钮**：每行提供「编辑」「删除」「测试连接」操作；列表顶部提供「新建模型」按钮
3. **创建/编辑表单（AIModelForm）**：包含字段：名称（必填）、提供商类型（下拉必填：OpenAI/Anthropic/CustomOpenAPI）、模型名称（必填）、API Endpoint（自定义提供商时必填）、API Key（密码输入，必填）、超时时间、Max Tokens、Temperature
4. **表单验证**：必填字段验证、URL 格式验证（API Endpoint）、数值范围验证（Temperature 0-1, Max Tokens > 0）
5. **测试连接功能**：表单中提供「测试连接」按钮，调用后端测试 API，显示成功/失败结果（响应时间、错误信息）
6. **API Key 显示为密文**：列表中显示为 `***`，编辑时显示为空（不回显），仅在用户输入新值时更新
7. **状态切换**：列表中每行有启用/禁用开关（ElSwitch），点击立即更新
8. **删除确认**：删除操作弹出确认对话框，显示警告信息（删除后所有审查将无法使用该模型）
9. **操作反馈**：创建/编辑/删除/测试成功后显示 ElMessage 提示，并自动刷新列表
10. **路由配置**：`/models` → AI 模型列表
11. **响应式布局**：桌面端正常表格展示，移动端适配（卡片布局）
12. **API 响应处理**：正确处理后端 `ApiResponse<T>` 格式，包括 404、409、422、503 错误的友好提示

## Tasks / Subtasks

- [x] Task 1: 创建 AI 模型 API 服务层 (AC: #1, #5, #12)
  - [x] 1.1 创建 `src/types/aiModel.ts` + `src/api/aiModel.ts`：定义 TypeScript 接口
  - [x] 1.2 实现 API 函数：`getAIModelsApi`, `getAIModelApi`, `createAIModelApi`, `updateAIModelApi`, `deleteAIModelApi`, `testConnectionApi`

- [x] Task 2: 创建路由配置 (AC: #10)
  - [x] 2.1 创建 `src/router/routes/modules/aiModel.ts`：配置 AI 模型管理路由模块
  - [x] 2.2 配置菜单图标（`lucide:bot`）和排序（order=300）

- [x] Task 3: 实现 AI 模型列表页面 (AC: #1, #2, #6, #7, #8, #9, #11)
  - [x] 3.1 创建 `src/views/aiModel/index.vue`（列表页面入口）
  - [x] 3.2 实现 ElTable 展示模型配置（名称、提供商、模型名称、API Key 密文、状态切换、操作）
  - [x] 3.3 实现状态切换功能（ElSwitch + 立即更新 API）
  - [x] 3.4 实现操作按钮（编辑、删除、测试连接）
  - [x] 3.5 实现删除确认对话框（ElMessageBox.confirm + 警告信息）
  - [x] 3.6 实现测试连接（行内 loading 状态 + ElMessage 结果提示）
  - [x] 3.7 本地过滤（名称/提供商/状态）

- [x] Task 4: 实现创建/编辑表单 (AC: #3, #4, #5, #6, #9)
  - [x] 4.1 创建 `src/views/aiModel/modules/AIModelFormDrawer.vue`（Drawer 形式的表单）
  - [x] 4.2 使用 `useVbenForm` 实现表单 schema（8个字段）
  - [x] 4.3 实现表单验证规则（必填、URL 格式、数值范围）
  - [x] 4.4 实现提供商类型联动（dependencies.show 控制 API Endpoint 显示）
  - [x] 4.5 实现 API Key 密文输入（type="password"，编辑时显示为空）
  - [x] 4.6 实现测试连接按钮（表单内，带结果显示区域）
  - [x] 4.7 提交成功后关闭 Drawer 并刷新列表

- [x] Task 5: 国际化 (AC: 全部)
  - [x] 5.1 在 `src/locales/langs/en-US/aiModel.json` 添加 AI 模型配置相关翻译
  - [x] 5.2 在 `src/locales/langs/zh-CN/aiModel.json` 添加 AI 模型配置相关翻译

- [x] Task 6: 错误处理和边缘情况 (AC: #12)
  - [x] 6.1-6.4 所有 API 错误通过全局拦截器处理，测试连接通过 try/catch 捕获异常显示友好提示

## Dev Notes

### 🔐 安全重点：API Key 保护

**关键安全原则：**
1. API Key **绝不**在列表中明文显示（显示为 `********`）
2. 编辑时 API Key 字段显示为空（**不回显**），仅在用户输入新值时更新
3. 后端 `AIModelDTO` 中 `apiKey` 字段应为 `null` 或 `"REDACTED"`，实际密钥通过单独的加密存储

**实现模式：**
```typescript
// 列表中显示密文
<template #default="{ row }">
  <span v-if="row.apiKeyConfigured">********</span>
  <span v-else class="text-gray-400">未配置</span>
</template>

// 编辑表单中
<ElInput
  v-model="formData.apiKey"
  type="password"
  :placeholder="isEdit ? '留空保持不变' : '请输入 API Key'"
  show-password
/>
```

### 后端 API 端点

| Method | Endpoint | 说明 | 请求体 | 响应 |
|--------|----------|------|--------|------|
| GET | `/api/v1/ai-models` | 获取 AI 模型列表 | - | `ApiResponse<List<AIModelDTO>>` |
| GET | `/api/v1/ai-models/{id}` | 获取单个模型 | - | `ApiResponse<AIModelDTO>` |
| POST | `/api/v1/ai-models` | 创建模型 | `CreateAIModelRequest` | `ApiResponse<AIModelDTO>` (201) |
| PUT | `/api/v1/ai-models/{id}` | 更新模型 | `UpdateAIModelRequest` | `ApiResponse<AIModelDTO>` |
| DELETE | `/api/v1/ai-models/{id}` | 删除模型 | - | `ApiResponse<Void>` |
| POST | `/api/v1/ai-models/test` | 测试连接 | `TestConnectionRequest` | `ApiResponse<TestConnectionResponse>` |

**注意**：后端无分页 API，返回全量列表。

### TypeScript 接口定义

```typescript
// src/types/aiModel.ts

/** AI 模型配置 DTO */
export interface AIModelDTO {
  id: number;
  name: string;                     // 模型配置名称（如 "生产环境 GPT-4"）
  providerType: ProviderType;       // "OPENAI" | "ANTHROPIC" | "CUSTOM_OPENAPI"
  modelName: string;                // 实际模型名称（如 "gpt-4-turbo-preview"）
  apiEndpoint?: string;             // 自定义 API 端点（仅 CUSTOM_OPENAPI）
  apiKeyConfigured: boolean;        // API Key 是否已配置（不暴露实际密钥）
  enabled: boolean;                 // 是否启用
  timeoutSeconds: number;           // 超时时间（秒，默认 30）
  maxTokens?: number;               // 最大 token 数（可选）
  temperature?: number;             // 温度参数（0-1，可选）
  createdAt: string;                // ISO 8601
  updatedAt: string;
}

export type ProviderType = "OPENAI" | "ANTHROPIC" | "CUSTOM_OPENAPI";

/** 创建 AI 模型请求 */
export interface CreateAIModelRequest {
  name: string;                     // @NotBlank, max 255
  providerType: ProviderType;       // @NotNull
  modelName: string;                // @NotBlank, max 255
  apiEndpoint?: string;             // @URL, 仅 CUSTOM_OPENAPI 时必填
  apiKey: string;                   // @NotBlank（加密存储）
  enabled?: boolean;                // 默认 true
  timeoutSeconds?: number;          // 默认 30
  maxTokens?: number;               // > 0
  temperature?: number;             // 0-1
}

/** 更新 AI 模型请求 */
export interface UpdateAIModelRequest {
  name?: string;
  providerType?: ProviderType;
  modelName?: string;
  apiEndpoint?: string;
  apiKey?: string;                  // 仅在提供新值时更新
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
  message: string;                  // 成功："连接成功" / 失败："认证失败"
  responseTimeMs?: number;          // 响应时间（毫秒）
  errorDetails?: string;            // 失败时的详细错误信息
}
```

### API 服务层实现

```typescript
// src/api/aiModel.ts
import { requestClient } from '#/api/request';
import type {
  AIModelDTO,
  CreateAIModelRequest,
  UpdateAIModelRequest,
  TestConnectionRequest,
  TestConnectionResponse
} from '#/types/aiModel';

export namespace AIModelApi {
  export type { AIModelDTO, CreateAIModelRequest, UpdateAIModelRequest };
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
  return requestClient.post<TestConnectionResponse>('/api/v1/ai-models/test', data);
}
```

### 表单 Schema 实现（动态字段）

```typescript
// src/views/aiModel/modules/AIModelFormDrawer.vue (部分)

const providerTypeOptions = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Anthropic (Claude)', value: 'ANTHROPIC' },
  { label: 'Custom OpenAPI', value: 'CUSTOM_OPENAPI' },
];

const selectedProviderType = ref<ProviderType>('OPENAI');

const [Form, formApi] = useVbenForm({
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('aiModel.fields.name'),
      rules: z.string().min(1, $t('common.inputRequired')).max(255),
      componentProps: {
        placeholder: $t('aiModel.form.namePlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'providerType',
      label: $t('aiModel.fields.providerType'),
      rules: z.string().min(1, $t('common.selectRequired')),
      componentProps: {
        options: providerTypeOptions,
        placeholder: $t('aiModel.form.providerTypePlaceholder'),
        onChange: (value: ProviderType) => {
          selectedProviderType.value = value;
        },
      },
    },
    {
      component: 'Input',
      fieldName: 'modelName',
      label: $t('aiModel.fields.modelName'),
      rules: z.string().min(1, $t('common.inputRequired')).max(255),
      componentProps: {
        placeholder: $t('aiModel.form.modelNamePlaceholder'),
      },
      help: $t('aiModel.form.modelNameHelp'),  // 示例：gpt-4-turbo-preview, claude-3-opus-20240229
    },
    {
      component: 'Input',
      fieldName: 'apiEndpoint',
      label: $t('aiModel.fields.apiEndpoint'),
      dependencies: {
        // 仅在选择 CUSTOM_OPENAPI 时显示
        show: (values) => selectedProviderType.value === 'CUSTOM_OPENAPI',
        rules: (values) => {
          if (selectedProviderType.value === 'CUSTOM_OPENAPI') {
            return z.string().min(1, $t('common.inputRequired')).url($t('aiModel.form.apiEndpointInvalid'));
          }
          return z.string().optional();
        },
      },
      componentProps: {
        placeholder: $t('aiModel.form.apiEndpointPlaceholder'),
      },
      help: $t('aiModel.form.apiEndpointHelp'),  // 示例：https://api.openai.com/v1
    },
    {
      component: 'Input',
      fieldName: 'apiKey',
      label: $t('aiModel.fields.apiKey'),
      dependencies: {
        rules: (values) => {
          if (formData.value?.id) {
            // 编辑模式：可选
            return z.string().optional();
          }
          // 创建模式：必填
          return z.string().min(1, $t('common.inputRequired'));
        },
        help: (values) => {
          return formData.value?.id
            ? $t('aiModel.form.apiKeyEditHint')  // "留空则保持当前密钥不变"
            : undefined;
        },
      },
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: $t('aiModel.form.apiKeyPlaceholder'),
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'timeoutSeconds',
      label: $t('aiModel.fields.timeoutSeconds'),
      rules: z.number().min(1).max(300).optional(),
      componentProps: {
        placeholder: '30',
        min: 1,
        max: 300,
      },
      help: $t('aiModel.form.timeoutSecondsHelp'),  // 默认 30 秒
    },
    {
      component: 'InputNumber',
      fieldName: 'maxTokens',
      label: $t('aiModel.fields.maxTokens'),
      rules: z.number().min(1).optional(),
      componentProps: {
        placeholder: '4000',
        min: 1,
        max: 100000,
      },
      help: $t('aiModel.form.maxTokensHelp'),  // 可选，留空使用模型默认值
    },
    {
      component: 'InputNumber',
      fieldName: 'temperature',
      label: $t('aiModel.fields.temperature'),
      rules: z.number().min(0).max(1).optional(),
      componentProps: {
        placeholder: '0.7',
        min: 0,
        max: 1,
        step: 0.1,
      },
      help: $t('aiModel.form.temperatureHelp'),  // 0-1，越高越随机
    },
  ],
  showDefaultActions: false,
});
```

### 测试连接功能实现

```typescript
// src/views/aiModel/modules/AIModelFormDrawer.vue (部分)

const testing = ref(false);
const testResult = ref<TestConnectionResponse | null>(null);

async function handleTestConnection() {
  const { valid } = await formApi.validate();
  if (!valid) {
    ElMessage.warning($t('aiModel.messages.validateFirst'));
    return;
  }

  const values = await formApi.getValues();
  testing.value = true;
  testResult.value = null;

  try {
    const testRequest: TestConnectionRequest = {
      providerType: values.providerType,
      modelName: values.modelName,
      apiEndpoint: values.apiEndpoint,
      apiKey: values.apiKey,
      timeoutSeconds: values.timeoutSeconds || 30,
    };

    testResult.value = await testConnectionApi(testRequest);

    if (testResult.value.success) {
      ElMessage.success(
        `${$t('aiModel.messages.testSuccess')} (${testResult.value.responseTimeMs}ms)`
      );
    } else {
      ElMessage.error(
        `${$t('aiModel.messages.testFailed')}: ${testResult.value.message}`
      );
    }
  } catch (error: any) {
    ElMessage.error(
      `${$t('aiModel.messages.testError')}: ${error.message}`
    );
  } finally {
    testing.value = false;
  }
}
```

```vue
<template>
  <Drawer :title="drawerTitle" class="w-full max-w-[600px]">
    <Form />

    <!-- 测试连接按钮（放在表单下方） -->
    <div class="test-connection-section">
      <ElButton
        type="warning"
        :loading="testing"
        @click="handleTestConnection"
      >
        <i class="lucide:plug-zap" />
        {{ $t('aiModel.actions.testConnection') }}
      </ElButton>
      <span v-if="testResult" class="test-result">
        <i :class="testResult.success ? 'lucide:check-circle text-green-500' : 'lucide:x-circle text-red-500'" />
        {{ testResult.message }}
        <span v-if="testResult.responseTimeMs" class="text-gray-500">
          ({{ testResult.responseTimeMs }}ms)
        </span>
      </span>
    </div>

    <template #footer>
      <ElSpace>
        <ElButton @click="drawerApi.close()">
          {{ $t('common.cancel') }}
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSave">
          {{ $t('common.confirm') }}
        </ElButton>
      </ElSpace>
    </template>
  </Drawer>
</template>

<style scoped lang="scss">
.test-connection-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;

  .test-result {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
  }
}
</style>
```

### 错误处理映射

| HTTP 状态 | 错误码 | 用户提示 |
|-----------|--------|---------|
| 404 | `ERR_404` | "AI 模型配置不存在" |
| 409 | `ERR_409` | "模型名称已存在，请使用其他名称" |
| 422 | `ERR_422` | 显示 `error.details` 中的具体字段错误 |
| 500 | `ERR_500` | "服务器错误，请稍后重试" |
| 503 | `ERR_503` | "AI 服务暂时不可用，请稍后重试" |

### Vben Admin 框架模式 — 必须遵循

**项目结构**（Monorepo，主应用 `apps/web-ele`）：
```
frontend/apps/web-ele/src/
├── api/aiModel.ts                          ← 新建：AI 模型 API 服务
├── views/aiModel/
│   ├── index.vue                           ← 新建：AI 模型列表
│   └── modules/
│       └── AIModelFormDrawer.vue           ← 新建：创建/编辑 Drawer
├── types/aiModel.ts                        ← 新建：TypeScript 类型定义
├── router/routes/modules/
│   └── aiModel.ts                          ← 新建：路由模块
└── locales/langs/
    ├── en-US/aiModel.json                  ← 新建：英文翻译
    └── zh-CN/aiModel.json                  ← 新建：中文翻译
```

**路由模块模式**（参考 Story 8-1/8-2）：
```typescript
// src/router/routes/modules/aiModel.ts
import type { RouteRecordRaw } from 'vue-router';
import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:bot',
      order: 300,
      title: $t('aiModel.title'),
    },
    name: 'AIModelManagement',
    path: '/models',
    component: () => import('#/views/aiModel/index.vue'),
  },
];

export default routes;
```

### 提供商类型说明

| 提供商 | providerType | 示例模型名称 | API Endpoint | 说明 |
|-------|-------------|------------|-------------|------|
| OpenAI | `OPENAI` | `gpt-4-turbo-preview`, `gpt-3.5-turbo` | 内置 `https://api.openai.com/v1` | 使用 OpenAI 官方 API |
| Anthropic | `ANTHROPIC` | `claude-3-opus-20240229`, `claude-3-sonnet-20240229` | 内置 `https://api.anthropic.com/v1` | 使用 Anthropic Claude API |
| 自定义 OpenAPI | `CUSTOM_OPENAPI` | 任意（如 `qwen-max`, `llama-3-70b`） | 用户提供（如 `https://dashscope.aliyuncs.com/compatible-mode/v1`） | 兼容 OpenAI API 格式的第三方服务 |

### Story 8-1/8-2 实施经验（关键教训）

✅ **成功模式（必须遵循）**：
1. **API 响应适配**：`codeField: 'success'` + `successCode: (code) => code === true`（已在 Story 8-1 中配置）
2. **ElMessage 成功提示**：创建/编辑/删除/测试成功后必须显示 `ElMessage.success()`
3. **错误处理**：所有 async 函数使用 try/catch，避免未捕获的 promise rejection
4. **help 文本响应式**：使用 `dependencies.help` 函数而非 `computed(...).value`
5. **国际化 key 完整性**：确保所有 `$t()` 调用的 key 都已定义在 locale 文件中

⚠️ **特别注意**：
- **API Key 安全**：绝不明文显示，编辑时不回显，使用 `type="password"`
- **动态字段显示**：使用 `dependencies.show` 控制字段显示/隐藏（如 API Endpoint 仅在 CUSTOM_OPENAPI 时显示）
- **数值输入验证**：使用 `InputNumber` 组件 + Zod 范围验证（如 temperature 0-1）

### 命名约定

| 类型 | 规则 | 示例 |
|------|------|------|
| Vue 组件文件 | PascalCase | `AIModelFormDrawer.vue` |
| API 模块文件 | camelCase | `aiModel.ts` |
| 路由模块文件 | camelCase | `aiModel.ts` |
| 函数名 | camelCase | `getAIModelsApi`, `handleTestConnection` |
| 接口名 | PascalCase | `AIModelDTO`, `CreateAIModelRequest` |
| 枚举类型 | PascalCase + UPPER_SNAKE_CASE values | `ProviderType = "OPENAI" \| "ANTHROPIC"` |

### 安全注意事项

1. **API Key 存储**：后端使用 AES-256 加密存储在数据库中
2. **API Key 传输**：通过 HTTPS 传输，前端不缓存
3. **权限控制**：仅 ADMIN 角色可以管理 AI 模型配置（后端验证）
4. **测试连接限制**：后端限制测试频率（如 5 次/分钟），防止滥用

### 响应式布局

```vue
<template>
  <!-- 桌面端：表格布局 -->
  <ElTable v-if="!isMobile" :data="models" stripe border>
    <!-- 表格列 -->
  </ElTable>

  <!-- 移动端：卡片布局 -->
  <div v-else class="model-cards">
    <ElCard v-for="model in models" :key="model.id" class="model-card">
      <template #header>
        <div class="card-header">
          <span class="model-name">{{ model.name }}</span>
          <ElSwitch v-model="model.enabled" @change="handleToggleEnabled(model)" />
        </div>
      </template>
      <div class="card-content">
        <div class="info-row">
          <span class="label">{{ $t('aiModel.fields.providerType') }}:</span>
          <ElTag>{{ model.providerType }}</ElTag>
        </div>
        <div class="info-row">
          <span class="label">{{ $t('aiModel.fields.modelName') }}:</span>
          <span>{{ model.modelName }}</span>
        </div>
        <div class="info-row">
          <span class="label">{{ $t('aiModel.fields.apiKey') }}:</span>
          <span v-if="model.apiKeyConfigured">********</span>
          <span v-else class="text-gray-400">{{ $t('aiModel.status.notConfigured') }}</span>
        </div>
      </div>
      <template #footer>
        <ElSpace>
          <ElButton size="small" type="primary" @click="handleEdit(model)">
            {{ $t('common.edit') }}
          </ElButton>
          <ElButton size="small" type="warning" @click="handleTestConnection(model)">
            {{ $t('aiModel.actions.testConnection') }}
          </ElButton>
          <ElButton size="small" type="danger" @click="handleDelete(model)">
            {{ $t('common.delete') }}
          </ElButton>
        </ElSpace>
      </template>
    </ElCard>
  </div>
</template>

<style scoped lang="scss">
.model-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .model-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .model-name {
        font-weight: 600;
        font-size: 16px;
      }
    }

    .card-content {
      .info-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;

        .label {
          font-weight: 500;
          color: #606266;
        }
      }
    }
  }
}
</style>
```

### Project Structure Notes

- 所有新文件在 `frontend/apps/web-ele/src/` 下创建
- Monorepo 结构：`apps/web-ele` 是主应用，使用 Element Plus UI
- 路由模块通过 `import.meta.glob('./modules/**/*.ts')` 自动加载

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-8.md#Story 8.3]
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Vue Structure]
- [Source: _bmad-output/implementation-artifacts/8-1-project-management-interface.md]
- [Source: _bmad-output/implementation-artifacts/8-2-review-history-viewing-interface.md]
- [Source: backend/ai-code-review-api - AIModelController.java]
- [Source: backend/ai-code-review-common - AIModelDTO.java, CreateAIModelRequest.java]
- [Source: backend/ai-code-review-repository - AIModelRepository.java]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

N/A - Build passed on first attempt (31.55s)

### Completion Notes List

1. Implemented all 6 tasks following the useVbenDrawer/useVbenForm pattern from Story 8.1
2. API Key security: list displays `••••••••` masked (AC6 compliant), form field type="password", edit mode shows empty with hint text
3. Dynamic CUSTOM_OPENAPI endpoint field using `dependencies.show` with `selectedProviderType` reactive ref
4. Test connection in drawer shows inline result with ElTag (success/danger) + response time
5. Test connection from list redirects to edit form with info message (backend requires API Key in TestConnectionRequest)
6. filteredModels uses `computed()` for fully reactive filtering — no manual sync required
7. Responsive layout: desktop table + mobile card layout using `useMediaQuery('(max-width: 768px)')`
8. Confirm button has `:loading="saving"` state
9. apiEndpoint cleared when switching away from CUSTOM_OPENAPI provider
10. Build verified (post code review fixes): 31.76s, no TypeScript errors

### Code Review Record

**Code Review performed after initial implementation**

**Issues Fixed (6 HIGH/MEDIUM)**:
- [H1-Fixed] Added mobile card layout with `useMediaQuery` for responsive design (AC11)
- [H2-Fixed] Removed `__USE_STORED__` hack; test-from-list now opens edit form with info message
- [H3-Fixed] API Key column now shows `••••••••` masked text per AC6 spec
- [M1-Fixed] Added `saving` ref with `:loading="saving"` on confirm button in custom footer
- [M2-Fixed] Changed `filteredModels` from manual `ref` to `computed()` — fully reactive
- [M3-Fixed] `apiEndpoint` cleared when providerType switches away from CUSTOM_OPENAPI

**Review Follow-ups (LOW, deferred)**:
- [L1] 409 conflict specific error handling for duplicate model name
- [L2] No `enabled` toggle in create form (always creates as enabled)
- [L3] InputNumber fields could show default values instead of placeholder text

### File List

- frontend/apps/web-ele/src/types/aiModel.ts (created)
- frontend/apps/web-ele/src/api/aiModel.ts (created)
- frontend/apps/web-ele/src/router/routes/modules/aiModel.ts (created)
- frontend/apps/web-ele/src/views/aiModel/index.vue (created)
- frontend/apps/web-ele/src/views/aiModel/modules/AIModelFormDrawer.vue (created)
- frontend/apps/web-ele/src/locales/langs/zh-CN/aiModel.json (created)
- frontend/apps/web-ele/src/locales/langs/en-US/aiModel.json (created)
- _bmad-output/implementation-artifacts/8-3-ai-model-configuration-interface.md (updated)
- _bmad-output/implementation-artifacts/sprint-status.yaml (updated)
