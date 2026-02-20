package com.aicodereview.service.impl;

import com.aicodereview.common.dto.notificationtemplate.CreateNotificationTemplateRequest;
import com.aicodereview.common.dto.notificationtemplate.NotificationTemplateDTO;
import com.aicodereview.common.dto.notificationtemplate.UpdateNotificationTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.PreviewResponse;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.common.exception.TemplateSyntaxException;
import com.aicodereview.repository.NotificationTemplateRepository;
import com.aicodereview.repository.entity.NotificationTemplate;
import com.aicodereview.service.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationTemplateServiceImpl.
 * Covers all AC8 scenarios for Story 7.4.
 */
@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceImplTest {

    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;

    @InjectMocks
    private NotificationTemplateServiceImpl notificationTemplateService;

    private NotificationTemplate sampleTemplate;
    private static final Long TEMPLATE_ID = 1L;
    private static final String TEMPLATE_NAME = "email-threshold-violation";
    private static final String TEMPLATE_CHANNEL = "EMAIL";
    private static final String TEMPLATE_CONTENT = "Project: {{project_name}}, Branch: {{branch}}";
    private static final String TEMPLATE_VARIABLES = "{\"project_name\": \"项目名称\"}";

    @BeforeEach
    void setUp() {
        sampleTemplate = NotificationTemplate.builder()
                .id(TEMPLATE_ID)
                .name(TEMPLATE_NAME)
                .channel(TEMPLATE_CHANNEL)
                .templateContent(TEMPLATE_CONTENT)
                .variables(TEMPLATE_VARIABLES)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    class CreateTemplate {

        @Test
        void createTemplateShouldSucceed() {
            // Given
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .variables(TEMPLATE_VARIABLES)
                    .enabled(true)
                    .build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.empty());
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(sampleTemplate);

            // When
            NotificationTemplateDTO result = notificationTemplateService.createTemplate(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
            assertThat(result.getName()).isEqualTo(TEMPLATE_NAME);
            assertThat(result.getChannel()).isEqualTo(TEMPLATE_CHANNEL);
            assertThat(result.getTemplateContent()).isEqualTo(TEMPLATE_CONTENT);
            assertThat(result.getVariables()).isEqualTo(TEMPLATE_VARIABLES);
            assertThat(result.getEnabled()).isTrue();
            verify(notificationTemplateRepository).save(any(NotificationTemplate.class));
        }

        @Test
        void createTemplateShouldDefaultEnabledToTrue() {
            // Given
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.empty());
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(sampleTemplate);

            // When
            notificationTemplateService.createTemplate(request);

            // Then
            verify(notificationTemplateRepository).save(argThat(t -> t.getEnabled()));
        }

        @Test
        void createTemplateShouldThrowOnDuplicateName() {
            // Given
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.of(sampleTemplate));

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.createTemplate(request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(notificationTemplateRepository, never()).save(any());
        }

        @Test
        void createTemplateShouldThrowOnInvalidSyntax() {
            // Given — Handlebars syntax error: unclosed block
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent("{{#each items}}unclosed block")
                    .build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.createTemplate(request))
                    .isInstanceOf(TemplateSyntaxException.class);
            verify(notificationTemplateRepository, never()).save(any());
        }

        @Test
        void createTemplateShouldRespectExplicitEnabledFalse() {
            // Given — caller explicitly sets enabled=false on creation
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .enabled(false)
                    .build();
            NotificationTemplate disabledTemplate = NotificationTemplate.builder()
                    .id(TEMPLATE_ID).name(TEMPLATE_NAME).channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT).enabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.empty());
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(disabledTemplate);

            // When
            NotificationTemplateDTO result = notificationTemplateService.createTemplate(request);

            // Then — enabled=false is passed through, not defaulted to true
            assertThat(result.getEnabled()).isFalse();
            verify(notificationTemplateRepository).save(argThat(t -> !t.getEnabled()));
        }

        @Test
        void createTemplateShouldThrowOnInvalidVariablesJson() {
            // Given — variables is not valid JSON
            CreateNotificationTemplateRequest request = CreateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .variables("this is not valid json")
                    .build();
            when(notificationTemplateRepository.findByName(TEMPLATE_NAME)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.createTemplate(request))
                    .isInstanceOf(TemplateSyntaxException.class)
                    .hasMessageContaining("Invalid JSON");
            verify(notificationTemplateRepository, never()).save(any());
        }
    }

    @Nested
    class ListTemplates {

        @Test
        void listTemplatesWithNoFilterShouldReturnAll() {
            // Given
            when(notificationTemplateRepository.findAll()).thenReturn(List.of(sampleTemplate));

            // When
            List<NotificationTemplateDTO> result = notificationTemplateService.listTemplates(null, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo(TEMPLATE_NAME);
            verify(notificationTemplateRepository).findAll();
        }

        @Test
        void listTemplatesByChannelShouldFilterByChannel() {
            // Given
            when(notificationTemplateRepository.findByChannel(TEMPLATE_CHANNEL)).thenReturn(List.of(sampleTemplate));

            // When
            List<NotificationTemplateDTO> result = notificationTemplateService.listTemplates(TEMPLATE_CHANNEL, null);

            // Then
            assertThat(result).hasSize(1);
            verify(notificationTemplateRepository).findByChannel(TEMPLATE_CHANNEL);
        }

        @Test
        void listTemplatesByEnabledShouldFilterByEnabled() {
            // Given
            when(notificationTemplateRepository.findByEnabled(true)).thenReturn(List.of(sampleTemplate));

            // When
            List<NotificationTemplateDTO> result = notificationTemplateService.listTemplates(null, true);

            // Then
            assertThat(result).hasSize(1);
            verify(notificationTemplateRepository).findByEnabled(true);
        }

        @Test
        void listTemplatesByChannelAndEnabledShouldUseCombinedFilter() {
            // Given
            when(notificationTemplateRepository.findByChannelAndEnabled(TEMPLATE_CHANNEL, true))
                    .thenReturn(List.of(sampleTemplate));

            // When
            List<NotificationTemplateDTO> result = notificationTemplateService.listTemplates(TEMPLATE_CHANNEL, true);

            // Then
            assertThat(result).hasSize(1);
            verify(notificationTemplateRepository).findByChannelAndEnabled(TEMPLATE_CHANNEL, true);
        }

        @Test
        void listTemplatesShouldReturnEmptyListWhenNoResults() {
            // Given — no templates exist
            when(notificationTemplateRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<NotificationTemplateDTO> result = notificationTemplateService.listTemplates(null, null);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetTemplateById {

        @Test
        void getTemplateByIdShouldReturnDTO() {
            // Given
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));

            // When
            NotificationTemplateDTO result = notificationTemplateService.getTemplateById(TEMPLATE_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
            assertThat(result.getChannel()).isEqualTo(TEMPLATE_CHANNEL);
        }

        @Test
        void getTemplateByIdShouldThrowWhenNotFound() {
            // Given
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.getTemplateById(TEMPLATE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class UpdateTemplate {

        @Test
        void updateTemplateShouldUpdateFields() {
            // Given
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder()
                    .channel("SLACK")
                    .templateContent("Updated: {{project_name}}")
                    .enabled(false)
                    .build();
            NotificationTemplate updated = NotificationTemplate.builder()
                    .id(TEMPLATE_ID)
                    .name(TEMPLATE_NAME)
                    .channel("SLACK")
                    .templateContent("Updated: {{project_name}}")
                    .variables(TEMPLATE_VARIABLES)
                    .enabled(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(updated);

            // When
            NotificationTemplateDTO result = notificationTemplateService.updateTemplate(TEMPLATE_ID, request);

            // Then
            assertThat(result.getChannel()).isEqualTo("SLACK");
            assertThat(result.getEnabled()).isFalse();
            verify(notificationTemplateRepository).save(any(NotificationTemplate.class));
        }

        @Test
        void updateTemplateShouldThrowWhenNotFound() {
            // Given
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder().build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.updateTemplate(TEMPLATE_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void updateTemplateShouldThrowOnDuplicateName() {
            // Given
            String newName = "duplicate-name";
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder()
                    .name(newName)
                    .build();
            NotificationTemplate existing = NotificationTemplate.builder()
                    .id(99L)
                    .name(newName)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent("other")
                    .enabled(true)
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));
            when(notificationTemplateRepository.findByName(newName)).thenReturn(Optional.of(existing));

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.updateTemplate(TEMPLATE_ID, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        void updateTemplateShouldThrowOnInvalidTemplateSyntax() {
            // Given
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder()
                    .templateContent("{{#each items}}unclosed")
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.updateTemplate(TEMPLATE_ID, request))
                    .isInstanceOf(TemplateSyntaxException.class);
        }

        @Test
        void updateTemplateWithAllNullFieldsShouldLeaveTemplateUnchanged() {
            // Given — empty update request (no fields set)
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder().build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(sampleTemplate);

            // When
            NotificationTemplateDTO result = notificationTemplateService.updateTemplate(TEMPLATE_ID, request);

            // Then — original values are preserved
            assertThat(result.getName()).isEqualTo(TEMPLATE_NAME);
            assertThat(result.getChannel()).isEqualTo(TEMPLATE_CHANNEL);
            assertThat(result.getTemplateContent()).isEqualTo(TEMPLATE_CONTENT);
            // Uniqueness check is NOT called because name is null
            verify(notificationTemplateRepository, never()).findByName(any());
        }

        @Test
        void updateTemplateWithSameNameShouldNotCheckUniqueness() {
            // Given — updating name to same value (no conflict check needed)
            UpdateNotificationTemplateRequest request = UpdateNotificationTemplateRequest.builder()
                    .name(TEMPLATE_NAME) // same name as existing
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class))).thenReturn(sampleTemplate);

            // When
            NotificationTemplateDTO result = notificationTemplateService.updateTemplate(TEMPLATE_ID, request);

            // Then — uniqueness check is skipped when name is unchanged
            assertThat(result.getName()).isEqualTo(TEMPLATE_NAME);
            verify(notificationTemplateRepository, never()).findByName(any());
        }
    }

    @Nested
    class DeleteTemplate {

        @Test
        void deleteTemplateShouldSucceed() {
            // Given
            when(notificationTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(true);

            // When
            notificationTemplateService.deleteTemplate(TEMPLATE_ID);

            // Then
            verify(notificationTemplateRepository).deleteById(TEMPLATE_ID);
        }

        @Test
        void deleteTemplateShouldThrowWhenNotFound() {
            // Given
            when(notificationTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.deleteTemplate(TEMPLATE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(notificationTemplateRepository, never()).deleteById(any());
        }
    }

    @Nested
    class PreviewTemplate {

        @Test
        void previewTemplateShouldRenderVariables() {
            // Given
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));
            Map<String, Object> vars = Map.of("project_name", "MyProject", "branch", "main");

            // When
            PreviewResponse result = notificationTemplateService.previewTemplate(TEMPLATE_ID, vars);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRenderedContent()).contains("MyProject");
            assertThat(result.getRenderedContent()).contains("main");
            assertThat(result.getRenderTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void previewTemplateShouldHandleNullVariables() {
            // Given
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));

            // When
            PreviewResponse result = notificationTemplateService.previewTemplate(TEMPLATE_ID, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRenderedContent()).isNotNull();
        }

        @Test
        void previewTemplateShouldThrowWhenNotFound() {
            // Given
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.previewTemplate(TEMPLATE_ID, Map.of()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void previewTemplateShouldThrowOnInvalidSyntax() {
            // Given — template with bad syntax stored in DB
            NotificationTemplate badTemplate = NotificationTemplate.builder()
                    .id(TEMPLATE_ID)
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent("{{#each items}}unclosed block")
                    .enabled(true)
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(badTemplate));

            // When / Then
            assertThatThrownBy(() -> notificationTemplateService.previewTemplate(TEMPLATE_ID, Map.of()))
                    .isInstanceOf(TemplateSyntaxException.class);
        }

        @Test
        void previewTemplateShouldRenderWithEmptyVariablesMap() {
            // Given — empty map (distinct from null) — verifies empty-map path through Handlebars
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(sampleTemplate));

            // When
            PreviewResponse result = notificationTemplateService.previewTemplate(TEMPLATE_ID, Map.of());

            // Then — template renders (variables just show as empty strings)
            assertThat(result).isNotNull();
            assertThat(result.getRenderedContent()).isNotNull();
            assertThat(result.getRenderTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class ToDTOMapping {

        @Test
        void toDTOShouldHandleNullVariables() {
            // Given — template with null variables field
            NotificationTemplate templateNoVars = NotificationTemplate.builder()
                    .id(TEMPLATE_ID)
                    .name(TEMPLATE_NAME)
                    .channel(TEMPLATE_CHANNEL)
                    .templateContent(TEMPLATE_CONTENT)
                    .variables(null)
                    .enabled(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            when(notificationTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(templateNoVars));

            // When
            NotificationTemplateDTO result = notificationTemplateService.getTemplateById(TEMPLATE_ID);

            // Then — no NPE, variables is null in DTO
            assertThat(result.getVariables()).isNull();
        }
    }
}
