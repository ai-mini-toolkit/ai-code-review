package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Language Enum Tests")
class LanguageTest {

    @Test
    @DisplayName("Should have exactly 21 language values")
    void shouldHaveCorrectNumberOfValues() {
        assertThat(Language.values()).hasSize(21);
    }

    @ParameterizedTest
    @CsvSource({
            ".java, JAVA",
            ".py, PYTHON",
            ".js, JAVASCRIPT",
            ".jsx, JAVASCRIPT",
            ".ts, TYPESCRIPT",
            ".tsx, TYPESCRIPT",
            ".go, GO",
            ".rs, RUST",
            ".rb, RUBY",
            ".php, PHP",
            ".kt, KOTLIN",
            ".c, C",
            ".cpp, CPP",
            ".h, C",
            ".cs, CSHARP",
            ".swift, SWIFT",
            ".yml, YAML",
            ".yaml, YAML",
            ".json, JSON",
            ".xml, XML",
            ".md, MARKDOWN",
            ".sql, SQL",
            ".sh, SHELL",
            ".dockerfile, DOCKERFILE"
    })
    @DisplayName("fromExtension should map known extensions correctly")
    void fromExtension_shouldMapKnownExtensions(String extension, String expectedLanguage) {
        assertThat(Language.fromExtension(extension)).isEqualTo(Language.valueOf(expectedLanguage));
    }

    @Test
    @DisplayName("fromExtension should return UNKNOWN for unrecognized extensions")
    void fromExtension_shouldReturnUnknownForUnrecognized() {
        assertThat(Language.fromExtension(".xyz")).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromExtension(".dat")).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromExtension(".bin")).isEqualTo(Language.UNKNOWN);
    }

    @Test
    @DisplayName("fromExtension should return UNKNOWN for null or empty")
    void fromExtension_shouldReturnUnknownForNullOrEmpty() {
        assertThat(Language.fromExtension(null)).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromExtension("")).isEqualTo(Language.UNKNOWN);
    }

    @Test
    @DisplayName("fromExtension should be case-insensitive")
    void fromExtension_shouldBeCaseInsensitive() {
        assertThat(Language.fromExtension(".JAVA")).isEqualTo(Language.JAVA);
        assertThat(Language.fromExtension(".Py")).isEqualTo(Language.PYTHON);
        assertThat(Language.fromExtension(".JS")).isEqualTo(Language.JAVASCRIPT);
    }

    @ParameterizedTest
    @CsvSource({
            "src/main/java/App.java, JAVA",
            "test.py, PYTHON",
            "components/Header.tsx, TYPESCRIPT",
            "config.yml, YAML",
            "README.md, MARKDOWN",
            "db/migration/V1.sql, SQL",
            "scripts/build.sh, SHELL"
    })
    @DisplayName("fromFileName should detect language from file paths")
    void fromFileName_shouldDetectFromFilePaths(String fileName, String expectedLanguage) {
        assertThat(Language.fromFileName(fileName)).isEqualTo(Language.valueOf(expectedLanguage));
    }

    @Test
    @DisplayName("fromFileName should detect Dockerfile without extension")
    void fromFileName_shouldDetectDockerfile() {
        assertThat(Language.fromFileName("Dockerfile")).isEqualTo(Language.DOCKERFILE);
        assertThat(Language.fromFileName("path/to/Dockerfile")).isEqualTo(Language.DOCKERFILE);
        assertThat(Language.fromFileName("build.dockerfile")).isEqualTo(Language.DOCKERFILE);
    }

    @Test
    @DisplayName("fromFileName should return UNKNOWN for files without extension")
    void fromFileName_shouldReturnUnknownForNoExtension() {
        assertThat(Language.fromFileName("Makefile")).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromFileName("LICENSE")).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromFileName("Gemfile")).isEqualTo(Language.UNKNOWN);
    }

    @Test
    @DisplayName("fromFileName should return UNKNOWN for null or empty")
    void fromFileName_shouldReturnUnknownForNullOrEmpty() {
        assertThat(Language.fromFileName(null)).isEqualTo(Language.UNKNOWN);
        assertThat(Language.fromFileName("")).isEqualTo(Language.UNKNOWN);
    }
}
