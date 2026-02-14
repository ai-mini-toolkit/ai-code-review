package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import com.aicodereview.common.exception.UnsupportedPlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting the appropriate Git platform API client.
 */
@Component
@Slf4j
public class GitPlatformClientFactory {

    private final Map<GitPlatform, GitPlatformClient> clientMap;

    public GitPlatformClientFactory(List<GitPlatformClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        GitPlatformClient::getPlatform,
                        Function.identity()
                ));
        log.info("Initialized Git platform client factory with {} platform(s)",
                clientMap.size());
    }

    /**
     * Returns the client for the specified platform.
     *
     * @param platform the Git platform
     * @return the corresponding API client
     * @throws UnsupportedPlatformException if no client registered for the platform
     */
    public GitPlatformClient getClient(GitPlatform platform) {
        GitPlatformClient client = clientMap.get(platform);
        if (client == null) {
            throw new UnsupportedPlatformException(
                    "No Git API client registered for platform: " + platform);
        }
        return client;
    }

    /**
     * Returns the client for the platform detected from a repository URL.
     *
     * @param repoUrl the repository URL
     * @return the corresponding API client
     * @throws UnsupportedPlatformException if the platform cannot be detected or is not supported
     */
    public GitPlatformClient getClient(String repoUrl) {
        GitPlatform platform = GitPlatform.fromRepoUrl(repoUrl);
        if (platform == null) {
            throw new UnsupportedPlatformException(
                    "Cannot detect Git platform from URL: " + repoUrl);
        }
        return getClient(platform);
    }
}
