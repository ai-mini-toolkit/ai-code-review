package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AWSCodeCommitClient Tests")
class AWSCodeCommitClientTest {

    private AWSCodeCommitClient client;

    @BeforeEach
    void setUp() {
        client = new AWSCodeCommitClient();
    }

    @Test
    @DisplayName("getPlatform should return AWS_CODECOMMIT")
    void shouldReturnAwsCodeCommit() {
        assertThat(client.getPlatform()).isEqualTo(GitPlatform.AWS_CODECOMMIT);
    }

    @Test
    @DisplayName("getFileContent should throw UnsupportedOperationException")
    void getFileContentShouldThrow() {
        assertThatThrownBy(() -> client.getFileContent(
                "https://codecommit.us-east-1.amazonaws.com/v1/repos/my-repo",
                "abc123", "src/App.java"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not yet implemented");
    }

    @Test
    @DisplayName("getDiff (commit) should throw UnsupportedOperationException")
    void getDiffCommitShouldThrow() {
        assertThatThrownBy(() -> client.getDiff(
                "https://codecommit.us-east-1.amazonaws.com/v1/repos/my-repo",
                "abc123"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not yet implemented");
    }

    @Test
    @DisplayName("getDiff (branches) should throw UnsupportedOperationException")
    void getDiffBranchesShouldThrow() {
        assertThatThrownBy(() -> client.getDiff(
                "https://codecommit.us-east-1.amazonaws.com/v1/repos/my-repo",
                "main", "feature"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not yet implemented");
    }
}
