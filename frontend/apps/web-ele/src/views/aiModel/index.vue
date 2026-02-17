<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { useMediaQuery } from '@vueuse/core';

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
  type AIModelApi,
  updateAIModelApi,
} from '#/api/aiModel';
import type { ProviderType } from '#/types/aiModel';
import AIModelFormDrawer from './modules/AIModelFormDrawer.vue';

const models = ref<AIModelApi.AIModelDTO[]>([]);
const loading = ref(false);

const searchName = ref('');
const filterProvider = ref<ProviderType | ''>('');
const filterEnabled = ref<boolean | undefined>(undefined);

// M2 fix: computed instead of manual ref — auto-reactive to all filter changes
const filteredModels = computed(() =>
  models.value.filter((m) => {
    const nameMatch =
      !searchName.value ||
      m.name.toLowerCase().includes(searchName.value.toLowerCase()) ||
      m.modelName.toLowerCase().includes(searchName.value.toLowerCase());
    const providerMatch =
      !filterProvider.value || m.providerType === filterProvider.value;
    const enabledMatch =
      filterEnabled.value === undefined || m.enabled === filterEnabled.value;
    return nameMatch && providerMatch && enabledMatch;
  }),
);

// H1 fix: responsive mobile detection
const isMobile = useMediaQuery('(max-width: 768px)');

// Drawer connection
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: AIModelFormDrawer,
  destroyOnClose: true,
});

async function fetchModels() {
  loading.value = true;
  try {
    models.value = (await getAIModelsApi()) as AIModelApi.AIModelDTO[];
  } catch (error: any) {
    ElMessage.error(error.message || $t('aiModel.messages.loadFailed'));
  } finally {
    loading.value = false;
  }
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

// H2 fix: test connection from list requires entering the API Key —
// redirect user to edit form where they can test with the key they supply
function handleTestConnection(row: AIModelApi.AIModelDTO) {
  ElMessage.info($t('aiModel.messages.testFromForm'));
  formDrawerApi.setData(row).open();
}

function onFormSuccess() {
  fetchModels();
}

function handleResetFilter() {
  searchName.value = '';
  filterProvider.value = '';
  filterEnabled.value = undefined;
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
      />
      <ElSelect
        v-model="filterProvider"
        :placeholder="$t('aiModel.search.providerPlaceholder')"
        clearable
        class="!w-44"
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

    <!-- 桌面端：表格布局 -->
    <ElTable
      v-if="!isMobile"
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
      <!-- H3 fix: display masked API Key as *** per AC6 spec -->
      <ElTableColumn
        prop="apiKeyConfigured"
        :label="$t('aiModel.fields.apiKey')"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          <span v-if="row.apiKeyConfigured" class="font-mono tracking-widest text-gray-500">
            ••••••••
          </span>
          <span v-else class="text-gray-400 text-sm">
            {{ $t('aiModel.status.notConfigured') }}
          </span>
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
              @click="handleTestConnection(row)"
            >
              {{ $t('aiModel.actions.testConnection') }}
            </ElButton>
            <ElButton size="small" type="danger" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </ElButton>
          </ElSpace>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- H1 fix: 移动端卡片布局 -->
    <div v-else v-loading="loading" class="model-cards flex flex-col gap-4">
      <ElCard
        v-for="model in filteredModels"
        :key="model.id"
        class="model-card"
      >
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-semibold">{{ model.name }}</span>
            <ElSwitch
              :model-value="model.enabled"
              @change="() => handleToggleEnabled(model)"
            />
          </div>
        </template>

        <div class="space-y-2 text-sm">
          <div class="flex justify-between">
            <span class="font-medium text-gray-500">{{ $t('aiModel.fields.providerType') }}:</span>
            <ElTag :type="getProviderTagType(model.providerType)" size="small">
              {{ $t(`aiModel.provider.${model.providerType}`) }}
            </ElTag>
          </div>
          <div class="flex justify-between">
            <span class="font-medium text-gray-500">{{ $t('aiModel.fields.modelName') }}:</span>
            <span>{{ model.modelName }}</span>
          </div>
          <div class="flex justify-between">
            <span class="font-medium text-gray-500">{{ $t('aiModel.fields.apiKey') }}:</span>
            <span v-if="model.apiKeyConfigured" class="font-mono tracking-widest text-gray-500">••••••••</span>
            <span v-else class="text-gray-400">{{ $t('aiModel.status.notConfigured') }}</span>
          </div>
          <div class="flex justify-between">
            <span class="font-medium text-gray-500">{{ $t('aiModel.fields.createdAt') }}:</span>
            <span>{{ formatDate(model.createdAt) }}</span>
          </div>
        </div>

        <template #footer>
          <ElSpace wrap>
            <ElButton size="small" type="primary" @click="handleEdit(model)">
              {{ $t('common.edit') }}
            </ElButton>
            <ElButton size="small" type="warning" @click="handleTestConnection(model)">
              {{ $t('aiModel.actions.testConnection') }}
            </ElButton>
            <ElButton size="small" type="danger" @click="handleDelete(model)">
              {{ $t('common.delete') }}
            </ElButton>
          </ElSpace>
        </template>
      </ElCard>

      <ElCard v-if="!loading && filteredModels.length === 0">
        <div class="py-8 text-center text-gray-400">
          {{ $t('aiModel.empty.noModels') }}
        </div>
      </ElCard>
    </div>

    <!-- 桌面端空状态 -->
    <ElCard v-if="!isMobile && !loading && filteredModels.length === 0" class="mt-4">
      <div class="py-8 text-center text-gray-400">
        {{ $t('aiModel.empty.noModels') }}
      </div>
    </ElCard>
  </Page>
</template>
