package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.notificationtemplate.CreateNotificationTemplateRequest;
import com.aicodereview.common.dto.notificationtemplate.NotificationTemplateDTO;
import com.aicodereview.common.dto.notificationtemplate.UpdateNotificationTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.service.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for notification template management.
 * Provides CRUD operations and template preview rendering.
 *
 * <p>Security model:
 * <ul>
 *   <li>Write operations (POST, PUT, DELETE, preview) require ADMIN role, enforced at two layers:
 *       URL-pattern-level in SecurityConfig and method-level via @PreAuthorize.</li>
 *   <li>Read operations (GET list, GET by ID) require authentication but not ADMIN role.</li>
 * </ul>
 *
 * <p>Pattern mirrors PromptTemplateController (Story 1.7).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService notificationTemplateService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationTemplateDTO>> createTemplate(
            @Valid @RequestBody CreateNotificationTemplateRequest request) {
        log.info("POST /api/v1/notification-templates - Creating notification template: {}", request.getName());
        NotificationTemplateDTO template = notificationTemplateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(template));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationTemplateDTO>>> listTemplates(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        log.debug("GET /api/v1/notification-templates - Listing templates, channel={}, enabled={}", channel, enabled);
        List<NotificationTemplateDTO> templates = notificationTemplateService.listTemplates(channel, enabled);
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplateDTO>> getTemplateById(
            @PathVariable("id") Long id) {
        log.debug("GET /api/v1/notification-templates/{} - Getting notification template", id);
        NotificationTemplateDTO template = notificationTemplateService.getTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplateDTO>> updateTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        log.info("PUT /api/v1/notification-templates/{} - Updating notification template", id);
        NotificationTemplateDTO template = notificationTemplateService.updateTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/notification-templates/{} - Deleting notification template", id);
        notificationTemplateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> previewTemplate(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> variables) {
        log.info("POST /api/v1/notification-templates/{}/preview - Previewing notification template", id);
        PreviewResponse result = notificationTemplateService.previewTemplate(id, variables);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
