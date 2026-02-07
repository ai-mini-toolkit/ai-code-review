# AWS CodeCommit Integration Verification Plan

**Document Version**: 1.0
**Date**: 2026-02-05
**Project**: ai-code-review
**Purpose**: Validate AWS CodeCommit Webhook and API integration feasibility

---

## 1. Executive Summary

This document provides a comprehensive technical verification plan (PoC) for integrating AWS CodeCommit as a Git platform in the ai-code-review system. AWS CodeCommit differs significantly from GitHub and GitLab in authentication, webhook mechanism, and API structure. This PoC validates the integration's technical feasibility and implementation complexity.

### Objectives

1. Validate AWS CodeCommit Webhook signature verification (AWS Signature V4)
2. Test difference retrieval using AWS SDK for Java (GetDifferences API)
3. Measure end-to-end latency (Webhook → Diff retrieval)
4. Assess implementation complexity compared to GitHub/GitLab
5. Make go/no-go decision for Phase 1 inclusion

---

## 2. AWS CodeCommit Architecture Overview

### 2.1 Key Differences from GitHub/GitLab

| Aspect | GitHub | GitLab | AWS CodeCommit |
|--------|--------|--------|----------------|
| **Webhook Authentication** | HMAC-SHA256 | Secret Token | AWS Signature V4 |
| **API Authentication** | Personal Access Token | Personal Access Token | AWS IAM Credentials (Access Key + Secret Key) |
| **Webhook Delivery** | Direct HTTP POST | Direct HTTP POST | Via AWS SNS/EventBridge (optional) or direct |
| **Diff API** | `/repos/:owner/:repo/compare` | `/projects/:id/repository/compare` | `GetDifferences` SDK method |
| **Pagination** | Link header | Page parameters | NextToken pattern |
| **Rate Limiting** | 5000 req/hour (authenticated) | 600 req/minute | Burst: 40 req/s, Sustained: 10 req/s |

### 2.2 Integration Complexity Assessment

**Complexity Level**: **Medium-High**

**Reasons**:
1. AWS Signature V4 verification is more complex than HMAC-SHA256
2. Requires AWS SDK dependency (vs simple HTTP REST calls)
3. IAM credential management (Access Key + Secret Key + Region)
4. Different error handling patterns (AWS SDK exceptions)
5. Pagination pattern different from GitHub/GitLab

---

## 3. Technical Verification Scenarios

### Scenario 1: Webhook Signature Verification

**Objective**: Validate AWS Signature Version 4 verification for incoming webhooks

**Test Steps**:
1. Configure AWS CodeCommit repository to send webhooks to test endpoint
2. Receive webhook payload with AWS SigV4 signature headers
3. Implement signature verification using AWS SDK
4. Validate against both valid and invalid signatures

**Key Headers**:
```
X-Amz-Signature: <signature>
X-Amz-Algorithm: AWS4-HMAC-SHA256
X-Amz-Credential: <access-key-id>/<date>/<region>/codecommit/aws4_request
X-Amz-Date: <timestamp>
X-Amz-SignedHeaders: <signed-headers>
```

**Verification Algorithm**:
```java
// AWS Signature V4 verification
String authHeader = request.getHeader("Authorization");
String dateHeader = request.getHeader("X-Amz-Date");
String signature = extractSignature(authHeader);

String canonicalRequest = buildCanonicalRequest(
    request.getMethod(),
    request.getRequestURI(),
    request.getQueryString(),
    getSignedHeaders(request),
    hash(request.getBody())
);

String stringToSign = buildStringToSign(
    "AWS4-HMAC-SHA256",
    dateHeader,
    "<region>/codecommit/aws4_request",
    hash(canonicalRequest)
);

String expectedSignature = calculateSignature(
    secretKey,
    dateHeader,
    region,
    "codecommit",
    stringToSign
);

return signature.equals(expectedSignature);
```

**Expected Outcome**:
- Valid signature: Verification passes ✅
- Invalid signature: Verification fails with security log ❌
- Missing headers: Return 401 Unauthorized

---

### Scenario 2: Diff Retrieval via GetDifferences API

**Objective**: Retrieve code differences between commits using AWS SDK

**API Details**:
- **SDK Method**: `codecommit.getDifferences()`
- **Request Parameters**:
  - `repositoryName` (String): Repository name
  - `beforeCommitSpecifier` (String): Base commit SHA or branch
  - `afterCommitSpecifier` (String): Target commit SHA or branch
  - `nextToken` (String, optional): For pagination
  - `maxResults` (Integer, optional): Max differences per page (default 100)

**Test Steps**:

**Step 1: Initialize AWS CodeCommit Client**
```java
import software.amazon.awssdk.services.codecommit.CodeCommitClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

CodeCommitClient client = CodeCommitClient.builder()
    .region(Region.of("us-east-1"))
    .credentialsProvider(ProfileCredentialsProvider.create())
    .build();
```

**Step 2: Call GetDifferences API**
```java
import software.amazon.awssdk.services.codecommit.model.*;

GetDifferencesRequest request = GetDifferencesRequest.builder()
    .repositoryName("my-test-repo")
    .beforeCommitSpecifier("main")
    .afterCommitSpecifier("feature-branch")
    .maxResults(100)
    .build();

GetDifferencesResponse response = client.getDifferences(request);
List<Difference> differences = response.differences();
```

**Step 3: Parse Difference Objects**
```java
for (Difference diff : differences) {
    String beforeBlobId = diff.beforeBlob().blobId();
    String afterBlobId = diff.afterBlob().blobId();
    String path = diff.afterBlob().path();
    ChangeTypeEnum changeType = diff.changeType();

    System.out.println("File: " + path);
    System.out.println("Change Type: " + changeType);
}
```

**Step 4: Handle Pagination**
```java
String nextToken = response.nextToken();
while (nextToken != null) {
    GetDifferencesRequest paginatedRequest = request.toBuilder()
        .nextToken(nextToken)
        .build();
    response = client.getDifferences(paginatedRequest);
    differences.addAll(response.differences());
    nextToken = response.nextToken();
}
```

**Expected Outcome**:
- Successfully retrieve all differences
- Correctly handle pagination (if > 100 files changed)
- Parse difference metadata (path, change type, blob IDs)

---

### Scenario 3: Retrieve Full File Content

**Objective**: Get full file content for before/after versions to generate unified diff

**API Details**:
- **SDK Method**: `codecommit.getBlob()`
- **Request Parameters**:
  - `repositoryName` (String): Repository name
  - `blobId` (String): Blob ID from Difference object

**Test Steps**:

**Step 1: Get Blob Content**
```java
import software.amazon.awssdk.services.codecommit.model.GetBlobRequest;
import software.amazon.awssdk.services.codecommit.model.GetBlobResponse;

GetBlobRequest blobRequest = GetBlobRequest.builder()
    .repositoryName("my-test-repo")
    .blobId(diff.afterBlob().blobId())
    .build();

GetBlobResponse blobResponse = client.getBlob(blobRequest);
byte[] content = blobResponse.content().asByteArray();
String fileContent = new String(content, StandardCharsets.UTF_8);
```

**Step 2: Generate Unified Diff**
```java
// Use java-diff-utils library
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.UnifiedDiffUtils;

List<String> beforeLines = getFileLines(beforeBlobId);
List<String> afterLines = getFileLines(afterBlobId);

Patch<String> patch = DiffUtils.diff(beforeLines, afterLines);
List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
    "a/" + path,
    "b/" + path,
    beforeLines,
    patch,
    3  // context lines
);

String diffText = String.join("\n", unifiedDiff);
```

**Expected Outcome**:
- Successfully retrieve file content for both versions
- Generate unified diff compatible with existing diff parser
- Handle binary files gracefully (skip or mark as binary)

---

### Scenario 4: End-to-End Integration Test

**Objective**: Measure end-to-end latency from Webhook receipt to diff retrieval

**Test Flow**:
```
1. AWS CodeCommit Push Event
   ↓
2. Webhook POST to /api/v1/webhooks/codecommit
   ↓
3. Verify AWS SigV4 signature
   ↓
4. Parse payload (extract repository name, commit SHAs)
   ↓
5. Call GetDifferences API
   ↓
6. Fetch blob content for changed files
   ↓
7. Generate unified diff
   ↓
8. Return 202 Accepted (task created)
```

**Performance Targets**:
- **Webhook Verification**: < 100ms
- **GetDifferences API Call**: < 1000ms
- **Blob Fetching** (per file): < 500ms
- **Total E2E Time** (for 5 changed files): **< 3 seconds**

**Test Data**:
- Test with 1, 5, 10, 20 changed files
- Test with small (10 KB) and large (500 KB) files
- Test with add, modify, delete change types

**Expected Outcome**:
- E2E latency < 3s for 5 files ✅
- Webhook verification < 100ms ✅
- No API throttling errors (within burst limit)

---

## 4. Implementation Details

### 4.1 Required Dependencies

**Maven Dependencies**:
```xml
<!-- AWS SDK for CodeCommit -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>codecommit</artifactId>
    <version>2.20.0</version>
</dependency>

<!-- AWS Core for authentication -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>auth</artifactId>
    <version>2.20.0</version>
</dependency>

<!-- Diff generation library -->
<dependency>
    <groupId>io.github.java-diff-utils</groupId>
    <artifactId>java-diff-utils</artifactId>
    <version>4.12</version>
</dependency>
```

### 4.2 Configuration Management

**Application Properties**:
```yaml
aws:
  codecommit:
    enabled: true
    region: us-east-1
    credentials:
      access-key-id: ${AWS_ACCESS_KEY_ID}
      secret-access-key: ${AWS_SECRET_ACCESS_KEY}
    webhook:
      secret-key: ${CODECOMMIT_WEBHOOK_SECRET}
    api:
      max-retries: 3
      timeout: 10000  # 10 seconds
      max-results: 100  # pagination size
```

**Security Considerations**:
- Store AWS credentials in encrypted database (AES-256-GCM)
- Support AWS IAM Role for EC2 instances (avoid hardcoded keys)
- Validate region parameter to prevent SSRF attacks
- Implement rate limiting to prevent quota exhaustion

---

### 4.3 Webhook Handler Implementation

**Controller**:
```java
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    @Autowired
    private AwsCodeCommitWebhookVerifier verifier;

    @Autowired
    private TaskService taskService;

    @PostMapping("/codecommit")
    public ResponseEntity<WebhookResponse> handleCodeCommit(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {

        // Step 1: Verify AWS SigV4 signature
        if (!verifier.verify(payload, headers)) {
            log.warn("Invalid AWS signature for CodeCommit webhook");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new WebhookResponse(false, "Invalid signature"));
        }

        // Step 2: Parse payload
        CodeCommitWebhookPayload webhookPayload =
            parsePayload(payload);

        // Step 3: Create review task
        ReviewTask task = taskService.createTask(webhookPayload);

        // Step 4: Return 202 Accepted
        return ResponseEntity.accepted()
            .body(new WebhookResponse(true, task.getId()));
    }
}
```

**Signature Verifier**:
```java
@Component
public class AwsCodeCommitWebhookVerifier {

    public boolean verify(String payload, Map<String, String> headers) {
        try {
            String authorization = headers.get("authorization");
            String dateHeader = headers.get("x-amz-date");
            String region = extractRegion(authorization);

            // Extract signature from Authorization header
            String providedSignature = extractSignature(authorization);

            // Calculate expected signature
            String expectedSignature = calculateAwsSigV4(
                payload,
                dateHeader,
                region,
                secretKey
            );

            return MessageDigest.isEqual(
                providedSignature.getBytes(),
                expectedSignature.getBytes()
            );
        } catch (Exception e) {
            log.error("Failed to verify AWS signature", e);
            return false;
        }
    }

    private String calculateAwsSigV4(
            String payload,
            String dateHeader,
            String region,
            String secretKey) {
        // Implementation of AWS Signature V4 algorithm
        // See: https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
    }
}
```

---

### 4.4 Diff Retrieval Service

**Service Implementation**:
```java
@Service
public class AwsCodeCommitDiffService implements GitDiffService {

    @Autowired
    private CodeCommitClient codeCommitClient;

    @Override
    public DiffResult getDiff(String repoName,
                              String beforeCommit,
                              String afterCommit) {
        try {
            // Step 1: Get differences
            List<Difference> differences = getAllDifferences(
                repoName, beforeCommit, afterCommit);

            // Step 2: Fetch file content and generate unified diff
            List<FileDiff> fileDiffs = new ArrayList<>();
            for (Difference diff : differences) {
                String unifiedDiff = generateUnifiedDiff(
                    repoName, diff);
                fileDiffs.add(new FileDiff(
                    diff.afterBlob().path(),
                    diff.changeType().toString(),
                    unifiedDiff
                ));
            }

            return new DiffResult(fileDiffs);

        } catch (CodeCommitException e) {
            log.error("Failed to get diff from CodeCommit", e);
            throw new GitIntegrationException(
                "CodeCommit API error: " + e.awsErrorDetails().errorMessage());
        }
    }

    private List<Difference> getAllDifferences(
            String repoName,
            String beforeCommit,
            String afterCommit) {
        List<Difference> allDifferences = new ArrayList<>();
        String nextToken = null;

        do {
            GetDifferencesRequest request = GetDifferencesRequest.builder()
                .repositoryName(repoName)
                .beforeCommitSpecifier(beforeCommit)
                .afterCommitSpecifier(afterCommit)
                .maxResults(100)
                .nextToken(nextToken)
                .build();

            GetDifferencesResponse response =
                codeCommitClient.getDifferences(request);
            allDifferences.addAll(response.differences());
            nextToken = response.nextToken();

        } while (nextToken != null);

        return allDifferences;
    }

    private String generateUnifiedDiff(
            String repoName,
            Difference diff) {
        // Handle different change types
        switch (diff.changeType()) {
            case A:  // Added
                String afterContent = getBlobContent(
                    repoName, diff.afterBlob().blobId());
                return generateAddDiff(
                    diff.afterBlob().path(), afterContent);

            case D:  // Deleted
                String beforeContent = getBlobContent(
                    repoName, diff.beforeBlob().blobId());
                return generateDeleteDiff(
                    diff.beforeBlob().path(), beforeContent);

            case M:  // Modified
                String before = getBlobContent(
                    repoName, diff.beforeBlob().blobId());
                String after = getBlobContent(
                    repoName, diff.afterBlob().blobId());
                return generateModifyDiff(
                    diff.afterBlob().path(), before, after);

            default:
                throw new IllegalArgumentException(
                    "Unknown change type: " + diff.changeType());
        }
    }

    private String getBlobContent(String repoName, String blobId) {
        GetBlobRequest request = GetBlobRequest.builder()
            .repositoryName(repoName)
            .blobId(blobId)
            .build();

        GetBlobResponse response = codeCommitClient.getBlob(request);
        return new String(
            response.content().asByteArray(),
            StandardCharsets.UTF_8
        );
    }
}
```

---

## 5. Error Handling and Retry Strategy

### 5.1 AWS SDK Exception Handling

**Common Exceptions**:

| Exception | Meaning | Retry? | Strategy |
|-----------|---------|--------|----------|
| `RepositoryDoesNotExistException` | Invalid repository name | No | Log error, return 404 |
| `CommitDoesNotExistException` | Invalid commit SHA | No | Log error, return 404 |
| `ThrottlingException` | Rate limit exceeded | Yes | Exponential backoff (1s, 2s, 4s) |
| `ServiceUnavailableException` | AWS service down | Yes | Exponential backoff |
| `InvalidRequestException` | Invalid API parameters | No | Log error, return 400 |
| `AccessDeniedException` | IAM permission issue | No | Log error, alert admin |

**Retry Logic**:
```java
@Retryable(
    value = { ThrottlingException.class, ServiceUnavailableException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public DiffResult getDiffWithRetry(String repoName,
                                   String beforeCommit,
                                   String afterCommit) {
    return getDiff(repoName, beforeCommit, afterCommit);
}
```

### 5.2 Rate Limiting Protection

**AWS CodeCommit Rate Limits**:
- **Burst**: 40 requests/second
- **Sustained**: 10 requests/second

**Protection Strategy**:
```java
@Component
public class CodeCommitRateLimiter {

    private final RateLimiter rateLimiter =
        RateLimiter.create(8.0);  // 8 req/s (80% of sustained limit)

    public <T> T executeWithRateLimit(Supplier<T> operation) {
        rateLimiter.acquire();  // Block until permit available
        return operation.get();
    }
}
```

---

## 6. Testing Plan

### 6.1 Unit Tests

**Test Cases**:
1. ✅ AWS SigV4 signature verification (valid signature)
2. ✅ AWS SigV4 signature verification (invalid signature)
3. ✅ Parse CodeCommit webhook payload
4. ✅ GetDifferences API call (mock AWS SDK)
5. ✅ Handle pagination (mock multiple pages)
6. ✅ Generate unified diff from blob content
7. ✅ Handle different change types (A, M, D)
8. ✅ Exception handling and retry logic

**Mock AWS SDK**:
```java
@MockBean
private CodeCommitClient mockClient;

@Test
public void testGetDifferences() {
    // Mock response
    GetDifferencesResponse mockResponse =
        GetDifferencesResponse.builder()
            .differences(
                Difference.builder()
                    .changeType(ChangeTypeEnum.M)
                    .afterBlob(BlobMetadata.builder()
                        .path("src/Main.java")
                        .blobId("abc123")
                        .build())
                    .build()
            )
            .build();

    when(mockClient.getDifferences(any()))
        .thenReturn(mockResponse);

    // Test
    DiffResult result = service.getDiff(
        "test-repo", "main", "feature");

    assertThat(result.getFileDiffs()).hasSize(1);
    assertThat(result.getFileDiffs().get(0).getPath())
        .isEqualTo("src/Main.java");
}
```

### 6.2 Integration Tests

**Prerequisites**:
- AWS CodeCommit test repository
- IAM user with CodeCommit permissions
- Test credentials (Access Key + Secret Key)

**Test Scenarios**:
1. ✅ E2E: Webhook → Signature verification → Diff retrieval
2. ✅ Performance: Measure latency for 1, 5, 10, 20 files
3. ✅ Pagination: Test with > 100 changed files
4. ✅ Large files: Test with 1 MB file changes
5. ✅ Error handling: Test with invalid credentials, invalid repo
6. ✅ Rate limiting: Test burst and sustained request rates

**Test Execution**:
```java
@SpringBootTest
@ActiveProfiles("integration-test")
public class AwsCodeCommitIntegrationTest {

    @Autowired
    private AwsCodeCommitDiffService diffService;

    @Test
    @Disabled("Requires AWS credentials")
    public void testRealCodeCommitIntegration() {
        // Use real AWS CodeCommit API
        DiffResult result = diffService.getDiff(
            "ai-code-review-test",
            "main",
            "feature-branch"
        );

        assertThat(result).isNotNull();
        assertThat(result.getFileDiffs()).isNotEmpty();
    }
}
```

---

## 7. Go/No-Go Decision Criteria

### 7.1 Must-Have Criteria (P0)

| Criterion | Target | Pass Condition |
|-----------|--------|----------------|
| **Signature Verification** | 100% accuracy | All valid signatures pass, all invalid fail |
| **Diff Retrieval** | Functional | Successfully retrieve diffs for test cases |
| **E2E Latency** | < 3s for 5 files | 90% of requests meet target |
| **Error Handling** | Robust | All exception types handled correctly |

### 7.2 Should-Have Criteria (P1)

| Criterion | Target | Pass Condition |
|-----------|--------|----------------|
| **Pagination** | Functional | Handle > 100 files correctly |
| **Large Files** | < 5s per file | 1 MB file retrieval within 5s |
| **Rate Limiting** | No throttling | No ThrottlingException in normal load |

### 7.3 Decision Matrix

| Pass Rate | Decision | Reasoning |
|-----------|----------|-----------|
| **≥ 90%** | ✅ Include in Phase 1 | Integration is mature and reliable |
| **70-89%** | ⚠️ Phase 1 with caveats | Include but document limitations |
| **< 70%** | ❌ Move to Phase 2 | Too risky, needs more development time |

---

## 8. Risk Assessment

### High Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **AWS SigV4 verification too complex** | 40% | High | Use AWS SDK's built-in signature verification utilities |
| **Rate limiting in production** | 50% | Medium | Implement rate limiter, monitor usage |
| **IAM credential management** | 30% | High | Use AWS Secrets Manager or EC2 IAM roles |
| **Higher implementation effort than GitHub** | 60% | Medium | Allocate 50% more dev time than GitHub integration |

### Medium Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Pagination bugs** | 30% | Medium | Thorough testing with large changesets |
| **Binary file handling** | 25% | Low | Skip binary files, add to exclusion list |
| **Region configuration errors** | 20% | Low | Validate region in config, provide clear error messages |

---

## 9. Implementation Effort Estimate

### Time Breakdown

| Task | Estimated Hours | Notes |
|------|----------------|-------|
| **AWS SDK Integration** | 8 hours | Add dependencies, configure client |
| **SigV4 Verification** | 16 hours | Complex algorithm, needs thorough testing |
| **Diff Retrieval Service** | 12 hours | GetDifferences + GetBlob implementation |
| **Unified Diff Generation** | 8 hours | Use java-diff-utils library |
| **Error Handling** | 6 hours | Exception mapping, retry logic |
| **Unit Tests** | 10 hours | Mock AWS SDK, test all scenarios |
| **Integration Tests** | 6 hours | Real AWS CodeCommit testing |
| **Documentation** | 4 hours | API docs, configuration guide |
| **Total** | **70 hours** | ~9 days for 1 developer |

**Comparison**:
- GitHub integration: ~40 hours (5 days)
- GitLab integration: ~45 hours (6 days)
- AWS CodeCommit integration: ~70 hours (9 days)

**Effort Ratio**: AWS CodeCommit is **1.75x more complex** than GitHub

---

## 10. Recommendation

### Recommended Approach

**Option 1: Include in Phase 1 (Recommended if PoC passes)**
- **Pros**: Complete 3-platform support, market differentiation
- **Cons**: +4 days to Phase 1 timeline, higher risk
- **Condition**: PoC must achieve ≥ 90% pass rate

**Option 2: Move to Phase 2 (Recommended if PoC fails)**
- **Pros**: De-risk Phase 1, focus on GitHub/GitLab
- **Cons**: Incomplete platform coverage, customer feedback delay
- **Condition**: PoC pass rate < 70%

**Option 3: Phase 1 with Limited Support**
- **Pros**: Early customer feedback, partial value delivery
- **Cons**: May set wrong expectations
- **Condition**: PoC pass rate 70-89%, document limitations clearly

---

## 11. Success Metrics

### PoC Success Criteria

- ✅ AWS SigV4 signature verification: 100% accuracy
- ✅ Diff retrieval: Functional for all change types (A, M, D)
- ✅ E2E latency: < 3s for 5 files (90% of requests)
- ✅ Pagination: Handle > 100 files without errors
- ✅ Error handling: All common exceptions handled gracefully
- ✅ Rate limiting: No throttling under normal load (< 5 req/s)

### Phase 1 Success Metrics (if included)

- ✅ Production availability: 99% uptime
- ✅ No signature verification failures due to bugs
- ✅ Diff retrieval latency p95 < 5s
- ✅ Zero customer-facing IAM permission errors (proper documentation)

---

## 12. Deliverables

### Code Deliverables
- [ ] AwsCodeCommitWebhookVerifier.java (signature verification)
- [ ] AwsCodeCommitDiffService.java (diff retrieval service)
- [ ] CodeCommitWebhookController.java (webhook endpoint)
- [ ] Unit tests (≥ 80% coverage)
- [ ] Integration tests (all scenarios)

### Documentation Deliverables
- [ ] PoC Test Report (this document + test results)
- [ ] AWS CodeCommit Configuration Guide (for users)
- [ ] IAM Permissions Guide (required permissions for integration)
- [ ] Go/No-Go Decision Document

### Configuration Deliverables
- [ ] application-codecommit.yml (AWS configuration template)
- [ ] Sample IAM policy JSON
- [ ] Docker Compose configuration with AWS credentials

---

## 13. Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| **Setup** | 0.5 day | Create AWS account, test repository, IAM user |
| **Implementation** | 2 days | Implement verifier, diff service, webhook handler |
| **Testing** | 1 day | Unit tests, integration tests, performance tests |
| **Analysis** | 0.5 day | Analyze results, document findings |
| **Decision** | 0.5 day | Make go/no-go decision, update project plan |
| **Total** | **4.5 days** | |

---

## 14. Appendix

### A. AWS CodeCommit Webhook Payload Example

```json
{
  "Records": [
    {
      "eventVersion": "1.0",
      "eventId": "abc123",
      "eventName": "TriggerEventTest",
      "eventTime": "2026-02-05T10:30:00.000Z",
      "eventSource": "aws:codecommit",
      "eventSourceARN": "arn:aws:codecommit:us-east-1:123456789012:my-repo",
      "awsRegion": "us-east-1",
      "codecommit": {
        "references": [
          {
            "commit": "abc123def456",
            "ref": "refs/heads/main"
          }
        ]
      }
    }
  ]
}
```

### B. Required IAM Permissions

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "codecommit:GetRepository",
        "codecommit:GetCommit",
        "codecommit:GetDifferences",
        "codecommit:GetBlob",
        "codecommit:GetBranch"
      ],
      "Resource": "arn:aws:codecommit:*:*:*"
    }
  ]
}
```

### C. Reference Documentation

- **AWS CodeCommit API Reference**: https://docs.aws.amazon.com/codecommit/latest/APIReference/
- **AWS Signature V4**: https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html
- **AWS SDK for Java**: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html
- **java-diff-utils**: https://github.com/java-diff-utils/java-diff-utils

---

**Document Prepared By**: Integration Architect
**Review Status**: Draft
**Next Review Date**: After PoC execution (Day 5)
