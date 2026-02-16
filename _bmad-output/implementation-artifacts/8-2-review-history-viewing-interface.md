# Story 8.2: 实现审查历史查看界面

Status: ready-for-dev

## Story

As a 开发者,
I want 查看项目的审查历史,
so that 了解代码质量趋势并快速定位关键问题。

## Acceptance Criteria

1. **审查历史列表页面（ReviewHistory）**：表格展示审查任务，包含列：时间、分支、提交哈希、状态、问题数（按严重性）、操作按钮
2. **过滤功能**：按项目、状态（PENDING/RUNNING/COMPLETED/FAILED）、日期范围过滤；搜索框支持按提交哈希、作者、分支名搜索
3. **排序功能**：支持按时间、问题数排序（默认按时间降序）
4. **分页**：每页 20 条，支持页码跳转和页大小选择
5. **状态徽章**：使用 ElTag 组件显示状态（不同颜色：PENDING=info, RUNNING=primary, COMPLETED=success, FAILED=danger）
6. **审查详情（ReviewDetail）**：展示完整审查结果，包含审查摘要卡片、问题列表、调用链路图（可选）
7. **审查摘要卡片**：显示总问题数、严重性分布（饼图或进度条）、审查耗时、阈值验证结果（✅ 通过/❌ 失败）
8. **问题列表**：按严重性排序（Critical → High → Medium → Low → Info），支持按严重性和类别筛选，使用虚拟滚动优化（性能目标：1000 个问题渲染 < 3 秒）
9. **问题卡片布局**：显示严重性徽章、类别、文件路径、行号、问题标题、展开按钮；点击展开显示详细说明、代码片段、修复建议
10. **代码片段展示**：语法高亮（使用 Prism.js 或 Highlight.js）、显示问题行 ± 5 行上下文、问题行高亮标记（红色背景）、行号显示、复制代码按钮
11. **路由配置**：`/reviews` → ReviewHistory.vue，`/reviews/:id` → ReviewDetail.vue
12. **响应式布局**：桌面端三栏布局（侧边栏 + 问题列表 + 详情），平板端两栏，移动端单栏（点击跳转）
13. **加载状态**：骨架屏加载（Skeleton Loading）和部分加载策略（优先加载摘要卡片，然后问题列表，最后调用链路图）
14. **API 响应处理**：正确处理后端 `ApiResponse<PaginatedResponse<ReviewTask>>` 格式，支持 404（审查不存在）和 500 错误的友好提示

## Tasks / Subtasks

- [ ] Task 1: 创建审查历史 API 服务层 (AC: #1, #14)
  - [ ] 1.1 创建 `src/api/review.ts`：定义 TypeScript 接口（ReviewTask, ReviewResult, ReviewIssue, PaginatedResponse）
  - [ ] 1.2 实现 API 函数：`getReviewTasksApi`（列表+分页）、`getReviewResultApi`（详情）、`getReviewIssuesApi`（问题列表）

- [ ] Task 2: 创建 Pinia 状态管理 (AC: #2, #3, #8)
  - [ ] 2.1 创建 `src/stores/review.ts`：定义状态（reviews, currentReview, filters, loading, error）
  - [ ] 2.2 实现 actions：`fetchReviews`, `fetchReviewDetail`, `setFilter`, `resetFilters`
  - [ ] 2.3 实现 getters：`filteredReviews`, `errorCount`, `warningCount`

- [ ] Task 3: 创建路由配置 (AC: #11)
  - [ ] 3.1 创建 `src/router/routes/modules/review.ts`：配置审查历史路由模块
  - [ ] 3.2 配置菜单图标（`lucide:clipboard-list`）和排序（order=200）

- [ ] Task 4: 实现审查历史列表页面 (AC: #1, #2, #3, #4, #5, #12, #13)
  - [ ] 4.1 创建 `src/views/review/ReviewHistory.vue`（列表页面入口）
  - [ ] 4.2 实现顶部过滤工具栏（项目选择器、状态选择器、搜索框、重置按钮）
  - [ ] 4.3 实现 ElTable 展示审查任务（时间、分支、提交、状态徽章、问题数徽章、操作）
  - [ ] 4.4 实现分页组件（ElPagination）
  - [ ] 4.5 实现骨架屏加载状态（v-loading + Skeleton）
  - [ ] 4.6 实现响应式布局（ElRow + ElCol 响应式网格）

- [ ] Task 5: 实现审查详情页面 (AC: #6, #7, #9, #10, #11, #12, #13)
  - [ ] 5.1 创建 `src/views/review/ReviewDetail.vue`（详情页面入口）
  - [ ] 5.2 实现审查摘要卡片（ElCard + ElDescriptions + ElProgress）
  - [ ] 5.3 实现阈值验证结果显示（✅/❌ 图标 + 颜色状态）
  - [ ] 5.4 实现问题列表（虚拟滚动 + 按严重性分组）
  - [ ] 5.5 实现问题卡片组件（`src/components/review/IssueCard.vue`）
  - [ ] 5.6 实现代码片段组件（`src/components/review/CodeSnippet.vue` + Prism.js 语法高亮）
  - [ ] 5.7 实现过滤和搜索功能（按严重性、类别、关键词实时过滤）
  - [ ] 5.8 实现响应式布局（三栏 → 两栏 → 单栏）

- [ ] Task 6: 实现可视化组件（可选，Story 8.5 范围）(AC: #6)
  - [ ] 6.1 创建 `src/components/chart/SeverityDistribution.vue`（饼图，使用 ECharts）
  - [ ] 6.2 创建 `src/components/chart/CallGraphViewer.vue`（Mermaid 图表渲染，按需加载）

- [ ] Task 7: 国际化 (AC: 全部)
  - [ ] 7.1 在 `src/locales/langs/en-US/review.json` 添加审查历史相关翻译
  - [ ] 7.2 在 `src/locales/langs/zh-CN/review.json` 添加审查历史相关翻译

- [ ] Task 8: 性能优化 (AC: #8, #13)
  - [ ] 8.1 实现虚拟滚动（使用 `vue-virtual-scroller` 或 ElVirtualList）
  - [ ] 8.2 实现代码片段懒加载（默认不加载，点击时才请求）
  - [ ] 8.3 实现 API 响应缓存（5 分钟 TTL，使用 Pinia 持久化）

## Dev Notes

### 🎯 核心 UX 目标：3 秒定位关键问题

**UX 设计原则（来自 ux-design-specification.md）：**
1. **3 秒规则**：首屏立即显示 Critical 问题数量
2. **10 秒规则**：点击问题后能看到详细说明和代码上下文
3. **30 秒规则**：看到修复建议和示例代码

**信息层次设计**：
- **第 1 层（Summary）**：总分数 + 问题数（按严重性）
- **第 2 层（Issue List）**：严重性排序的问题列表（仅标题 + 行号）
- **第 3 层（Issue Detail）**：点击展开显示详细说明、代码片段、修复建议

### 后端 API 端点

| Method | Endpoint | 说明 | 请求参数 | 响应 |
|--------|----------|------|---------|------|
| GET | `/api/v1/tasks` | 获取审查任务列表 | `?projectId=1&status=COMPLETED&page=1&pageSize=20` | `ApiResponse<PaginatedResponse<ReviewTask>>` |
| GET | `/api/v1/results/{resultId}` | 获取审查结果详情 | - | `ApiResponse<ReviewResult>` |
| GET | `/api/v1/results/{resultId}/issues` | 获取审查问题列表 | `?severity=Error&category=security` | `ApiResponse<List<ReviewIssue>>` |

**注意**：后端已有分页 API，前端需正确处理 `PaginatedResponse` 格式。

### TypeScript 接口定义

```typescript
// src/types/review.ts

/** 审查任务（来自 review_task 表） */
export interface ReviewTask {
  id: number;                  // taskId
  type: string;                // "PR" | "MR" | "PUSH"
  priority: number;            // 0-100
  status: TaskStatus;          // "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"
  projectId: number;
  repoUrl: string;
  branch: string;
  commitHash: string;
  author: string;
  prNumber?: number;
  prTitle?: string;
  prDescription?: string;
  createdAt: string;           // ISO 8601
  completedAt?: string;
  resultId?: number;           // 关联 review_result 表
}

export type TaskStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

/** 审查结果（来自 review_result 表） */
export interface ReviewResult {
  id: number;
  taskId: number;
  projectId: number;
  summary: ReviewSummary;      // JSONB 字段
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
  score: number;               // 0-100
  thresholdStatus: "pass" | "blocked" | "warning";
  dimensions: DimensionResult[];
  callGraph?: CallGraphData;
  processingTimeMs: number;
}

/** 六维度审查结果 */
export interface DimensionResult {
  dimension: string;           // "security" | "performance" | "quality" | "style" | "bug" | "call-graph"
  score: number;
  issues: ReviewIssue[];
}

/** 审查问题 */
export interface ReviewIssue {
  severity: IssueSeverity;     // "Critical" | "High" | "Medium" | "Low" | "Info"
  category: IssueCategory;
  title: string;
  description: string;
  filePath: string;
  lineNumber: number;
  codeSnippet: string;
  fixSuggestion: string;
  referenceLinks?: string[];
}

export type IssueSeverity = "Critical" | "High" | "Medium" | "Low" | "Info";
export type IssueCategory = "security" | "performance" | "quality" | "style" | "bug" | "call-graph";

/** 调用链路图数据 */
export interface CallGraphData {
  nodes: CallGraphNode[];
  edges: CallGraphEdge[];
}

export interface CallGraphNode {
  id: string;
  label: string;
  type: "class" | "method";
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
```

### API 服务层实现模式

```typescript
// src/api/review.ts
import { requestClient } from '#/api/request';
import type {
  ReviewTask,
  ReviewResult,
  ReviewIssue,
  PaginatedResponse
} from '#/types/review';

export namespace ReviewApi {
  export interface TaskQueryParams {
    projectId?: number;
    status?: TaskStatus;
    page?: number;
    pageSize?: number;
  }

  export interface IssueQueryParams {
    severity?: IssueSeverity;
    category?: IssueCategory;
  }
}

/** 获取审查任务列表（带分页） */
export async function getReviewTasksApi(params?: ReviewApi.TaskQueryParams) {
  return requestClient.get<PaginatedResponse<ReviewTask>>('/api/v1/tasks', { params });
}

/** 获取审查结果详情 */
export async function getReviewResultApi(resultId: number) {
  return requestClient.get<ReviewResult>(`/api/v1/results/${resultId}`);
}

/** 获取审查问题列表 */
export async function getReviewIssuesApi(resultId: number, params?: ReviewApi.IssueQueryParams) {
  return requestClient.get<ReviewIssue[]>(`/api/v1/results/${resultId}/issues`, { params });
}
```

### Pinia Store 实现模式

```typescript
// src/stores/review.ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { getReviewTasksApi, getReviewResultApi } from '#/api/review';
import type { ReviewTask, ReviewResult, TaskStatus } from '#/types/review';

export const useReviewStore = defineStore('review', () => {
  // State
  const reviews = ref<ReviewTask[]>([]);
  const currentReview = ref<ReviewResult | null>(null);
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
    let result = reviews.value;

    if (filters.value.status !== 'all') {
      result = result.filter(r => r.status === filters.value.status);
    }

    if (filters.value.searchText) {
      const search = filters.value.searchText.toLowerCase();
      result = result.filter(r =>
        r.commitHash.toLowerCase().includes(search) ||
        r.author.toLowerCase().includes(search) ||
        r.branch.toLowerCase().includes(search)
      );
    }

    return result;
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
    setFilter,
    resetFilters,
    setPage,
    setPageSize,
  };
});
```

### Vben Admin 框架模式 — 必须遵循

**项目结构**（Monorepo，主应用 `apps/web-ele`）：
```
frontend/apps/web-ele/src/
├── api/review.ts                    ← 新建：审查 API 服务
├── views/review/
│   ├── ReviewHistory.vue            ← 新建：审查历史列表
│   └── ReviewDetail.vue             ← 新建：审查详情
├── components/review/
│   ├── IssueCard.vue                ← 新建：问题卡片
│   ├── CodeSnippet.vue              ← 新建：代码片段展示
│   └── ThresholdIndicator.vue       ← 新建：阈值状态指示器
├── components/chart/
│   ├── SeverityDistribution.vue     ← 新建：严重性分布饼图（可选）
│   └── CallGraphViewer.vue          ← 新建：调用链路图（可选）
├── stores/review.ts                 ← 新建：Pinia store
├── types/review.ts                  ← 新建：TypeScript 类型定义
├── router/routes/modules/
│   └── review.ts                    ← 新建：审查历史路由模块
└── locales/langs/
    ├── en-US/review.json            ← 新建：英文翻译
    └── zh-CN/review.json            ← 新建：中文翻译
```

**路由模块模式**（参考 Story 8-1 的 `project.ts`）：
```typescript
// src/router/routes/modules/review.ts
import type { RouteRecordRaw } from 'vue-router';
import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:clipboard-list',
      order: 200,
      title: $t('review.title'),
    },
    name: 'ReviewManagement',
    path: '/reviews',
    children: [
      {
        meta: {
          title: $t('review.history'),
        },
        name: 'ReviewHistory',
        path: '/reviews',
        component: () => import('#/views/review/ReviewHistory.vue'),
      },
      {
        meta: {
          title: $t('review.detail'),
          hideInMenu: true,
        },
        name: 'ReviewDetail',
        path: '/reviews/:id',
        component: () => import('#/views/review/ReviewDetail.vue'),
      },
    ],
  },
];

export default routes;
```

### 问题列表虚拟滚动实现

**性能要求**：1000 个问题渲染时间 < 3 秒

**方案 1：使用 Element Plus ElVirtualList（推荐）**
```vue
<script setup lang="ts">
import { ElVirtualList } from 'element-plus';
import IssueCard from '#/components/review/IssueCard.vue';

const issues = ref<ReviewIssue[]>([]);
const itemHeight = 120; // 每个问题卡片的高度（px）
</script>

<template>
  <ElVirtualList
    :data="issues"
    :item-size="itemHeight"
    height="600px"
    class="issue-list"
  >
    <template #default="{ item, index }">
      <IssueCard :issue="item" :index="index" />
    </template>
  </ElVirtualList>
</template>
```

**方案 2：使用 vue-virtual-scroller（更灵活）**
```bash
pnpm add vue-virtual-scroller
```

```vue
<script setup lang="ts">
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller';
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css';
import IssueCard from '#/components/review/IssueCard.vue';

const issues = ref<ReviewIssue[]>([]);
</script>

<template>
  <DynamicScroller
    :items="issues"
    :min-item-size="100"
    class="issue-scroller"
  >
    <template #default="{ item, index, active }">
      <DynamicScrollerItem
        :item="item"
        :active="active"
        :size-dependencies="[item.description]"
        :data-index="index"
      >
        <IssueCard :issue="item" />
      </DynamicScrollerItem>
    </template>
  </DynamicScroller>
</template>

<style scoped>
.issue-scroller {
  height: 600px;
  overflow-y: auto;
}
</style>
```

### 代码片段语法高亮实现

**使用 Prism.js（推荐，轻量级）**

```bash
pnpm add prismjs @types/prismjs
```

```vue
<!-- src/components/review/CodeSnippet.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import Prism from 'prismjs';
import 'prismjs/themes/prism-tomorrow.css'; // 暗色主题
import 'prismjs/components/prism-java';     // 支持 Java 语法
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-python';
// ... 其他语言按需加载

interface Props {
  code: string;
  language: string;  // 'java', 'typescript', 'python', etc.
  highlightLines?: number[];  // 需要高亮的行号
}

const props = defineProps<Props>();
const codeRef = ref<HTMLElement | null>(null);

onMounted(() => {
  if (codeRef.value) {
    Prism.highlightElement(codeRef.value);
  }
});

function copyCode() {
  navigator.clipboard.writeText(props.code);
  ElMessage.success('代码已复制');
}
</script>

<template>
  <div class="code-snippet">
    <div class="code-header">
      <span class="language-tag">{{ language }}</span>
      <el-button link type="primary" @click="copyCode">
        <i class="lucide:copy" /> 复制代码
      </el-button>
    </div>
    <pre class="line-numbers"><code ref="codeRef" :class="`language-${language}`">{{ code }}</code></pre>
  </div>
</template>

<style scoped lang="scss">
.code-snippet {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  background: #282c34;

  .code-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 16px;
    background: #21252b;
    border-bottom: 1px solid #181a1f;

    .language-tag {
      color: #abb2bf;
      font-size: 12px;
      text-transform: uppercase;
    }
  }

  pre {
    margin: 0;
    padding: 16px;
    overflow-x: auto;

    code {
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 13px;
      line-height: 1.6;
    }
  }
}
</style>
```

### 严重性徽章颜色映射

```typescript
// src/utils/severity.ts
export function getSeverityColor(severity: IssueSeverity): string {
  const colorMap: Record<IssueSeverity, string> = {
    'Critical': '#F56C6C',  // 红色（error）
    'High': '#E6A23C',      // 橙色（warning）
    'Medium': '#409EFF',    // 蓝色（primary）
    'Low': '#909399',       // 灰色（info）
    'Info': '#67C23A',      // 绿色（success）
  };
  return colorMap[severity] || '#909399';
}

export function getSeverityType(severity: IssueSeverity): 'danger' | 'warning' | 'primary' | 'info' | 'success' {
  const typeMap: Record<IssueSeverity, any> = {
    'Critical': 'danger',
    'High': 'warning',
    'Medium': 'primary',
    'Low': 'info',
    'Info': 'success',
  };
  return typeMap[severity] || 'info';
}

export function getSeverityIcon(severity: IssueSeverity): string {
  const iconMap: Record<IssueSeverity, string> = {
    'Critical': 'lucide:shield-alert',
    'High': 'lucide:alert-triangle',
    'Medium': 'lucide:info',
    'Low': 'lucide:message-circle',
    'Info': 'lucide:lightbulb',
  };
  return iconMap[severity] || 'lucide:circle';
}
```

### 响应式布局断点

```typescript
// 使用 Tailwind CSS / Element Plus 响应式类
// 或使用 useBreakpoints composable
import { useBreakpoints } from '@vben/hooks';

const { isMobile, isTablet, isDesktop } = useBreakpoints();

// 桌面端：三栏布局（> 1024px）
// 平板端：两栏布局（768-1024px）
// 移动端：单栏布局（< 768px）
```

```vue
<template>
  <el-row :gutter="16" class="review-layout">
    <!-- 左侧：审查列表（桌面/平板显示） -->
    <el-col
      :xs="24"       <!-- 移动端全宽 -->
      :sm="24"       <!-- 小屏全宽 -->
      :md="14"       <!-- 平板端 14/24 -->
      :lg="10"       <!-- 桌面端 10/24 -->
      :xl="8"        <!-- 超大屏 8/24 -->
    >
      <ReviewTaskList />
    </el-col>

    <!-- 右侧：审查详情（桌面/平板显示） -->
    <el-col
      v-if="!isMobile"
      :md="10"
      :lg="14"
      :xl="16"
    >
      <ReviewDetailPanel />
    </el-col>
  </el-row>
</template>
```

### 骨架屏加载实现

```vue
<template>
  <div v-if="loading" class="skeleton-container">
    <el-skeleton :rows="5" animated />
    <el-skeleton :rows="3" animated style="margin-top: 16px" />
  </div>
  <div v-else>
    <!-- 实际内容 -->
  </div>
</template>
```

### 错误处理映射

| HTTP 状态 | 错误码 | 用户提示 |
|-----------|--------|---------|
| 404 | `ERR_404` | "审查结果不存在或已被删除" |
| 500 | `ERR_500` | "服务器错误，请稍后重试" |
| 503 | `ERR_503` | "审查服务暂时不可用，请稍后重试" |

### Story 8-1 实施经验（关键教训）

✅ **成功模式**：
1. **API 响应适配**：使用 `codeField: 'success'` + `successCode: (code) => code === true` 适配后端 `ApiResponse<T>` 格式
2. **Drawer/Form 模式**：`useVbenDrawer` + `useVbenForm` + `connectedComponent` 模式非常高效
3. **路由自动加载**：路由模块放在 `router/routes/modules/` 目录下会自动加载，无需手动注册
4. **国际化**：locale JSON 文件放在 `locales/langs/{locale}/` 目录下自动生效

⚠️ **需要注意的问题**（从 Story 8-1 代码审查中学到）：
1. **ElMessage 成功提示**：创建/编辑成功后必须显示 `ElMessage.success()`（AC #7 要求）
2. **错误处理**：所有 async 函数都需要 try/catch，避免未捕获的 promise rejection
3. **help 文本响应式**：不能使用 `computed(...).value` 立即求值，应使用 `dependencies.help` 函数
4. **国际化 key**：确保所有 locale key 都已定义，避免显示为原始 key 字符串
5. **Webhook URL**：使用后端 `apiURL` 而不是前端 `window.location.origin`

### 命名约定

| 类型 | 规则 | 示例 |
|------|------|------|
| Vue 组件文件 | PascalCase | `ReviewHistory.vue`, `IssueCard.vue` |
| API 模块文件 | camelCase | `review.ts` |
| 路由模块文件 | camelCase | `review.ts` |
| 函数名 | camelCase | `getReviewTasksApi`, `handleRowClick` |
| 接口名 | PascalCase | `ReviewTask`, `ReviewResult`, `ReviewIssue` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE`, `MAX_ISSUES_PER_PAGE` |

### 安全注意事项

- 用户权限检查：审查详情可能包含敏感代码，需在后端验证用户权限
- 代码片段过滤：后端应过滤敏感信息（密码、API 密钥）
- XSS 防护：代码片段展示时使用 Prism.js 自动转义，不直接渲染 HTML

### 性能优化策略

1. **虚拟滚动**：使用 `ElVirtualList` 或 `vue-virtual-scroller` 处理大量问题列表
2. **懒加载**：代码片段默认不加载，点击时才请求
3. **分页加载**：每页 20 条，避免一次性加载所有数据
4. **缓存策略**：使用 Pinia 持久化缓存审查结果（5 分钟 TTL）
5. **图表按需渲染**：调用链路图和统计图表在切换到对应 Tab 时才渲染
6. **骨架屏**：使用 ElSkeleton 提升加载体验

### Project Structure Notes

- 所有新文件在 `frontend/apps/web-ele/src/` 下创建（不是 `frontend/src/`）
- Monorepo 结构：`apps/web-ele` 是主应用，使用 Element Plus UI
- 共享包在 `packages/` 目录中，通过 workspace 引用
- 路由模块通过 `import.meta.glob('./modules/**/*.ts')` 自动加载

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-8.md#Story 8.2]
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Vue Structure]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Review Detail Interface UX]
- [Source: _bmad-output/implementation-artifacts/8-1-project-management-interface.md]
- [Source: backend/ai-code-review-api - ReviewResultController.java]
- [Source: backend/ai-code-review-common - ReviewTaskDTO.java, ReviewResultDTO.java, ReviewIssueDTO.java]
- [Source: backend/ai-code-review-repository - ReviewTaskRepository.java, ReviewResultRepository.java]
- [Source: frontend/apps/web-ele/src/api/request.ts]
- [Source: frontend/apps/web-ele/src/router/routes/modules/project.ts]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
