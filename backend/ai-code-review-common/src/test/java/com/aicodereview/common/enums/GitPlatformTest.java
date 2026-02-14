package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitPlatform Enum Tests")
class GitPlatformTest {

    @Test
    @DisplayName("Should have exactly 3 platform values")
    void shouldHaveCorrectNumberOfValues() {
        assertThat(GitPlatform.values()).hasSize(3);
        assertThat(GitPlatform.values()).containsExactlyInAnyOrder(
                GitPlatform.GITHUB, GitPlatform.GITLAB, GitPlatform.AWS_CODECOMMIT);
    }

    @ParameterizedTest
    @CsvSource({
            "https://github.com/owner/repo, GITHUB",
            "https://github.com/owner/repo.git, GITHUB",
            "https://GITHUB.COM/Owner/Repo, GITHUB",
            "https://gitlab.com/namespace/project, GITLAB",
            "https://gitlab.example.com/ns/proj, GITLAB",
            "https://my-gitlab.corp.com/team/app, GITLAB",
            "https://codecommit.us-east-1.amazonaws.com/v1/repos/my-repo, AWS_CODECOMMIT"
    })
    @DisplayName("fromRepoUrl should detect platform from URL")
    void fromRepoUrl_shouldDetectPlatform(String url, String expectedPlatform) {
        assertThat(GitPlatform.fromRepoUrl(url)).isEqualTo(GitPlatform.valueOf(expectedPlatform));
    }

    @Test
    @DisplayName("fromRepoUrl should return null for unrecognized URLs")
    void fromRepoUrl_shouldReturnNullForUnrecognized() {
        assertThat(GitPlatform.fromRepoUrl("https://bitbucket.org/owner/repo")).isNull();
        assertThat(GitPlatform.fromRepoUrl("https://example.com/repo")).isNull();
    }

    @Test
    @DisplayName("fromRepoUrl should return null for null or empty")
    void fromRepoUrl_shouldReturnNullForNullOrEmpty() {
        assertThat(GitPlatform.fromRepoUrl(null)).isNull();
        assertThat(GitPlatform.fromRepoUrl("")).isNull();
    }
}
