package com.aicodereview.service;

import com.aicodereview.common.dto.prompttemplate.CreatePromptTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.common.dto.prompttemplate.PromptTemplateDTO;
import com.aicodereview.common.dto.prompttemplate.UpdatePromptTemplateRequest;

import java.util.List;
import java.util.Map;

/**
 * Service interface for prompt template management.
 */
public interface PromptTemplateService {

    PromptTemplateDTO createPromptTemplate(CreatePromptTemplateRequest request);

    List<PromptTemplateDTO> listPromptTemplates(Boolean enabled, String category);

    PromptTemplateDTO getPromptTemplateById(Long id);

    PromptTemplateDTO updatePromptTemplate(Long id, UpdatePromptTemplateRequest request);

    void deletePromptTemplate(Long id);

    PreviewResponse previewTemplate(Long id, Map<String, Object> sampleData);
}
