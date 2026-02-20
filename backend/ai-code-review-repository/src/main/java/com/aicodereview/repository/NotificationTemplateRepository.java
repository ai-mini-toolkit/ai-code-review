package com.aicodereview.repository;

import com.aicodereview.repository.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for NotificationTemplate entity.
 * Supports querying by channel, enabled status, and combinations thereof.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByName(String name);

    List<NotificationTemplate> findByChannel(String channel);

    List<NotificationTemplate> findByEnabled(Boolean enabled);

    List<NotificationTemplate> findByChannelAndEnabled(String channel, Boolean enabled);
}
