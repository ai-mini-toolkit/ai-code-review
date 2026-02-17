package com.aicodereview.service;

import com.aicodereview.common.dto.project.CreateProjectRequest;
import com.aicodereview.common.dto.project.ProjectDTO;
import com.aicodereview.common.dto.project.UpdateProjectRequest;
import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;

import java.util.List;

/**
 * Service interface for project configuration management.
 */
public interface ProjectService {

    ProjectDTO createProject(CreateProjectRequest request);

    List<ProjectDTO> listProjects(Boolean enabled);

    ProjectDTO getProjectById(Long id);

    ProjectDTO updateProject(Long id, UpdateProjectRequest request);

    void deleteProject(Long id);

    /**
     * Finds a project by repository URL.
     * <p>
     * Used by webhook processing to identify which project the webhook belongs to.
     * </p>
     *
     * @param repoUrl the repository URL
     * @return the project DTO
     * @throws ResourceNotFoundException if project with given repoUrl does not exist
     */
    ProjectDTO findByRepoUrl(String repoUrl);

    /**
     * Retrieves the threshold configuration for a project.
     *
     * @param projectId the project ID
     * @return the threshold configuration DTO
     * @throws ResourceNotFoundException if project does not exist
     */
    ThresholdConfigDTO getThresholds(Long projectId);

    /**
     * Updates the threshold configuration for a project.
     *
     * @param projectId the project ID
     * @param config    the new threshold configuration
     * @return the updated threshold configuration DTO
     * @throws ResourceNotFoundException  if project does not exist
     * @throws IllegalArgumentException   if configuration is invalid
     */
    ThresholdConfigDTO updateThresholds(Long projectId, ThresholdConfigDTO config);
}
