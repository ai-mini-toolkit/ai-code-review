# Story 6.2: 实现阈值验证引擎

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 系统,
I want 根据项目阈值配置验证代码审查结果,
so that 可以判断 PR/MR 是否满足质量门禁标准，并生成违规详情。

## Acceptance Criteria (BDD)

### AC1: 阈值验证服务 — 创建 ThresholdValidationService
**Given** Story 6.1 已实现阈值配置管理
**When** 审查结果保存后触发阈值验证
**Then** 创建 `ThresholdValidationService` 接口及实现：
- `validate(Long projectId, ReviewStatisticsDTO statistics)` → `ThresholdValidationResult`
- 自动从 ProjectService 加载项目阈值配置（利用 Redis 缓存）

### AC2: 按严重性规则校验
**Given** 阈值规则为 `{"severity": "CRITICAL", "maxCount": 0}`
**When** 审查结果中 CRITICAL 问题数 = 2
**Then** 该规则判定为 violated
**And** violations 列表包含 `{rule: "CRITICAL <= 0", actual: 2, threshold: 0}`

### AC3: 总量规则校验
**Given** 阈值规则为 `{"totalIssues": 20}`
**When** 审查结果中总问题数 = 25
**Then** 该规则判定为 violated
**And** violations 列表包含 `{rule: "totalIssues <= 20", actual: 25, threshold: 20}`

### AC4: 阈值未启用时跳过验证
**Given** 项目阈值配置 `enabled = false`
**When** 触发阈值验证
**Then** 直接返回 `passed = true, violations = [], action = null`
**And** 不执行任何规则校验

### AC5: 综合验证结果
**Given** 多条规则中有 1+ 条 violated
**When** 验证完成
**Then** `ThresholdValidationResult.passed = false`
**And** `action` 值来自 ThresholdConfigDTO（BLOCK_MERGE 或 WARN_ONLY）
**And** 所有违规规则记录在 violations 列表中

### AC6: 集成到 ReviewResultServiceImpl
**Given** 审查结果保存流程（saveResult 方法）
**When** 审查结果的统计信息已计算完成
**Then** 在更新 task 状态为 COMPLETED 之前调用阈值验证
**And** 将验证结果保存到 ReviewResultEntity（新增 thresholdResult JSONB 字段）

### AC7: 单元测试与集成测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- 严重性规则通过/违规两种情况
- 总量规则通过/违规两种情况
- enabled=false 跳过验证
- 多规则混合场景
- 全部通过场景
- 集成到 saveResult 流程的端到端验证

## Tasks / Subtasks

- [x] Task 1: 创建阈值验证结果 DTO (AC: #1, #2, #3, #5)
  - [x] 1.1 创建 `ThresholdValidationResultDTO.java`（passed, violations, action）
  - [x] 1.2 创建 `ThresholdViolationDTO.java`（rule, actual, threshold）
- [x] Task 2: 创建 ThresholdValidationService 接口和实现 (AC: #1, #2, #3, #4, #5)
  - [x] 2.1 在 service 模块创建 `ThresholdValidationService` 接口
  - [x] 2.2 实现 `ThresholdValidationServiceImpl`
  - [x] 2.3 实现严重性规则校验逻辑（从 bySeverity Map 获取计数）
  - [x] 2.4 实现总量规则校验逻辑（从 statistics.total 获取）
  - [x] 2.5 实现 enabled=false 时的快速返回
- [x] Task 3: 数据库扩展 — ReviewResultEntity 添加 thresholdResult 字段 (AC: #6)
  - [x] 3.1 创建 Flyway V10 迁移，ALTER TABLE review_result ADD COLUMN threshold_result JSONB
  - [x] 3.2 在 ReviewResultEntity 添加 thresholdResult String 字段
  - [x] 3.3 在 ReviewResultDTO 添加 ThresholdValidationResultDTO 字段
  - [x] 3.4 更新 ReviewResultMapper.toDTO() 包含反序列化
- [x] Task 4: 集成到 ReviewResultServiceImpl (AC: #6)
  - [x] 4.1 注入 ThresholdValidationService 和 ProjectService
  - [x] 4.2 在 saveResult() 中 statistics 计算后调用阈值验证
  - [x] 4.3 将验证结果序列化存入 entity 的 thresholdResult 字段
- [x] Task 5: 编写单元测试 (AC: #7)
  - [x] 5.1 ThresholdValidationServiceImpl 单元测试（各种规则场景）
  - [x] 5.2 ReviewResultServiceImpl 阈值集成的 mock 测试
- [x] Task 6: 编写集成测试 (AC: #7)
  - [x] 6.1 阈值验证的端到端集成测试

## Dev Notes

### 架构模式与约束

- **模块放置**：遵循已建立的分层架构：
  - DTO → `ai-code-review-common` 模块 `dto/threshold/` 包
  - Service 接口+实现 → `ai-code-review-service` 模块
  - Flyway 迁移 → `ai-code-review-repository/src/main/resources/db/migration/`
  - Entity 修改 → `ai-code-review-repository` 模块
- **不创建新 Controller**：此 Story 为纯后端逻辑，无新 API 端点
- **不引入新依赖**：所有需要的库已在项目中

### 关键技术细节

1. **-parameters 编译器标志未启用**（关键！）：
   - **必须** `@Cacheable(key = "#p0")` 用位置参数
   - 构造器注入不受影响

2. **ReviewStatisticsDTO 结构**（已存在，直接使用）：
   ```java
   public class ReviewStatisticsDTO {
       private int total;                        // 总问题数
       private Map<String, Integer> bySeverity;  // {"CRITICAL": 1, "HIGH": 3, ...}
       private Map<String, Integer> byCategory;  // {"SECURITY": 2, ...}
   }
   ```
   **关键**: `bySeverity` 的 key 是 `IssueSeverity.name()` 字符串，使用 `LinkedHashMap` 保持枚举声明顺序。

3. **ThresholdValidationResultDTO 设计**：
   ```java
   @Data @Builder @NoArgsConstructor @AllArgsConstructor
   public class ThresholdValidationResultDTO {
       private boolean passed;                    // 所有规则是否通过
       private List<ThresholdViolationDTO> violations;  // 违规规则列表
       private String action;                     // 来自 ThresholdConfigDTO: BLOCK_MERGE | WARN_ONLY | null
   }
   ```

4. **ThresholdViolationDTO 设计**：
   ```java
   @Data @Builder @NoArgsConstructor @AllArgsConstructor
   public class ThresholdViolationDTO {
       private String rule;       // 规则描述: "CRITICAL <= 0" 或 "totalIssues <= 20"
       private int actual;        // 实际值
       private int threshold;     // 阈值
   }
   ```

5. **验证逻辑伪代码**：
   ```java
   public ThresholdValidationResultDTO validate(Long projectId, ReviewStatisticsDTO statistics) {
       ThresholdConfigDTO config = projectService.getThresholds(projectId);

       if (!Boolean.TRUE.equals(config.getEnabled())) {
           return ThresholdValidationResultDTO.builder()
               .passed(true).violations(List.of()).action(null).build();
       }

       List<ThresholdViolationDTO> violations = new ArrayList<>();
       for (ThresholdRuleDTO rule : config.getRules()) {
           if (rule.getSeverity() != null) {
               // 严重性规则
               int actual = statistics.getBySeverity()
                   .getOrDefault(rule.getSeverity(), 0);
               if (actual > rule.getMaxCount()) {
                   violations.add(ThresholdViolationDTO.builder()
                       .rule(rule.getSeverity() + " <= " + rule.getMaxCount())
                       .actual(actual).threshold(rule.getMaxCount()).build());
               }
           } else if (rule.getTotalIssues() != null) {
               // 总量规则
               if (statistics.getTotal() > rule.getTotalIssues()) {
                   violations.add(ThresholdViolationDTO.builder()
                       .rule("totalIssues <= " + rule.getTotalIssues())
                       .actual(statistics.getTotal()).threshold(rule.getTotalIssues()).build());
               }
           }
       }

       return ThresholdValidationResultDTO.builder()
           .passed(violations.isEmpty())
           .violations(violations)
           .action(violations.isEmpty() ? null : config.getAction())
           .build();
   }
   ```

6. **Flyway V10 迁移**：
   ```sql
   ALTER TABLE review_result
   ADD COLUMN threshold_result JSONB;
   -- nullable: 旧的审查结果没有阈值验证
   ```
   **注意**: 此列为 nullable（不设 NOT NULL），因为历史审查记录没有阈值验证。

7. **集成到 ReviewResultServiceImpl.saveResult()**：
   ```java
   // 在 statistics 计算后、task status 更新前：
   ReviewStatisticsDTO statistics = ReviewResultMapper.calculateStatistics(issues);

   // NEW: 阈值验证
   ThresholdValidationResultDTO thresholdResult =
       thresholdValidationService.validate(task.getProject().getId(), statistics);

   // 序列化存入 entity
   entity.setThresholdResult(serializeThresholdResult(thresholdResult));

   // 然后继续 save entity 和 update task status
   ```

8. **序列化 ThresholdValidationResult 到 JSONB**：
   - 复用 `ReviewResultMapper` 中已有的 `OBJECT_MAPPER`
   - 或在 `ThresholdMapper` 中新增 `serializeValidationResult()` / `deserializeValidationResult()` 方法
   - **建议**: 在 `ThresholdMapper` 中添加，保持阈值相关逻辑集中

9. **ReviewResultDTO 中添加 thresholdResult 字段**：
   ```java
   // ReviewResultDTO.java — 添加:
   private ThresholdValidationResultDTO thresholdResult;
   ```
   **注意**: 更新 `ReviewResultMapper.toDTO()` 时使用 `ThresholdMapper.deserializeValidationResult(entity.getThresholdResult())`

### 已建立的代码模式参考

| 模式 | 参考文件 | 关键点 |
|------|---------|--------|
| Service 接口+实现 | `ReviewResultServiceImpl.java` | @Slf4j, @Service, 构造器注入 |
| JSONB 序列化 | `ThresholdMapper.java` | 静态 ObjectMapper, serialize/deserialize |
| 统计信息结构 | `ReviewStatisticsDTO.java` | bySeverity Map, total int |
| 规则 DTO | `ThresholdRuleDTO.java` | severity/maxCount 或 totalIssues |
| 缓存读取 | `ProjectServiceImpl.getThresholds()` | @Cacheable(value="thresholds", key="#p0") |
| Flyway 迁移 | `V9__add_thresholds_to_project.sql` | 下一个版本 = V10 |
| 集成点 | `ReviewResultServiceImpl.saveResult()` | statistics 计算后 + task 状态更新前 |
| DTO builder | `ThresholdConfigDTO.java` | @Data @Builder @NoArgsConstructor @AllArgsConstructor |

### 之前 Story 的 Code Review 教训

1. **`-parameters` 未启用**：所有注解必须带显式 value 属性
2. **`Boolean.TRUE.equals()`** 避免 NPE（检查 enabled 时必须用）
3. **使用 LinkedHashMap** 保持 JSON 字段顺序稳定
4. **Mapper 使用静态方法**，不放 Service 的 private 方法里
5. **空规则列表已被 ThresholdMapper.validate() 拒绝**（Story 6.1 code review M2 修复）
6. **createProject() 已设置完整默认阈值 JSON**（Story 6.1 code review M1 修复）
7. **bySeverity Map** 使用 `getOrDefault(key, 0)` 避免 NPE
8. **nullable JSONB 列**: 反序列化时需处理 null（返回空结果而非异常）

### 依赖的已实现类（直接使用，无需修改）

| 类 | 模块 | 用途 |
|---|------|------|
| `ThresholdConfigDTO` | common/dto/threshold | 阈值配置（enabled, rules, action） |
| `ThresholdRuleDTO` | common/dto/threshold | 规则定义（severity/maxCount 或 totalIssues） |
| `ThresholdMapper` | service/mapper | 序列化/反序列化/校验阈值配置 |
| `ProjectService.getThresholds()` | service | 获取项目阈值配置（带 Redis 缓存） |
| `ReviewStatisticsDTO` | common/dto/result | 审查统计（total, bySeverity, byCategory） |
| `ReviewResultMapper` | service/mapper | calculateStatistics(), toDTO() |
| `ReviewResultEntity` | repository/entity | 审查结果实体 |
| `ReviewResultDTO` | common/dto/result | 审查结果 DTO |
| `ReviewTask` | repository/entity | 审查任务（关联 project） |
| `IssueSeverity` | common/enums | CRITICAL(5), HIGH(4), MEDIUM(3), LOW(2), INFO(1) |

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-repository/src/main/resources/db/migration/
  └── V10__add_threshold_result_to_review_result.sql    ← 新增

backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── threshold/
          ├── ThresholdValidationResultDTO.java          ← 新增
          └── ThresholdViolationDTO.java                 ← 新增

backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  ├── ThresholdValidationService.java                    ← 新增（接口）
  └── impl/
      └── ThresholdValidationServiceImpl.java            ← 新增

backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/
  └── ThresholdValidationServiceImplTest.java            ← 新增
```

**修改文件清单**：
```
backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/
  └── ReviewResultEntity.java                            ← 修改（添加 thresholdResult 字段）

backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/
  └── ReviewResultDTO.java                               ← 修改（添加 thresholdResult 字段）

backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/
  └── ThresholdMapper.java                               ← 修改（添加 validation result 序列化方法）

backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImpl.java                       ← 修改（集成阈值验证）

backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/
  └── ReviewResultMapper.java                            ← 修改（toDTO 添加 thresholdResult 映射）
```

### 集成点

- **上游**：Story 6.1（ThresholdConfigDTO, getThresholds(), ThresholdMapper）
- **下游**：Story 6.3（GitHub Check Runs）→ 使用 ThresholdValidationResult 判定 conclusion
- **下游**：Story 6.4（GitLab/AWS CodeCommit 状态更新）→ 使用 ThresholdValidationResult

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-6.md#Story 6.2]
- [Source: _bmad-output/implementation-artifacts/6-1-threshold-configuration-management.md — Story 6.1 实现]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java — saveResult() 集成点]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewStatisticsDTO.java — 统计结构]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ThresholdMapper.java — 序列化工具]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewResultMapper.java — 统计计算]
- [Source: _bmad-output/planning-artifacts/architecture.md#Threshold Interception — 架构约束]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Windows file-lock on `ai-code-review-api-1.0.0-SNAPSHOT.jar` prevented `mvn clean install` — worked around by running `mvn test -pl ai-code-review-api -am` directly
- ReviewTaskIntegrationTest (4 errors) is pre-existing, unrelated to Story 6.2

### Completion Notes List

- ✅ Task 1: Created `ThresholdValidationResultDTO` and `ThresholdViolationDTO` in common module
- ✅ Task 2: Created `ThresholdValidationService` interface and `ThresholdValidationServiceImpl` with severity rules, total-issues rules, and enabled=false fast-return
- ✅ Task 3: V10 Flyway migration adds nullable `threshold_result JSONB` column; updated entity, DTO, and mapper
- ✅ Task 4: Integrated into `ReviewResultServiceImpl.saveResult()` — threshold validation runs between statistics computation and task status update
- ✅ Task 5: 14 unit tests for ThresholdValidationServiceImpl (severity violations, total issues, disabled, multi-rule, edge cases) + 2 mock tests in ReviewResultServiceImplTest
- ✅ Task 6: 2 integration tests in ReviewResultControllerIntegrationTest — disabled thresholds return passed, enabled thresholds detect violations via API
- ✅ All 219 service tests pass, 17 ReviewResultController integration tests pass, 0 regressions

### Test Count Summary

| Test Class | Count | Status |
|-----------|-------|--------|
| ThresholdValidationServiceImplTest | 14 | ✅ Pass |
| ReviewResultServiceImplTest | 15 (12+3 new) | ✅ Pass |
| ThresholdMapperTest | 15 | ✅ Pass |
| ReviewResultControllerIntegrationTest | 17 (15+2 new) | ✅ Pass |
| Service module total | 219 | ✅ Pass |

### File List

**New Files:**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/threshold/ThresholdValidationResultDTO.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/threshold/ThresholdViolationDTO.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ThresholdValidationService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ThresholdValidationServiceImpl.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ThresholdValidationServiceImplTest.java`
- `backend/ai-code-review-repository/src/main/resources/db/migration/V10__add_threshold_result_to_review_result.sql`

**Modified Files:**
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewResultEntity.java` — added `thresholdResult` JSONB field
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewResultDTO.java` — added `thresholdResult` field
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ThresholdMapper.java` — added `serializeValidationResult()` / `deserializeValidationResult()`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewResultMapper.java` — updated `toDTO()` to include thresholdResult
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java` — integrated ThresholdValidationService
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java` — added ThresholdValidationService mock + 2 integration tests
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/ReviewResultControllerIntegrationTest.java` — added 2 threshold validation integration tests

### Change Log

| Date | Change | Details |
|------|--------|---------|
| 2026-02-17 | Story 6.2 implementation | Implemented threshold validation engine with severity rules, total-issues rules, DB persistence, and integration into saveResult flow |
| 2026-02-17 | Code review fixes | M1: Fixed misleading test display name; M2: Added @Builder.Default on violations field; M3: Added getResultByTaskId threshold deserialization test; M4: Added defensive null-check on config.getRules(); L1: Added per-violation debug logging |
