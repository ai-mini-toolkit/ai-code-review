package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.project.CreateProjectRequest;
import com.aicodereview.common.dto.project.ProjectDTO;
import com.aicodereview.common.dto.project.UpdateProjectRequest;
import com.aicodereview.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO: Add @PreAuthorize annotations when spring-boot-starter-security is introduced (Epic 8, Story 8.6)
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        log.info("POST /api/v1/projects - Creating project: {}", request.getName());
        ProjectDTO project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(project));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> listProjects(
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        log.debug("GET /api/v1/projects - Listing projects, enabled={}", enabled);
        List<ProjectDTO> projects = projectService.listProjects(enabled);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProjectById(@PathVariable("id") Long id) {
        log.debug("GET /api/v1/projects/{} - Getting project", id);
        ProjectDTO project = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProject(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProjectRequest request) {
        log.info("PUT /api/v1/projects/{} - Updating project", id);
        ProjectDTO project = projectService.updateProject(id, request);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/projects/{} - Deleting project", id);
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
