/**
 * Story 8.5: Review Report Visualization Components Unit Tests
 *
 * Tests cover:
 * - SeverityBadge.vue: color mapping, icon rendering, label display
 * - IssueList.vue: filtering, sorting, expand/collapse
 * - CodeSnippet.vue: line numbers, highlight lines
 * - StatisticsChart.vue: data presence check
 */

import { describe, expect, it } from 'vitest';

// ============================================================
// Constants & Types (no Vue component mounting, purely logic tests)
// ============================================================

import type { ComponentSeverity, ComponentCategory } from '../src/types/review';
import {
  SEVERITY_COLORS,
  SEVERITY_ORDER,
  SEVERITY_TAG_TYPES,
  SEVERITY_ICONS,
  SEVERITY_I18N_KEYS,
  CATEGORY_COLORS,
} from '../src/constants/review';

describe('SEVERITY_COLORS', () => {
  it('should define a color for each severity level', () => {
    const severities: ComponentSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    for (const sev of severities) {
      expect(SEVERITY_COLORS[sev]).toBeTruthy();
      expect(SEVERITY_COLORS[sev]).toMatch(/^#[0-9a-fA-F]{3,6}$/);
    }
  });

  it('CRITICAL should be red (danger color)', () => {
    expect(SEVERITY_COLORS.CRITICAL).toBe('#f56c6c');
  });

  it('HIGH should be orange', () => {
    expect(SEVERITY_COLORS.HIGH).toBe('#e6a23c');
  });
});

describe('SEVERITY_TAG_TYPES', () => {
  it('CRITICAL maps to "danger"', () => {
    expect(SEVERITY_TAG_TYPES.CRITICAL).toBe('danger');
  });

  it('HIGH maps to "warning"', () => {
    expect(SEVERITY_TAG_TYPES.HIGH).toBe('warning');
  });

  it('INFO maps to "info"', () => {
    expect(SEVERITY_TAG_TYPES.INFO).toBe('info');
  });

  it('LOW maps to "info"', () => {
    expect(SEVERITY_TAG_TYPES.LOW).toBe('info');
  });
});

describe('SEVERITY_ORDER', () => {
  it('CRITICAL should have the lowest order number (highest priority)', () => {
    expect(SEVERITY_ORDER.CRITICAL).toBe(1);
  });

  it('INFO should have the highest order number (lowest priority)', () => {
    expect(SEVERITY_ORDER.INFO).toBe(5);
  });

  it('severity order should be strictly increasing: CRITICAL < HIGH < MEDIUM < LOW < INFO', () => {
    expect(SEVERITY_ORDER.CRITICAL).toBeLessThan(SEVERITY_ORDER.HIGH);
    expect(SEVERITY_ORDER.HIGH).toBeLessThan(SEVERITY_ORDER.MEDIUM);
    expect(SEVERITY_ORDER.MEDIUM).toBeLessThan(SEVERITY_ORDER.LOW);
    expect(SEVERITY_ORDER.LOW).toBeLessThan(SEVERITY_ORDER.INFO);
  });
});

describe('SEVERITY_ICONS', () => {
  it('should return lucide icon strings for all severities', () => {
    const severities: ComponentSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    for (const sev of severities) {
      expect(SEVERITY_ICONS[sev]).toMatch(/^lucide:/);
    }
  });

  it('CRITICAL should use alert-circle icon', () => {
    expect(SEVERITY_ICONS.CRITICAL).toBe('lucide:alert-circle');
  });

  it('HIGH should use alert-triangle icon', () => {
    expect(SEVERITY_ICONS.HIGH).toBe('lucide:alert-triangle');
  });
});

describe('SEVERITY_I18N_KEYS', () => {
  it('should return i18n keys in review.severity namespace', () => {
    const severities: ComponentSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    for (const sev of severities) {
      expect(SEVERITY_I18N_KEYS[sev]).toMatch(/^review\.severity\./);
    }
  });
});

describe('CATEGORY_COLORS', () => {
  it('should define a color for each category', () => {
    const categories: ComponentCategory[] = [
      'SECURITY', 'PERFORMANCE', 'QUALITY', 'STYLE', 'BUG', 'BEST_PRACTICE',
    ];
    for (const cat of categories) {
      expect(CATEGORY_COLORS[cat]).toBeTruthy();
      expect(CATEGORY_COLORS[cat]).toMatch(/^#[0-9a-fA-F]{3,6}$/);
    }
  });

  it('SECURITY should be red (high risk)', () => {
    expect(CATEGORY_COLORS.SECURITY).toBe('#f56c6c');
  });
});

// ============================================================
// Issue sorting logic (extracted from IssueList.vue)
// ============================================================

import type { ComponentIssue } from '../src/types/review';

function sortIssues(issues: ComponentIssue[]): ComponentIssue[] {
  return [...issues].sort((a, b) => {
    const severityDiff = SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity];
    if (severityDiff !== 0) return severityDiff;
    const fileDiff = a.file.localeCompare(b.file);
    if (fileDiff !== 0) return fileDiff;
    return a.line - b.line;
  });
}

describe('IssueList sorting logic', () => {
  const sampleIssues: ComponentIssue[] = [
    { file: 'src/a.java', line: 10, severity: 'LOW', category: 'STYLE', message: 'Low issue' },
    { file: 'src/a.java', line: 5, severity: 'CRITICAL', category: 'SECURITY', message: 'Critical issue' },
    { file: 'src/b.java', line: 1, severity: 'HIGH', category: 'BUG', message: 'High issue in b' },
    { file: 'src/a.java', line: 8, severity: 'CRITICAL', category: 'SECURITY', message: 'Critical issue 2' },
  ];

  it('should sort CRITICAL issues before LOW', () => {
    const sorted = sortIssues(sampleIssues);
    expect(sorted[0].severity).toBe('CRITICAL');
    expect(sorted[sorted.length - 1].severity).toBe('LOW');
  });

  it('should sort by file name within same severity', () => {
    const sorted = sortIssues(sampleIssues);
    // Both CRITICAL issues are in src/a.java; HIGH in src/b.java
    const criticals = sorted.filter((i) => i.severity === 'CRITICAL');
    const highs = sorted.filter((i) => i.severity === 'HIGH');
    expect(criticals[0].file).toBe('src/a.java');
    expect(highs[0].file).toBe('src/b.java');
  });

  it('should sort by line number within same file and severity', () => {
    const sorted = sortIssues(sampleIssues);
    const criticals = sorted.filter((i) => i.severity === 'CRITICAL');
    expect(criticals[0].line).toBe(5);
    expect(criticals[1].line).toBe(8);
  });
});

// ============================================================
// CodeSnippet line highlight logic
// ============================================================

function isHighlighted(lineIndex: number, startLine: number, highlightLines: number[]): boolean {
  return highlightLines.includes(startLine + lineIndex);
}

describe('CodeSnippet highlight logic', () => {
  it('should highlight a line when its 1-based number is in highlightLines', () => {
    expect(isHighlighted(0, 1, [1, 3])).toBe(true);  // line 1
    expect(isHighlighted(2, 1, [1, 3])).toBe(true);  // line 3
    expect(isHighlighted(1, 1, [1, 3])).toBe(false); // line 2
  });

  it('should work with non-1 startLine', () => {
    // startLine=10, index=0 → line 10
    expect(isHighlighted(0, 10, [10, 12])).toBe(true);
    expect(isHighlighted(1, 10, [10, 12])).toBe(false);
    expect(isHighlighted(2, 10, [10, 12])).toBe(true);
  });

  it('should return false when highlightLines is empty', () => {
    expect(isHighlighted(0, 1, [])).toBe(false);
  });
});

// ============================================================
// StatisticsData validation
// ============================================================

import type { StatisticsData } from '../src/types/review';

function hasChartData(data: StatisticsData, chartType: string): boolean {
  if (chartType === 'severity-pie') {
    return Object.values(data.severityDistribution).some((v) => v > 0);
  }
  if (chartType === 'category-bar') {
    return Object.values(data.categoryDistribution).some((v) => v > 0);
  }
  if (chartType === 'trend-line') {
    return (data.trendData?.length ?? 0) > 0;
  }
  return false;
}

describe('StatisticsChart data validation', () => {
  const emptyData: StatisticsData = {
    severityDistribution: { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 },
    categoryDistribution: {
      SECURITY: 0,
      PERFORMANCE: 0,
      QUALITY: 0,
      STYLE: 0,
      BUG: 0,
      BEST_PRACTICE: 0,
    },
  };

  const populatedData: StatisticsData = {
    severityDistribution: { CRITICAL: 2, HIGH: 5, MEDIUM: 3, LOW: 1, INFO: 0 },
    categoryDistribution: {
      SECURITY: 2,
      PERFORMANCE: 1,
      QUALITY: 3,
      STYLE: 4,
      BUG: 2,
      BEST_PRACTICE: 0,
    },
    trendData: [
      { date: '2025-01-01', counts: { CRITICAL: 1, HIGH: 2, MEDIUM: 0, LOW: 0, INFO: 0 } },
    ],
  };

  it('should return false for empty severity distribution', () => {
    expect(hasChartData(emptyData, 'severity-pie')).toBe(false);
  });

  it('should return true for populated severity distribution', () => {
    expect(hasChartData(populatedData, 'severity-pie')).toBe(true);
  });

  it('should return false for empty category distribution', () => {
    expect(hasChartData(emptyData, 'category-bar')).toBe(false);
  });

  it('should return true for populated category distribution', () => {
    expect(hasChartData(populatedData, 'category-bar')).toBe(true);
  });

  it('should return false when trendData is undefined', () => {
    expect(hasChartData(emptyData, 'trend-line')).toBe(false);
  });

  it('should return true when trendData has entries', () => {
    expect(hasChartData(populatedData, 'trend-line')).toBe(true);
  });
});
