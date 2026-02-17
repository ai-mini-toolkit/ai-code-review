package com.aicodereview.service.mapper;

import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ThresholdMapper.
 *
 * @since 6.1.0
 */
@DisplayName("ThresholdMapper Unit Tests")
class ThresholdMapperTest {

    @Nested
    @DisplayName("Serialization / Deserialization")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize round-trip")
        void shouldRoundTrip() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().severity("HIGH").maxCount(3).build(),
                            ThresholdRuleDTO.builder().totalIssues(20).build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();

            String json = ThresholdMapper.serialize(config);
            ThresholdConfigDTO deserialized = ThresholdMapper.deserialize(json);

            assertThat(deserialized.getEnabled()).isTrue();
            assertThat(deserialized.getAction()).isEqualTo("BLOCK_MERGE");
            assertThat(deserialized.getRules()).hasSize(3);
            assertThat(deserialized.getRules().get(0).getSeverity()).isEqualTo("CRITICAL");
            assertThat(deserialized.getRules().get(0).getMaxCount()).isEqualTo(0);
            assertThat(deserialized.getRules().get(2).getTotalIssues()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should serialize null to default config")
        void shouldSerializeNullToDefault() {
            String json = ThresholdMapper.serialize(null);
            ThresholdConfigDTO deserialized = ThresholdMapper.deserialize(json);

            assertThat(deserialized.getEnabled()).isFalse();
            assertThat(deserialized.getAction()).isEqualTo("BLOCK_MERGE");
            assertThat(deserialized.getRules()).hasSize(3);
        }

        @Test
        @DisplayName("Should deserialize null/blank/empty to default config")
        void shouldDeserializeEmptyToDefault() {
            ThresholdConfigDTO fromNull = ThresholdMapper.deserialize(null);
            ThresholdConfigDTO fromBlank = ThresholdMapper.deserialize("");
            ThresholdConfigDTO fromEmpty = ThresholdMapper.deserialize("{}");

            assertThat(fromNull.getEnabled()).isFalse();
            assertThat(fromBlank.getEnabled()).isFalse();
            assertThat(fromEmpty.getEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should accept valid BLOCK_MERGE config")
        void shouldAcceptValidBlockMergeConfig() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();

            ThresholdMapper.validate(config);
            // No exception = pass
        }

        @Test
        @DisplayName("Should accept valid WARN_ONLY config")
        void shouldAcceptValidWarnOnlyConfig() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(false)
                    .rules(List.of(ThresholdRuleDTO.builder().totalIssues(10).build()))
                    .action("WARN_ONLY")
                    .build();

            ThresholdMapper.validate(config);
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> ThresholdMapper.validate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("Should reject invalid action")
        void shouldRejectInvalidAction() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build()))
                    .action("INVALID_ACTION")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BLOCK_MERGE or WARN_ONLY");
        }

        @Test
        @DisplayName("Should reject negative maxCount")
        void shouldRejectNegativeMaxCount() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(-1).build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxCount")
                    .hasMessageContaining(">= 0");
        }

        @Test
        @DisplayName("Should reject negative totalIssues")
        void shouldRejectNegativeTotalIssues() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().totalIssues(-5).build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalIssues")
                    .hasMessageContaining(">= 0");
        }

        @Test
        @DisplayName("Should reject invalid severity value")
        void shouldRejectInvalidSeverity() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("UNKNOWN").maxCount(5).build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid severity")
                    .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("Should reject rule with both severity and totalIssues")
        void shouldRejectMixedRule() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(3).totalIssues(10).build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot have both");
        }

        @Test
        @DisplayName("Should reject rule with neither severity nor totalIssues")
        void shouldRejectEmptyRule() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must have either");
        }

        @Test
        @DisplayName("Should reject severity rule without maxCount")
        void shouldRejectSeverityWithoutMaxCount() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").build()))
                    .action("BLOCK_MERGE")
                    .build();

            assertThatThrownBy(() -> ThresholdMapper.validate(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires 'maxCount'");
        }
    }

    @Nested
    @DisplayName("Default Config")
    class DefaultConfigTests {

        @Test
        @DisplayName("Should return sensible default config")
        void shouldReturnSensibleDefaults() {
            ThresholdConfigDTO defaults = ThresholdMapper.defaultConfig();

            assertThat(defaults.getEnabled()).isFalse();
            assertThat(defaults.getAction()).isEqualTo("BLOCK_MERGE");
            assertThat(defaults.getRules()).hasSize(3);
            assertThat(defaults.getRules().get(0).getSeverity()).isEqualTo("CRITICAL");
            assertThat(defaults.getRules().get(0).getMaxCount()).isEqualTo(0);
            assertThat(defaults.getRules().get(1).getSeverity()).isEqualTo("HIGH");
            assertThat(defaults.getRules().get(1).getMaxCount()).isEqualTo(5);
            assertThat(defaults.getRules().get(2).getTotalIssues()).isEqualTo(30);
        }
    }
}
