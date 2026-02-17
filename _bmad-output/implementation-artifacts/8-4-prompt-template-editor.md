# Story 8.4: 实现 Prompt 模板编辑器

Status: review

## Story

As a 系统管理员,
I want 通过 Web 界面编辑 Prompt 模板,
so that 定制 AI 审查的提示词，优化审查质量。

## Acceptance Criteria

1. **Prompt 模板列表页面（TemplateList）**：表格展示模板，包含列：名称、类别（六维度）、版本号、状态（启用/禁用）、最后修改时间、操作按钮
2. **按类别过滤**：下拉选择器过滤六维度类别（Security、Performance、Quality、Style、Bug、CallGraph）
3. **操作按钮**：每行提供「编辑」「删除」「复制」操作；列表顶部提供「新建模板」按钮
4. **模板编辑器（TemplateEditor）**：使用代码编辑器组件（Monaco Editor 或 CodeMirror），支持 Mustache 语法高亮
5. **模板变量提示**：编辑器中自动补全模板变量（如 `{{fileName}}`, `{{codeSnippet}}`, `{{language}}`）
6. **实时预览**：编辑器右侧分栏显示使用示例数据渲染后的 Prompt 内容
7. **保存和版本管理**：保存时自动生成版本号（v1.0, v1.1, v2.0），支持查看历史版本
8. **恢复默认模板**：提供「恢复到默认模板」按钮，弹出确认对话框
9. **模板变量文档**：编辑器下方显示可用变量列表和说明（如 `{{fileName}}` - 当前文件名）
10. **表单验证**：模板名称必填、Mustache 语法校验（检测未闭合的 `{{` 或 `}}`）
11. **操作反馈**：保存/删除/恢复成功后显示 ElMessage 提示，并自动刷新列表
12. **路由配置**：`/templates` → 模板列表，`/templates/:id/edit` → 模板编辑器
13. **响应式布局**：桌面端分屏编辑（编辑器 + 预览），移动端单屏切换（编辑/预览 Tab）
14. **API 响应处理**：正确处理后端 `ApiResponse<T>` 格式，包括 404、409、422 错误的友好提示

## Tasks / Subtasks

- [x] Task 1: 创建 Prompt 模板 API 服务层 (AC: #1, #14)
  - [x] 1.1 创建 `src/api/template.ts`：定义 TypeScript 接口（TemplateDTO, CreateTemplateRequest, UpdateTemplateRequest, TemplatePreviewRequest, TemplatePreviewResponse）
  - [x] 1.2 实现 API 函数：`getTemplatesApi`, `getTemplateApi`, `createTemplateApi`, `updateTemplateApi`, `deleteTemplateApi`, `restoreDefaultTemplateApi`, `previewTemplateApi`

- [x] Task 2: 创建路由配置 (AC: #12)
  - [x] 2.1 创建 `src/router/routes/modules/template.ts`：配置 Prompt 模板路由模块
  - [x] 2.2 配置菜单图标（`lucide:file-code`）和排序（order=400）

- [x] Task 3: 实现 Prompt 模板列表页面 (AC: #1, #2, #3, #11, #13)
  - [x] 3.1 创建 `src/views/template/index.vue`（列表页面入口）
  - [x] 3.2 实现顶部过滤工具栏（类别选择器、搜索框）
  - [x] 3.3 实现 ElTable 展示模板（名称、类别 Tag、版本号、状态切换、最后修改时间、操作）
  - [x] 3.4 实现操作按钮（编辑、删除、复制）
  - [x] 3.5 实现删除确认对话框
  - [x] 3.6 实现复制模板功能（复制后自动命名为 "原名称 - 副本"）

- [x] Task 4: 集成代码编辑器组件 (AC: #4, #5)
  - [x] 4.1 使用 CodeMirror 6 替代 @monaco-editor/react（React-specific，不适用于 Vue 3）
  - [x] 4.2 创建 `src/components/editor/CodeMirrorEditor.vue` 封装组件
  - [x] 4.3 配置 Mustache 变量高亮（StateField + Decoration，黄色 #e5c07b）
  - [x] 4.4 实现模板变量自动补全（autocompletion，{{ 后触发）
  - [x] 4.5 实现变量高亮（regex /\{\{[^}]+\}\}/g 扫描高亮）

- [x] Task 5: 实现模板编辑器页面 (AC: #4, #5, #6, #7, #8, #9, #10, #11, #13)
  - [x] 5.1 创建 `src/views/template/editor.vue`（编辑器页面入口）
  - [x] 5.2 实现分屏布局（左侧编辑器 + 右侧预览，ElRow/ElCol :span=12）
  - [x] 5.3 实现编辑器区域（CodeMirrorEditor 组件）
  - [x] 5.4 实现实时预览功能（500ms 防抖，客户端 renderMustacheTemplate）
  - [x] 5.5 实现保存功能（create/update 双模式，后端自动生成版本号）
  - [x] 5.6 实现「恢复到默认模板」按钮和确认对话框
  - [x] 5.7 实现模板变量文档面板（ElCollapse 可折叠，16个变量说明）
  - [x] 5.8 实现响应式布局（useMediaQuery 移动端 Tab 切换）

- [x] Task 6: 国际化 (AC: 全部)
  - [x] 6.1 在 `src/locales/langs/en-US/template.json` 添加完整翻译
  - [x] 6.2 在 `src/locales/langs/zh-CN/template.json` 添加完整翻译

- [x] Task 7: Mustache 模板解析和验证 (AC: #10)
  - [x] 7.1 安装 `mustache` + `@codemirror/*` 子包
  - [x] 7.2 创建 `src/utils/templateUtils.ts`：validateMustacheSyntax, renderMustacheTemplate, extractTemplateVariables
  - [x] 7.3 在保存前调用 validateMustacheSyntax 进行语法校验

## Dev Notes

### 📝 Mustache 模板语法

**基本语法：**
```mustache
{{variableName}}         <!-- 变量替换 -->
{{#section}}...{{/section}}  <!-- 条件渲染 -->
{{^section}}...{{/section}}  <!-- 反向条件 -->
{{! This is a comment }}  <!-- 注释 -->
```

**审查 Prompt 可用变量：**
```typescript
interface PromptContext {
  // 文件信息
  fileName: string;           // 文件名（如 UserService.java）
  filePath: string;           // 文件路径（如 src/main/java/com/example/UserService.java）
  language: string;           // 编程语言（如 java, typescript, python）

  // 代码内容
  codeSnippet: string;        // 代码片段
  fullCode: string;           // 完整文件代码
  lineStart: number;          // 起始行号
  lineEnd: number;            // 结束行号

  // 差异信息（PR/MR）
  changedLines: string;       // 变更的行
  addedLines: number;         // 新增行数
  deletedLines: number;       // 删除行数

  // 上下文信息
  projectName: string;        // 项目名称
  branch: string;             // 分支名
  author: string;             // 提交者
  commitMessage: string;      // 提交信息

  // 审查维度
  dimension: string;          // 当前审查维度（security, performance, etc.）
  previousIssues: string;     // 之前发现的问题（用于关联分析）
}
```

**示例 Prompt 模板：**
```mustache
请审查以下 {{language}} 代码的{{dimension}}问题：

文件：{{filePath}}
行号：{{lineStart}}-{{lineEnd}}

代码内容：
```{{language}}
{{codeSnippet}}
```

请检查：
{{#isSecurity}}
- SQL 注入、XSS、CSRF 等安全漏洞
- 敏感信息泄露
- 身份验证和授权问题
{{/isSecurity}}

{{#isPerformance}}
- 性能瓶颈（如 N+1 查询、不必要的循环）
- 资源泄露（如未关闭的连接、流）
- 缓存机会
{{/isPerformance}}

请用 JSON 格式返回问题列表。
```

### 后端 API 端点

| Method | Endpoint | 说明 | 请求体 | 响应 |
|--------|----------|------|--------|------|
| GET | `/api/v1/templates` | 获取模板列表 | `?category=security` | `ApiResponse<List<TemplateDTO>>` |
| GET | `/api/v1/templates/{id}` | 获取单个模板 | - | `ApiResponse<TemplateDTO>` |
| POST | `/api/v1/templates` | 创建模板 | `CreateTemplateRequest` | `ApiResponse<TemplateDTO>` (201) |
| PUT | `/api/v1/templates/{id}` | 更新模板 | `UpdateTemplateRequest` | `ApiResponse<TemplateDTO>` |
| DELETE | `/api/v1/templates/{id}` | 删除模板 | - | `ApiResponse<Void>` |
| POST | `/api/v1/templates/{id}/restore-default` | 恢复默认模板 | - | `ApiResponse<TemplateDTO>` |
| POST | `/api/v1/templates/preview` | 预览模板渲染 | `TemplatePreviewRequest` | `ApiResponse<TemplatePreviewResponse>` |

### TypeScript 接口定义

```typescript
// src/types/template.ts

/** Prompt 模板 DTO */
export interface TemplateDTO {
  id: number;
  name: string;                     // 模板名称（如 "Java 安全审查模板"）
  category: TemplateCategory;       // "security" | "performance" | "quality" | "style" | "bug" | "call-graph"
  content: string;                  // Mustache 模板内容
  version: string;                  // 版本号（如 "v1.2"）
  isDefault: boolean;               // 是否为系统默认模板
  enabled: boolean;                 // 是否启用
  createdAt: string;                // ISO 8601
  updatedAt: string;
}

export type TemplateCategory = "security" | "performance" | "quality" | "style" | "bug" | "call-graph";

/** 创建 Prompt 模板请求 */
export interface CreateTemplateRequest {
  name: string;                     // @NotBlank, max 255
  category: TemplateCategory;       // @NotNull
  content: string;                  // @NotBlank, Mustache 模板内容
  enabled?: boolean;                // 默认 true
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
  templateContent: string;          // Mustache 模板内容
  sampleData: PromptContext;        // 示例数据
}

/** 模板预览响应 */
export interface TemplatePreviewResponse {
  renderedContent: string;          // 渲染后的 Prompt 内容
  variables: string[];              // 模板中使用的变量列表
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
```

### Monaco Editor 集成

**安装依赖：**
```bash
pnpm add @monaco-editor/react monaco-editor
```

**Monaco Editor 封装组件：**
```vue
<!-- src/components/editor/MonacoEditor.vue -->
<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import Editor from '@monaco-editor/react';
import type { editor } from 'monaco-editor';

interface Props {
  modelValue: string;
  language?: string;
  theme?: 'vs-dark' | 'vs-light';
  height?: string;
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  language: 'mustache',
  theme: 'vs-dark',
  height: '600px',
  readonly: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'change': [value: string];
}>();

const editorRef = ref<editor.IStandaloneCodeEditor | null>(null);

function handleEditorMount(editor: editor.IStandaloneCodeEditor) {
  editorRef.value = editor;

  // 注册自定义 Mustache 语言
  const monaco = (window as any).monaco;
  if (monaco && !monaco.languages.getLanguages().find((lang: any) => lang.id === 'mustache')) {
    monaco.languages.register({ id: 'mustache' });
    monaco.languages.setMonarchTokensProvider('mustache', {
      tokenizer: {
        root: [
          [/\{\{[!#^/]?/, 'tag'],
          [/\}\}/, 'tag'],
          [/[a-zA-Z_]\w*/, 'variable'],
        ]
      }
    });
  }

  // 配置自动补全
  monaco.languages.registerCompletionItemProvider('mustache', {
    provideCompletionItems: (model: any, position: any) => {
      const suggestions = [
        {
          label: 'fileName',
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: 'fileName',
          documentation: '文件名（如 UserService.java）'
        },
        {
          label: 'filePath',
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: 'filePath',
          documentation: '文件路径'
        },
        {
          label: 'language',
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: 'language',
          documentation: '编程语言（如 java, typescript）'
        },
        {
          label: 'codeSnippet',
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: 'codeSnippet',
          documentation: '代码片段'
        },
        // ... 其他变量
      ];

      return { suggestions };
    }
  });
}

function handleEditorChange(value: string | undefined) {
  if (value !== undefined) {
    emit('update:modelValue', value);
    emit('change', value);
  }
}
</script>

<template>
  <div class="monaco-editor-wrapper">
    <Editor
      :value="modelValue"
      :language="language"
      :theme="theme"
      :height="height"
      :options="{
        readOnly: readonly,
        minimap: { enabled: false },
        fontSize: 14,
        lineNumbers: 'on',
        wordWrap: 'on',
        automaticLayout: true,
      }"
      @mount="handleEditorMount"
      @change="handleEditorChange"
    />
  </div>
</template>

<style scoped lang="scss">
.monaco-editor-wrapper {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}
</style>
```

### 模板编辑器页面实现

```vue
<!-- src/views/template/editor.vue -->
<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import MonacoEditor from '#/components/editor/MonacoEditor.vue';
import {
  getTemplateApi,
  updateTemplateApi,
  restoreDefaultTemplateApi,
  previewTemplateApi
} from '#/api/template';
import type { TemplateDTO, PromptContext } from '#/types/template';
import { useBreakpoints } from '@vben/hooks';

const route = useRoute();
const router = useRouter();
const { isMobile } = useBreakpoints();

const templateId = Number(route.params.id);
const template = ref<TemplateDTO | null>(null);
const content = ref('');
const preview = ref('');
const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const activeTab = ref<'editor' | 'preview'>('editor'); // 移动端 Tab

// 示例数据（用于预览）
const sampleData = ref<PromptContext>({
  fileName: 'UserService.java',
  filePath: 'src/main/java/com/example/service/UserService.java',
  language: 'java',
  codeSnippet: 'public User findById(String id) {\n  return userRepository.findById(id);\n}',
  fullCode: '/* ... 完整代码 ... */',
  lineStart: 42,
  lineEnd: 44,
  changedLines: '+  return userRepository.findById(id);',
  addedLines: 1,
  deletedLines: 0,
  projectName: 'example-project',
  branch: 'feature/user-service',
  author: 'John Doe',
  commitMessage: 'Add findById method',
  dimension: 'security',
  previousIssues: ''
});

// 模板变量文档
const templateVariables = [
  { name: 'fileName', description: '文件名（如 UserService.java）' },
  { name: 'filePath', description: '文件路径' },
  { name: 'language', description: '编程语言（如 java, typescript）' },
  { name: 'codeSnippet', description: '代码片段' },
  { name: 'lineStart', description: '起始行号' },
  { name: 'lineEnd', description: '结束行号' },
  { name: 'projectName', description: '项目名称' },
  { name: 'branch', description: '分支名' },
  { name: 'author', description: '提交者' },
  { name: 'dimension', description: '审查维度（security, performance 等）' },
];

async function loadTemplate() {
  loading.value = true;
  try {
    template.value = await getTemplateApi(templateId);
    content.value = template.value.content;
    await updatePreview();
  } catch (error: any) {
    ElMessage.error(`加载模板失败: ${error.message}`);
  } finally {
    loading.value = false;
  }
}

// 实时预览（防抖）
let previewTimer: NodeJS.Timeout;
watch(content, () => {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(() => {
    updatePreview();
  }, 500); // 500ms 防抖
});

async function updatePreview() {
  if (!content.value) {
    preview.value = '';
    return;
  }

  previewing.value = true;
  try {
    const response = await previewTemplateApi({
      templateContent: content.value,
      sampleData: sampleData.value
    });
    preview.value = response.renderedContent;
  } catch (error: any) {
    preview.value = `[预览失败]: ${error.message}`;
  } finally {
    previewing.value = false;
  }
}

async function handleSave() {
  if (!template.value) return;

  saving.value = true;
  try {
    await updateTemplateApi(templateId, {
      content: content.value
    });
    ElMessage.success('模板保存成功');
    await loadTemplate(); // 重新加载（获取新版本号）
  } catch (error: any) {
    ElMessage.error(`保存失败: ${error.message}`);
  } finally {
    saving.value = false;
  }
}

async function handleRestoreDefault() {
  try {
    await ElMessageBox.confirm(
      '确定要恢复到系统默认模板吗？当前的修改将会丢失。',
      '确认恢复',
      { type: 'warning' }
    );

    loading.value = true;
    await restoreDefaultTemplateApi(templateId);
    ElMessage.success('已恢复到默认模板');
    await loadTemplate();
  } catch {
    // 用户取消
  } finally {
    loading.value = false;
  }
}

function handleBack() {
  router.push({ name: 'TemplateList' });
}

onMounted(() => {
  loadTemplate();
});
</script>

<template>
  <Page :title="template?.name || '模板编辑器'" v-loading="loading">
    <template #extra>
      <ElSpace>
        <ElButton @click="handleBack">
          {{ $t('common.back') }}
        </ElButton>
        <ElButton type="warning" @click="handleRestoreDefault">
          恢复默认模板
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSave">
          {{ $t('common.save') }}
        </ElButton>
      </ElSpace>
    </template>

    <!-- 桌面端：分屏布局 -->
    <ElRow v-if="!isMobile" :gutter="16" class="editor-layout">
      <ElCol :span="12">
        <ElCard>
          <template #header>
            <div class="card-header">
              <span>模板编辑器</span>
              <span class="version-badge">{{ template?.version }}</span>
            </div>
          </template>
          <MonacoEditor
            v-model="content"
            language="mustache"
            theme="vs-dark"
            height="600px"
          />

          <!-- 变量文档 -->
          <ElCollapse class="mt-4">
            <ElCollapseItem title="可用变量" name="variables">
              <ElTable :data="templateVariables" size="small">
                <ElTableColumn prop="name" label="变量名" width="180">
                  <template #default="{ row }">
                    <code>{{ '{{' }}{{ row.name }}{{ '}}' }}</code>
                  </template>
                </ElTableColumn>
                <ElTableColumn prop="description" label="说明" />
              </ElTable>
            </ElCollapseItem>
          </ElCollapse>
        </ElCard>
      </ElCol>

      <ElCol :span="12">
        <ElCard v-loading="previewing">
          <template #header>
            <span>实时预览</span>
          </template>
          <pre class="preview-content">{{ preview }}</pre>
        </ElCard>
      </ElCol>
    </ElRow>

    <!-- 移动端：Tab 切换 -->
    <ElCard v-else>
      <ElTabs v-model="activeTab">
        <ElTabPane label="编辑" name="editor">
          <MonacoEditor
            v-model="content"
            language="mustache"
            theme="vs-dark"
            height="400px"
          />
        </ElTabPane>
        <ElTabPane label="预览" name="preview">
          <div v-loading="previewing">
            <pre class="preview-content">{{ preview }}</pre>
          </div>
        </ElTabPane>
      </ElTabs>
    </ElCard>
  </Page>
</template>

<style scoped lang="scss">
.editor-layout {
  min-height: 600px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .version-badge {
    font-size: 12px;
    color: #909399;
    padding: 2px 8px;
    background: #f4f4f5;
    border-radius: 4px;
  }
}

.preview-content {
  padding: 16px;
  background: #282c34;
  color: #abb2bf;
  border-radius: 4px;
  max-height: 600px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
```

### Mustache 语法验证

```typescript
// src/utils/template.ts
import Mustache from 'mustache';

/**
 * 验证 Mustache 模板语法
 */
export function validateMustacheSyntax(template: string): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  try {
    // Mustache.parse 会抛出异常如果语法无效
    Mustache.parse(template);
  } catch (error: any) {
    errors.push(error.message);
    return { valid: false, errors };
  }

  // 检查未闭合的标签
  const openTags = (template.match(/\{\{[^}]/g) || []).length;
  const closeTags = (template.match(/[^{]\}\}/g) || []).length;

  if (openTags !== closeTags) {
    errors.push(`未闭合的标签：打开标签 ${openTags} 个，关闭标签 ${closeTags} 个`);
  }

  return {
    valid: errors.length === 0,
    errors
  };
}

/**
 * 渲染 Mustache 模板
 */
export function renderMustacheTemplate(
  template: string,
  context: Record<string, any>
): string {
  try {
    return Mustache.render(template, context);
  } catch (error: any) {
    throw new Error(`模板渲染失败: ${error.message}`);
  }
}

/**
 * 提取模板中使用的变量
 */
export function extractTemplateVariables(template: string): string[] {
  const variables = new Set<string>();
  const tokens = Mustache.parse(template);

  tokens.forEach((token: any) => {
    if (token[0] === 'name' || token[0] === '#' || token[0] === '^') {
      variables.add(token[1]);
    }
  });

  return Array.from(variables);
}
```

### 错误处理映射

| HTTP 状态 | 错误码 | 用户提示 |
|-----------|--------|---------|
| 404 | `ERR_404` | "模板不存在" |
| 409 | `ERR_409` | "模板名称已存在，请使用其他名称" |
| 422 | `ERR_422` | 显示 Mustache 语法错误详情 |
| 500 | `ERR_500` | "服务器错误，请稍后重试" |

### Story 8-1/8-2/8-3 实施经验（关键教训）

✅ **成功模式（必须遵循）**：
1. **API 响应适配**：已在 Story 8-1 中配置完成
2. **ElMessage 成功提示**：保存/删除/恢复成功后必须显示
3. **错误处理**：所有 async 函数使用 try/catch
4. **响应式布局**：桌面分屏 + 移动端 Tab 切换
5. **国际化 key 完整性**：确保所有 locale key 都已定义

⚠️ **特别注意**：
- **实时预览防抖**：使用 500ms 防抖避免频繁 API 调用
- **Monaco Editor 异步加载**：首次加载可能较慢，显示 loading 状态
- **Mustache 语法验证**：在保存前验证，提供友好错误提示
- **默认模板保护**：系统默认模板（`isDefault: true`）不允许删除，只能恢复

### 命名约定

| 类型 | 规则 | 示例 |
|------|------|------|
| Vue 组件文件 | PascalCase | `MonacoEditor.vue` |
| API 模块文件 | camelCase | `template.ts` |
| 函数名 | camelCase | `getTemplatesApi`, `validateMustacheSyntax` |
| 接口名 | PascalCase | `TemplateDTO`, `PromptContext` |

### Project Structure Notes

- 所有新文件在 `frontend/apps/web-ele/src/` 下创建
- Monaco Editor 封装为可复用组件放在 `components/editor/`
- Mustache 工具函数放在 `utils/template.ts`

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-8.md#Story 8.4]
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Vue Structure]
- [Source: _bmad-output/implementation-artifacts/8-1-project-management-interface.md]
- [Source: backend/ai-code-review-api - PromptTemplateController.java]
- [Source: backend/ai-code-review-common - PromptTemplateDTO.java]
- [Monaco Editor 文档: https://microsoft.github.io/monaco-editor/]
- [Mustache.js 文档: https://github.com/janl/mustache.js]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

- Build fix: Template `{{ '{{' }}` HTML entity pattern caused Vue compiler error → replaced with `fmtVar(name)` helper function
- Dependency fix: `@codemirror/*` sub-packages required explicit listing in package.json for pnpm strict mode

### Completion Notes List

1. Used CodeMirror 6 instead of `@monaco-editor/react` — the React package is incompatible with Vue 3; CodeMirror 6 uses vanilla DOM API (`new EditorView()`) that works in `onMounted`
2. Client-side preview using `renderMustacheTemplate(content, sampleData)` with 500ms debounce — avoids unnecessary API calls while still showing real-time preview
3. Editor supports both create (`id='new'`) and edit (numeric id) modes
4. Name and category fields included in editor page (not just content), with form validation before save
5. `showRestoreDefault` computed — only shows "Restore Default" button for existing default templates (`isDefault: true`)
6. All 7 pnpm packages added: `@codemirror/autocomplete`, `@codemirror/commands`, `@codemirror/language`, `@codemirror/state`, `@codemirror/view`, `codemirror`, `mustache`

### Code Review Record

**Review Date:** 2026-02-17
**Issues Fixed (4):** H1, M1, M2, M2+L3 side-fix (previewPlaceholder i18n)

| ID | Severity | Description | Fix Applied |
|----|----------|-------------|-------------|
| H1 | HIGH | 两个 locale 文件中 `template.editor` 键重复定义（字符串被对象覆盖），`$t('template.editor')` 返回 `[object Object]` | 删除顶层字符串 key；router 和 editor.vue 改用 `$t('template.editor.title')` |
| H2 | HIGH | AC7 "支持查看历史版本" 未实现；后端无 `/templates/{id}/versions` 端点 | 添加 backlog follow-up task；前端已展示版本 badge，历史功能等后端 API 支持 |
| M1 | MEDIUM | `previewTemplateApi` 是死代码；Task 5.4 明确要求"调用预览 API" | `updatePreview()` 改为调用 `previewTemplateApi`，加客户端降级；增加 `previewing` loading 状态 |
| M2 | MEDIUM | `index.vue:102` 硬编码 `' - 副本'`，英文界面也显示中文 | 添加 `template.messages.copySuffix` i18n key（"- Copy" / "- 副本"），index.vue 改用 `$t` |

**Follow-up Tasks (not yet backended):**
- [ ] [AI-Review][HIGH] AC7 版本历史查看需要后端 `/api/v1/templates/{id}/versions` 端点支持后才可实现 [editor.vue:296]

### File List

- `frontend/apps/web-ele/src/types/template.ts` (created)
- `frontend/apps/web-ele/src/api/template.ts` (created)
- `frontend/apps/web-ele/src/router/routes/modules/template.ts` (created)
- `frontend/apps/web-ele/src/utils/templateUtils.ts` (created)
- `frontend/apps/web-ele/src/components/editor/CodeMirrorEditor.vue` (created)
- `frontend/apps/web-ele/src/views/template/index.vue` (created)
- `frontend/apps/web-ele/src/views/template/editor.vue` (created)
- `frontend/apps/web-ele/src/locales/langs/zh-CN/template.json` (created)
- `frontend/apps/web-ele/src/locales/langs/en-US/template.json` (created)
- `frontend/apps/web-ele/package.json` (modified — added @codemirror/* and mustache deps)
