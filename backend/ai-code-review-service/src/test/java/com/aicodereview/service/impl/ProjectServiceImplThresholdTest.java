package com.aicodereview.service.impl;

import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.service.mapper.ThresholdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectServiceImpl threshold methods.
 *
 * @since 6.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectServiceImpl Threshold Tests")
class ProjectServiceImplThresholdTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = Project.builder()
                .id(1L)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .gitPlatform("GitHub")
                .webhookSecret("secret")
                .enabled(true)
                .thresholds(ThresholdMapper.serialize(ThresholdMapper.defaultConfig()))
                .build();
    }

    @Nested
    @DisplayName("getThresholds")
    class GetThresholdsTests {

        @Test
        @DisplayName("Should return default thresholds for project")
        void shouldReturnDefaultThresholds() {
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

            ThresholdConfigDTO result = projectService.getThresholds(1L);

            assertThat(result).isNotNull();
            assertThat(result.getEnabled()).isFalse();
            assertThat(result.getAction()).isEqualTo("BLOCK_MERGE");
            assertThat(result.getRules()).hasSize(3);
            verify(projectRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw 404 when project not found")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getThresholds(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should return custom thresholds if configured")
        void shouldReturnCustomThresholds() {
            ThresholdConfigDTO custom = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("WARN_ONLY")
                    .build();
            testProject.setThresholds(ThresholdMapper.serialize(custom));
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

            ThresholdConfigDTO result = projectService.getThresholds(1L);

            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getAction()).isEqualTo("WARN_ONLY");
            assertThat(result.getRules()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("updateThresholds")
    class UpdateThresholdsTests {

        @Test
        @DisplayName("Should update thresholds successfully")
        void shouldUpdateThresholds() {
            ThresholdConfigDTO newConfig = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().totalIssues(15).build()
                    ))
                    .action("WARN_ONLY")
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            when(projectRepository.save(any(Project.class))).thenReturn(testProject);

            ThresholdConfigDTO result = projectService.updateThresholds(1L, newConfig);

            assertThat(result).isNotNull();
            verify(projectRepository).save(testProject);
        }

        @Test
        @DisplayName("Should throw 404 when project not found on update")
        void shouldThrowWhenProjectNotFoundOnUpdate() {
            ThresholdConfigDTO config = ThresholdMapper.defaultConfig();
            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.updateThresholds(999L, config))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(projectRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw for invalid config before DB access")
        void shouldThrowForInvalidConfigBeforeDbAccess() {
            ThresholdConfigDTO invalid = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(-1).build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> projectService.updateThresholds(1L, invalid))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(projectRepository, never()).findById(any());
            verify(projectRepository, never()).save(any());
        }
    }
}
