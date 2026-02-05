package com.aicr.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Mock code review task for testing
 */
public class MockReviewTask {

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("repository_name")
    private String repositoryName;

    @JsonProperty("commit_id")
    private String commitId;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("review_dimension")
    private String reviewDimension;

    @JsonProperty("processing_time_ms")
    private long processingTimeMs;

    @JsonProperty("created_at")
    private long createdAt;

    @JsonProperty("started_at")
    private long startedAt;

    @JsonProperty("completed_at")
    private long completedAt;

    @JsonProperty("status")
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @JsonProperty("latencyMs")
    private long latencyMs;

    @JsonProperty("totalTimeMs")
    private long totalTimeMs;

    public MockReviewTask() {
        this.taskId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.status = "PENDING";
    }

    public MockReviewTask(String repositoryName, String commitId, String filePath, String reviewDimension) {
        this();
        this.repositoryName = repositoryName;
        this.commitId = commitId;
        this.filePath = filePath;
        this.reviewDimension = reviewDimension;
    }

    /**
     * Simulate task processing
     */
    public void process() throws InterruptedException {
        this.startedAt = System.currentTimeMillis();
        this.status = "PROCESSING";

        // Simulate variable processing time (50-200ms)
        int processingTime = 50 + (int) (Math.random() * 150);
        Thread.sleep(processingTime);

        this.completedAt = System.currentTimeMillis();
        this.processingTimeMs = this.completedAt - this.startedAt;
        this.status = "COMPLETED";
    }

    /**
     * Get task latency (time from creation to start)
     */
    public long getLatencyMs() {
        if (startedAt > 0) {
            return startedAt - createdAt;
        }
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    /**
     * Get total time (creation to completion)
     */
    public long getTotalTimeMs() {
        if (completedAt > 0) {
            return completedAt - createdAt;
        }
        return totalTimeMs;
    }

    public void setTotalTimeMs(long totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }

    // Getters and setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getReviewDimension() {
        return reviewDimension;
    }

    public void setReviewDimension(String reviewDimension) {
        this.reviewDimension = reviewDimension;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
