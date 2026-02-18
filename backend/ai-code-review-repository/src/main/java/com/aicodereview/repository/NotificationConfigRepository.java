package com.aicodereview.repository;

import com.aicodereview.repository.entity.NotificationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for NotificationConfigEntity CRUD operations.
 *
 * @since 7.1.0
 */
@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfigEntity, Long> {

    Optional<NotificationConfigEntity> findByProjectId(Long projectId);
}
