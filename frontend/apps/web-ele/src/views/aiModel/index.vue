<script lang="ts" setup>
import { Page, useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAIModelApi,
  getAIModelsApi,
  type AIModelApi,
  updateAIModelApi,
} from '#/api/aiModel';
import AIModelFormDrawer from './modules/AIModelFormDrawer.vue';
import { useColumns, useGridFormSchema } from './data';

// Drawer connection
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: AIModelFormDrawer,
  destroyOnClose: true,
});

function onActionClick({ code, row }: { code: string; row: AIModelApi.AIModelDTO }) {
  switch (code) {
    case 'edit': {
      formDrawerApi.setData(row).open();
      break;
    }
    case 'test': {
      ElMessage.info($t('aiModel.messages.testFromForm'));
      formDrawerApi.setData(row).open();
      break;
    }
    case 'delete': {
      onDelete(row);
      break;
    }
  }
}

async function onStatusChange(newVal: boolean, row: AIModelApi.AIModelDTO) {
  try {
    await updateAIModelApi(row.id, { enabled: newVal });
    ElMessage.success(
      newVal ? $t('aiModel.messages.enableSuccess') : $t('aiModel.messages.disableSuccess'),
    );
    gridApi.query();
    return true;
  } catch {
    return false;
  }
}

async function onDelete(row: AIModelApi.AIModelDTO) {
  try {
    await ElMessageBox.confirm(
      $t('aiModel.messages.deleteConfirm').replace('{name}', row.name),
      $t('aiModel.messages.deleteTitle'),
      { type: 'warning' },
    );
    await deleteAIModelApi(row.id);
    ElMessage.success($t('aiModel.messages.deleteSuccess'));
    gridApi.query();
  } catch {
    // cancelled or API error handled by interceptor
  }
}

function onCreate() {
  formDrawerApi.setData({}).open();
}

function onRefresh() {
  gridApi.query();
}

// Build columns with action handler and status change handler
const columns = useColumns(onActionClick);
// Wire up the CellSwitch beforeChange for the enabled column
const enabledCol = columns?.find((c: any) => c.field === 'enabled');
if (enabledCol?.cellRender?.attrs) {
  enabledCol.cellRender.attrs.beforeChange = onStatusChange;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns,
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (_params: any, formValues?: Record<string, any>) => {
          const data = await getAIModelsApi() as AIModelApi.AIModelDTO[];
          // Client-side filtering until backend supports pagination
          const name = formValues?.name?.toLowerCase() || '';
          const providerType = formValues?.providerType || '';
          const enabled = formValues?.enabled;
          const filtered = data.filter((m) => {
            const nameMatch = !name || m.name.toLowerCase().includes(name) || m.modelName.toLowerCase().includes(name);
            const providerMatch = !providerType || m.providerType === providerType;
            const enabledMatch = enabled === undefined || enabled === null || m.enabled === enabled;
            return nameMatch && providerMatch && enabledMatch;
          });
          return { items: filtered, total: filtered.length };
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Grid :table-title="$t('aiModel.list')">
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">
          {{ $t('aiModel.create') }}
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
