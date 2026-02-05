package com.aicr.poc;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codecommit.CodeCommitClient;
import software.amazon.awssdk.services.codecommit.model.*;
import software.amazon.awssdk.services.codecommit.paginators.GetDifferencesIterable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves differences from AWS CodeCommit with pagination and error handling
 */
public class DifferenceRetriever implements AutoCloseable {

    private final CodeCommitClient codeCommitClient;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    public DifferenceRetriever(Region region) {
        this.codeCommitClient = CodeCommitClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
    }

    public DifferenceRetriever(CodeCommitClient client) {
        this.codeCommitClient = client;
    }

    /**
     * Retrieve all differences between two commits with pagination
     */
    public DifferenceResult getDifferences(String repositoryName, String beforeCommitId, String afterCommitId) {
        DifferenceResult result = new DifferenceResult();
        result.setRepositoryName(repositoryName);
        result.setBeforeCommitId(beforeCommitId);
        result.setAfterCommitId(afterCommitId);
        result.setStartTime(System.currentTimeMillis());

        int retryCount = 0;
        List<Difference> allDifferences = new ArrayList<>();

        while (retryCount < MAX_RETRIES) {
            try {
                System.out.println("Retrieving differences (attempt " + (retryCount + 1) + ")...");

                GetDifferencesRequest request = GetDifferencesRequest.builder()
                        .repositoryName(repositoryName)
                        .beforeCommitSpecifier(beforeCommitId)
                        .afterCommitSpecifier(afterCommitId)
                        .maxResults(100) // Max allowed per request
                        .build();

                // Use paginator for automatic pagination
                GetDifferencesIterable responses = codeCommitClient.getDifferencesPaginator(request);

                int pageCount = 0;
                for (GetDifferencesResponse response : responses) {
                    pageCount++;
                    allDifferences.addAll(response.differences());
                    System.out.println("  Page " + pageCount + ": " + response.differences().size() + " differences");

                    // Handle rate limiting
                    if (response.nextToken() != null) {
                        Thread.sleep(100); // Small delay between pages
                    }
                }

                result.setDifferences(allDifferences);
                result.setTotalDifferences(allDifferences.size());
                result.setSuccess(true);
                result.setPageCount(pageCount);
                break;

            } catch (CodeCommitException e) {
                System.err.println("CodeCommit error: " + e.getMessage());
                result.setErrorMessage(e.getMessage());

                // Handle specific error types
                if (e.statusCode() == 429) { // Too Many Requests
                    System.out.println("Rate limited, retrying after delay...");
                    retryCount++;
                    sleep(RETRY_DELAY.multipliedBy(retryCount));
                } else if (e.statusCode() == 400) { // Bad Request
                    System.err.println("Bad request - check commit IDs");
                    result.setSuccess(false);
                    break;
                } else {
                    retryCount++;
                    sleep(RETRY_DELAY);
                }

            } catch (SdkException e) {
                System.err.println("SDK error: " + e.getMessage());
                result.setErrorMessage(e.getMessage());
                retryCount++;
                sleep(RETRY_DELAY);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.setErrorMessage("Interrupted: " + e.getMessage());
                result.setSuccess(false);
                break;
            }
        }

        if (retryCount >= MAX_RETRIES && !result.isSuccess()) {
            result.setSuccess(false);
            result.setErrorMessage("Max retries exceeded");
        }

        result.setEndTime(System.currentTimeMillis());
        result.setDurationMs(result.getEndTime() - result.getStartTime());

        return result;
    }

    /**
     * Analyze differences and categorize them
     */
    public DifferenceAnalysis analyzeDifferences(List<Difference> differences) {
        DifferenceAnalysis analysis = new DifferenceAnalysis();

        int added = 0, modified = 0, deleted = 0, other = 0;
        int javaFiles = 0, totalFiles = 0;

        for (Difference diff : differences) {
            totalFiles++;

            // Categorize by change type
            if (diff.changeType() != null) {
                switch (diff.changeType()) {
                    case A -> added++;       // Added
                    case M -> modified++;    // Modified
                    case D -> deleted++;     // Deleted
                    default -> other++;
                }
            }

            // Count Java files
            if (diff.afterBlob() != null && diff.afterBlob().path() != null) {
                if (diff.afterBlob().path().endsWith(".java")) {
                    javaFiles++;
                }
            } else if (diff.beforeBlob() != null && diff.beforeBlob().path() != null) {
                if (diff.beforeBlob().path().endsWith(".java")) {
                    javaFiles++;
                }
            }
        }

        analysis.setTotalFiles(totalFiles);
        analysis.setAddedFiles(added);
        analysis.setModifiedFiles(modified);
        analysis.setDeletedFiles(deleted);
        analysis.setOtherFiles(other);
        analysis.setJavaFiles(javaFiles);

        return analysis;
    }

    /**
     * Sleep with error handling
     */
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Close the client
     */
    public void close() {
        if (codeCommitClient != null) {
            codeCommitClient.close();
        }
    }

    /**
     * Result container
     */
    public static class DifferenceResult {
        private String repositoryName;
        private String beforeCommitId;
        private String afterCommitId;
        private List<Difference> differences = new ArrayList<>();
        private int totalDifferences;
        private int pageCount;
        private boolean success;
        private String errorMessage;
        private long startTime;
        private long endTime;
        private long durationMs;

        // Getters and setters
        public String getRepositoryName() { return repositoryName; }
        public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

        public String getBeforeCommitId() { return beforeCommitId; }
        public void setBeforeCommitId(String beforeCommitId) { this.beforeCommitId = beforeCommitId; }

        public String getAfterCommitId() { return afterCommitId; }
        public void setAfterCommitId(String afterCommitId) { this.afterCommitId = afterCommitId; }

        public List<Difference> getDifferences() { return differences; }
        public void setDifferences(List<Difference> differences) { this.differences = differences; }

        public int getTotalDifferences() { return totalDifferences; }
        public void setTotalDifferences(int totalDifferences) { this.totalDifferences = totalDifferences; }

        public int getPageCount() { return pageCount; }
        public void setPageCount(int pageCount) { this.pageCount = pageCount; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * Analysis result
     */
    public static class DifferenceAnalysis {
        private int totalFiles;
        private int addedFiles;
        private int modifiedFiles;
        private int deletedFiles;
        private int otherFiles;
        private int javaFiles;

        // Getters and setters
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

        public int getAddedFiles() { return addedFiles; }
        public void setAddedFiles(int addedFiles) { this.addedFiles = addedFiles; }

        public int getModifiedFiles() { return modifiedFiles; }
        public void setModifiedFiles(int modifiedFiles) { this.modifiedFiles = modifiedFiles; }

        public int getDeletedFiles() { return deletedFiles; }
        public void setDeletedFiles(int deletedFiles) { this.deletedFiles = deletedFiles; }

        public int getOtherFiles() { return otherFiles; }
        public void setOtherFiles(int otherFiles) { this.otherFiles = otherFiles; }

        public int getJavaFiles() { return javaFiles; }
        public void setJavaFiles(int javaFiles) { this.javaFiles = javaFiles; }
    }
}
