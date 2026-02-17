<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { $t } from '@vben/locales';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElInput,
} from 'element-plus';
import { useRouter } from 'vue-router';

import {
  createTemplateApi,
  deleteTemplateApi,
  getTemplatesApi,
  updateTemplateApi,
  type TemplateApi,
} from '#/api/template';
import type { TemplateCategory } from '#/types/template';

const router = useRouter();

const templates = ref<TemplateApi.TemplateDTO[]>([]);
const loading = ref(false);
const searchName = ref('');
const filterCategory = ref<TemplateCategory | ''>('');

// M2 pattern: computed for reactive filtering
const filteredTemplates = computed(() =>
  templates.value.filter((t) => {
    const nameMatch =
      !searchName.value ||
      t.name.toLowerCase().includes(searchName.value.toLowerCase());
    const categoryMatch =
      !filterCategory.value || t.category === filterCategory.value;
    return nameMatch && categoryMatch;
  }),
);

const categories: Array<{ label: string; value: TemplateCategory }> = [
  { label: $t('template.category.security'), value: 'security' },
  { label: $t('template.category.performance'), value: 'performance' },
  { label: $t('template.category.quality'), value: 'quality' },
  { label: $t('template.category.style'), value: 'style' },
  { label: $t('template.category.bug'), value: 'bug' },
  { label: $t('template.category.call-graph'), value: 'call-graph' },
];

async function fetchTemplates() {
  loading.value = true;
  try {
    templates.value = (await getTemplatesApi()) as TemplateApi.TemplateDTO[];
  } catch (error: any) {
    ElMessage.error(error.message || $t('template.messages.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function handleEdit(row: TemplateApi.TemplateDTO) {
  router.push({ name: 'TemplateEditor', params: { id: row.id } });
}

async function handleDelete(row: TemplateApi.TemplateDTO) {
  if (row.isDefault) {
    ElMessage.warning($t('template.messages.deleteDefaultWarning'));
    return;
  }

  try {
    await ElMessageBox.confirm(
      $t('template.messages.deleteConfirm').replace('{name}', row.name),
      $t('template.messages.deleteTitle'),
      { type: 'warning' },
    );
  } catch {
    return;
  }

  try {
    await deleteTemplateApi(row.id);
    ElMessage.success($t('template.messages.deleteSuccess'));
    await fetchTemplates();
  } catch {
    // API errors handled by global interceptor
  }
}

async function handleCopy(row: TemplateApi.TemplateDTO) {
  try {
    await createTemplateApi({
      name: `${row.name} - 副本`,
      category: row.category,
      content: row.content,
      enabled: true,
    });
    ElMessage.success($t('template.messages.copySuccess'));
    await fetchTemplates();
  } catch {
    // API errors handled by global interceptor
  }
}

async function handleToggleEnabled(row: TemplateApi.TemplateDTO) {
  try {
    await updateTemplateApi(row.id, { enabled: !row.enabled });
    ElMessage.success(
      !row.enabled
        ? $t('aiModel.messages.enableSuccess')
        : $t('aiModel.messages.disableSuccess'),
    );
    await fetchTemplates();
  } catch {
    // API errors handled by global interceptor
  }
}

function handleCreate() {
  router.push({ name: 'TemplateEditor', params: { id: 'new' } });
}

function handleResetFilter() {
  searchName.value = '';
  filterCategory.value = '';
}

function getCategoryTagType(
  category: string,
): '' | 'danger' | 'info' | 'success' | 'warning' {
  const map: Record<string, '' | 'danger' | 'info' | 'success' | 'warning'> = {
    security: 'danger',
    performance: 'warning',
    quality: 'success',
    style: 'info',
    bug: 'danger',
    'call-graph': '',
  };
  return map[category] ?? 'info';
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

onMounted(() => {
  fetchTemplates();
});
</script>

<template>
  <Page :title="$t('template.list')">
    <!-- 搜索工具栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <ElInput
        v-model="searchName"
        :placeholder="$t('template.search.namePlaceholder')"
        clearable
        class="!w-64"
      />
      <ElSelect
        v-model="filterCategory"
        :placeholder="$t('template.search.categoryPlaceholder')"
        clearable
        class="!w-40"
      >
        <ElOption :label="$t('template.search.all')" value="" />
        <ElOption
          v-for="cat in categories"
          :key="cat.value"
          :label="cat.label"
          :value="cat.value"
        />
      </ElSelect>
      <ElButton @click="handleResetFilter">
        {{ $t('common.reset') }}
      </ElButton>
      <div class="flex-1" />
      <ElButton type="primary" @click="handleCreate">
        {{ $t('template.create') }}
      </ElButton>
    </div>

    <!-- 模板表格 -->
    <ElTable
      v-loading="loading"
      :data="filteredTemplates"
      stripe
      border
      style="width: 100%"
    >
      <ElTableColumn
        prop="name"
        :label="$t('template.fields.name')"
        min-width="200"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="category"
        :label="$t('template.fields.category')"
        width="130"
        align="center"
      >
        <template #default="{ row }">
          <ElTag :type="getCategoryTagType(row.category)" size="small">
            {{ $t(`template.category.${row.category}`) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="version"
        :label="$t('template.fields.version')"
        width="90"
        align="center"
      />
      <ElTableColumn
        prop="isDefault"
        :label="$t('template.fields.isDefault')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <ElTag v-if="row.isDefault" type="success" size="small">
            {{ $t('template.status.default') }}
          </ElTag>
          <ElTag v-else type="info" size="small">
            {{ $t('template.status.custom') }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="enabled"
        :label="$t('template.fields.enabled')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <ElSwitch
            :model-value="row.enabled"
            @change="() => handleToggleEnabled(row)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="updatedAt"
        :label="$t('template.fields.updatedAt')"
        width="180"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.updatedAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="$t('template.fields.operations')"
        width="220"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <ElSpace>
            <ElButton size="small" type="primary" @click="handleEdit(row)">
              {{ $t('template.actions.editTemplate') }}
            </ElButton>
            <ElButton size="small" @click="handleCopy(row)">
              {{ $t('template.actions.copyTemplate') }}
            </ElButton>
            <ElButton
              size="small"
              type="danger"
              :disabled="row.isDefault"
              @click="handleDelete(row)"
            >
              {{ $t('common.delete') }}
            </ElButton>
          </ElSpace>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 空状态 -->
    <div v-if="!loading && filteredTemplates.length === 0" class="mt-8 py-12 text-center text-gray-400">
      {{ $t('template.empty.noTemplates') }}
    </div>
  </Page>
</template>
