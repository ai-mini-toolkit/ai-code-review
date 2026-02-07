# Epic 5: 审查报告与结果存储

**用户价值**：开发者可以查看结构化的审查报告、可视化的调用链路图和问题统计，系统持久化存储所有审查结果供后续查询。

**用户成果**：
- 生成结构化审查报告（问题列表 + 严重性 + 修复建议）
- 生成调用链路图（Mermaid/PlantUML/D3.js JSON 格式）
- 统计问题分布（按严重性、类别分类）
- 持久化存储到 PostgreSQL（使用 JSONB 列）
- 提供报告查询 API

**覆盖的功能需求**：FR 1.5（审查报告生成）
**覆盖的非功能需求**：NFR 2（可靠性）、NFR 5（可维护性）
**覆盖的附加需求**：数据流要求、数据持久化要求

---

## Stories


### Story 5.1: 实现审查结果持久化存储

**用户故事**：
作为系统，
我想要持久化审查结果到数据库，
以便后续查询和展示。

**验收标准**：

**Given** AI 审查引擎已实现
**When** 审查完成
**Then** 创建 `review_result` 表：
- id、task_id（外键，关联 review_task）
- issues（JSONB，存储问题列表）
- call_graph（JSONB，存储调用链路图）
- statistics（JSONB，问题统计）
- review_duration_ms、total_tokens
- created_at

**And** issues JSONB 结构：
```json
[
  {
    "severity": "CRITICAL",
    "category": "security",
    "line": 42,
    "file": "UserService.java",
    "message": "SQL injection vulnerability",
    "suggestion": "Use PreparedStatement instead"
  }
]
```

**And** statistics JSONB 结构：
```json
{
  "total": 15,
  "by_severity": {"CRITICAL": 2, "HIGH": 5, "MEDIUM": 8},
  "by_category": {"security": 3, "performance": 4, "maintainability": 8}
}
```

**And** 实现 ReviewResultRepository JPA 仓库
**And** 实现 ReviewResultService.saveResult() 方法
**And** 更新 review_task 状态为 COMPLETED
**And** 编写单元测试验证存储逻辑

---

### Story 5.2: 实现审查报告生成服务

**用户故事**：
作为开发者，
我想要生成结构化的审查报告，
以便清晰地了解代码问题和改进建议。

**验收标准**：

**Given** 审查结果已持久化
**When** 生成审查报告
**Then** 创建 ReportGenerator 服务：
- generateReport(ReviewResult result): Report
- Report 包含：
  - summary（总问题数、严重性分布）
  - issuesByFile（按文件分组的问题列表）
  - issuesByCategory（按类别分组）
  - callGraph（Mermaid 格式调用链路图）
  - recommendations（修复建议列表）

**And** 按严重性排序问题（CRITICAL → HIGH → MEDIUM → LOW）
**And** 生成 Markdown 格式报告
**And** 生成 HTML 格式报告（用于邮件）
**And** 生成 JSON 格式报告（用于 API）
**And** 编写单元测试验证报告生成逻辑

---

### Story 5.3: 实现调用链路图可视化格式转换

**用户故事**：
作为前端开发者，
我想要将调用链路图转换为可视化格式，
以便在 Web 界面展示。

**验收标准**：

**Given** 调用链路分析已完成
**When** 需要可视化调用图
**Then** 创建 CallGraphRenderer 服务：
- toMermaid(CallGraph graph): String
- toPlantUML(CallGraph graph): String
- toD3Json(CallGraph graph): String（D3.js 格式）

**And** Mermaid 格式：
```
graph TD
  A[UserService.createUser] --> B[UserRepository.save]
  A --> C[EmailService.sendWelcomeEmail]
```

**And** PlantUML 格式：
```
@startuml
UserService.createUser -> UserRepository.save
UserService.createUser -> EmailService.sendWelcomeEmail
@enduml
```

**And** D3.js JSON 格式：
```json
{
  "nodes": [{"id": "UserService.createUser"}, ...],
  "links": [{"source": 0, "target": 1}, ...]
}
```

**And** 编写单元测试验证所有格式转换

---

### Story 5.4: 实现审查结果查询 API

**用户故事**：
作为前端开发者，
我想要通过 API 查询审查结果，
以便在 Web 界面展示。

**验收标准**：

**Given** 审查结果已存储
**When** 查询审查结果
**Then** 创建 ReviewResultController REST API：
- GET /api/v1/reviews/{taskId}/result（获取审查结果）
- GET /api/v1/reviews/{taskId}/report（获取报告，支持 format=markdown|html|json）
- GET /api/v1/reviews/{taskId}/call-graph（获取调用图，支持 format=mermaid|plantuml|d3）
- GET /api/v1/reviews（列出审查历史，支持分页、过滤、排序）

**And** 列表 API 支持过滤：
- projectId（按项目）
- status（按状态）
- dateRange（按日期范围）

**And** 列表 API 支持排序：
- created_at DESC（默认）
- total_issues DESC

**And** API 响应时间 < 500ms（NFR 1）
**And** 编写集成测试覆盖所有端点

---

