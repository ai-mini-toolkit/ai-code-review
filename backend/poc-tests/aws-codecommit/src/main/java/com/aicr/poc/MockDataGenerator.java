package com.aicr.poc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates mock test data for AWS CodeCommit integration testing
 */
public class MockDataGenerator {

    /**
     * Generate mock test scenarios
     */
    public static List<TestScenario> generateTestScenarios() {
        List<TestScenario> scenarios = new ArrayList<>();

        // Note: These are example scenarios. In real testing, you'll need to:
        // 1. Create an actual CodeCommit repository
        // 2. Make commits with different file counts
        // 3. Use real commit IDs

        scenarios.add(new TestScenario(
                "Small commit",
                "my-test-repo",
                "commit-id-1",
                "commit-id-2",
                5,  // Expected files
                "Test basic API functionality with < 10 files"
        ));

        scenarios.add(new TestScenario(
                "Medium commit",
                "my-test-repo",
                "commit-id-2",
                "commit-id-3",
                25,  // Expected files
                "Test pagination with 10-50 files"
        ));

        scenarios.add(new TestScenario(
                "Large commit",
                "my-test-repo",
                "commit-id-3",
                "commit-id-4",
                75,  // Expected files
                "Test handling of large changesets (50+ files)"
        ));

        return scenarios;
    }

    /**
     * Generate setup instructions for CodeCommit repository
     */
    public static String generateSetupInstructions() {
        return """
                AWS CodeCommit Test Repository Setup Instructions:

                1. Create a CodeCommit repository:
                   aws codecommit create-repository --repository-name ai-code-review-test

                2. Clone the repository:
                   git clone codecommit://ai-code-review-test

                3. Create small commit (5-10 files):
                   cd ai-code-review-test
                   for i in {1..8}; do echo "Test file $i" > file$i.java; done
                   git add .
                   git commit -m "Small commit - 8 files"
                   git push
                   COMMIT_1=$(git rev-parse HEAD)

                4. Create medium commit (10-50 files):
                   for i in {9..30}; do echo "Test file $i" > file$i.java; done
                   git add .
                   git commit -m "Medium commit - 22 files"
                   git push
                   COMMIT_2=$(git rev-parse HEAD)

                5. Create large commit (50+ files):
                   for i in {31..100}; do echo "Test file $i" > file$i.java; done
                   git add .
                   git commit -m "Large commit - 70 files"
                   git push
                   COMMIT_3=$(git rev-parse HEAD)

                6. Update test scenarios with actual commit IDs:
                   - Small: HEAD~2 to HEAD~1
                   - Medium: HEAD~1 to HEAD
                   - Large: Use all files from initial commit to HEAD

                7. Configure AWS credentials:
                   aws configure
                   # Or set environment variables:
                   # export AWS_ACCESS_KEY_ID=your-key
                   # export AWS_SECRET_ACCESS_KEY=your-secret
                   # export AWS_REGION=us-east-1
                """;
    }

    /**
     * Test scenario data structure
     */
    public static class TestScenario {
        private final String name;
        private final String repositoryName;
        private final String beforeCommitId;
        private final String afterCommitId;
        private final int expectedFileCount;
        private final String description;

        public TestScenario(String name, String repositoryName, String beforeCommitId,
                            String afterCommitId, int expectedFileCount, String description) {
            this.name = name;
            this.repositoryName = repositoryName;
            this.beforeCommitId = beforeCommitId;
            this.afterCommitId = afterCommitId;
            this.expectedFileCount = expectedFileCount;
            this.description = description;
        }

        public String getName() { return name; }
        public String getRepositoryName() { return repositoryName; }
        public String getBeforeCommitId() { return beforeCommitId; }
        public String getAfterCommitId() { return afterCommitId; }
        public int getExpectedFileCount() { return expectedFileCount; }
        public String getDescription() { return description; }

        public boolean isConfigured() {
            // Check if commit IDs are actual SHA-1 hashes (40 chars) vs placeholders
            return beforeCommitId.length() == 40 && afterCommitId.length() == 40;
        }
    }

    /**
     * Generate example configuration file content
     */
    public static String generateExampleConfig() {
        return """
                # test-scenarios.properties
                # Update these values with your actual CodeCommit repository and commit IDs

                # Repository configuration
                repository.name=ai-code-review-test
                aws.region=us-east-1

                # Small commit scenario (< 10 files)
                scenario.small.before=<COMMIT_ID_1>
                scenario.small.after=<COMMIT_ID_2>
                scenario.small.expected=5-10

                # Medium commit scenario (10-50 files)
                scenario.medium.before=<COMMIT_ID_2>
                scenario.medium.after=<COMMIT_ID_3>
                scenario.medium.expected=10-50

                # Large commit scenario (50+ files)
                scenario.large.before=<COMMIT_ID_3>
                scenario.large.after=<COMMIT_ID_4>
                scenario.large.expected=50-100

                # Performance thresholds
                threshold.small.max_time_ms=2000
                threshold.medium.max_time_ms=5000
                threshold.large.max_time_ms=15000
                """;
    }
}
