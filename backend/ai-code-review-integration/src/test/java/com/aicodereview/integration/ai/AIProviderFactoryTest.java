package com.aicodereview.integration.ai;

import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.exception.UnsupportedPlatformException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AIProviderFactory}.
 *
 * @since 4.1.0
 */
@DisplayName("AIProviderFactory Tests")
class AIProviderFactoryTest {

    @Test
    @DisplayName("Should return provider by ID")
    void shouldReturnProviderById() {
        AIProvider openai = createStubProvider("openai");
        AIProvider anthropic = createStubProvider("anthropic");
        AIProviderFactory factory = new AIProviderFactory(List.of(openai, anthropic), "openai");

        assertThat(factory.getProvider("openai")).isSameAs(openai);
        assertThat(factory.getProvider("anthropic")).isSameAs(anthropic);
    }

    @Test
    @DisplayName("Should return default provider")
    void shouldReturnDefaultProvider() {
        AIProvider openai = createStubProvider("openai");
        AIProvider anthropic = createStubProvider("anthropic");
        AIProviderFactory factory = new AIProviderFactory(List.of(openai, anthropic), "anthropic");

        assertThat(factory.getDefaultProvider()).isSameAs(anthropic);
    }

    @Test
    @DisplayName("Should throw UnsupportedPlatformException for unknown provider")
    void shouldThrowForUnknownProvider() {
        AIProvider openai = createStubProvider("openai");
        AIProviderFactory factory = new AIProviderFactory(List.of(openai), "openai");

        assertThatThrownBy(() -> factory.getProvider("unknown"))
                .isInstanceOf(UnsupportedPlatformException.class)
                .hasMessageContaining("No AI provider registered with id: unknown");
    }

    @Test
    @DisplayName("Should throw for null provider ID")
    void shouldThrowForNullProviderId() {
        AIProvider openai = createStubProvider("openai");
        AIProviderFactory factory = new AIProviderFactory(List.of(openai), "openai");

        assertThatThrownBy(() -> factory.getProvider(null))
                .isInstanceOf(UnsupportedPlatformException.class);
    }

    @Test
    @DisplayName("Should throw when default provider is not registered")
    void shouldThrowWhenDefaultProviderNotRegistered() {
        AIProvider openai = createStubProvider("openai");
        AIProviderFactory factory = new AIProviderFactory(List.of(openai), "anthropic");

        assertThatThrownBy(() -> factory.getDefaultProvider())
                .isInstanceOf(UnsupportedPlatformException.class)
                .hasMessageContaining("anthropic");
    }

    @Test
    @DisplayName("Should throw IllegalStateException for duplicate provider IDs")
    void shouldThrowForDuplicateProviderIds() {
        AIProvider openai1 = createStubProvider("openai");
        AIProvider openai2 = createStubProvider("openai");

        assertThatThrownBy(() -> new AIProviderFactory(List.of(openai1, openai2), "openai"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate AI provider registered with id: openai");
    }

    @Test
    @DisplayName("Should handle empty provider list")
    void shouldHandleEmptyProviderList() {
        AIProviderFactory factory = new AIProviderFactory(List.of(), "openai");

        assertThatThrownBy(() -> factory.getProvider("openai"))
                .isInstanceOf(UnsupportedPlatformException.class);
    }

    private AIProvider createStubProvider(String providerId) {
        return new AIProvider() {
            @Override
            public ReviewResult analyze(CodeContext context, String renderedPrompt) {
                return ReviewResult.failed("stub");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getProviderId() {
                return providerId;
            }

            @Override
            public int getMaxTokens() {
                return 4096;
            }
        };
    }
}
