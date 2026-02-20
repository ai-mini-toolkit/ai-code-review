# 设计文档：`/bmad-verify-story` Skill

**日期：** 2026-02-20
**作者：** ethan
**状态：** 已实现

---

## 背景与目标

项目采用 BMAD Method 开发，Epic 1–8 功能均已实现完毕。需要一个能够自动打开 Chrome 浏览器、读取 Story 的验收标准（AC），并进行全链路验证的 Claude Code slash command，从而替代手动用户测试。

**核心目标：**
- 自动操作浏览器验证 UI 行为
- 同步检查 API 响应（status code、response body）
- 验证数据持久化（操作后刷新仍存在）
- 生成结构化 Pass/Fail 报告
- 不修改 story 文件或 sprint 状态（验证与开发流程职责分离）

---

## 调用方式

```bash
/bmad-verify-story 8-1    # 验证 Story 8.1 项目管理界面
/bmad-verify-story 8-2    # 验证 Story 8.2 审查历史界面
```

---

## 架构设计

完全对标现有 `/bmad-bmm-code-review` 的三文件结构：

```
.claude/commands/bmad-verify-story.md                           # Slash command 入口
_bmad/bmm/workflows/4-implementation/verify-story/
├── workflow.yaml                                                # Workflow 配置
└── instructions.xml                                            # 执行指令（5步工作流）
```

验证报告输出到：
```
_bmad-output/verification-reports/
├── 8-1-verification.md
├── 8-2-verification.md
└── ...
```

---

## 工作流（5 步）

| 步骤 | 目标 | 关键操作 |
|------|------|----------|
| Step 1 | 加载 Story 与环境检查 | 读取 story 文件提取 AC；检查前端(5173)和后端(8080)是否可达 |
| Step 2 | 制定验证计划 | 为每条 AC 分类：UI/API/PERSIST/MANUAL；规划操作序列 |
| Step 3 | 执行浏览器验证 | 登录 → 逐条 AC 操作验证 → 记录 PASS/FAIL/MANUAL/SKIP |
| Step 4 | 生成验证报告 | 写入 `_bmad-output/verification-reports/{{story_id}}-verification.md` |
| Step 5 | 输出最终摘要 | 在 chat 中打印统计结果与报告路径 |

---

## AC 验证类型

| 类型 | 说明 | 工具 |
|------|------|------|
| `UI` | 页面元素、导航、表单操作 | `navigate`, `find`, `computer`, `read_page` |
| `API` | HTTP status code、response body 字段 | `read_network_requests` |
| `PERSIST` | 操作后刷新确认数据持久化 | `navigate` + `find` |
| `MANUAL` | 无法自动验证（邮件、Webhook、剪贴板等） | 写入报告，人工确认 |
| `SKIP` | 服务未运行时自动跳过 | 记录原因，提示启动命令 |

---

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 验证粒度 | Story 级 | 与 code-review 对称；context 不超限；可重跑单个 story |
| 登录凭据 | admin/admin123 | Story 8.6 定义的默认管理员账号 |
| 前端地址 | http://localhost:5173 | web-ele (Element Plus) 的开发端口 |
| 后端地址 | http://localhost:8080 | Spring Boot dev 端口 |
| 服务不可达 | 警告 + 降级（SKIP），不中断 | 避免因基础设施问题导致整个验证失败 |
| 状态更新 | 不修改 story/sprint-status | 验证与开发流程职责分离 |
| MANUAL 项 | 跳过执行，写入报告说明 | 提升自动化率，保留人工补充空间 |

---

## 报告格式示例

```markdown
# Story 8-2 验证报告
验证时间：2026-02-20 18:30
环境：http://localhost:5173 → http://localhost:8080
整体结论：✅ 全部通过

| 指标 | 数量 |
|------|------|
| ✅ 通过（PASS） | 12 |
| ❌ 失败（FAIL） | 0 |
| ⚠️ 需人工验证（MANUAL） | 2 |
| ⏭️ 跳过 | 0 |

## 验证结果汇总
| AC | 描述 | 结果 | 备注 |
|----|------|------|------|
| AC#1 | 审查历史列表展示 | ✅ PASS | |
| AC#13 | 邮件通知发送 | ⚠️ MANUAL | 需配置 SMTP 后人工确认 |
```

---

## 首次验证建议

```bash
# Story 8.2（审查历史）状态为 done，是最佳首次验证候选
/bmad-verify-story 8-2
```
