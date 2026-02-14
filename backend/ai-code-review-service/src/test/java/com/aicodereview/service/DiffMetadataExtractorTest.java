package com.aicodereview.service;

import com.aicodereview.common.dto.DiffMetadata;
import com.aicodereview.common.dto.FileDiffInfo;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiffMetadataExtractor Tests")
class DiffMetadataExtractorTest {

    private DiffMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DiffMetadataExtractor();
    }

    @Nested
    @DisplayName("Empty / Null Input")
    class EmptyInput {

        @Test
        @DisplayName("Should return empty metadata for null input")
        void shouldReturnEmptyForNull() {
            DiffMetadata result = extractor.extractMetadata(null);

            assertThat(result.getFiles()).isEmpty();
            assertThat(result.getStatistics().getTotalFilesChanged()).isZero();
            assertThat(result.getStatistics().getTotalLinesAdded()).isZero();
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }

        @Test
        @DisplayName("Should return empty metadata for empty string")
        void shouldReturnEmptyForEmptyString() {
            DiffMetadata result = extractor.extractMetadata("");

            assertThat(result.getFiles()).isEmpty();
            assertThat(result.getStatistics().getTotalFilesChanged()).isZero();
        }

        @Test
        @DisplayName("Should return empty metadata for blank string")
        void shouldReturnEmptyForBlankString() {
            DiffMetadata result = extractor.extractMetadata("   \n\t  ");

            assertThat(result.getFiles()).isEmpty();
            assertThat(result.getStatistics().getTotalFilesChanged()).isZero();
        }
    }

    @Nested
    @DisplayName("MODIFY Change Type")
    class ModifyChangeType {

        @Test
        @DisplayName("Should detect MODIFY with correct line counts")
        void shouldDetectModifyWithLineCounts() {
            String diff = """
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
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.getChangeType()).isEqualTo(ChangeType.MODIFY);
            assertThat(file.getOldPath()).isEqualTo("src/main/java/App.java");
            assertThat(file.getNewPath()).isEqualTo("src/main/java/App.java");
            assertThat(file.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(file.isBinary()).isFalse();
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(2);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ADD Change Type")
    class AddChangeType {

        @Test
        @DisplayName("Should detect ADD with new file mode")
        void shouldDetectAdd() {
            String diff = """
                    diff --git a/src/main/java/NewFile.java b/src/main/java/NewFile.java
                    new file mode 100644
                    index 0000000..abc1234
                    --- /dev/null
                    +++ b/src/main/java/NewFile.java
                    @@ -0,0 +1,3 @@
                    +public class NewFile {
                    +    // new file
                    +}
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.getChangeType()).isEqualTo(ChangeType.ADD);
            assertThat(file.getOldPath()).isNull();
            assertThat(file.getNewPath()).isEqualTo("src/main/java/NewFile.java");
            assertThat(file.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(3);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }
    }

    @Nested
    @DisplayName("DELETE Change Type")
    class DeleteChangeType {

        @Test
        @DisplayName("Should detect DELETE with deleted file mode")
        void shouldDetectDelete() {
            String diff = """
                    diff --git a/old/Removed.py b/old/Removed.py
                    deleted file mode 100644
                    index def5678..0000000
                    --- a/old/Removed.py
                    +++ /dev/null
                    @@ -1,2 +0,0 @@
                    -def removed():
                    -    pass
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.getChangeType()).isEqualTo(ChangeType.DELETE);
            assertThat(file.getOldPath()).isEqualTo("old/Removed.py");
            assertThat(file.getNewPath()).isNull();
            assertThat(file.getLanguage()).isEqualTo(Language.PYTHON);
            assertThat(result.getStatistics().getTotalLinesAdded()).isZero();
            assertThat(result.getStatistics().getTotalLinesDeleted()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("RENAME Change Type")
    class RenameChangeType {

        @Test
        @DisplayName("Should detect RENAME without content changes")
        void shouldDetectRenameWithoutContentChanges() {
            String diff = """
                    diff --git a/docs/old-name.md b/docs/new-name.md
                    similarity index 100%
                    rename from docs/old-name.md
                    rename to docs/new-name.md
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.getChangeType()).isEqualTo(ChangeType.RENAME);
            assertThat(file.getOldPath()).isEqualTo("docs/old-name.md");
            assertThat(file.getNewPath()).isEqualTo("docs/new-name.md");
            assertThat(file.getLanguage()).isEqualTo(Language.MARKDOWN);
            assertThat(result.getStatistics().getTotalLinesAdded()).isZero();
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }

        @Test
        @DisplayName("Should detect RENAME with content changes")
        void shouldDetectRenameWithContentChanges() {
            String diff = """
                    diff --git a/src/old.java b/src/new.java
                    similarity index 80%
                    rename from src/old.java
                    rename to src/new.java
                    --- a/src/old.java
                    +++ b/src/new.java
                    @@ -1,3 +1,4 @@
                     public class Foo {
                    +    // renamed and modified
                         void bar() {}
                     }
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.getChangeType()).isEqualTo(ChangeType.RENAME);
            assertThat(file.getOldPath()).isEqualTo("src/old.java");
            assertThat(file.getNewPath()).isEqualTo("src/new.java");
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(1);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }
    }

    @Nested
    @DisplayName("Binary Files")
    class BinaryFiles {

        @Test
        @DisplayName("Should detect binary files with correct paths and language")
        void shouldDetectBinaryFiles() {
            String diff = """
                    diff --git a/image.png b/image.png
                    new file mode 100644
                    index 0000000..abc1234
                    Binary files /dev/null and b/image.png differ
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.isBinary()).isTrue();
            assertThat(file.getChangeType()).isEqualTo(ChangeType.ADD);
            assertThat(file.getOldPath()).isNull();
            assertThat(file.getNewPath()).isEqualTo("image.png");
            assertThat(file.getLanguage()).isEqualTo(Language.UNKNOWN);
            assertThat(result.getStatistics().getTotalLinesAdded()).isZero();
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }

        @Test
        @DisplayName("Should detect binary file rename with correct paths")
        void shouldDetectBinaryRename() {
            String diff = """
                    diff --git a/assets/old-logo.png b/assets/new-logo.png
                    similarity index 100%
                    rename from assets/old-logo.png
                    rename to assets/new-logo.png
                    Binary files a/assets/old-logo.png and b/assets/new-logo.png differ
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            FileDiffInfo file = result.getFiles().get(0);
            assertThat(file.isBinary()).isTrue();
            assertThat(file.getChangeType()).isEqualTo(ChangeType.RENAME);
            assertThat(file.getOldPath()).isEqualTo("assets/old-logo.png");
            assertThat(file.getNewPath()).isEqualTo("assets/new-logo.png");
            assertThat(result.getStatistics().getTotalLinesAdded()).isZero();
            assertThat(result.getStatistics().getTotalLinesDeleted()).isZero();
        }
    }

    @Nested
    @DisplayName("Multi-File Diff")
    class MultiFileDiff {

        @Test
        @DisplayName("Should parse multiple files with correct aggregate statistics")
        void shouldParseMultipleFiles() {
            String diff = """
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
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            // Verify file count and order
            assertThat(result.getFiles()).hasSize(4);

            // File 1: MODIFY Java
            FileDiffInfo file1 = result.getFiles().get(0);
            assertThat(file1.getChangeType()).isEqualTo(ChangeType.MODIFY);
            assertThat(file1.getLanguage()).isEqualTo(Language.JAVA);

            // File 2: ADD Java
            FileDiffInfo file2 = result.getFiles().get(1);
            assertThat(file2.getChangeType()).isEqualTo(ChangeType.ADD);
            assertThat(file2.getLanguage()).isEqualTo(Language.JAVA);

            // File 3: DELETE Python
            FileDiffInfo file3 = result.getFiles().get(2);
            assertThat(file3.getChangeType()).isEqualTo(ChangeType.DELETE);
            assertThat(file3.getLanguage()).isEqualTo(Language.PYTHON);

            // File 4: RENAME Markdown
            FileDiffInfo file4 = result.getFiles().get(3);
            assertThat(file4.getChangeType()).isEqualTo(ChangeType.RENAME);
            assertThat(file4.getLanguage()).isEqualTo(Language.MARKDOWN);

            // Aggregate statistics: +2 -1 (modify) + +3 (add) + -2 (delete) + 0 (rename)
            assertThat(result.getStatistics().getTotalFilesChanged()).isEqualTo(4);
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(5);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Language Detection Integration")
    class LanguageDetection {

        @Test
        @DisplayName("Should detect correct language for various file types")
        void shouldDetectLanguageForVariousTypes() {
            String diff = """
                    diff --git a/app.ts b/app.ts
                    index abc..def 100644
                    --- a/app.ts
                    +++ b/app.ts
                    @@ -1 +1 @@
                    -old
                    +new
                    diff --git a/config.yml b/config.yml
                    index abc..def 100644
                    --- a/config.yml
                    +++ b/config.yml
                    @@ -1 +1 @@
                    -old
                    +new
                    diff --git a/script.sh b/script.sh
                    index abc..def 100644
                    --- a/script.sh
                    +++ b/script.sh
                    @@ -1 +1 @@
                    -old
                    +new
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(3);
            assertThat(result.getFiles().get(0).getLanguage()).isEqualTo(Language.TYPESCRIPT);
            assertThat(result.getFiles().get(1).getLanguage()).isEqualTo(Language.YAML);
            assertThat(result.getFiles().get(2).getLanguage()).isEqualTo(Language.SHELL);
        }
    }

    @Nested
    @DisplayName("Real Git Diff Sample")
    class RealGitDiff {

        @Test
        @DisplayName("Should correctly parse a realistic multi-file diff")
        void shouldParseRealisticDiff() {
            // This is the exact sample from the story file
            String diff = "diff --git a/src/main/java/App.java b/src/main/java/App.java\n"
                    + "index abc1234..def5678 100644\n"
                    + "--- a/src/main/java/App.java\n"
                    + "+++ b/src/main/java/App.java\n"
                    + "@@ -1,5 +1,7 @@\n"
                    + " public class App {\n"
                    + "     public static void main(String[] args) {\n"
                    + "-        System.out.println(\"Hello\");\n"
                    + "+        System.out.println(\"Hello World\");\n"
                    + "+        System.out.println(\"Version 2\");\n"
                    + "     }\n"
                    + " }\n"
                    + "diff --git a/src/main/java/NewFile.java b/src/main/java/NewFile.java\n"
                    + "new file mode 100644\n"
                    + "index 0000000..abc1234\n"
                    + "--- /dev/null\n"
                    + "+++ b/src/main/java/NewFile.java\n"
                    + "@@ -0,0 +1,3 @@\n"
                    + "+public class NewFile {\n"
                    + "+    // new file\n"
                    + "+}\n"
                    + "diff --git a/old/Removed.py b/old/Removed.py\n"
                    + "deleted file mode 100644\n"
                    + "index def5678..0000000\n"
                    + "--- a/old/Removed.py\n"
                    + "+++ /dev/null\n"
                    + "@@ -1,2 +0,0 @@\n"
                    + "-def removed():\n"
                    + "-    pass\n"
                    + "diff --git a/docs/old-name.md b/docs/new-name.md\n"
                    + "similarity index 100%\n"
                    + "rename from docs/old-name.md\n"
                    + "rename to docs/new-name.md\n";

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(4);

            // File 1: MODIFY, JAVA, +2 -1
            assertThat(result.getFiles().get(0).getChangeType()).isEqualTo(ChangeType.MODIFY);
            assertThat(result.getFiles().get(0).getOldPath()).isEqualTo("src/main/java/App.java");
            assertThat(result.getFiles().get(0).getLanguage()).isEqualTo(Language.JAVA);

            // File 2: ADD, JAVA, +3
            assertThat(result.getFiles().get(1).getChangeType()).isEqualTo(ChangeType.ADD);
            assertThat(result.getFiles().get(1).getOldPath()).isNull();
            assertThat(result.getFiles().get(1).getNewPath()).isEqualTo("src/main/java/NewFile.java");

            // File 3: DELETE, PYTHON, -2
            assertThat(result.getFiles().get(2).getChangeType()).isEqualTo(ChangeType.DELETE);
            assertThat(result.getFiles().get(2).getOldPath()).isEqualTo("old/Removed.py");
            assertThat(result.getFiles().get(2).getNewPath()).isNull();

            // File 4: RENAME, MARKDOWN, +0 -0
            assertThat(result.getFiles().get(3).getChangeType()).isEqualTo(ChangeType.RENAME);
            assertThat(result.getFiles().get(3).getOldPath()).isEqualTo("docs/old-name.md");
            assertThat(result.getFiles().get(3).getNewPath()).isEqualTo("docs/new-name.md");

            // Aggregate: 4 files, +5, -3
            assertThat(result.getStatistics().getTotalFilesChanged()).isEqualTo(4);
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(5);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Multiple Hunks")
    class MultipleHunks {

        @Test
        @DisplayName("Should aggregate line counts across multiple hunks")
        void shouldAggregateAcrossHunks() {
            String diff = """
                    diff --git a/src/App.java b/src/App.java
                    index abc..def 100644
                    --- a/src/App.java
                    +++ b/src/App.java
                    @@ -1,3 +1,4 @@
                     class App {
                    +    int x;
                         void foo() {}
                     }
                    @@ -10,3 +11,4 @@
                     class Other {
                    -    void old() {}
                    +    void bar() {}
                    +    void baz() {}
                     }
                    """;

            DiffMetadata result = extractor.extractMetadata(diff);

            assertThat(result.getFiles()).hasSize(1);
            // Hunk 1: +1, Hunk 2: +2 -1 → total +3 -1
            assertThat(result.getStatistics().getTotalLinesAdded()).isEqualTo(3);
            assertThat(result.getStatistics().getTotalLinesDeleted()).isEqualTo(1);
        }
    }
}
