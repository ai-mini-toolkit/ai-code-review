<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElButton, ElMessage } from 'element-plus';
import { IconifyIcon } from '@vben/icons';
import Prism from 'prismjs';

// Import commonly needed languages (Java, JS/TS, Python, Go, SQL are most common for code review)
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-go';
import 'prismjs/components/prism-sql';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-yaml';
import 'prismjs/components/prism-json';

interface Props {
  code: string;
  language?: string;
  /** 1-based line numbers to highlight as error lines */
  highlightLines?: number[];
  /** Starting line number for display (default 1) */
  startLine?: number;
  filename?: string;
}

const props = withDefaults(defineProps<Props>(), {
  language: 'text',
  highlightLines: () => [],
  startLine: 1,
  filename: undefined,
});

const { t } = useI18n();

const copied = ref(false);
const codeRef = ref<HTMLElement | null>(null);

// Map common language aliases to Prism keys
function normalizeLang(lang: string): string {
  const map: Record<string, string> = {
    js: 'javascript',
    ts: 'typescript',
    py: 'python',
    sh: 'bash',
    shell: 'bash',
    yml: 'yaml',
    cs: 'csharp',
  };
  return map[lang.toLowerCase()] ?? lang.toLowerCase();
}

const normalizedLang = computed(() => normalizeLang(props.language));

const lines = computed(() => props.code.split('\n'));

const highlightedLines = computed(() => {
  const grammar = Prism.languages[normalizedLang.value] ?? Prism.languages.plain;
  // Highlight entire block then split by newlines
  const html = Prism.highlight(props.code, grammar, normalizedLang.value);
  return html.split('\n');
});

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.code);
    copied.value = true;
    ElMessage({
      message: t('review.codeSnippet.copied'),
      type: 'success',
      duration: 1500,
    });
    setTimeout(() => {
      copied.value = false;
    }, 1500);
  } catch {
    ElMessage({ message: t('review.codeSnippet.copyFailed'), type: 'error' });
  }
}

function isHighlighted(lineIndex: number): boolean {
  const lineNum = props.startLine + lineIndex;
  return props.highlightLines.includes(lineNum);
}
</script>

<template>
  <div class="code-snippet">
    <!-- Header bar -->
    <div class="code-snippet__header">
      <div class="code-snippet__filename">
        <IconifyIcon icon="lucide:file-code" style="width: 0.9em; height: 0.9em; margin-right: 4px" />
        <span>{{ filename || language }}</span>
      </div>
      <ElButton
        link
        size="small"
        class="code-snippet__copy-btn"
        @click="copyCode"
      >
        <IconifyIcon
          :icon="copied ? 'lucide:check' : 'lucide:copy'"
          style="width: 0.9em; height: 0.9em; margin-right: 4px"
        />
        {{ copied ? t('review.codeSnippet.copied') : t('review.codeSnippet.copy') }}
      </ElButton>
    </div>

    <!-- Code block -->
    <div class="code-snippet__body">
      <table class="code-snippet__table">
        <tbody>
          <tr
            v-for="(lineHtml, index) in highlightedLines"
            :key="index"
            :class="['code-snippet__row', isHighlighted(index) && 'code-snippet__row--highlighted']"
          >
            <!-- Line number -->
            <td class="code-snippet__lineno">{{ startLine + index }}</td>
            <!-- Code content -->
            <td class="code-snippet__line">
              <!-- eslint-disable-next-line vue/no-v-html -->
              <span class="code-snippet__line-content" v-html="lineHtml || '&nbsp;'" />
              <span v-if="isHighlighted(index)" class="code-snippet__wave" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.code-snippet {
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  overflow: hidden;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.code-snippet__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: var(--el-fill-color-dark, #ebedf0);
  border-bottom: 1px solid var(--el-border-color, #dcdfe6);
}

.code-snippet__filename {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.code-snippet__copy-btn {
  font-size: 12px;
}

.code-snippet__body {
  overflow-x: auto;
  background: var(--el-fill-color-blank, #fff);
}

.code-snippet__table {
  width: 100%;
  border-collapse: collapse;
  tab-size: 2;
}

.code-snippet__row {
  line-height: 1.6;
}

.code-snippet__row--highlighted {
  background-color: rgba(245, 108, 108, 0.12);
  position: relative;
}

.code-snippet__lineno {
  user-select: none;
  text-align: right;
  padding: 2px 12px 2px 8px;
  min-width: 40px;
  color: var(--el-text-color-placeholder, #a8abb2);
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  font-size: 11px;
  vertical-align: top;
}

.code-snippet__row--highlighted .code-snippet__lineno {
  background-color: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
}

.code-snippet__line {
  padding: 2px 12px;
  white-space: pre;
  position: relative;
}

.code-snippet__line-content {
  display: inline;
}

.code-snippet__wave {
  position: absolute;
  bottom: 0;
  left: 12px;
  right: 0;
  height: 2px;
  background-image: repeating-linear-gradient(
    90deg,
    #f56c6c,
    #f56c6c 4px,
    transparent 4px,
    transparent 8px
  );
}

/* Dark mode support */
:root.dark .code-snippet {
  background: #1e1e1e;
}

:root.dark .code-snippet__body {
  background: #1e1e1e;
}

:root.dark .code-snippet__header {
  background: #2d2d2d;
}

:root.dark .code-snippet__lineno {
  background: #252526;
  color: #858585;
  border-right-color: #3c3c3c;
}
</style>
