# Story 3.1: Diff 元数据提取与变更分析

Status: done

## Story

As a **system**,
I want to **extract basic metadata from raw Git Diff content (changed files, change types, statistics)**,
so that **I can understand the scope of changes while preserving the raw Diff for AI to read directly**.

**Design Philosophy**: AI models natively understand Unified Diff format — no need to engineer hunk-level parsing.
This Story only extracts structured metadata; the raw Diff content is passed directly to AI in Story 3.3.

## Acceptance Criteria

1. **AC1: DiffMetadataExtractor Class** - Create `DiffMetadataExtractor` in service module:
   - `extractMetadata(String rawDiff): DiffMetadata`
   - `DiffMetadata` contains: `List<FileDiffInfo>` and `DiffStatistics`

2. **AC2: FileDiffInfo Data Model** - Each changed file produces a `FileDiffInfo`:
   - `oldPath: String` (pre-change path, null for ADD)
   - `newPath: String` (post-change path, null for DELETE)
   - `changeType: ChangeType` (ADD / MODIFY / DELETE / RENAME)
   - `language: Language` (detected from file extension)
   - `isBinary: boolean`

3. **AC3: DiffStatistics Data Model** - Aggregate statistics across all files:
   - `totalFilesChanged: int`
   - `totalLinesAdded: int`
   - `totalLinesDeleted: int`

4. **AC4: Diff Header Parsing** - Extract file paths and change types from diff headers:
   - `--- a/file` / `+++ b/file` → extract oldPath / newPath
   - `new file mode` → ChangeType.ADD
   - `deleted file mode` → ChangeType.DELETE
   - `rename from/to` → ChangeType.RENAME
   - Otherwise → ChangeType.MODIFY

5. **AC5: Line Count Statistics** - Count changed lines:
   - Lines starting with `+` (excluding `+++`) → added line
   - Lines starting with `-` (excluding `---`) → deleted line

6. **AC6: Language Detection by Extension** - Detect programming language from file extension:
   - .java → JAVA, .py → PYTHON, .js → JAVASCRIPT, .ts → TYPESCRIPT
   - .go → GO, .rs → RUST, .rb → RUBY, .php → PHP, .kt → KOTLIN
   - .c/.cpp/.h → C/CPP, .cs → CSHARP, .swift → SWIFT
   - .yml/.yaml → YAML, .json → JSON, .xml → XML, .md → MARKDOWN
   - .sql → SQL, .sh → SHELL, .dockerfile/Dockerfile → DOCKERFILE
   - Unrecognized → UNKNOWN

7. **AC7: Binary File Handling** - Detect `Binary files ... differ` pattern and set `isBinary: true`

8. **AC8: No Hunk Parsing** - `@@ ... @@` lines used only for line counting, NOT for building Hunk objects

9. **AC9: Enums in Common Module** - `Language` and `ChangeType` enums defined in common module

10. **AC10: Unit Tests** - Comprehensive unit tests:
    - Test each ChangeType identification (ADD / MODIFY / DELETE / RENAME)
    - Test line count statistics accuracy
    - Test Language detection covering all supported extensions
    - Test binary file handling
    - Test edge cases (empty diff, rename-only without content changes)
    - Test with real Git Diff samples

## Tasks / Subtasks

- [x] Task 1: Create ChangeType enum in common module (AC: #9)
  - [x] 1.1 Create `ChangeType.java` in `common/enums/` with values: ADD, MODIFY, DELETE, RENAME
  - [x] 1.2 Add unit test `ChangeTypeTest` for enum count and values

- [x] Task 2: Create Language enum in common module (AC: #6, #9)
  - [x] 2.1 Create `Language.java` in `common/enums/` with all supported languages + UNKNOWN
  - [x] 2.2 Add `fromExtension(String extension)` static method for extension-to-language mapping
  - [x] 2.3 Add `fromFileName(String fileName)` static method that extracts extension from filename
  - [x] 2.4 Add unit test `LanguageTest` covering all supported extensions, case-insensitivity, unknown extensions, and filenames without extension

- [x] Task 3: Create DiffMetadata, FileDiffInfo, DiffStatistics DTOs in common module (AC: #1, #2, #3)
  - [x] 3.1 Create `DiffMetadata.java` in `common/dto/` with `List<FileDiffInfo> files` and `DiffStatistics statistics`
  - [x] 3.2 Create `FileDiffInfo.java` in `common/dto/` with fields: oldPath, newPath, changeType, language, isBinary
  - [x] 3.3 Create `DiffStatistics.java` in `common/dto/` with fields: totalFilesChanged, totalLinesAdded, totalLinesDeleted

- [x] Task 4: Implement DiffMetadataExtractor in service module (AC: #1, #4, #5, #6, #7, #8)
  - [x] 4.1 Create `DiffMetadataExtractor.java` as a `@Service` in `com.aicodereview.service`
  - [x] 4.2 Implement diff splitting logic: split raw diff into per-file sections by `diff --git` delimiter
  - [x] 4.3 Implement header parsing: extract oldPath/newPath from `--- a/` and `+++ b/` lines
  - [x] 4.4 Implement change type detection from mode indicators (`new file mode`, `deleted file mode`, `rename from/to`)
  - [x] 4.5 Implement line counting: `+` lines (excluding `+++`) and `-` lines (excluding `---`)
  - [x] 4.6 Implement binary file detection: `Binary files ... differ` pattern
  - [x] 4.7 Implement language detection: delegate to `Language.fromFileName()` using newPath (or oldPath for DELETE)
  - [x] 4.8 Assemble DiffMetadata with FileDiffInfo list and aggregated DiffStatistics

- [x] Task 5: Write comprehensive unit tests (AC: #10)
  - [x] 5.1 Create `DiffMetadataExtractorTest.java` in service test module
  - [x] 5.2 Test MODIFY: standard file modification with added/deleted lines
  - [x] 5.3 Test ADD: new file with `new file mode` indicator
  - [x] 5.4 Test DELETE: deleted file with `deleted file mode` indicator
  - [x] 5.5 Test RENAME: file rename with `rename from/to` and optional content changes
  - [x] 5.6 Test binary file: `Binary files ... differ` detection
  - [x] 5.7 Test multi-file diff: multiple files in one diff with correct aggregate statistics
  - [x] 5.8 Test empty diff: returns empty DiffMetadata with zero statistics
  - [x] 5.9 Test language detection integration: verify correct Language enum assigned per file extension
  - [x] 5.10 Test real Git diff sample: use a realistic multi-file diff with mixed change types

- [x] Task 6: Update ErrorCodeTest count assertion if ChangeType/Language added to ErrorCode (AC: all)
  - [x] 6.1 Check if any ErrorCode references need updating — NOT needed, new enums are unrelated to ErrorCode

- [x] Task 7: Verify build and run full test suite (AC: all)
  - [x] 7.1 Run `mvn compile test` across all modules
  - [x] 7.2 Verify no regressions in existing tests — all unit tests pass; API integration test failures are pre-existing (Docker dependency)
  - [x] 7.3 Verify new tests pass — 55 new tests (2 ChangeTypeTest + 38 LanguageTest + 15 DiffMetadataExtractorTest)

### Review Follow-ups (AI) — Fixed

- [x] [AI-Review][HIGH] Binary files missing path extraction — Added `extractPathsFromDiffHeader()` fallback to extract paths from `diff --git a/X b/Y` header line when `---`/`+++` lines are absent. Updated binary file test to assert on paths and language.
- [x] [AI-Review][MEDIUM] Missing Binary + Rename edge case test — Added `shouldDetectBinaryRename()` test covering rename + binary combination with correct paths, changeType, and zero line counts.
- [x] [AI-Review][MEDIUM] Header patterns parsed from hunk content — Added `inHunk` flag to `parseFileSection()` to stop scanning for `---`/`+++`/metadata patterns after first `@@` line. Removed unnecessary `"--- ---"` / `"+++ +++"` guards.

## Dev Notes

### Architecture Decisions

**Module Placement**:
- `ChangeType` enum → `common/enums/` (shared across modules, used by future Stories 3.2, 3.3)
- `Language` enum → `common/enums/` (shared, used by Story 3.3 context assembly)
- `DiffMetadata`, `FileDiffInfo`, `DiffStatistics` → `common/dto/` (data transfer objects used across service boundaries)
- `DiffMetadataExtractor` → `service/` module (business logic, no external dependencies)
- [Source: epics.md - Story 3.1 - "Language 枚举定义在 common 模块", "ChangeType 枚举定义在 common 模块"]

**Design Philosophy — AI-First Approach**:
- The redesigned Epic 3 (commit d77d864) moved from heavy code parsing to AI-driven hybrid approach
- AI models natively understand Unified Diff format — we do NOT parse hunk content or build Hunk objects
- DiffMetadataExtractor only extracts structural metadata (file paths, change types, line counts)
- The raw Diff string is passed directly to AI in Story 3.3 (ReviewContextAssembler)
- [Source: epics.md - Story 3.1 - "设计理念" and "不解析 hunk 内容"]

**No Database Changes in This Story**:
- Database changes (`code_context` JSONB column) are in Story 3.3
- DiffMetadataExtractor is a pure in-memory service with no persistence
- Next Flyway migration version is V7 (not needed here)

### Existing Code to Reuse / Integrate With

**Enum Pattern** (follow existing FailureType.java, TaskType.java):
```java
// Example: ChangeType follows same pattern as existing enums
public enum ChangeType {
    ADD, MODIFY, DELETE, RENAME
}
```

**DTO Pattern** (follow existing CreateReviewTaskRequest, ReviewTaskDTO):
- Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` Lombok annotations
- DTOs in `common/dto/` package (existing convention)

**Test Pattern** (follow existing FailureTypeTest, ErrorCodeTest):
- Enum tests: assert count of values, assert specific mappings
- Service tests: `@ExtendWith(MockitoExtension.class)` — but DiffMetadataExtractor has NO dependencies to mock, so plain JUnit tests suffice

### File Structure (MUST follow)

```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   ├── enums/
│   │   ├── ChangeType.java              ← NEW: ADD, MODIFY, DELETE, RENAME
│   │   └── Language.java                ← NEW: JAVA, PYTHON, JS, TS, GO, etc. + fromExtension()
│   └── dto/
│       ├── DiffMetadata.java            ← NEW: files + statistics
│       ├── FileDiffInfo.java            ← NEW: oldPath, newPath, changeType, language, isBinary
│       └── DiffStatistics.java          ← NEW: totalFilesChanged, totalLinesAdded, totalLinesDeleted
│
├── ai-code-review-common/src/test/java/com/aicodereview/common/
│   └── enums/
│       ├── ChangeTypeTest.java          ← NEW: Enum validation tests
│       └── LanguageTest.java            ← NEW: Extension mapping tests
│
├── ai-code-review-service/src/main/java/com/aicodereview/service/
│   └── DiffMetadataExtractor.java       ← NEW: @Service, extractMetadata()
│
└── ai-code-review-service/src/test/java/com/aicodereview/service/
    └── DiffMetadataExtractorTest.java   ← NEW: Comprehensive unit tests
```

### Implementation Patterns

**Language enum with extension mapping:**
```java
public enum Language {
    JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST, RUBY, PHP, KOTLIN,
    C, CPP, CSHARP, SWIFT,
    YAML, JSON, XML, MARKDOWN, SQL, SHELL, DOCKERFILE,
    UNKNOWN;

    private static final Map<String, Language> EXTENSION_MAP = Map.ofEntries(
        Map.entry(".java", JAVA),
        Map.entry(".py", PYTHON),
        Map.entry(".js", JAVASCRIPT),
        Map.entry(".ts", TYPESCRIPT),
        Map.entry(".go", GO),
        Map.entry(".rs", RUST),
        Map.entry(".rb", RUBY),
        Map.entry(".php", PHP),
        Map.entry(".kt", KOTLIN),
        Map.entry(".c", C),
        Map.entry(".cpp", CPP),
        Map.entry(".h", C),         // C header
        Map.entry(".cs", CSHARP),
        Map.entry(".swift", SWIFT),
        Map.entry(".yml", YAML),
        Map.entry(".yaml", YAML),
        Map.entry(".json", JSON),
        Map.entry(".xml", XML),
        Map.entry(".md", MARKDOWN),
        Map.entry(".sql", SQL),
        Map.entry(".sh", SHELL)
    );

    public static Language fromExtension(String extension) {
        if (extension == null) return UNKNOWN;
        return EXTENSION_MAP.getOrDefault(extension.toLowerCase(), UNKNOWN);
    }

    public static Language fromFileName(String fileName) {
        if (fileName == null) return UNKNOWN;
        // Handle "Dockerfile" (no extension)
        if (fileName.endsWith("Dockerfile") || fileName.endsWith(".dockerfile")) return DOCKERFILE;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return UNKNOWN;
        return fromExtension(fileName.substring(dotIndex));
    }
}
```

**DiffMetadataExtractor core parsing logic:**
```java
@Service
public class DiffMetadataExtractor {

    public DiffMetadata extractMetadata(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            return DiffMetadata.builder()
                .files(List.of())
                .statistics(DiffStatistics.builder()
                    .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0).build())
                .build();
        }

        // Split by "diff --git" delimiter
        // Parse each file section for paths, change type, line counts, binary
        // Aggregate statistics
    }
}
```

**Real Git Diff sample for testing (multi-file, mixed types):**
```
diff --git a/src/main/java/App.java b/src/main/java/App.java
index abc1234..def5678 100644
--- a/src/main/java/App.java
+++ b/src/main/java/App.java
@@ -1,5 +1,7 @@
 public class App {
     public static void main(String[] args) {
-        System.out.println("Hello");
+        System.out.println("Hello World");
+        System.out.println("Version 2");
     }
 }
diff --git a/src/main/java/NewFile.java b/src/main/java/NewFile.java
new file mode 100644
index 0000000..abc1234
--- /dev/null
+++ b/src/main/java/NewFile.java
@@ -0,0 +1,3 @@
+public class NewFile {
+    // new file
+}
diff --git a/old/Removed.py b/old/Removed.py
deleted file mode 100644
index def5678..0000000
--- a/old/Removed.py
+++ /dev/null
@@ -1,2 +0,0 @@
-def removed():
-    pass
diff --git a/docs/old-name.md b/docs/new-name.md
similarity index 100%
rename from docs/old-name.md
rename to docs/new-name.md
```

Expected test results for above:
- 4 files changed
- File 1: MODIFY, JAVA, oldPath="src/main/java/App.java", +2 -1
- File 2: ADD, JAVA, newPath="src/main/java/NewFile.java", +3 -0
- File 3: DELETE, PYTHON, oldPath="old/Removed.py", +0 -2
- File 4: RENAME, MARKDOWN, from "docs/old-name.md" to "docs/new-name.md", +0 -0
- Aggregate: totalFilesChanged=4, totalLinesAdded=5, totalLinesDeleted=3

### Critical Patterns from Previous Stories (MUST follow)

1. **-parameters compiler flag NOT enabled**: Use `@PathVariable("id")` with explicit value. Not directly relevant here (no controllers), but maintain awareness.
2. **Service layer returns DTOs**: DiffMetadataExtractor returns `DiffMetadata` (a DTO), NOT entities. Consistent with project convention.
3. **Enum test pattern**: Follow `FailureTypeTest` — assert enum size (`assertEquals(N, ChangeType.values().length)`), test descriptions.
4. **Lombok DTOs**: Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` for all DTO classes.
5. **No integration tests needed**: DiffMetadataExtractor is a pure in-memory parser with no external dependencies (no DB, no Redis, no HTTP). Unit tests suffice.
6. **Windows file lock**: Use `mvn compile test` (omit `clean` phase) if JAR is locked.

### Project Structure Notes

- All new files align with existing package conventions (`com.aicodereview.common.enums`, `com.aicodereview.common.dto`, `com.aicodereview.service`)
- No new Maven dependencies required — pure Java string parsing
- No Spring Boot configuration changes needed
- No Flyway migrations needed (database changes are in Story 3.3)

### Edge Cases to Handle

1. **Empty/null/blank diff input** → Return empty DiffMetadata with zero statistics
2. **Diff with only binary files** → `isBinary: true`, zero line counts for that file
3. **Rename without content changes** → RENAME type, zero added/deleted lines
4. **Rename WITH content changes** → RENAME type, count the changed lines
5. **Binary + Rename** → `isBinary: true` takes precedence, line counts must be 0
6. **Files with no extension** → `Language.UNKNOWN`
7. **Case-insensitive extensions** → `.JAVA` should map to JAVA
8. **Special file names** → `Dockerfile` (no extension) → DOCKERFILE
9. **`/dev/null` paths** → oldPath null for ADD (`--- /dev/null`), newPath null for DELETE (`+++ /dev/null`)
10. **Multiple hunks in one file** → Aggregate line counts across all hunks
11. **FileDiffInfo ordering** → Preserve original diff order (FIFO, not sorted)
12. **Empty string fileName** → `Language.UNKNOWN`

### Dependencies

**No new Maven dependencies required**:
- Pure Java String parsing (String.split, startsWith, indexOf)
- All existing common module dependencies (Lombok, Jackson annotations) are sufficient

### References

- [Source: epics.md - Epic 3: 代码获取与AI审查上下文组装 - redesigned approach]
- [Source: epics.md - Story 3.1: Diff 元数据提取与变更分析 - full acceptance criteria]
- [Source: epics.md - FR 1.3: 代码获取与AI审查上下文组装 - "Diff 元数据提取"]
- [Source: architecture.md - Module Structure - common, service modules]
- [Source: architecture.md - Package Naming - com.aicodereview.{module}]
- [Source: architecture.md - Service Layer Patterns]
- [Source: 2-7-task-retry-mechanism.md - FailureType enum pattern, test patterns]
- [Source: commit d77d864 - "Redesign Epic 3 from heavy code parsing to AI-driven hybrid approach"]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- **splitIntoFileSections regex fix**: Initial implementation used `(?=^diff --git )` regex with `String.split()`, but `^` doesn't work in multiline mode without `Pattern.MULTILINE`. Fixed by switching to line-by-line iteration with StringBuilder accumulation.
- **Code review: binary path extraction**: Binary file diffs lack `---`/`+++` lines, causing null paths. Added `extractPathsFromDiffHeader()` to parse `diff --git a/X b/Y` as fallback.
- **Code review: header parsing boundary**: `parseFileSection()` was scanning hunk content for `---`/`+++` patterns. Added `inHunk` guard to stop metadata parsing after first `@@` marker.

### Completion Notes List

- All 10 ACs implemented and verified
- 2 unit tests in ChangeTypeTest (enum count + valueOf)
- 38 unit tests in LanguageTest (24 extension mappings, case-insensitivity, null/empty, file paths, Dockerfile, no-extension)
- 15 unit tests in DiffMetadataExtractorTest (null/empty/blank, MODIFY, ADD, DELETE, RENAME pure + with content, binary with path assertions, binary+rename, multi-file, language detection, real Git diff sample, multiple hunks)
- Language enum supports 21 languages with 24 extension mappings (including .jsx, .tsx, .dockerfile)
- Pre-existing API integration test failures (QueueIntegrationTest, RetryIntegrationTest, ReviewTaskIntegrationTest) are Docker-dependent and unrelated to Story 3.1
- Adversarial code review: 1 HIGH + 2 MEDIUM issues found and fixed, 3 LOW issues documented

### File List

**New Files:**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/ChangeType.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/Language.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/DiffMetadata.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/FileDiffInfo.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/DiffStatistics.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/ChangeTypeTest.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/LanguageTest.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/DiffMetadataExtractor.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/DiffMetadataExtractorTest.java`

### Change Log

| Change | File | Description |
|--------|------|-------------|
| ADD | ChangeType.java | Enum with 4 values: ADD, MODIFY, DELETE, RENAME |
| ADD | Language.java | Enum with 21 languages, fromExtension() and fromFileName() static methods |
| ADD | DiffMetadata.java | DTO: List<FileDiffInfo> files + DiffStatistics statistics |
| ADD | FileDiffInfo.java | DTO: oldPath, newPath, changeType, language, isBinary |
| ADD | DiffStatistics.java | DTO: totalFilesChanged, totalLinesAdded, totalLinesDeleted |
| ADD | DiffMetadataExtractor.java | @Service: extractMetadata(rawDiff) parses Unified Diff into structured metadata |
| ADD | ChangeTypeTest.java | 2 unit tests for enum validation |
| ADD | LanguageTest.java | 38 unit tests for extension mapping and language detection |
| ADD | DiffMetadataExtractorTest.java | 14 unit tests covering all change types, edge cases, and real Git diff samples |
