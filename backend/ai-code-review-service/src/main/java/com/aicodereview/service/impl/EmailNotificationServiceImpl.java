package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.EmailNotificationService;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of EmailNotificationService for sending review notification emails.
 * <p>
 * Reuses {@link ReviewReportService#renderHtml(ReviewReportDTO)} for email body generation.
 * Supports both global (application.yml) and per-project SMTP configuration.
 * </p>
 *
 * @since 7.1.0
 */
@Slf4j
@Service
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final NotificationConfigRepository notificationConfigRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewReportService reviewReportService;
    private final ReviewResultService reviewResultService;
    private final JavaMailSender defaultMailSender;
    private final String fromAddress;
    private final ConcurrentHashMap<Long, JavaMailSender> projectMailSenderCache = new ConcurrentHashMap<>();

    public EmailNotificationServiceImpl(
            NotificationConfigRepository notificationConfigRepository,
            ReviewTaskRepository reviewTaskRepository,
            ReviewReportService reviewReportService,
            @Lazy ReviewResultService reviewResultService,
            @Autowired(required = false) JavaMailSender defaultMailSender,
            @Value("${notification.email.from:noreply@aicodereview.com}") String fromAddress) {
        this.notificationConfigRepository = notificationConfigRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewReportService = reviewReportService;
        this.reviewResultService = reviewResultService;
        this.defaultMailSender = defaultMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendReviewCompleteNotification(Long taskId) {
        log.debug("Preparing review complete notification for task: {}", taskId);
        sendNotification(taskId, false);
    }

    @Override
    public void sendThresholdViolationNotification(Long taskId) {
        log.debug("Preparing threshold violation notification for task: {}", taskId);
        sendNotification(taskId, true);
    }

    private void sendNotification(Long taskId, boolean isViolation) {
        try {
            // 1. Look up project from task
            ReviewTask task = reviewTaskRepository.findById(taskId).orElse(null);
            if (task == null || task.getProject() == null) {
                log.debug("Task {} not found or has no project, skipping email notification", taskId);
                return;
            }
            Long projectId = task.getProject().getId();

            // 2. Check notification config
            NotificationConfigEntity config = notificationConfigRepository.findByProjectId(projectId)
                    .orElse(null);
            if (config == null || !Boolean.TRUE.equals(config.getEmailEnabled())) {
                log.debug("Email notification not enabled for project {}, skipping", projectId);
                return;
            }

            // 3. Parse recipients
            List<String> recipients = parseRecipients(config.getEmailRecipients());
            if (recipients.isEmpty()) {
                log.warn("No valid email recipients configured for project {}, skipping", projectId);
                return;
            }

            // 4. Generate report and render HTML
            ReviewReportDTO report = reviewReportService.generateReport(taskId);
            String htmlBody = reviewReportService.renderHtml(report);

            // 5. Add threshold violation banner if applicable
            if (isViolation) {
                ReviewResultDTO result = reviewResultService.getResultByTaskId(taskId);
                htmlBody = prependViolationBanner(htmlBody, result);
            }

            // 6. Build subject
            String subject = buildSubject(report, isViolation);

            // 7. Resolve mail sender (project-level or global)
            JavaMailSender mailSender = resolveMailSender(config);
            if (mailSender == null) {
                log.warn("No mail sender available for project {}, skipping email notification", projectId);
                return;
            }

            // 8. Send email
            sendEmail(mailSender, recipients, subject, htmlBody);
            log.info("Email notification sent for task {} to {} recipients", taskId, recipients.size());

        } catch (Exception e) {
            log.warn("Failed to send email notification for task {}: {}", taskId, e.getMessage());
        }
    }

    List<String> parseRecipients(String emailRecipients) {
        if (emailRecipients == null || emailRecipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(emailRecipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(this::isValidEmail)
                .toList();
    }

    private boolean isValidEmail(String email) {
        // Basic format check: must contain exactly one @ with non-empty local and domain parts
        int atIdx = email.indexOf('@');
        if (atIdx <= 0 || atIdx >= email.length() - 1) {
            log.warn("Skipping invalid email address: {}", email);
            return false;
        }
        String domain = email.substring(atIdx + 1);
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            log.warn("Skipping invalid email address: {}", email);
            return false;
        }
        return true;
    }

    String buildSubject(ReviewReportDTO report, boolean isViolation) {
        String status;
        if (isViolation) {
            status = "Threshold Violation";
        } else {
            status = Boolean.TRUE.equals(report.getSuccess()) ? "Passed" : "Failed";
        }
        return String.format("[AI Code Review] %s - %s - %s",
                report.getProjectName(), report.getBranch(), status);
    }

    String prependViolationBanner(String htmlBody, ReviewResultDTO result) {
        ThresholdValidationResultDTO threshold = result.getThresholdResult();
        int violationCount = threshold != null && threshold.getViolations() != null
                ? threshold.getViolations().size() : 0;

        String banner = String.format(
                "<div style=\"background:#f8d7da;border:1px solid #f5c6cb;padding:12px;border-radius:4px;margin:0 0 16px 0;\">"
                + "<strong style=\"color:#721c24;\">Threshold Violation</strong> "
                + "<span style=\"color:#721c24;\">%d violation(s) detected. Merge may be blocked.</span>"
                + "</div>", violationCount);

        // Insert after <body ...> tag
        int bodyIdx = htmlBody.indexOf("<body");
        if (bodyIdx >= 0) {
            int closeTag = htmlBody.indexOf(">", bodyIdx);
            if (closeTag >= 0) {
                return htmlBody.substring(0, closeTag + 1) + "\n" + banner + "\n" + htmlBody.substring(closeTag + 1);
            }
        }
        return banner + htmlBody;
    }

    JavaMailSender resolveMailSender(NotificationConfigEntity config) {
        if (config.getSmtpHost() != null && !config.getSmtpHost().isBlank()) {
            return projectMailSenderCache.computeIfAbsent(config.getId(), id -> createMailSender(config));
        }
        return defaultMailSender;
    }

    private JavaMailSender createMailSender(NotificationConfigEntity config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.connectiontimeout", "5000");
        return mailSender;
    }

    private void sendEmail(JavaMailSender mailSender, List<String> recipients,
                           String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }
}
