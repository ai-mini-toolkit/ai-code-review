package com.aicodereview.repository;

import com.aicodereview.repository.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for PromptTemplate entity.
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByName(String name);

    List<PromptTemplate> findByCategory(String category);

    List<PromptTemplate> findByEnabled(Boolean enabled);

    List<PromptTemplate> findByCategoryAndEnabled(String category, Boolean enabled);
}
