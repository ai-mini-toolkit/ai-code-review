package com.aicodereview.service;

import com.aicodereview.common.dto.aimodel.AiModelConfigDTO;
import com.aicodereview.common.dto.aimodel.CreateAiModelRequest;
import com.aicodereview.common.dto.aimodel.TestConnectionResponse;
import com.aicodereview.common.dto.aimodel.UpdateAiModelRequest;

import java.util.List;

/**
 * Service interface for AI model configuration management.
 */
public interface AiModelConfigService {

    AiModelConfigDTO createAiModel(CreateAiModelRequest request);

    List<AiModelConfigDTO> listAiModels(Boolean enabled, String provider);

    AiModelConfigDTO getAiModelById(Long id);

    AiModelConfigDTO updateAiModel(Long id, UpdateAiModelRequest request);

    void deleteAiModel(Long id);

    TestConnectionResponse testConnection(Long id);
}
