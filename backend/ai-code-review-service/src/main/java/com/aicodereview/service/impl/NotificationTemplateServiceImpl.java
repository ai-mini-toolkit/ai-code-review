package com.aicodereview.service.impl;

import com.aicodereview.common.dto.notificationtemplate.CreateNotificationTemplateRequest;
import com.aicodereview.common.dto.notificationtemplate.NotificationTemplateDTO;
import com.aicodereview.common.dto.notificationtemplate.UpdateNotificationTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.common.exception.TemplateSyntaxException;
import com.aicodereview.repository.NotificationTemplateRepository;
import com.aicodereview.repository.entity.NotificationTemplate;
import com.aicodereview.service.NotificationTemplateService;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of NotificationTemplateService for notification template CRUD and preview operations.
 * Uses Handlebars 4.4.0 for Mustache-compatible template rendering.
 * Implements Redis-based caching for getTemplateById (cache: "notification-templates", TTL 10 min).
 *
 * <p>Pattern mirrors PromptTemplateServiceImpl (Story 1.7) with channel instead of category,
 * and variables (JSONB) as an additional field.
 *
 * <p>Security: Write operations require ADMIN role (enforced at both URL-pattern level in
 * SecurityConfig and method level via @PreAuthorize in NotificationTemplateController).
 * Read operations require authentication only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository notificationTemplateRepository;

    // Handlebars is thread-safe for compileInline() operations
    private static final Handlebars HANDLEBARS = new Handlebars();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public NotificationTemplateDTO createTemplate(CreateNotificationTemplateRequest request) {
        log.info("Creating notification template: {}", request.getName());

        notificationTemplateRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new DuplicateResourceException("NotificationTemplate", "name", request.getName());
        });

        validateTemplateSyntax(request.getTemplateContent());
        if (request.getVariables() != null) {
            validateVariablesJson(request.getVariables());
        }

        NotificationTemplate template = NotificationTemplate.builder()
                .name(request.getName())
                .channel(request.getChannel())
                .templateContent(request.getTemplateContent())
                .variables(request.getVariables())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        NotificationTemplate saved = notificationTemplateRepository.save(template);
        log.info("Notification template created with id: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplateDTO> listTemplates(String channel, Boolean enabled) {
        List<NotificationTemplate> templates;
        if (channel != null && enabled != null) {
            log.debug("Listing notification templates with channel={} and enabled={}", channel, enabled);
            templates = notificationTemplateRepository.findByChannelAndEnabled(channel, enabled);
        } else if (channel != null) {
            log.debug("Listing notification templates with channel={}", channel);
            templates = notificationTemplateRepository.findByChannel(channel);
        } else if (enabled != null) {
            log.debug("Listing notification templates with enabled={}", enabled);
            templates = notificationTemplateRepository.findByEnabled(enabled);
        } else {
            log.debug("Listing all notification templates");
            templates = notificationTemplateRepository.findAll();
        }
        return templates.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "notification-templates", key = "#p0")
    public NotificationTemplateDTO getTemplateById(Long id) {
        log.debug("Getting notification template by id: {}", id);
        NotificationTemplate template = notificationTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationTemplate", id));
        return toDTO(template);
    }

    @Override
    @CacheEvict(value = "notification-templates", key = "#p0")
    public NotificationTemplateDTO updateTemplate(Long id, UpdateNotificationTemplateRequest request) {
        log.info("Updating notification template id: {}", id);
        NotificationTemplate template = notificationTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationTemplate", id));

        if (request.getName() != null && !request.getName().equals(template.getName())) {
            notificationTemplateRepository.findByName(request.getName()).ifPresent(existing -> {
                throw new DuplicateResourceException("NotificationTemplate", "name", request.getName());
            });
            template.setName(request.getName());
        }

        if (request.getChannel() != null) {
            template.setChannel(request.getChannel());
        }
        if (request.getTemplateContent() != null) {
            validateTemplateSyntax(request.getTemplateContent());
            template.setTemplateContent(request.getTemplateContent());
        }
        if (request.getVariables() != null) {
            validateVariablesJson(request.getVariables());
            template.setVariables(request.getVariables());
        }
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }

        NotificationTemplate saved = notificationTemplateRepository.save(template);
        log.info("Notification template updated: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @CacheEvict(value = "notification-templates", key = "#p0")
    public void deleteTemplate(Long id) {
        log.info("Deleting notification template id: {}", id);
        if (!notificationTemplateRepository.existsById(id)) {
            throw new ResourceNotFoundException("NotificationTemplate", id);
        }
        notificationTemplateRepository.deleteById(id);
        log.info("Notification template deleted: {}", id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PreviewResponse previewTemplate(Long id, Map<String, Object> variables) {
        log.info("Previewing notification template id: {}", id);
        NotificationTemplate template = notificationTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationTemplate", id));

        Map<String, Object> context = variables != null ? variables : Map.of();
        long startTime = System.currentTimeMillis();
        try {
            Template compiled = HANDLEBARS.compileInline(template.getTemplateContent());
            String rendered = compiled.apply(context);
            long elapsed = System.currentTimeMillis() - startTime;
            return PreviewResponse.builder()
                    .renderedContent(rendered)
                    .renderTimeMs(elapsed)
                    .build();
        } catch (IOException | HandlebarsException e) {
            log.warn("Notification template rendering failed for id {}: {}", id,
                    e.getMessage() != null ? e.getMessage().substring(0, Math.min(200, e.getMessage().length())) : "unknown");
            throw new TemplateSyntaxException("Template rendering failed: " + e.getMessage(), e);
        }
    }

    private void validateTemplateSyntax(String templateContent) {
        try {
            HANDLEBARS.compileInline(templateContent);
        } catch (IOException | HandlebarsException e) {
            throw new TemplateSyntaxException("Invalid Mustache template syntax: " + e.getMessage(), e);
        }
    }

    private void validateVariablesJson(String variables) {
        try {
            OBJECT_MAPPER.readTree(variables);
        } catch (IOException e) {
            throw new TemplateSyntaxException("Invalid JSON in variables field: " + e.getMessage(), e);
        }
    }

    private NotificationTemplateDTO toDTO(NotificationTemplate template) {
        return NotificationTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .channel(template.getChannel())
                .templateContent(template.getTemplateContent())
                .variables(template.getVariables())
                .enabled(template.getEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
