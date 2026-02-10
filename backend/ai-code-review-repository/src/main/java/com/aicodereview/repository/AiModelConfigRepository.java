package com.aicodereview.repository;

import com.aicodereview.repository.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for AiModelConfig entity.
 */
@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {

    Optional<AiModelConfig> findByName(String name);

    List<AiModelConfig> findByEnabled(Boolean enabled);

    List<AiModelConfig> findByProvider(String provider);

    List<AiModelConfig> findByProviderAndEnabled(String provider, Boolean enabled);
}
