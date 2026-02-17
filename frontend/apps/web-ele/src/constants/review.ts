import type { ComponentCategory, ComponentSeverity } from '#/types/review';

/** 严重性颜色映射 */
export const SEVERITY_COLORS: Record<ComponentSeverity, string> = {
  CRITICAL: '#f56c6c',
  HIGH: '#e6a23c',
  MEDIUM: '#ffd21e',
  LOW: '#409eff',
  INFO: '#909399',
};

/** 严重性 Element Plus tag 类型映射 */
export const SEVERITY_TAG_TYPES: Record<
  ComponentSeverity,
  'danger' | 'info' | 'primary' | 'success' | 'warning' | undefined
> = {
  CRITICAL: 'danger',
  HIGH: 'warning',
  MEDIUM: undefined,
  LOW: 'info',
  INFO: 'info',
};

/** 严重性图标映射（lucide-icons） */
export const SEVERITY_ICONS: Record<ComponentSeverity, string> = {
  CRITICAL: 'lucide:alert-circle',
  HIGH: 'lucide:alert-triangle',
  MEDIUM: 'lucide:info',
  LOW: 'lucide:check-circle',
  INFO: 'lucide:message-circle',
};

/** 严重性 i18n key 映射 */
export const SEVERITY_I18N_KEYS: Record<ComponentSeverity, string> = {
  CRITICAL: 'review.severity.critical',
  HIGH: 'review.severity.high',
  MEDIUM: 'review.severity.medium',
  LOW: 'review.severity.low',
  INFO: 'review.severity.info',
};

/** 类别颜色映射 */
export const CATEGORY_COLORS: Record<ComponentCategory, string> = {
  SECURITY: '#f56c6c',
  PERFORMANCE: '#e6a23c',
  QUALITY: '#409eff',
  STYLE: '#909399',
  BUG: '#f56c6c',
  BEST_PRACTICE: '#67c23a',
};

/** 严重性排序权重 */
export const SEVERITY_ORDER: Record<ComponentSeverity, number> = {
  CRITICAL: 1,
  HIGH: 2,
  MEDIUM: 3,
  LOW: 4,
  INFO: 5,
};
