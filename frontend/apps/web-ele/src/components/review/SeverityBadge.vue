<script setup lang="ts">
import { computed } from 'vue';
import { $t } from '@vben/locales';
import { IconifyIcon } from '@vben/icons';

import type { ComponentSeverity } from '#/types/review';
import {
  SEVERITY_ICONS,
  SEVERITY_I18N_KEYS,
  SEVERITY_TAG_TYPES,
} from '#/constants/review';

interface Props {
  severity: ComponentSeverity;
  size?: 'small' | 'default' | 'large';
  showIcon?: boolean;
  showLabel?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  showIcon: true,
  showLabel: true,
});


const tagType = computed(() => SEVERITY_TAG_TYPES[props.severity]);
const iconName = computed(() => SEVERITY_ICONS[props.severity]);
const label = computed(() => $t(SEVERITY_I18N_KEYS[props.severity]));
</script>

<template>
  <el-tag
    :type="tagType"
    :size="size === 'large' ? 'default' : size"
    :class="['severity-badge', `severity-badge--${size}`]"
  >
    <IconifyIcon
      v-if="showIcon"
      :icon="iconName"
      :class="['severity-icon', showLabel ? 'mr-1' : '']"
    />
    <span v-if="showLabel">{{ label }}</span>
  </el-tag>
</template>

<style scoped>
.severity-badge {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.severity-badge--small {
  font-size: 11px;
}

.severity-badge--large {
  font-size: 14px;
  padding: 0 10px;
  height: 28px;
}

.severity-icon {
  width: 1em;
  height: 1em;
  flex-shrink: 0;
}
</style>
