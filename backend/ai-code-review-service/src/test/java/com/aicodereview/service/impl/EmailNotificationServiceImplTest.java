package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailNotificationServiceImpl.
 *
 * @since 7.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationServiceImpl Unit Tests")
class EmailNotificationServiceImplTest {

    @Mock
    private NotificationConfigRepository notificationConfigRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private ReviewReportService reviewReportService;

    @Mock
    private ReviewResultService reviewResultService;

    @Mock
    private JavaMailSender defaultMailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailNotificationServiceImpl service;

    private static final Long TASK_ID = 100L;
    private static final Long PROJECT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationServiceImpl(
                notificationConfigRepository,
                reviewTaskRepository,
                reviewReportService,
                reviewResultService,
                defaultMailSender,
                "noreply@test.com");
    }

    private ReviewTask buildTask() {
        Project project = Project.builder().id(PROJECT_ID).name("Test Project").build();
        return ReviewTask.builder().id(TASK_ID).project(project).build();
    }

    private NotificationConfigEntity buildConfig(boolean enabled, String recipients) {
        Project project = Project.builder().id(PROJECT_ID).build();
        return NotificationConfigEntity.builder()
                .id(1L)
                .project(project)
                .emailEnabled(enabled)
                .emailRecipients(recipients)
                .build();
    }

    private ReviewReportDTO buildReport(boolean success) {
        return ReviewReportDTO.builder()
                .taskId(TASK_ID)
                .projectName("Test Project")
                .branch("main")
                .author("dev@test.com")
                .success(success)
                .summary(ReviewStatisticsDTO.builder()
                        .total(3)
                        .bySeverity(Map.of("HIGH", 1, "MEDIUM", 2))
                        .byCategory(Map.of())
                        .build())
                .build();
    }

    @Nested
    @DisplayName("parseRecipients")
    class ParseRecipients {

        @Test
        @DisplayName("Should parse comma-separated email addresses")
        void shouldParseCommaSeparated() {
            List<String> result = service.parseRecipients("a@test.com, b@test.com, c@test.com");
            assertThat(result).containsExactly("a@test.com", "b@test.com", "c@test.com");
        }

        @Test
        @DisplayName("Should trim whitespace from addresses")
        void shouldTrimWhitespace() {
            List<String> result = service.parseRecipients("  a@test.com  ,  b@test.com  ");
            assertThat(result).containsExactly("a@test.com", "b@test.com");
        }

        @Test
        @DisplayName("Should filter empty entries")
        void shouldFilterEmpty() {
            List<String> result = service.parseRecipients("a@test.com,,, ,b@test.com");
            assertThat(result).containsExactly("a@test.com", "b@test.com");
        }

        @Test
        @DisplayName("Should return empty list for null input")
        void shouldReturnEmptyForNull() {
            assertThat(service.parseRecipients(null)).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for blank input")
        void shouldReturnEmptyForBlank() {
            assertThat(service.parseRecipients("  ")).isEmpty();
        }

        @Test
        @DisplayName("Should handle single recipient")
        void shouldHandleSingleRecipient() {
            List<String> result = service.parseRecipients("solo@test.com");
            assertThat(result).containsExactly("solo@test.com");
        }

        @Test
        @DisplayName("Should filter out invalid email addresses")
        void shouldFilterInvalidEmails() {
            List<String> result = service.parseRecipients("valid@test.com, not-an-email, missing@, @nodomain, ok@example.org");
            assertThat(result).containsExactly("valid@test.com", "ok@example.org");
        }
    }

    @Nested
    @DisplayName("buildSubject")
    class BuildSubject {

        @Test
        @DisplayName("Should build passed subject")
        void shouldBuildPassedSubject() {
            ReviewReportDTO report = buildReport(true);
            String subject = service.buildSubject(report, false);
            assertThat(subject).isEqualTo("[AI Code Review] Test Project - main - Passed");
        }

        @Test
        @DisplayName("Should build failed subject")
        void shouldBuildFailedSubject() {
            ReviewReportDTO report = buildReport(false);
            String subject = service.buildSubject(report, false);
            assertThat(subject).isEqualTo("[AI Code Review] Test Project - main - Failed");
        }

        @Test
        @DisplayName("Should build threshold violation subject")
        void shouldBuildViolationSubject() {
            ReviewReportDTO report = buildReport(true);
            String subject = service.buildSubject(report, true);
            assertThat(subject).isEqualTo("[AI Code Review] Test Project - main - Threshold Violation");
        }
    }

    @Nested
    @DisplayName("prependViolationBanner")
    class PrependViolationBanner {

        @Test
        @DisplayName("Should insert banner after body tag")
        void shouldInsertBannerAfterBody() {
            String html = "<html><body style=\"font-family:Arial;\"><h1>Report</h1></body></html>";
            ReviewResultDTO result = ReviewResultDTO.builder()
                    .thresholdResult(ThresholdValidationResultDTO.builder()
                            .passed(false)
                            .violations(List.of(
                                    ThresholdViolationDTO.builder().rule("HIGH <= 0").actual(2).threshold(0).build()))
                            .build())
                    .build();

            String modified = service.prependViolationBanner(html, result);
            assertThat(modified).contains("Threshold Violation");
            assertThat(modified).contains("1 violation(s) detected");
            assertThat(modified.indexOf("Threshold Violation")).isGreaterThan(modified.indexOf("<body"));
        }

        @Test
        @DisplayName("Should handle null threshold result")
        void shouldHandleNullThreshold() {
            String html = "<html><body><h1>Report</h1></body></html>";
            ReviewResultDTO result = ReviewResultDTO.builder().thresholdResult(null).build();

            String modified = service.prependViolationBanner(html, result);
            assertThat(modified).contains("0 violation(s) detected");
        }
    }

    @Nested
    @DisplayName("resolveMailSender")
    class ResolveMailSender {

        @Test
        @DisplayName("Should use project-level SMTP when configured")
        void shouldUseProjectSmtp() {
            NotificationConfigEntity config = buildConfig(true, "dev@test.com");
            config.setSmtpHost("smtp.project.com");
            config.setSmtpPort(465);
            config.setSmtpUsername("user");
            config.setSmtpPassword("pass");

            JavaMailSender sender = service.resolveMailSender(config);
            assertThat(sender).isInstanceOf(JavaMailSenderImpl.class);
            JavaMailSenderImpl impl = (JavaMailSenderImpl) sender;
            assertThat(impl.getHost()).isEqualTo("smtp.project.com");
            assertThat(impl.getPort()).isEqualTo(465);
        }

        @Test
        @DisplayName("Should fall back to default when no project SMTP")
        void shouldFallBackToDefault() {
            NotificationConfigEntity config = buildConfig(true, "dev@test.com");
            // No smtp_host set

            JavaMailSender sender = service.resolveMailSender(config);
            assertThat(sender).isSameAs(defaultMailSender);
        }

        @Test
        @DisplayName("Should fall back to default when SMTP host is blank")
        void shouldFallBackWhenBlank() {
            NotificationConfigEntity config = buildConfig(true, "dev@test.com");
            config.setSmtpHost("  ");

            JavaMailSender sender = service.resolveMailSender(config);
            assertThat(sender).isSameAs(defaultMailSender);
        }

        @Test
        @DisplayName("Should use default port 587 when port is null")
        void shouldUseDefaultPort() {
            NotificationConfigEntity config = buildConfig(true, "dev@test.com");
            config.setSmtpHost("smtp.project.com");
            config.setSmtpPort(null);

            JavaMailSender sender = service.resolveMailSender(config);
            assertThat(((JavaMailSenderImpl) sender).getPort()).isEqualTo(587);
        }

        @Test
        @DisplayName("Should cache project-level mail sender instance")
        void shouldCacheProjectMailSender() {
            NotificationConfigEntity config = buildConfig(true, "dev@test.com");
            config.setId(42L);
            config.setSmtpHost("smtp.project.com");
            config.setSmtpPort(465);

            JavaMailSender sender1 = service.resolveMailSender(config);
            JavaMailSender sender2 = service.resolveMailSender(config);
            assertThat(sender1).isSameAs(sender2);
        }
    }

    @Nested
    @DisplayName("sendReviewCompleteNotification")
    class SendReviewCompleteNotification {

        @Test
        @DisplayName("Should send email when all conditions met")
        void shouldSendEmail() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true, "dev@test.com")));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(true));
            when(reviewReportService.renderHtml(any())).thenReturn("<html><body>Report</body></html>");
            when(defaultMailSender.createMimeMessage()).thenReturn(mimeMessage);

            service.sendReviewCompleteNotification(TASK_ID);

            verify(defaultMailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should skip when task not found")
        void shouldSkipWhenTaskNotFound() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            service.sendReviewCompleteNotification(TASK_ID);

            verifyNoInteractions(defaultMailSender);
        }

        @Test
        @DisplayName("Should skip when email not enabled")
        void shouldSkipWhenNotEnabled() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(false, "dev@test.com")));

            service.sendReviewCompleteNotification(TASK_ID);

            verifyNoInteractions(defaultMailSender);
        }

        @Test
        @DisplayName("Should skip when no notification config")
        void shouldSkipWhenNoConfig() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

            service.sendReviewCompleteNotification(TASK_ID);

            verifyNoInteractions(defaultMailSender);
        }

        @Test
        @DisplayName("Should skip when no recipients configured")
        void shouldSkipWhenNoRecipients() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true, "")));

            service.sendReviewCompleteNotification(TASK_ID);

            verifyNoInteractions(defaultMailSender);
        }

        @Test
        @DisplayName("Should skip when no mail sender available")
        void shouldSkipWhenNoMailSender() {
            // Create service with no default mail sender
            EmailNotificationServiceImpl noMailService = new EmailNotificationServiceImpl(
                    notificationConfigRepository, reviewTaskRepository,
                    reviewReportService, reviewResultService,
                    null, "noreply@test.com");

            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true, "dev@test.com")));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(true));
            when(reviewReportService.renderHtml(any())).thenReturn("<html><body>Report</body></html>");

            noMailService.sendReviewCompleteNotification(TASK_ID);

            // Should not throw, just skip
        }

        @Test
        @DisplayName("Should not propagate exceptions from mail sending")
        void shouldNotPropagateExceptions() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true, "dev@test.com")));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(true));
            when(reviewReportService.renderHtml(any())).thenReturn("<html><body>Report</body></html>");
            when(defaultMailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP error"));

            // Should not throw
            service.sendReviewCompleteNotification(TASK_ID);
        }
    }

    @Nested
    @DisplayName("sendThresholdViolationNotification")
    class SendThresholdViolationNotification {

        @Test
        @DisplayName("Should send violation email with banner")
        void shouldSendViolationEmail() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true, "dev@test.com")));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(false));
            when(reviewReportService.renderHtml(any())).thenReturn(
                    "<html><body style=\"font-family:Arial;\"><h1>Report</h1></body></html>");
            when(reviewResultService.getResultByTaskId(TASK_ID)).thenReturn(
                    ReviewResultDTO.builder()
                            .taskId(TASK_ID)
                            .thresholdResult(ThresholdValidationResultDTO.builder()
                                    .passed(false)
                                    .violations(List.of(
                                            ThresholdViolationDTO.builder()
                                                    .rule("CRITICAL <= 0").actual(2).threshold(0).build()))
                                    .action("BLOCK_MERGE")
                                    .build())
                            .build());
            when(defaultMailSender.createMimeMessage()).thenReturn(mimeMessage);

            service.sendThresholdViolationNotification(TASK_ID);

            verify(defaultMailSender).send(any(MimeMessage.class));
        }
    }
}
