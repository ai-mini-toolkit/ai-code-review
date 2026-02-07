# Epic 8: Web 管理界面

**用户价值**：团队通过现代化的 Web 界面管理项目、查看审查历史、配置 AI 模型和模板，可视化审查报告和调用链路图。

**用户成果**：
- 实现项目管理界面（创建、编辑、删除、列表）
- 实现审查历史查看（任务列表、详情、问题列表）
- 实现 AI 模型配置界面（模型管理、API 密钥管理）
- 实现 Prompt 模板编辑器（编辑、预览、版本管理）
- 实现审查报告可视化（问题列表、调用链路图、统计图表）
- 基于 Vue-Vben-Admin 的响应式布局

**覆盖的功能需求**：FR 1.9（Web 管理界面）
**覆盖的非功能需求**：NFR 1（性能）、NFR 4（可扩展性）
**覆盖的附加需求**：技术栈（Vue 3、Vben-Admin）、命名约定

---

## Stories


### Story 8.1: 实现项目管理界面

**用户故事**：
作为系统管理员，
我想要通过 Web 界面管理项目，
以便可视化地配置和查看项目列表。

**验收标准**：

**Given** 项目配置后端 API 已实现（Epic 1）
**When** 访问项目管理界面
**Then** 创建 Vue 组件：
- src/views/project/ProjectList.vue（项目列表）
- src/views/project/ProjectDetail.vue（项目详情）
- src/views/project/ProjectForm.vue（创建/编辑表单）

**And** ProjectList 功能：
- 表格展示项目（名称、Git 平台、仓库 URL、状态）
- 搜索过滤（按名称、平台）
- 操作按钮（编辑、删除、启用/禁用）
- 新建项目按钮

**And** ProjectForm 功能：
- 表单字段：名称、描述、Git 平台、仓库 URL、Webhook 密钥
- 表单验证（必填字段、URL 格式）
- 提交到后端 API

**And** ProjectDetail 功能：
- 展示项目完整信息
- Webhook URL 显示和复制
- 配置标签页（阈值、通知）

**And** 基于 Element Plus 组件库
**And** 响应式布局（移动端适配）

---

### Story 8.2: 实现审查历史查看界面

**用户故事**：
作为开发者，
我想要查看项目的审查历史，
以便了解代码质量趋势。

**验收标准**：

**Given** 审查结果查询 API 已实现（Epic 5）
**When** 访问审查历史界面
**Then** 创建 Vue 组件：
- src/views/review/ReviewHistory.vue（审查历史列表）
- src/views/review/ReviewDetail.vue（审查详情）

**And** ReviewHistory 功能：
- 表格展示审查任务（时间、分支、提交、状态、问题数）
- 过滤：按项目、状态、日期范围
- 排序：按时间、问题数
- 分页（每页 20 条）
- 状态徽章（PENDING/RUNNING/COMPLETED/FAILED）

**And** ReviewDetail 功能（详细 UX 交互规范见 ux-design-specification.md）：

**审查摘要卡片**:
- 显示总问题数、严重性分布（饼图）
- 显示审查耗时、代码行数、文件数
- 阈值验证结果：✅ 通过（绿色） / ❌ 失败（红色）
- 快速筛选按钮：仅显示 Critical、仅显示 High、显示全部

**问题列表（核心 UX 目标：3 秒内定位关键问题）**:
- 默认按严重性排序（Critical → High → Medium → Low → Info）
- 问题卡片布局（而非表格）：
  ```
  [🔴 Critical] Security - SQL Injection              Line 42 ▶
  在 UserService.java 的查询中检测到 SQL 注入漏洞...
  [展开详情] [查看代码] [标记已处理]
  ```

- 虚拟滚动优化（性能要求）：
  - 仅渲染可见区域的问题（使用 vue-virtual-scroller）
  - 目标: 1000 个问题渲染时间 < 3 秒
  - 懒加载代码片段（点击"查看代码"时才加载）

- 智能分组和筛选：
  - 按文件分组：`UserService.java (5 issues) ▶`
  - 按类别分组：`Security (10 issues) ▶`
  - 按严重性分组：`Critical (3 issues) ▶`
  - 搜索框：实时过滤（按关键词、文件名、行号）

- 交互细节：
  - 点击问题卡片 → 展开详细说明和修复建议
  - 点击行号 → 跳转到代码片段（高亮显示问题行）
  - 点击"标记已处理" → 问题置灰，从"待处理"计数中移除
  - 支持键盘导航：J/K 上下切换问题，Enter 展开/收起

**代码片段展示（核心 UX 目标：理解问题上下文）**:
- 语法高亮（使用 Prism.js 或 Highlight.js）
- 显示上下文（问题行 ± 5 行）
- 问题行高亮标记（红色背景 + 波浪下划线）
- 行号显示（点击复制行号）
- 复制代码按钮
- 差异视图：Before（旧代码）vs After（新代码）

**调用链路图展示**:
- Mermaid 图表渲染（mermaid.js）
- 或使用 D3.js 交互式图表（可拖拽、缩放）
- 节点点击 → 显示方法详情（参数、返回值）
- 高亮变更节点（红色边框）
- 图表导出功能（PNG / SVG）

**统计图表可视化**:
- 饼图：按严重性分布（ECharts）
- 柱状图：按类别分布（Security、Performance、Maintainability 等）
- 趋势图：历史审查统计（最近 30 天问题数趋势）
- 热力图：问题分布热点文件

**加载状态和错误处理（核心 UX 原则：信息透明）**:
- 骨架屏加载（Skeleton Loading）：
  ```
  ████████████ 85% ████████░░
  正在加载审查详情...（预计还需 2 秒）
  ```

- 部分加载策略：
  1. 优先加载摘要卡片（Critical 问题数）
  2. 然后加载问题列表（前 20 个）
  3. 最后加载调用链路图（可选，异步加载）

- 错误处理：
  - 审查失败 → 显示错误原因和重试按钮
  - 部分维度失败 → 显示警告"仅显示 4/6 维度结果"
  - 调用图生成失败 → 显示占位消息"调用图不可用"

**性能优化**:
- 问题列表分页加载（每页 50 个，滚动触发加载下一页）
- 代码片段懒加载（默认不加载，点击时才请求）
- 图表按需渲染（切换到"统计"Tab 时才渲染）
- API 响应缓存（5 分钟 TTL）

**响应式设计**:
- 桌面端（> 1024px）：三栏布局（侧边栏 + 问题列表 + 详情）
- 平板端（768-1024px）：两栏布局（问题列表 + 详情）
- 移动端（< 768px）：单栏布局（列表视图 → 点击 → 详情视图）

**可访问性（A11y）**:
- 支持键盘导航（Tab, Enter, Escape）
- ARIA 标签完整（role, aria-label, aria-describedby）
- 高对比度模式支持
- 屏幕阅读器友好

**And** 使用 Vue Router 路由：
- `/reviews` → ReviewHistory.vue
- `/reviews/:id` → ReviewDetail.vue
- `/reviews/:id/issues/:issueId` → 问题详情（深度链接）

**And** 使用 Pinia 状态管理：
```typescript
// stores/review.ts
export const useReviewStore = defineStore('review', {
  state: () => ({
    currentReview: null,
    issues: [],
    filters: { severity: 'all', category: 'all' },
    loading: false,
    error: null
  }),
  actions: {
    async fetchReviewDetail(id: string) {
      this.loading = true;
      try {
        this.currentReview = await api.getReviewDetail(id);
        this.issues = this.currentReview.issues;
      } catch (error) {
        this.error = error.message;
      } finally {
        this.loading = false;
      }
    },
    filterIssues(filters) {
      this.filters = filters;
      // Apply filters to issues
    }
  }
});
```

**And** 用户测试验收标准：
- ✅ 用户能在 3 秒内看到首屏关键问题（Critical 级别）
- ✅ 用户能在 10 秒内理解问题本质（查看代码上下文）
- ✅ 用户能在 30 秒内知道如何修复（查看修复建议）
- ✅ 1000 个问题的页面加载时间 < 3 秒（虚拟滚动）
- ✅ 用户满意度 ≥ 4.5/5（用户反馈调研）

---

### Story 8.3: 实现 AI 模型配置界面

**用户故事**：
作为系统管理员，
我想要通过 Web 界面管理 AI 模型配置，
以便可视化地添加和配置 AI 提供商。

**验收标准**：

**Given** AI 模型配置后端 API 已实现（Epic 1）
**When** 访问 AI 模型配置界面
**Then** 创建 Vue 组件：
- src/views/model/AIModelList.vue（模型列表）
- src/views/model/AIModelForm.vue（创建/编辑表单）

**And** AIModelList 功能：
- 表格展示模型（名称、提供商、模型名称、状态）
- 操作按钮（编辑、删除、测试连接）
- 新建模型按钮
- 状态开关（启用/禁用）

**And** AIModelForm 功能：
- 表单字段：
  - 名称、提供商（下拉选择：OpenAI/Anthropic/CustomOpenAPI）
  - 模型名称、API Endpoint（自定义提供商）
  - API Key（密码输入）
  - 参数：Timeout、Max Tokens、Temperature
- 表单验证
- 测试连接按钮（调用测试 API）

**And** 测试连接显示结果（成功/失败 + 响应时间）
**And** API Key 显示为密文（***）

---

### Story 8.4: 实现 Prompt 模板编辑器

**用户故事**：
作为系统管理员，
我想要通过 Web 界面编辑 Prompt 模板，
以便定制 AI 审查的提示词。

**验收标准**：

**Given** Prompt 模板后端 API 已实现（Epic 1）
**When** 访问 Prompt 模板编辑器
**Then** 创建 Vue 组件：
- src/views/template/TemplateList.vue（模板列表）
- src/views/template/TemplateEditor.vue（模板编辑器）

**And** TemplateList 功能：
- 表格展示模板（名称、类别、版本、状态）
- 按类别过滤（六维度）
- 操作按钮（编辑、删除、复制）

**And** TemplateEditor 功能：
- 代码编辑器（Monaco Editor 或 CodeMirror）
- 语法高亮（Mustache）
- 模板变量提示（自动补全）
- 实时预览（右侧分栏）
- 保存和版本管理
- 恢复到默认模板按钮

**And** 预览功能：
- 使用示例数据渲染模板
- 显示渲染后的 Prompt 内容

**And** 模板变量文档说明

---

### Story 8.5: 实现审查报告可视化组件

**用户故事**：
作为开发者，
我想要在 Web 界面看到可视化的审查报告，
以便更直观地理解代码问题。

**验收标准**：

**Given** 审查详情界面已实现（Story 8.2）
**When** 查看审查详情
**Then** 创建可视化组件：
- src/components/review/IssueList.vue（问题列表）
- src/components/review/CodeSnippet.vue（代码片段）
- src/components/chart/CallGraphChart.vue（调用链路图）
- src/components/chart/StatisticsChart.vue（统计图表）

**And** IssueList 功能：
- 问题卡片布局
- 严重性徽章（Critical/High/Medium/Low）
- 代码行号链接（点击跳转到代码）
- 修复建议展开/收起

**And** CodeSnippet 功能：
- 代码语法高亮（Prism.js 或 Highlight.js）
- 行号显示
- 问题行高亮标记
- 复制代码按钮

**And** CallGraphChart 功能：
- Mermaid 图表渲染（mermaid.js）
- 或 D3.js 交互式图表
- 节点点击查看详情
- 图表缩放和拖拽

**And** StatisticsChart 功能：
- 饼图（按严重性分布）- ECharts
- 柱状图（按类别分布）- ECharts
- 趋势图（历史审查统计）

---

### Story 8.6: 实现用户认证与授权（JWT）

**用户故事**：
作为系统管理员，
我想要实现用户认证和授权，
以便保护 Web 界面和 API。

**验收标准**：

**Given** Web 界面已实现
**When** 实现认证授权
**Then** 创建 `user` 表：
- id、username、password_hash、email
- role（ADMIN/USER）、enabled、created_at

**And** 后端实现 Spring Security 6 配置：
- JWT Token 生成和验证
- 登录端点：POST /api/v1/auth/login
- 注册端点：POST /api/v1/auth/register（仅管理员）
- 刷新端点：POST /api/v1/auth/refresh

**And** 前端实现认证逻辑：
- 登录页面（src/views/auth/Login.vue）
- Token 存储（localStorage）
- Axios 拦截器（添加 Authorization header）
- 路由守卫（未登录跳转登录页）

**And** API 权限控制：
- ADMIN 角色：所有操作
- USER 角色：仅查看（不能删除、修改配置）

**And** Token 过期时间：24 小时
**And** Refresh Token 有效期：7 天
**And** 编写集成测试验证认证流程

---

