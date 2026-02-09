package com.aicodereview.service.impl;

import com.aicodereview.common.dto.project.CreateProjectRequest;
import com.aicodereview.common.dto.project.ProjectDTO;
import com.aicodereview.common.dto.project.UpdateProjectRequest;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ProjectService for project CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    @Override
    public ProjectDTO createProject(CreateProjectRequest request) {
        log.info("Creating project: {}", request.getName());

        projectRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new DuplicateResourceException("Project", "name", request.getName());
        });

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .gitPlatform(request.getGitPlatform())
                .repoUrl(request.getRepoUrl())
                .webhookSecret(request.getWebhookSecret())
                .build();

        Project saved = projectRepository.save(project);
        log.info("Project created with id: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDTO> listProjects(Boolean enabled) {
        List<Project> projects;
        if (enabled != null) {
            log.debug("Listing projects with enabled={}", enabled);
            projects = projectRepository.findByEnabled(enabled);
        } else {
            log.debug("Listing all projects");
            projects = projectRepository.findAll();
        }
        return projects.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "projects", key = "#p0")
    public ProjectDTO getProjectById(Long id) {
        log.debug("Getting project by id: {}", id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        return toDTO(project);
    }

    @Override
    @CacheEvict(value = "projects", key = "#p0")
    public ProjectDTO updateProject(Long id, UpdateProjectRequest request) {
        log.info("Updating project id: {}", id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        // Check name uniqueness if name is being changed
        if (request.getName() != null && !request.getName().equals(project.getName())) {
            projectRepository.findByName(request.getName()).ifPresent(existing -> {
                throw new DuplicateResourceException("Project", "name", request.getName());
            });
            project.setName(request.getName());
        }

        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            project.setEnabled(request.getEnabled());
        }
        if (request.getGitPlatform() != null) {
            project.setGitPlatform(request.getGitPlatform());
        }
        if (request.getRepoUrl() != null) {
            project.setRepoUrl(request.getRepoUrl());
        }
        if (request.getWebhookSecret() != null) {
            project.setWebhookSecret(request.getWebhookSecret());
        }

        Project saved = projectRepository.save(project);
        log.info("Project updated: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @CacheEvict(value = "projects", key = "#p0")
    public void deleteProject(Long id) {
        log.info("Deleting project id: {}", id);
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
        projectRepository.deleteById(id);
        log.info("Project deleted: {}", id);
    }

    private ProjectDTO toDTO(Project project) {
        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .enabled(project.getEnabled())
                .gitPlatform(project.getGitPlatform())
                .repoUrl(project.getRepoUrl())
                .webhookSecretConfigured(project.getWebhookSecret() != null && !project.getWebhookSecret().isEmpty())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
