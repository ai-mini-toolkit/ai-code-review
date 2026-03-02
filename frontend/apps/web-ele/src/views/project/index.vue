<script lang="ts" setup>
import { Page, useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteProjectApi,
  getProjectsApi,
  updateProjectApi,
  type ProjectApi,
} from '#/api/project';
import ProjectFormDrawer from './modules/ProjectFormDrawer.vue';

import { useColumns, useGridFormSchema } from './data';

const router = useRouter();

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: ProjectFormDrawer,
  destroyOnClose: true,
});

function onActionClick({ code, row }: { code: string; row: ProjectApi.ProjectDTO }) {
  switch (code) {
    case 'detail': {
      router.push({ name: 'ProjectDetail', params: { id: row.id } });
      break;
    }
    case 'edit': {
      formDrawerApi.setData(row).open();
      break;
    }
    case 'delete': {
      onDelete(row);
      break;
    }
  }
}

async function onStatusChange(newVal: boolean, row: ProjectApi.ProjectDTO) {
  try {
    await updateProjectApi(row.id, { enabled: newVal });
    ElMessage.success(
      newVal ? $t('project.messages.enableSuccess') : $t('project.messages.disableSuccess'),
    );
    gridApi.query();
    return true;
  } catch {
    return false;
  }
}

async function onDelete(row: ProjectApi.ProjectDTO) {
  try {
    await ElMessageBox.confirm(
      $t('project.messages.deleteConfirm').replace('{name}', row.name),
      $t('project.messages.deleteTitle'),
      { type: 'warning' },
    );
    await deleteProjectApi(row.id);
    ElMessage.success($t('project.messages.deleteSuccess'));
    gridApi.query();
  } catch {
    // cancelled or API error
  }
}

function onCreate() {
  formDrawerApi.setData({}).open();
}

function onRefresh() {
  gridApi.query();
}

const columns = useColumns(onActionClick);

// Wire up CellSwitch beforeChange for enabled column
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
          const data = (await getProjectsApi()) as ProjectApi.ProjectDTO[];
          const name = formValues?.name?.toLowerCase() || '';
          const gitPlatform = formValues?.gitPlatform || '';
          const enabled = formValues?.enabled;
          const filtered = data.filter((p) => {
            const nameMatch = !name || p.name.toLowerCase().includes(name);
            const platformMatch = !gitPlatform || p.gitPlatform === gitPlatform;
            const enabledMatch =
              enabled === undefined || enabled === null || p.enabled === enabled;
            return nameMatch && platformMatch && enabledMatch;
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
    <Grid :table-title="$t('project.list')">
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">
          {{ $t('project.create') }}
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
