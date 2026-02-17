<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { $t } from '@vben/locales';

import {
  ElButton,
  ElCard,
  ElCol,
  ElCollapse,
  ElCollapseItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElSpace,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
} from 'element-plus';
import { useMediaQuery } from '@vueuse/core';
import { useRoute, useRouter } from 'vue-router';

import {
  createTemplateApi,
  getTemplateApi,
  previewTemplateApi,
  restoreDefaultTemplateApi,
  updateTemplateApi,
} from '#/api/template';
import CodeMirrorEditor from '#/components/editor/CodeMirrorEditor.vue';
import type { TemplateCategory, TemplateDTO } from '#/types/template';
import { renderMustacheTemplate, validateMustacheSyntax } from '#/utils/templateUtils';

const route = useRoute();
const router = useRouter();
const isMobile = useMediaQuery('(max-width: 768px)');

const isNew = route.params.id === 'new';
const templateId = isNew ? null : Number(route.params.id);

const template = ref<TemplateDTO | null>(null);
const formName = ref('');
const formCategory = ref<TemplateCategory | ''>('');
const content = ref('');
const previewContent = ref('');
const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const activeTab = ref<'editor' | 'preview'>('editor');

const categories: Array<{ label: string; value: TemplateCategory }> = [
  { label: $t('template.category.security'), value: 'security' },
  { label: $t('template.category.performance'), value: 'performance' },
  { label: $t('template.category.quality'), value: 'quality' },
  { label: $t('template.category.style'), value: 'style' },
  { label: $t('template.category.bug'), value: 'bug' },
  { label: $t('template.category.call-graph'), value: 'call-graph' },
];

// Sample data for client-side Mustache preview
const sampleData = {
  fileName: 'UserService.java',
  filePath: 'src/main/java/com/example/service/UserService.java',
  language: 'java',
  codeSnippet: 'public User findById(String id) {\n  return userRepository.findById(id);\n}',
  fullCode: '/* full file code */',
  lineStart: 42,
  lineEnd: 44,
  changedLines: '+  return userRepository.findById(id);',
  addedLines: 1,
  deletedLines: 0,
  projectName: 'example-project',
  branch: 'feature/user-service',
  author: 'John Doe',
  commitMessage: 'Add findById method',
  dimension: 'security',
  previousIssues: '',
};

const templateVariables = [
  { name: 'fileName', description: $t('template.variables.fileName') },
  { name: 'filePath', description: $t('template.variables.filePath') },
  { name: 'language', description: $t('template.variables.language') },
  { name: 'codeSnippet', description: $t('template.variables.codeSnippet') },
  { name: 'fullCode', description: $t('template.variables.fullCode') },
  { name: 'lineStart', description: $t('template.variables.lineStart') },
  { name: 'lineEnd', description: $t('template.variables.lineEnd') },
  { name: 'changedLines', description: $t('template.variables.changedLines') },
  { name: 'addedLines', description: $t('template.variables.addedLines') },
  { name: 'deletedLines', description: $t('template.variables.deletedLines') },
  { name: 'projectName', description: $t('template.variables.projectName') },
  { name: 'branch', description: $t('template.variables.branch') },
  { name: 'author', description: $t('template.variables.author') },
  { name: 'commitMessage', description: $t('template.variables.commitMessage') },
  { name: 'dimension', description: $t('template.variables.dimension') },
  { name: 'previousIssues', description: $t('template.variables.previousIssues') },
];

const pageTitle = computed(
  () => template.value?.name || (isNew ? $t('template.create') : $t('template.editor.title')),
);

const showRestoreDefault = computed(
  () => !isNew && template.value?.isDefault === true,
);

async function loadTemplate() {
  if (!templateId) return;
  loading.value = true;
  try {
    const data = await getTemplateApi(templateId);
    template.value = data;
    formName.value = data.name;
    formCategory.value = data.category;
    content.value = data.content;
  } catch (error: any) {
    ElMessage.error(error.message || $t('template.messages.loadFailed'));
  } finally {
    loading.value = false;
  }
}

// 500ms debounced client-side preview
let previewTimer: ReturnType<typeof setTimeout> | null = null;

function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer);
  previewTimer = setTimeout(() => {
    updatePreview();
  }, 500);
}

async function updatePreview() {
  if (!content.value) {
    previewContent.value = '';
    return;
  }
  previewing.value = true;
  try {
    const response = await previewTemplateApi({
      templateContent: content.value,
      sampleData: sampleData as any,
    });
    previewContent.value = response.renderedContent;
  } catch {
    // Backend unavailable: fall back to client-side rendering
    try {
      previewContent.value = renderMustacheTemplate(content.value, sampleData);
    } catch (renderError: any) {
      previewContent.value = `[${$t('template.form.syntaxError')}]: ${renderError.message}`;
    }
  } finally {
    previewing.value = false;
  }
}

watch(content, () => {
  schedulePreview();
});

function validateForm(): boolean {
  if (!formName.value.trim()) {
    ElMessage.warning($t('template.form.nameRequired'));
    return false;
  }
  if (!formCategory.value) {
    ElMessage.warning($t('template.form.categoryRequired'));
    return false;
  }
  if (!content.value.trim()) {
    ElMessage.warning($t('template.form.contentRequired'));
    return false;
  }
  const { valid, errors } = validateMustacheSyntax(content.value);
  if (!valid) {
    ElMessage.error(`${$t('template.form.syntaxError')}: ${errors.join(', ')}`);
    return false;
  }
  return true;
}

async function handleSave() {
  if (!validateForm()) return;

  saving.value = true;
  try {
    if (isNew) {
      await createTemplateApi({
        name: formName.value.trim(),
        category: formCategory.value as TemplateCategory,
        content: content.value,
        enabled: true,
      });
      ElMessage.success($t('template.messages.createSuccess'));
      router.push({ name: 'TemplateList' });
    } else {
      await updateTemplateApi(templateId!, {
        name: formName.value.trim(),
        category: formCategory.value as TemplateCategory,
        content: content.value,
      });
      ElMessage.success($t('template.messages.updateSuccess'));
      await loadTemplate();
    }
  } catch {
    // API errors handled by global interceptor
  } finally {
    saving.value = false;
  }
}

async function handleRestoreDefault() {
  if (!templateId) return;
  try {
    await ElMessageBox.confirm(
      $t('template.messages.restoreConfirm'),
      $t('template.messages.restoreTitle'),
      { type: 'warning' },
    );
  } catch {
    return;
  }

  loading.value = true;
  try {
    await restoreDefaultTemplateApi(templateId);
    ElMessage.success($t('template.messages.restoreSuccess'));
    await loadTemplate();
  } catch {
    // API errors handled by global interceptor
  } finally {
    loading.value = false;
  }
}

function handleBack() {
  router.push({ name: 'TemplateList' });
}

function fmtVar(name: string) {
  return `{{${name}}}`;
}

onMounted(() => {
  if (!isNew) {
    loadTemplate();
  }
});

onUnmounted(() => {
  if (previewTimer) clearTimeout(previewTimer);
});
</script>

<template>
  <Page v-loading="loading" :title="pageTitle">
    <template #extra>
      <ElSpace>
        <ElButton @click="handleBack">{{ $t('common.back') }}</ElButton>
        <ElButton
          v-if="showRestoreDefault"
          type="warning"
          @click="handleRestoreDefault"
        >
          {{ $t('template.actions.restoreDefault') }}
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSave">
          {{ $t('template.actions.save') }}
        </ElButton>
      </ElSpace>
    </template>

    <!-- 名称 & 类别字段 -->
    <ElForm label-width="90px" class="mb-4">
      <ElRow :gutter="16">
        <ElCol :lg="10" :md="14" :span="24">
          <ElFormItem :label="$t('template.fields.name')" required>
            <ElInput
              v-model="formName"
              :placeholder="$t('template.form.namePlaceholder')"
              clearable
            />
          </ElFormItem>
        </ElCol>
        <ElCol :lg="8" :md="10" :span="24">
          <ElFormItem :label="$t('template.fields.category')" required>
            <ElSelect
              v-model="formCategory"
              :placeholder="$t('template.form.categoryPlaceholder')"
              class="w-full"
            >
              <ElOption
                v-for="cat in categories"
                :key="cat.value"
                :label="cat.label"
                :value="cat.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol v-if="template?.version" :lg="6" :span="24">
          <ElFormItem :label="$t('template.fields.version')">
            <span class="version-badge">{{ template.version }}</span>
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>

    <!-- 桌面端：分屏布局 -->
    <ElRow v-if="!isMobile" :gutter="16">
      <!-- 左侧：编辑器 + 变量文档 -->
      <ElCol :span="12">
        <ElCard>
          <template #header>
            <span>{{ $t('template.editor.title') }}</span>
          </template>
          <CodeMirrorEditor v-model="content" height="480px" />
          <ElCollapse class="mt-4">
            <ElCollapseItem
              :title="$t('template.editor.variablesTitle')"
              name="variables"
            >
              <ElTable :data="templateVariables" size="small" border>
                <ElTableColumn
                  :label="$t('template.editor.variableName')"
                  width="160"
                >
                  <template #default="{ row }">
                    <code class="variable-code">{{ fmtVar(row.name) }}</code>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="description"
                  :label="$t('template.editor.variableDesc')"
                />
              </ElTable>
            </ElCollapseItem>
          </ElCollapse>
        </ElCard>
      </ElCol>

      <!-- 右侧：实时预览 -->
      <ElCol :span="12">
        <ElCard v-loading="previewing">
          <template #header>
            <span>{{ $t('template.editor.previewTitle') }}</span>
          </template>
          <pre class="preview-content">{{ previewContent || $t('template.editor.previewPlaceholder') }}</pre>
        </ElCard>
      </ElCol>
    </ElRow>

    <!-- 移动端：Tab 切换 -->
    <ElCard v-else>
      <ElTabs v-model="activeTab">
        <ElTabPane :label="$t('template.editor.editTab')" name="editor">
          <CodeMirrorEditor v-model="content" height="400px" />
          <ElCollapse class="mt-4">
            <ElCollapseItem
              :title="$t('template.editor.variablesTitle')"
              name="variables"
            >
              <ElTable :data="templateVariables" size="small" border>
                <ElTableColumn
                  :label="$t('template.editor.variableName')"
                  width="140"
                >
                  <template #default="{ row }">
                    <code class="variable-code">{{ fmtVar(row.name) }}</code>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="description"
                  :label="$t('template.editor.variableDesc')"
                />
              </ElTable>
            </ElCollapseItem>
          </ElCollapse>
        </ElTabPane>
        <ElTabPane :label="$t('template.editor.previewTab')" name="preview">
          <div v-loading="previewing">
            <pre class="preview-content">{{ previewContent || $t('template.editor.previewPlaceholder') }}</pre>
          </div>
        </ElTabPane>
      </ElTabs>
    </ElCard>
  </Page>
</template>

<style scoped lang="scss">
.version-badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.variable-code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: #e5c07b;
  background: rgba(229, 192, 123, 0.1);
  padding: 2px 4px;
  border-radius: 3px;
}

.preview-content {
  padding: 16px;
  margin: 0;
  background: #282c34;
  color: #abb2bf;
  border-radius: 4px;
  min-height: 200px;
  max-height: 580px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
