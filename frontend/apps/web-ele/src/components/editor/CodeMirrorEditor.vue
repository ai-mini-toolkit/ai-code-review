<script lang="ts" setup>
/**
 * CodeMirror 6 Vue 封装组件
 * 支持 Mustache 变量高亮和自动补全
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { autocompletion, type CompletionContext } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { bracketMatching, foldGutter, foldKeymap, indentOnInput } from '@codemirror/language';
import { Decoration, type DecorationSet, EditorView, keymap, lineNumbers } from '@codemirror/view';
import { EditorState, RangeSetBuilder, StateEffect, StateField } from '@codemirror/state';

interface Props {
  modelValue: string;
  height?: string;
  readonly?: boolean;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  height: '500px',
  readonly: false,
  placeholder: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'change': [value: string];
}>();

const containerRef = ref<HTMLDivElement>();
let editorView: EditorView | null = null;

// Mustache 变量自动补全选项
const MUSTACHE_VARIABLES = [
  { label: 'fileName', detail: '文件名（如 UserService.java）' },
  { label: 'filePath', detail: '文件路径' },
  { label: 'language', detail: '编程语言（如 java, typescript）' },
  { label: 'codeSnippet', detail: '代码片段' },
  { label: 'fullCode', detail: '完整文件代码' },
  { label: 'lineStart', detail: '起始行号' },
  { label: 'lineEnd', detail: '结束行号' },
  { label: 'changedLines', detail: '变更的行' },
  { label: 'addedLines', detail: '新增行数' },
  { label: 'deletedLines', detail: '删除行数' },
  { label: 'projectName', detail: '项目名称' },
  { label: 'branch', detail: '分支名' },
  { label: 'author', detail: '提交者' },
  { label: 'commitMessage', detail: '提交信息' },
  { label: 'dimension', detail: '审查维度（security, performance 等）' },
  { label: 'previousIssues', detail: '之前发现的问题' },
];

// Mustache 变量高亮装饰器
const mustacheMark = Decoration.mark({ class: 'cm-mustache-var' });

function buildMustacheDecorations(doc: any): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const text = doc.toString();
  const regex = /\{\{[^}]+\}\}/g;
  let match;
  while ((match = regex.exec(text)) !== null) {
    builder.add(match.index, match.index + match[0].length, mustacheMark);
  }
  return builder.finish();
}

const mustacheField = StateField.define<DecorationSet>({
  create(state) {
    return buildMustacheDecorations(state.doc);
  },
  update(decorations, tr) {
    if (tr.docChanged) {
      return buildMustacheDecorations(tr.newDoc);
    }
    return decorations;
  },
  provide: (f) => EditorView.decorations.from(f),
});

// Mustache 自动补全（在 {{ 后触发）
function mustacheCompletions(context: CompletionContext) {
  const before = context.matchBefore(/\{\{(\w*)/);
  if (!before || (before.from === before.to && !context.explicit)) return null;

  const wordStart = before.text.startsWith('{{') ? before.from + 2 : before.from;

  return {
    from: wordStart,
    options: MUSTACHE_VARIABLES.map((v) => ({
      label: v.label,
      detail: v.detail,
      apply: v.label,
    })),
  };
}

// 构建 EditorView
function createEditor(parent: HTMLElement, initialValue: string) {
  const state = EditorState.create({
    doc: initialValue,
    extensions: [
      lineNumbers(),
      history(),
      indentOnInput(),
      bracketMatching(),
      foldGutter(),
      autocompletion({ override: [mustacheCompletions] }),
      keymap.of([...defaultKeymap, ...historyKeymap, ...foldKeymap]),
      mustacheField,
      EditorView.lineWrapping,
      EditorView.editable.of(!props.readonly),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          const value = update.state.doc.toString();
          emit('update:modelValue', value);
          emit('change', value);
        }
      }),
      EditorView.theme({
        '&': { fontSize: '13px' },
        '.cm-content': {
          fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
          padding: '8px 0',
        },
        '.cm-mustache-var': {
          color: '#e5c07b',
          fontWeight: '600',
          backgroundColor: 'rgba(229, 192, 123, 0.1)',
        },
        '.cm-scroller': { overflow: 'auto' },
        '.cm-focused': { outline: 'none' },
      }),
    ],
  });

  return new EditorView({ state, parent });
}

onMounted(() => {
  if (!containerRef.value) return;
  editorView = createEditor(containerRef.value, props.modelValue);
});

// 外部值变化时同步到编辑器（不触发 emit 循环）
watch(
  () => props.modelValue,
  (newValue) => {
    if (!editorView) return;
    const current = editorView.state.doc.toString();
    if (current !== newValue) {
      editorView.dispatch({
        changes: { from: 0, to: current.length, insert: newValue },
      });
    }
  },
);

onBeforeUnmount(() => {
  editorView?.destroy();
  editorView = null;
});
</script>

<template>
  <div class="codemirror-wrapper">
    <div ref="containerRef" :style="{ height }" class="cm-container" />
  </div>
</template>

<style scoped lang="scss">
.codemirror-wrapper {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;

  .cm-container {
    overflow: auto;

    :deep(.cm-editor) {
      height: 100%;
      background: #1e1e1e;
      color: #d4d4d4;
    }

    :deep(.cm-gutters) {
      background: #252526;
      border-right: 1px solid #333;
      color: #858585;
    }

    :deep(.cm-activeLineGutter),
    :deep(.cm-activeLine) {
      background: rgba(255, 255, 255, 0.05);
    }
  }
}
</style>
