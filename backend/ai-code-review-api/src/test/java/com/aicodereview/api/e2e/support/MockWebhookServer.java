package com.aicodereview.api.e2e.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Test helper that simulates incoming Webhook requests from GitHub, GitLab,
 * and AWS CodeCommit, including correct authentication headers.
 *
 * <p>Use in E2E tests to trigger the full webhook-to-task-creation pipeline
 * without connecting to real Git platforms.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * MockWebhookServer webhook = new MockWebhookServer(restTemplate, githubSecret, gitlabToken);
 * ResponseEntity&lt;String&gt; response = webhook.sendGitHubPushEvent(repoUrl, "main", "abc123");
 * assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
 * </pre>
 *
 * @since 9.1.0
 */
public class MockWebhookServer {

    private final TestRestTemplate restTemplate;
    private final String githubWebhookSecret;
    private final String gitlabWebhookToken;

    /**
     * @param restTemplate       bound to the running test server
     * @param githubWebhookSecret HMAC-SHA256 secret for GitHub signature computation
     * @param gitlabWebhookToken  plain token sent in {@code X-Gitlab-Token} header
     */
    public MockWebhookServer(TestRestTemplate restTemplate,
                              String githubWebhookSecret,
                              String gitlabWebhookToken) {
        this.restTemplate = restTemplate;
        this.githubWebhookSecret = githubWebhookSecret;
        this.gitlabWebhookToken = gitlabWebhookToken;
    }

    // ─── GitHub ───────────────────────────────────────────────────────────────

    /**
     * Sends a GitHub push event webhook.
     *
     * @param repoHtmlUrl full HTML URL of the repository (must match a configured project)
     * @param branch      branch name (without "refs/heads/" prefix)
     * @param commitSha   SHA of the head commit
     */
    public ResponseEntity<String> sendGitHubPushEvent(String repoHtmlUrl,
                                                       String branch,
                                                       String commitSha) {
        String repoName = extractRepoName(repoHtmlUrl);
        String fullName = extractFullName(repoHtmlUrl);

        String payload = String.format(
                "{\"ref\":\"refs/heads/%s\"," +
                "\"after\":\"%s\"," +
                "\"repository\":{\"name\":\"%s\",\"full_name\":\"%s\",\"html_url\":\"%s\"}," +
                "\"pusher\":{\"name\":\"e2e-test-user\"}}",
                branch, commitSha, repoName, fullName, repoHtmlUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeGitHubSignature(payload));
        headers.set("X-GitHub-Event", "push");

        return restTemplate.postForEntity("/api/webhook/github",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a GitHub pull request opened event webhook.
     *
     * @param repoHtmlUrl full HTML URL of the repository
     * @param branch      source branch name
     * @param commitSha   head commit SHA
     * @param prNumber    pull request number
     */
    public ResponseEntity<String> sendGitHubPullRequestEvent(String repoHtmlUrl,
                                                              String branch,
                                                              String commitSha,
                                                              int prNumber) {
        String repoName = extractRepoName(repoHtmlUrl);
        String fullName = extractFullName(repoHtmlUrl);

        String payload = String.format(
                "{\"action\":\"opened\"," +
                "\"pull_request\":{" +
                    "\"number\":%d," +
                    "\"title\":\"E2E Test PR\"," +
                    "\"body\":\"Automated E2E test pull request\"," +
                    "\"user\":{\"login\":\"e2e-test-user\"}," +
                    "\"head\":{\"ref\":\"%s\",\"sha\":\"%s\"}" +
                "}," +
                "\"repository\":{\"name\":\"%s\",\"full_name\":\"%s\",\"html_url\":\"%s\"}}",
                prNumber, branch, commitSha, repoName, fullName, repoHtmlUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeGitHubSignature(payload));
        headers.set("X-GitHub-Event", "pull_request");

        return restTemplate.postForEntity("/api/webhook/github",
                new HttpEntity<>(payload, headers), String.class);
    }

    // ─── GitLab ───────────────────────────────────────────────────────────────

    /**
     * Sends a GitLab merge request event webhook.
     *
     * @param repoWebUrl  web URL of the GitLab project
     * @param branch      source branch name
     * @param commitSha   last commit SHA
     * @param mrIid       merge request internal ID
     */
    public ResponseEntity<String> sendGitLabMergeRequestEvent(String repoWebUrl,
                                                               String branch,
                                                               String commitSha,
                                                               int mrIid) {
        String repoName = extractRepoName(repoWebUrl);
        String pathWithNamespace = extractFullName(repoWebUrl);

        String payload = String.format(
                "{\"object_kind\":\"merge_request\"," +
                "\"user_username\":\"e2e-test-user\"," +
                "\"merge_request\":{" +
                    "\"iid\":%d," +
                    "\"title\":\"E2E Test MR\"," +
                    "\"description\":\"Automated E2E test merge request\"," +
                    "\"source_branch\":\"%s\"," +
                    "\"last_commit\":{\"id\":\"%s\"}" +
                "}," +
                "\"project\":{\"name\":\"%s\",\"path_with_namespace\":\"%s\",\"web_url\":\"%s\"}}",
                mrIid, branch, commitSha, repoName, pathWithNamespace, repoWebUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gitlab-Token", gitlabWebhookToken);
        headers.set("X-Gitlab-Event", "Merge Request Hook");

        return restTemplate.postForEntity("/api/webhook/gitlab",
                new HttpEntity<>(payload, headers), String.class);
    }

    // ─── AWS CodeCommit ───────────────────────────────────────────────────────

    /**
     * Sends a simulated AWS CodeCommit SNS notification for a push event.
     *
     * <p>The inner {@code Message} JSON uses the repository state change format expected
     * by {@code WebhookController.extractRepoUrl/Branch/CommitHash}:
     * <pre>
     * {
     *   "repositoryName": "...",
     *   "referenceFullName": "refs/heads/...",
     *   "newCommitId": "..."
     * }
     * </pre>
     *
     * @param repoName the CodeCommit repository name (used directly as repoUrl in project)
     * @param branch   branch name (without "refs/heads/" prefix)
     * @param commitId commit ID (SHA)
     */
    public ResponseEntity<String> sendCodeCommitPushEvent(String repoName,
                                                           String branch,
                                                           String commitId) {
        // Inner SNS Message — uses CodeCommit repository state change notification format.
        // WebhookController reads: repositoryName → repoUrl, referenceFullName → branch,
        // newCommitId → commitHash, author → author (required by extractAuthor).
        String innerMessage = String.format(
                "{\\\"repositoryName\\\":\\\"%s\\\"," +
                "\\\"referenceFullName\\\":\\\"refs/heads/%s\\\"," +
                "\\\"newCommitId\\\":\\\"%s\\\"," +
                "\\\"author\\\":\\\"e2e-test-user\\\"}",
                repoName, branch, commitId
        );

        String payload = String.format(
                "{\"Type\":\"Notification\"," +
                "\"Subject\":\"CodeCommit Repository State Change\"," +
                "\"Message\":\"%s\"}",
                innerMessage
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-amz-sns-message-type", "Notification");

        return restTemplate.postForEntity("/api/webhook/codecommit",
                new HttpEntity<>(payload, headers), String.class);
    }

    // ─── Invalid Signature / Boundary Testing ──────────────────────────────

    /**
     * Sends a GitHub push event with an intentionally WRONG HMAC signature.
     * Expected: 401 Unauthorized.
     */
    public ResponseEntity<String> sendGitHubPushEventWithInvalidSignature(String repoHtmlUrl,
                                                                           String branch,
                                                                           String commitSha) {
        String repoName = extractRepoName(repoHtmlUrl);
        String fullName = extractFullName(repoHtmlUrl);

        String payload = String.format(
                "{\"ref\":\"refs/heads/%s\"," +
                "\"after\":\"%s\"," +
                "\"repository\":{\"name\":\"%s\",\"full_name\":\"%s\",\"html_url\":\"%s\"}," +
                "\"pusher\":{\"name\":\"e2e-test-user\"}}",
                branch, commitSha, repoName, fullName, repoHtmlUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        headers.set("X-GitHub-Event", "push");

        return restTemplate.postForEntity("/api/webhook/github",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a GitLab MR event with an intentionally WRONG token.
     * Expected: 401 Unauthorized.
     */
    public ResponseEntity<String> sendGitLabMREventWithInvalidToken(String repoWebUrl,
                                                                      String branch,
                                                                      String commitSha,
                                                                      int mrIid) {
        String repoName = extractRepoName(repoWebUrl);
        String pathWithNamespace = extractFullName(repoWebUrl);

        String payload = String.format(
                "{\"object_kind\":\"merge_request\"," +
                "\"user_username\":\"e2e-test-user\"," +
                "\"merge_request\":{" +
                    "\"iid\":%d," +
                    "\"title\":\"E2E Test MR\"," +
                    "\"description\":\"Invalid token test\"," +
                    "\"source_branch\":\"%s\"," +
                    "\"last_commit\":{\"id\":\"%s\"}" +
                "}," +
                "\"project\":{\"name\":\"%s\",\"path_with_namespace\":\"%s\",\"web_url\":\"%s\"}}",
                mrIid, branch, commitSha, repoName, pathWithNamespace, repoWebUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gitlab-Token", "wrong-token-value");
        headers.set("X-Gitlab-Event", "Merge Request Hook");

        return restTemplate.postForEntity("/api/webhook/gitlab",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a malformed CodeCommit SNS message (missing {@code Type} field).
     * Expected: non-2xx response (verification fails on malformed structure).
     */
    public ResponseEntity<String> sendCodeCommitEventMalformed(String repoName,
                                                                 String branch,
                                                                 String commitId) {
        // Deliberately omit "Type" field — AWSCodeCommitWebhookVerifier checks for it.
        String innerMessage = String.format(
                "{\\\"repositoryName\\\":\\\"%s\\\"," +
                "\\\"referenceFullName\\\":\\\"refs/heads/%s\\\"," +
                "\\\"newCommitId\\\":\\\"%s\\\"," +
                "\\\"author\\\":\\\"e2e-test-user\\\"}",
                repoName, branch, commitId
        );

        String payload = String.format(
                "{\"Subject\":\"CodeCommit Repository State Change\"," +
                "\"Message\":\"%s\"}",
                innerMessage
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-amz-sns-message-type", "Notification");

        return restTemplate.postForEntity("/api/webhook/codecommit",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a GitHub event with a custom event type header (e.g. "issues", "release").
     * The payload includes a repository but no "pusher" or "pull_request" fields,
     * so validation should reject it (422).
     */
    public ResponseEntity<String> sendGitHubEventWithCustomType(String repoHtmlUrl,
                                                                  String eventType) {
        String repoName = extractRepoName(repoHtmlUrl);
        String fullName = extractFullName(repoHtmlUrl);

        String payload = String.format(
                "{\"action\":\"opened\"," +
                "\"repository\":{\"name\":\"%s\",\"full_name\":\"%s\",\"html_url\":\"%s\"}}",
                repoName, fullName, repoHtmlUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeGitHubSignature(payload));
        headers.set("X-GitHub-Event", eventType);

        return restTemplate.postForEntity("/api/webhook/github",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a GitLab push event webhook (not merge_request).
     * Expected: creates a PUSH type task (GitLab push path).
     */
    public ResponseEntity<String> sendGitLabPushEvent(String repoWebUrl,
                                                        String branch,
                                                        String commitSha) {
        String repoName = extractRepoName(repoWebUrl);
        String pathWithNamespace = extractFullName(repoWebUrl);

        String payload = String.format(
                "{\"object_kind\":\"push\"," +
                "\"ref\":\"refs/heads/%s\"," +
                "\"after\":\"%s\"," +
                "\"user_username\":\"e2e-test-user\"," +
                "\"project\":{\"name\":\"%s\",\"path_with_namespace\":\"%s\",\"web_url\":\"%s\"}}",
                branch, commitSha, repoName, pathWithNamespace, repoWebUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gitlab-Token", gitlabWebhookToken);
        headers.set("X-Gitlab-Event", "Push Hook");

        return restTemplate.postForEntity("/api/webhook/gitlab",
                new HttpEntity<>(payload, headers), String.class);
    }

    /**
     * Sends a raw payload to any webhook endpoint — for boundary/fuzz testing.
     *
     * @param platform  platform name (github, gitlab, codecommit)
     * @param body      raw request body (may be non-JSON)
     * @param headers   pre-built HTTP headers
     */
    public ResponseEntity<String> sendRawPayload(String platform,
                                                   String body,
                                                   HttpHeaders headers) {
        return restTemplate.postForEntity("/api/webhook/" + platform,
                new HttpEntity<>(body, headers), String.class);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Computes HMAC-SHA256 signature in GitHub's {@code sha256=<hex>} format.
     * Same algorithm as in ReviewTaskIntegrationTest.
     */
    private String computeGitHubSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    githubWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hmac) {
                hex.append(String.format("%02x", b));
            }
            return "sha256=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute GitHub HMAC-SHA256 signature", e);
        }
    }

    /** Extracts "repo" from "https://github.com/org/repo". */
    private static String extractRepoName(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    /** Extracts "org/repo" from "https://github.com/org/repo". */
    private static String extractFullName(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return url;
    }
}
