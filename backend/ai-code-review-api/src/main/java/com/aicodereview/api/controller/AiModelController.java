package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.aimodel.AiModelConfigDTO;
import com.aicodereview.common.dto.aimodel.CreateAiModelRequest;
import com.aicodereview.common.dto.aimodel.TestConnectionResponse;
import com.aicodereview.common.dto.aimodel.UpdateAiModelRequest;
import com.aicodereview.service.AiModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai-models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelConfigService aiModelConfigService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<AiModelConfigDTO>> createAiModel(
            @Valid @RequestBody CreateAiModelRequest request) {
        log.info("POST /api/v1/ai-models - Creating AI model config: {}", request.getName());
        AiModelConfigDTO config = aiModelConfigService.createAiModel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(config));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiModelConfigDTO>>> listAiModels(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "provider", required = false) String provider) {
        log.debug("GET /api/v1/ai-models - Listing AI models, enabled={}, provider={}", enabled, provider);
        List<AiModelConfigDTO> configs = aiModelConfigService.listAiModels(enabled, provider);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiModelConfigDTO>> getAiModelById(@PathVariable("id") Long id) {
        log.debug("GET /api/v1/ai-models/{} - Getting AI model config", id);
        AiModelConfigDTO config = aiModelConfigService.getAiModelById(id);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AiModelConfigDTO>> updateAiModel(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAiModelRequest request) {
        log.info("PUT /api/v1/ai-models/{} - Updating AI model config", id);
        AiModelConfigDTO config = aiModelConfigService.updateAiModel(id, request);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAiModel(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/ai-models/{} - Deleting AI model config", id);
        aiModelConfigService.deleteAiModel(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<TestConnectionResponse>> testConnection(@PathVariable("id") Long id) {
        log.info("POST /api/v1/ai-models/{}/test - Testing connection", id);
        TestConnectionResponse result = aiModelConfigService.testConnection(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
