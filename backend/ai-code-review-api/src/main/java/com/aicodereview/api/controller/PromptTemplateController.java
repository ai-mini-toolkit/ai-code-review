package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.prompttemplate.CreatePromptTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.common.dto.prompttemplate.PromptTemplateDTO;
import com.aicodereview.common.dto.prompttemplate.UpdatePromptTemplateRequest;
import com.aicodereview.service.PromptTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @PostMapping
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> createPromptTemplate(
            @Valid @RequestBody CreatePromptTemplateRequest request) {
        log.info("POST /api/v1/prompt-templates - Creating prompt template: {}", request.getName());
        PromptTemplateDTO template = promptTemplateService.createPromptTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(template));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptTemplateDTO>>> listPromptTemplates(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "category", required = false) String category) {
        log.debug("GET /api/v1/prompt-templates - Listing templates, enabled={}, category={}", enabled, category);
        List<PromptTemplateDTO> templates = promptTemplateService.listPromptTemplates(enabled, category);
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> getPromptTemplateById(
            @PathVariable("id") Long id) {
        log.debug("GET /api/v1/prompt-templates/{} - Getting prompt template", id);
        PromptTemplateDTO template = promptTemplateService.getPromptTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> updatePromptTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdatePromptTemplateRequest request) {
        log.info("PUT /api/v1/prompt-templates/{} - Updating prompt template", id);
        PromptTemplateDTO template = promptTemplateService.updatePromptTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromptTemplate(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/prompt-templates/{} - Deleting prompt template", id);
        promptTemplateService.deletePromptTemplate(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> previewTemplate(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> sampleData) {
        log.info("POST /api/v1/prompt-templates/{}/preview - Previewing template", id);
        PreviewResponse result = promptTemplateService.previewTemplate(id, sampleData);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
