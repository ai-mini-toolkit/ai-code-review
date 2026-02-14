package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import com.aicodereview.common.enums.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeContext Serialization Tests")
class CodeContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should serialize and deserialize CodeContext via JSON")
    void shouldRoundTripThroughJson() throws Exception {
        CodeContext original = CodeContext.builder()
                .rawDiff("diff --git a/f.java b/f.java\n--- a/f.java\n+++ b/f.java\n")
                .files(List.of(
                        FileInfo.builder()
                                .path("src/App.java")
                                .changeType(ChangeType.MODIFY)
                                .language(Language.JAVA)
                                .build(),
                        FileInfo.builder()
                                .path("README.md")
                                .changeType(ChangeType.ADD)
                                .language(Language.MARKDOWN)
                                .build()
                ))
                .fileContents(Map.of(
                        "src/App.java", "public class App { }",
                        "README.md", "# Hello"
                ))
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(2)
                        .totalLinesAdded(5)
                        .totalLinesDeleted(2)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .prTitle("Add feature")
                        .prDescription("Description here")
                        .author("dev")
                        .branch("feature/x")
                        .commitHash("abc123")
                        .taskType(TaskType.PULL_REQUEST)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(original);
        CodeContext deserialized = objectMapper.readValue(json, CodeContext.class);

        assertThat(deserialized.getRawDiff()).isEqualTo(original.getRawDiff());
        assertThat(deserialized.getFiles()).hasSize(2);
        assertThat(deserialized.getFiles().get(0).getPath()).isEqualTo("src/App.java");
        assertThat(deserialized.getFiles().get(0).getChangeType()).isEqualTo(ChangeType.MODIFY);
        assertThat(deserialized.getFiles().get(0).getLanguage()).isEqualTo(Language.JAVA);
        assertThat(deserialized.getFileContents()).hasSize(2);
        assertThat(deserialized.getFileContents().get("src/App.java")).isEqualTo("public class App { }");
        assertThat(deserialized.getStatistics().getTotalFilesChanged()).isEqualTo(2);
        assertThat(deserialized.getStatistics().getTotalLinesAdded()).isEqualTo(5);
        assertThat(deserialized.getTaskMeta().getPrTitle()).isEqualTo("Add feature");
        assertThat(deserialized.getTaskMeta().getTaskType()).isEqualTo(TaskType.PULL_REQUEST);
    }

    @Test
    @DisplayName("Should handle empty CodeContext serialization")
    void shouldHandleEmptyCodeContext() throws Exception {
        CodeContext empty = CodeContext.builder()
                .rawDiff("")
                .files(List.of())
                .fileContents(Map.of())
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .author("user").branch("main").commitHash("sha")
                        .taskType(TaskType.PUSH).build())
                .build();

        String json = objectMapper.writeValueAsString(empty);
        CodeContext deserialized = objectMapper.readValue(json, CodeContext.class);

        assertThat(deserialized.getRawDiff()).isEmpty();
        assertThat(deserialized.getFiles()).isEmpty();
        assertThat(deserialized.getFileContents()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null PR fields in TaskMetadata")
    void shouldHandleNullPrFields() throws Exception {
        CodeContext context = CodeContext.builder()
                .rawDiff("diff")
                .files(List.of())
                .fileContents(Map.of())
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .author("user").branch("main").commitHash("sha")
                        .taskType(TaskType.PUSH)
                        .prTitle(null).prDescription(null)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(context);
        CodeContext deserialized = objectMapper.readValue(json, CodeContext.class);

        assertThat(deserialized.getTaskMeta().getPrTitle()).isNull();
        assertThat(deserialized.getTaskMeta().getPrDescription()).isNull();
    }
}
