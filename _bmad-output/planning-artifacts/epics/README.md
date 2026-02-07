# Epics 文档结构

本目录包含 AI 智能代码审查系统的所有 Epic 和 Story 详细文档。

## 文档组织

原始的单体 `epics.md` 文件（3616行）已被拆分为以下结构化文档：

### 总览文档
- **index.md** - Epic 总览，包含需求摘要、需求映射和 Epic 列表

### Epic 详细文档
1. **epic-1.md** - 项目基础设施与配置管理 (8 个 Stories)
2. **epic-2.md** - Webhook 集成与任务队列 (7 个 Stories)
3. **epic-3.md** - 代码解析与上下文提取 (5 个 Stories)
4. **epic-4.md** - AI 智能审查引擎 (5 个 Stories)
5. **epic-5.md** - 审查报告与结果存储 (4 个 Stories)
6. **epic-6.md** - 质量阈值与 PR/MR 拦截 (4 个 Stories)
7. **epic-7.md** - 多渠道通知系统 (4 个 Stories)
8. **epic-8.md** - Web 管理界面 (6 个 Stories)
9. **epic-9.md** - 端到端测试与集成验证 (7 个 Stories)
10. **epic-10.md** - 性能测试与优化 (7 个 Stories)

## 文档内容

每个 epic 文件包含：
- **用户价值**: Epic 为用户提供的业务价值
- **用户成果**: Epic 完成后的具体交付物
- **需求覆盖**: 覆盖的功能需求（FR）、非功能需求（NFR）和附加需求
- **Stories**: 详细的用户故事及验收标准

## 使用指南

### 查看总览
```bash
# 查看所有 Epic 的摘要和需求映射
cat index.md
```

### 查看特定 Epic
```bash
# 例如：查看 Epic 1 的所有 Stories
cat epic-1.md
```

### 搜索特定内容
```bash
# 例如：搜索所有与 Redis 相关的 Stories
grep -n "Redis" epic-*.md
```

## 文档统计

- **总行数**: 3616 行（原始文件）
- **Epic 数量**: 10 个
- **Story 总数**: 57 个
- **功能需求**: 9 个 FR
- **非功能需求**: 5 个 NFR
- **需求覆盖率**: 100%

## 文档维护

拆分日期: 2026-02-05
拆分工具: Claude Code
原始文件: `../epics.md`（保留作为备份）

---

**导航**: [返回上级目录](../) | [查看原始文件](../epics.md)
