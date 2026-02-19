package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.integration.im.DingTalkWebhookService;
import com.aicodereview.integration.im.LarkWebhookService;
import com.aicodereview.integration.im.SlackWebhookService;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IMNotificationServiceImpl.
 *
 * @since 7.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IMNotificationServiceImpl Unit Tests")
class IMNotificationServiceImplTest {

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private NotificationConfigRepository notificationConfigRepository;

    @Mock
    private ReviewReportService reviewReportService;

    @Mock
    private ReviewResultService reviewResultService;

    @Mock
    private DingTalkWebhookService dingTalkWebhookService;

    @Mock
    private SlackWebhookService slackWebhookService;

    @Mock
    private LarkWebhookService larkWebhookService;

    private IMNotificationServiceImpl service;

    private static final Long TASK_ID = 100L;
    private static final Long PROJECT_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new IMNotificationServiceImpl(
                reviewTaskRepository,
                notificationConfigRepository,
                reviewReportService,
                reviewResultService,
                dingTalkWebhookService,
                slackWebhookService,
                larkWebhookService);
    }

    private ReviewTask createTask() {
        Project project = Project.builder().id(PROJECT_ID).name("Test Project").build();
        return ReviewTask.builder()
                .id(TASK_ID)
                .project(project)
                .branch("feature/login")
                .author("dev@test.com")
                .build();
    }

    private NotificationConfigEntity createConfig(boolean dingtalk, boolean slack, boolean lark) {
        return NotificationConfigEntity.builder()
                .dingtalkEnabled(dingtalk)
                .dingtalkWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=test")
                .dingtalkSecret("SEC_secret")
                .slackEnabled(slack)
                .slackWebhookUrl("https://hooks.slack.com/services/T/B/x")
                .larkEnabled(lark)
                .larkWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test")
                .build();
    }

    private ReviewReportDTO createReport() {
        return ReviewReportDTO.builder()
                .taskId(TASK_ID)
                .projectName("Test Project")
                .branch("feature/login")
                .author("dev@test.com")
                .summary(ReviewStatisticsDTO.builder()
                        .total(10)
                        .bySeverity(Map.of("CRITICAL", 2, "HIGH", 5, "MEDIUM", 3))
                        .byCategory(Map.of("SECURITY", 3, "CORRECTNESS", 7))
                        .build())
                .build();
    }

    private ReviewResultDTO createResult() {
        return ReviewResultDTO.builder()
                .taskId(TASK_ID)
                .thresholdResult(ThresholdValidationResultDTO.builder()
                        .passed(false)
                        .action("BLOCK_MERGE")
                        .violations(List.of(
                                ThresholdViolationDTO.builder()
                                        .rule("CRITICAL <= 0").actual(2).threshold(0).build(),
                                ThresholdViolationDTO.builder()
                                        .rule("HIGH <= 3").actual(5).threshold(3).build()))
                        .build())
                .build();
    }

    private void mockTaskAndConfig(NotificationConfigEntity config) {
        when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask()));
        when(notificationConfigRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config));
        when(reviewReportService.generateReport(TASK_ID)).thenReturn(createReport());
        when(reviewResultService.getResultByTaskId(TASK_ID)).thenReturn(createResult());
    }

    @Nested
    @DisplayName("sendThresholdViolationNotifications")
    class SendThresholdViolationNotifications {

        @Test
        @DisplayName("Should send DingTalk notification when enabled")
        void shouldSendDingTalkWhenEnabled() {
            NotificationConfigEntity config = createConfig(true, false, false);
            mockTaskAndConfig(config);
            when(dingTalkWebhookService.sendNotification(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            service.sendThresholdViolationNotifications(TASK_ID);

            verify(dingTalkWebhookService).sendNotification(
                    eq(config.getDingtalkWebhookUrl()),
                    eq(config.getDingtalkSecret()),
                    contains("审查超阈值警告"),
                    contains("CRITICAL"));
            verifyNoInteractions(slackWebhookService, larkWebhookService);
        }

        @Test
        @DisplayName("Should send Slack notification when enabled")
        void shouldSendSlackWhenEnabled() {
            NotificationConfigEntity config = createConfig(false, true, false);
            mockTaskAndConfig(config);
            when(slackWebhookService.sendNotification(anyString(), anyString())).thenReturn(true);

            service.sendThresholdViolationNotifications(TASK_ID);

            verify(slackWebhookService).sendNotification(
                    eq(config.getSlackWebhookUrl()),
                    contains("Threshold Violation"));
            verifyNoInteractions(dingTalkWebhookService, larkWebhookService);
        }

        @Test
        @DisplayName("Should send Lark notification when enabled")
        void shouldSendLarkWhenEnabled() {
            NotificationConfigEntity config = createConfig(false, false, true);
            mockTaskAndConfig(config);
            when(larkWebhookService.sendNotification(anyString(), anyString(), anyString())).thenReturn(true);

            service.sendThresholdViolationNotifications(TASK_ID);

            verify(larkWebhookService).sendNotification(
                    eq(config.getLarkWebhookUrl()),
                    contains("审查超阈值警告"),
                    contains("CRITICAL"));
            verifyNoInteractions(dingTalkWebhookService, slackWebhookService);
        }

        @Test
        @DisplayName("Should send to all platforms when all enabled")
        void shouldSendToAllPlatformsWhenAllEnabled() {
            NotificationConfigEntity config = createConfig(true, true, true);
            mockTaskAndConfig(config);
            when(dingTalkWebhookService.sendNotification(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(true);
            when(slackWebhookService.sendNotification(anyString(), anyString())).thenReturn(true);
            when(larkWebhookService.sendNotification(anyString(), anyString(), anyString())).thenReturn(true);

            service.sendThresholdViolationNotifications(TASK_ID);

            verify(dingTalkWebhookService).sendNotification(anyString(), anyString(), anyString(), anyString());
            verify(slackWebhookService).sendNotification(anyString(), anyString());
            verify(larkWebhookService).sendNotification(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip when task not found")
        void shouldSkipWhenTaskNotFound() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            service.sendThresholdViolationNotifications(TASK_ID);

            verifyNoInteractions(notificationConfigRepository, reviewReportService,
                    dingTalkWebhookService, slackWebhookService, larkWebhookService);
        }

        @Test
        @DisplayName("Should skip when no notification config")
        void shouldSkipWhenNoConfig() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

            service.sendThresholdViolationNotifications(TASK_ID);

            verifyNoInteractions(reviewReportService,
                    dingTalkWebhookService, slackWebhookService, larkWebhookService);
        }

        @Test
        @DisplayName("Should skip when no IM platform enabled")
        void shouldSkipWhenNoImEnabled() {
            NotificationConfigEntity config = createConfig(false, false, false);
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask()));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config));

            service.sendThresholdViolationNotifications(TASK_ID);

            verifyNoInteractions(reviewReportService,
                    dingTalkWebhookService, slackWebhookService, larkWebhookService);
        }

        @Test
        @DisplayName("Should continue to other platforms when one fails")
        void shouldContinueWhenOnePlatformFails() {
            NotificationConfigEntity config = createConfig(true, true, true);
            mockTaskAndConfig(config);
            when(dingTalkWebhookService.sendNotification(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DingTalk error"));
            when(slackWebhookService.sendNotification(anyString(), anyString())).thenReturn(true);
            when(larkWebhookService.sendNotification(anyString(), anyString(), anyString())).thenReturn(true);

            assertThatCode(() -> service.sendThresholdViolationNotifications(TASK_ID))
                    .doesNotThrowAnyException();

            verify(slackWebhookService).sendNotification(anyString(), anyString());
            verify(larkWebhookService).sendNotification(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("buildNotificationContent")
    class BuildNotificationContent {

        @Test
        @DisplayName("DingTalk content should contain violations and issue summary")
        void dingtalkContentShouldContainViolations() {
            ReviewReportDTO report = createReport();
            ThresholdValidationResultDTO threshold = createResult().getThresholdResult();

            String content = service.buildDingTalkContent(report, threshold);

            assertThat(content).contains("AI Code Review 超阈值警告");
            assertThat(content).contains("Test Project");
            assertThat(content).contains("feature/login");
            assertThat(content).contains("dev@test.com");
            assertThat(content).contains("CRITICAL <= 0");
            assertThat(content).contains("实际: 2, 限制: 0");
            assertThat(content).contains("BLOCK_MERGE");
        }

        @Test
        @DisplayName("Slack content should use mrkdwn format")
        void slackContentShouldUseMrkdwn() {
            ReviewReportDTO report = createReport();
            ThresholdValidationResultDTO threshold = createResult().getThresholdResult();

            String content = service.buildSlackContent(report, threshold);

            assertThat(content).contains(":warning:");
            assertThat(content).contains("*AI Code Review Threshold Violation*");
            assertThat(content).contains("*Project:*");
            assertThat(content).contains("`CRITICAL <= 0`");
            assertThat(content).contains("BLOCK_MERGE");
        }

        @Test
        @DisplayName("Lark content should contain violations")
        void larkContentShouldContainViolations() {
            ReviewReportDTO report = createReport();
            ThresholdValidationResultDTO threshold = createResult().getThresholdResult();

            String content = service.buildLarkContent(report, threshold);

            assertThat(content).contains("Test Project");
            assertThat(content).contains("CRITICAL <= 0");
            assertThat(content).contains("BLOCK_MERGE");
        }
    }
}
