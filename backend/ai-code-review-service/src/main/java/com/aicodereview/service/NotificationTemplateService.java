package com.aicodereview.service;

import com.aicodereview.common.dto.notificationtemplate.CreateNotificationTemplateRequest;
import com.aicodereview.common.dto.notificationtemplate.NotificationTemplateDTO;
import com.aicodereview.common.dto.notificationtemplate.UpdateNotificationTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;

import java.util.List;
import java.util.Map;

/**
 * Service interface for notification template management.
 * Supports CRUD operations and Mustache/Handlebars template preview rendering.
 * Templates are cached in Redis to reduce database load.
 */
public interface NotificationTemplateService {

    /**
     * Create a new notification template.
     *
     * @param request template creation request with name, channel, content
     * @return the created template as DTO
     * @throws com.aicodereview.common.exception.DuplicateResourceException if name already exists
     * @throws com.aicodereview.common.exception.TemplateSyntaxException if template syntax is invalid
     */
    NotificationTemplateDTO createTemplate(CreateNotificationTemplateRequest request);

    /**
     * List notification templates with optional filtering by channel and/or enabled status.
     *
     * @param channel optional channel filter (EMAIL, GIT_COMMENT, DINGTALK, SLACK, LARK)
     * @param enabled optional enabled status filter
     * @return list of matching templates
     */
    List<NotificationTemplateDTO> listTemplates(String channel, Boolean enabled);

    /**
     * Get a notification template by its ID.
     * Result is cached in Redis for 10 minutes.
     *
     * @param id template ID
     * @return the template as DTO
     * @throws com.aicodereview.common.exception.ResourceNotFoundException if ID not found
     */
    NotificationTemplateDTO getTemplateById(Long id);

    /**
     * Update an existing notification template.
     * Only non-null fields in the request are applied.
     * Evicts the cache entry for this template.
     *
     * @param id template ID to update
     * @param request update request (all fields optional)
     * @return updated template as DTO
     * @throws com.aicodereview.common.exception.ResourceNotFoundException if ID not found
     * @throws com.aicodereview.common.exception.DuplicateResourceException if new name conflicts
     * @throws com.aicodereview.common.exception.TemplateSyntaxException if template syntax is invalid
     */
    NotificationTemplateDTO updateTemplate(Long id, UpdateNotificationTemplateRequest request);

    /**
     * Delete a notification template by ID.
     * Evicts the cache entry for this template.
     *
     * @param id template ID to delete
     * @throws com.aicodereview.common.exception.ResourceNotFoundException if ID not found
     */
    void deleteTemplate(Long id);

    /**
     * Preview a template by rendering it with the given variables.
     * Returns the rendered content and render time in milliseconds.
     *
     * @param id template ID to preview
     * @param variables runtime variables for rendering
     * @return preview result with rendered content and timing
     * @throws com.aicodereview.common.exception.ResourceNotFoundException if ID not found
     * @throws com.aicodereview.common.exception.TemplateSyntaxException if rendering fails
     */
    PreviewResponse previewTemplate(Long id, Map<String, Object> variables);
}
