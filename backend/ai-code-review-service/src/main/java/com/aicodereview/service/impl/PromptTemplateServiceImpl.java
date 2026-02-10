package com.aicodereview.service.impl;

import com.aicodereview.common.dto.prompttemplate.CreatePromptTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.common.dto.prompttemplate.PromptTemplateDTO;
import com.aicodereview.common.dto.prompttemplate.UpdatePromptTemplateRequest;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.common.exception.TemplateSyntaxException;
import com.aicodereview.repository.PromptTemplateRepository;
import com.aicodereview.repository.entity.PromptTemplate;
import com.aicodereview.service.PromptTemplateService;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of PromptTemplateService for prompt template CRUD and preview operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    private static final Handlebars HANDLEBARS = new Handlebars();

    @Override
    public PromptTemplateDTO createPromptTemplate(CreatePromptTemplateRequest request) {
        log.info("Creating prompt template: {}", request.getName());

        promptTemplateRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new DuplicateResourceException("PromptTemplate", "name", request.getName());
        });

        validateTemplateSyntax(request.getTemplateContent());

        PromptTemplate template = PromptTemplate.builder()
                .name(request.getName())
                .category(request.getCategory())
                .templateContent(request.getTemplateContent())
                .version(request.getVersion() != null ? request.getVersion() : 1)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        PromptTemplate saved = promptTemplateRepository.save(template);
        log.info("Prompt template created with id: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplateDTO> listPromptTemplates(Boolean enabled, String category) {
        List<PromptTemplate> templates;
        if (category != null && enabled != null) {
            log.debug("Listing prompt templates with category={} and enabled={}", category, enabled);
            templates = promptTemplateRepository.findByCategoryAndEnabled(category, enabled);
        } else if (category != null) {
            log.debug("Listing prompt templates with category={}", category);
            templates = promptTemplateRepository.findByCategory(category);
        } else if (enabled != null) {
            log.debug("Listing prompt templates with enabled={}", enabled);
            templates = promptTemplateRepository.findByEnabled(enabled);
        } else {
            log.debug("Listing all prompt templates");
            templates = promptTemplateRepository.findAll();
        }
        return templates.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "prompt-templates", key = "#p0")
    public PromptTemplateDTO getPromptTemplateById(Long id) {
        log.debug("Getting prompt template by id: {}", id);
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromptTemplate", id));
        return toDTO(template);
    }

    @Override
    @CacheEvict(value = "prompt-templates", key = "#p0")
    public PromptTemplateDTO updatePromptTemplate(Long id, UpdatePromptTemplateRequest request) {
        log.info("Updating prompt template id: {}", id);
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromptTemplate", id));

        if (request.getName() != null && !request.getName().equals(template.getName())) {
            promptTemplateRepository.findByName(request.getName()).ifPresent(existing -> {
                throw new DuplicateResourceException("PromptTemplate", "name", request.getName());
            });
            template.setName(request.getName());
        }

        if (request.getCategory() != null) {
            template.setCategory(request.getCategory());
        }
        if (request.getTemplateContent() != null) {
            validateTemplateSyntax(request.getTemplateContent());
            template.setTemplateContent(request.getTemplateContent());
        }
        if (request.getVersion() != null) {
            template.setVersion(request.getVersion());
        }
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }

        PromptTemplate saved = promptTemplateRepository.save(template);
        log.info("Prompt template updated: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @CacheEvict(value = "prompt-templates", key = "#p0")
    public void deletePromptTemplate(Long id) {
        log.info("Deleting prompt template id: {}", id);
        if (!promptTemplateRepository.existsById(id)) {
            throw new ResourceNotFoundException("PromptTemplate", id);
        }
        promptTemplateRepository.deleteById(id);
        log.info("Prompt template deleted: {}", id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PreviewResponse previewTemplate(Long id, Map<String, Object> sampleData) {
        log.info("Previewing template id: {}", id);
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromptTemplate", id));

        Map<String, Object> context = sampleData != null ? sampleData : Map.of();
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
            log.warn("Template rendering failed for id {}: {}", id, e.getMessage());
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

    private PromptTemplateDTO toDTO(PromptTemplate template) {
        return PromptTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .category(template.getCategory())
                .templateContent(template.getTemplateContent())
                .version(template.getVersion())
                .enabled(template.getEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
