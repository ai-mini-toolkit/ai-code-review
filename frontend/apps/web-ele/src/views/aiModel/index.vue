<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import {
  ElButton,
  ElCard,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  deleteAIModelApi,
  getAIModelsApi,
  testConnectionApi,
  type AIModelApi,
  updateAIModelApi,
} from '#/api/aiModel';
import type { ProviderType, TestConnectionResponse } from '#/types/aiModel';
import AIModelFormDrawer from './modules/AIModelFormDrawer.vue';

const models = ref<AIModelApi.AIModelDTO[]>([]);
const loading = ref(false);

const searchName = ref('');
const filterProvider = ref<ProviderType | ''>('');
const filterEnabled = ref<boolean | undefined>(undefined);

// Drawer connection
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: AIModelFormDrawer,
  destroyOnClose: true,
});

// Test connection result dialog state
const testingId = ref<number | null>(null);

// Filter models locally (backend returns full list)
function getFilteredModels() {
  return models.value.filter((m) => {
    const nameMatch =
      !searchName.value ||
      m.name.toLowerCase().includes(searchName.value.toLowerCase()) ||
      m.modelName.toLowerCase().includes(searchName.value.toLowerCase());
    const providerMatch = !filterProvider.value || m.providerType === filterProvider.value;
    const enabledMatch =
      filterEnabled.value === undefined || m.enabled === filterEnabled.value;
    return nameMatch && providerMatch && enabledMatch;
  });
}

const filteredModels = ref<AIModelApi.AIModelDTO[]>([]);

async function fetchModels() {
  loading.value = true;
  try {
    models.value = (await getAIModelsApi()) as AIModelApi.AIModelDTO[];
    filteredModels.value = getFilteredModels();
  } catch (error: any) {
    ElMessage.error(error.message || $t('aiModel.messages.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function applyFilters() {
  filteredModels.value = getFilteredModels();
}

function handleCreate() {
  formDrawerApi.setData({}).open();
}

function handleEdit(row: AIModelApi.AIModelDTO) {
  formDrawerApi.setData(row).open();
}

async function handleDelete(row: AIModelApi.AIModelDTO) {
  try {
    await ElMessageBox.confirm(
      $t('aiModel.messages.deleteConfirm').replace('{name}', row.name),
      $t('aiModel.messages.deleteTitle'),
      { type: 'warning' },
    );
  } catch {
    return;
  }

  try {
    await deleteAIModelApi(row.id);
    ElMessage.success($t('aiModel.messages.deleteSuccess'));
    await fetchModels();
  } catch {
    // API errors handled by global interceptor
  }
}

async function handleToggleEnabled(row: AIModelApi.AIModelDTO) {
  const newEnabled = !row.enabled;
  try {
    await updateAIModelApi(row.id, { enabled: newEnabled });
    ElMessage.success(
      newEnabled
        ? $t('aiModel.messages.enableSuccess')
        : $t('aiModel.messages.disableSuccess'),
    );
    await fetchModels();
  } catch {
    // API errors handled by global interceptor
  }
}

async function handleTestConnection(row: AIModelApi.AIModelDTO) {
  if (!row.apiKeyConfigured) {
    ElMessage.warning($t('aiModel.status.notConfigured'));
    return;
  }

  testingId.value = row.id;
  try {
    // Test with existing config (backend will use stored API key)
    const result = (await testConnectionApi({
      providerType: row.providerType as ProviderType,
      modelName: row.modelName,
      apiEndpoint: row.apiEndpoint,
      apiKey: '__USE_STORED__',
    })) as TestConnectionResponse;

    if (result.success) {
      ElMessage.success(
        `${$t('aiModel.messages.testSuccess')} (${result.responseTimeMs}ms)`,
      );
    } else {
      ElMessage.error(
        `${$t('aiModel.messages.testFailed')}: ${result.message}`,
      );
    }
  } catch (error: any) {
    ElMessage.error(`${$t('aiModel.messages.testError')}: ${error.message}`);
  } finally {
    testingId.value = null;
  }
}

function onFormSuccess() {
  fetchModels();
}

function handleResetFilter() {
  searchName.value = '';
  filterProvider.value = '';
  filterEnabled.value = undefined;
  filteredModels.value = models.value;
}

function getProviderTagType(
  provider: string,
): '' | 'danger' | 'info' | 'success' | 'warning' {
  switch (provider) {
    case 'OPENAI': {
      return '';
    }
    case 'ANTHROPIC': {
      return 'success';
    }
    case 'CUSTOM_OPENAPI': {
      return 'info';
    }
    default: {
      return 'info';
    }
  }
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

onMounted(() => {
  fetchModels();
});
</script>

<template>
  <Page :title="$t('aiModel.list')">
    <FormDrawer @success="onFormSuccess" />

    <!-- 搜索工具栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <ElInput
        v-model="searchName"
        :placeholder="$t('aiModel.search.namePlaceholder')"
        clearable
        class="!w-64"
        @update:model-value="applyFilters"
      />
      <ElSelect
        v-model="filterProvider"
        :placeholder="$t('aiModel.search.providerPlaceholder')"
        clearable
        class="!w-44"
        @update:model-value="applyFilters"
      >
        <ElOption label="OpenAI" value="OPENAI" />
        <ElOption label="Anthropic" value="ANTHROPIC" />
        <ElOption :label="$t('aiModel.provider.CUSTOM_OPENAPI')" value="CUSTOM_OPENAPI" />
      </ElSelect>
      <ElSelect
        v-model="filterEnabled"
        :placeholder="$t('aiModel.search.statusPlaceholder')"
        clearable
        class="!w-36"
        @update:model-value="applyFilters"
      >
        <ElOption :label="$t('aiModel.status.enabled')" :value="true" />
        <ElOption :label="$t('aiModel.status.disabled')" :value="false" />
      </ElSelect>
      <ElButton @click="handleResetFilter">
        {{ $t('common.reset') }}
      </ElButton>
      <div class="flex-1" />
      <ElButton type="primary" @click="handleCreate">
        {{ $t('aiModel.create') }}
      </ElButton>
    </div>

    <!-- AI 模型表格 -->
    <ElTable
      v-loading="loading"
      :data="filteredModels"
      stripe
      border
      style="width: 100%"
    >
      <ElTableColumn
        prop="name"
        :label="$t('aiModel.fields.name')"
        min-width="160"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="providerType"
        :label="$t('aiModel.fields.providerType')"
        width="160"
        align="center"
      >
        <template #default="{ row }">
          <ElTag :type="getProviderTagType(row.providerType)" size="small">
            {{ $t(`aiModel.provider.${row.providerType}`) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="modelName"
        :label="$t('aiModel.fields.modelName')"
        min-width="180"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="apiKeyConfigured"
        :label="$t('aiModel.fields.apiKeyStatus')"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          <ElTag v-if="row.apiKeyConfigured" type="success" size="small">
            {{ $t('aiModel.status.configured') }}
          </ElTag>
          <ElTag v-else type="info" size="small">
            {{ $t('aiModel.status.notConfigured') }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="enabled"
        :label="$t('aiModel.fields.enabled')"
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
        prop="createdAt"
        :label="$t('aiModel.fields.createdAt')"
        width="180"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="$t('aiModel.fields.operations')"
        width="240"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <ElSpace>
            <ElButton size="small" type="primary" @click="handleEdit(row)">
              {{ $t('common.edit') }}
            </ElButton>
            <ElButton
              size="small"
              type="warning"
              :loading="testingId === row.id"
              @click="handleTestConnection(row)"
            >
              {{ $t('aiModel.actions.testConnection') }}
            </ElButton>
            <ElButton
              size="small"
              type="danger"
              @click="handleDelete(row)"
            >
              {{ $t('common.delete') }}
            </ElButton>
          </ElSpace>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 空状态 -->
    <ElCard v-if="!loading && filteredModels.length === 0" class="mt-4">
      <div class="py-8 text-center text-gray-400">
        {{ $t('aiModel.empty.noModels') }}
      </div>
    </ElCard>
  </Page>
</template>
