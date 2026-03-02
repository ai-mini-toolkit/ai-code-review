<script lang="ts" setup>
import { Page } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  createTemplateApi,
  deleteTemplateApi,
  getTemplatesApi,
  updateTemplateApi,
  type TemplateApi,
} from '#/api/template';

import { useColumns, useGridFormSchema } from './data';

const router = useRouter();

function onActionClick({ code, row }: { code: string; row: TemplateApi.TemplateDTO }) {
  switch (code) {
    case 'edit': {
      router.push({ name: 'TemplateEditor', params: { id: row.id } });
      break;
    }
    case 'copy': {
      onCopy(row);
      break;
    }
    case 'delete': {
      onDelete(row);
      break;
    }
  }
}

async function onStatusChange(newVal: boolean, row: TemplateApi.TemplateDTO) {
  try {
    await updateTemplateApi(row.id, { enabled: newVal });
    ElMessage.success(
      newVal ? $t('aiModel.messages.enableSuccess') : $t('aiModel.messages.disableSuccess'),
    );
    gridApi.query();
    return true;
  } catch {
    return false;
  }
}

async function onCopy(row: TemplateApi.TemplateDTO) {
  try {
    await createTemplateApi({
      name: `${row.name}${$t('template.messages.copySuffix')}`,
      category: row.category,
      content: row.content,
      enabled: true,
    });
    ElMessage.success($t('template.messages.copySuccess'));
    gridApi.query();
  } catch {
    // API errors handled by global interceptor
  }
}

async function onDelete(row: TemplateApi.TemplateDTO) {
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
    await deleteTemplateApi(row.id);
    ElMessage.success($t('template.messages.deleteSuccess'));
    gridApi.query();
  } catch {
    // cancelled or API error
  }
}

function onCreate() {
  router.push({ name: 'TemplateEditor', params: { id: 'new' } });
}

const columns = useColumns(onActionClick);

// Wire up CellSwitch beforeChange for enabled column
const enabledCol = columns?.find((c: any) => c.field === 'enabled');
if (enabledCol?.cellRender?.attrs) {
  enabledCol.cellRender.attrs.beforeChange = onStatusChange;
}

// Disable delete button for isDefault rows
const deleteBtn = columns
  ?.find((c: any) => c.field === 'operation')
  ?.cellRender?.attrs?.buttons?.find((b: any) => b.code === 'delete');
if (deleteBtn) {
  deleteBtn.disabled = (row: TemplateApi.TemplateDTO) => row.isDefault;
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
          const data = (await getTemplatesApi()) as TemplateApi.TemplateDTO[];
          const name = formValues?.name?.toLowerCase() || '';
          const category = formValues?.category || '';
          const filtered = data.filter((t) => {
            const nameMatch = !name || t.name.toLowerCase().includes(name);
            const categoryMatch = !category || t.category === category;
            return nameMatch && categoryMatch;
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
    <Grid :table-title="$t('template.list')">
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">
          {{ $t('template.create') }}
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
