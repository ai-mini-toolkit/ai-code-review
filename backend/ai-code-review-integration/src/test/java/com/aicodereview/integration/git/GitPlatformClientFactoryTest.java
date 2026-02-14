package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import com.aicodereview.common.exception.UnsupportedPlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GitPlatformClientFactory Tests")
class GitPlatformClientFactoryTest {

    private GitPlatformClient githubClient;
    private GitPlatformClient gitlabClient;
    private GitPlatformClientFactory factory;

    @BeforeEach
    void setUp() {
        githubClient = mock(GitPlatformClient.class);
        gitlabClient = mock(GitPlatformClient.class);
        when(githubClient.getPlatform()).thenReturn(GitPlatform.GITHUB);
        when(gitlabClient.getPlatform()).thenReturn(GitPlatform.GITLAB);
        factory = new GitPlatformClientFactory(List.of(githubClient, gitlabClient));
    }

    @Nested
    @DisplayName("getClient(GitPlatform)")
    class GetClientByPlatform {

        @Test
        @DisplayName("Should return GitHub client for GITHUB platform")
        void shouldReturnGitHubClient() {
            assertThat(factory.getClient(GitPlatform.GITHUB)).isSameAs(githubClient);
        }

        @Test
        @DisplayName("Should return GitLab client for GITLAB platform")
        void shouldReturnGitLabClient() {
            assertThat(factory.getClient(GitPlatform.GITLAB)).isSameAs(gitlabClient);
        }

        @Test
        @DisplayName("Should throw UnsupportedPlatformException for unregistered platform")
        void shouldThrowForUnregistered() {
            assertThatThrownBy(() -> factory.getClient(GitPlatform.AWS_CODECOMMIT))
                    .isInstanceOf(UnsupportedPlatformException.class)
                    .hasMessageContaining("AWS_CODECOMMIT");
        }
    }

    @Nested
    @DisplayName("getClient(String repoUrl)")
    class GetClientByUrl {

        @Test
        @DisplayName("Should detect GitHub from URL")
        void shouldDetectGitHub() {
            GitPlatformClient result = factory.getClient("https://github.com/owner/repo");
            assertThat(result).isSameAs(githubClient);
        }

        @Test
        @DisplayName("Should detect GitLab from URL")
        void shouldDetectGitLab() {
            GitPlatformClient result = factory.getClient("https://gitlab.com/ns/project");
            assertThat(result).isSameAs(gitlabClient);
        }

        @Test
        @DisplayName("Should throw for unrecognized URL")
        void shouldThrowForUnrecognizedUrl() {
            assertThatThrownBy(() -> factory.getClient("https://bitbucket.org/owner/repo"))
                    .isInstanceOf(UnsupportedPlatformException.class)
                    .hasMessageContaining("Cannot detect");
        }

        @Test
        @DisplayName("Should throw for null URL")
        void shouldThrowForNullUrl() {
            assertThatThrownBy(() -> factory.getClient((String) null))
                    .isInstanceOf(UnsupportedPlatformException.class);
        }
    }

    @Nested
    @DisplayName("Factory initialization")
    class Initialization {

        @Test
        @DisplayName("Should work with empty client list")
        void shouldWorkWithEmptyList() {
            GitPlatformClientFactory emptyFactory = new GitPlatformClientFactory(List.of());
            assertThatThrownBy(() -> emptyFactory.getClient(GitPlatform.GITHUB))
                    .isInstanceOf(UnsupportedPlatformException.class);
        }
    }
}
