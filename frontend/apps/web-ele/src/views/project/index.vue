<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import {
  ElButton,
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

import { useRouter } from 'vue-router';

import {
  deleteProjectApi,
  getProjectsApi,
  type ProjectApi,
  updateProjectApi,
} from '#/api/project';
import ProjectFormDrawer from './modules/ProjectFormDrawer.vue';

const router = useRouter();

// 数据状态
const projects = ref<ProjectApi.ProjectDTO[]>([]);
const loading = ref(false);

// 搜索过滤状态
const searchName = ref('');
const filterPlatform = ref('');
const filterEnabled = ref<boolean | undefined>(undefined);

// Drawer 连接
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: ProjectFormDrawer,
  destroyOnClose: true,
});

// 过滤后的项目列表
const filteredProjects = computed(() => {
  return projects.value.filter((p) => {
    const nameMatch =
      !searchName.value ||
      p.name.toLowerCase().includes(searchName.value.toLowerCase());
    const platformMatch =
      !filterPlatform.value || p.gitPlatform === filterPlatform.value;
    const enabledMatch =
      filterEnabled.value === undefined || p.enabled === filterEnabled.value;
    return nameMatch && platformMatch && enabledMatch;
  });
});

// 加载项目列表
async function fetchProjects() {
  loading.value = true;
  try {
    projects.value = (await getProjectsApi()) as ProjectApi.ProjectDTO[];
  } finally {
    loading.value = false;
  }
}

// 创建项目
function handleCreate() {
  formDrawerApi.setData({}).open();
}

// 编辑项目
function handleEdit(row: ProjectApi.ProjectDTO) {
  formDrawerApi.setData(row).open();
}

// 删除项目
async function handleDelete(row: ProjectApi.ProjectDTO) {
  try {
    await ElMessageBox.confirm(
      $t('project.messages.deleteConfirm').replace('{name}', row.name),
      $t('project.messages.deleteTitle'),
      { type: 'warning' },
    );
  } catch {
    // 用户取消操作
    return;
  }

  try {
    await deleteProjectApi(row.id);
    ElMessage.success($t('project.messages.deleteSuccess'));
    await fetchProjects();
  } catch (error) {
    // API 错误已由全局错误拦截器处理
  }
}

// 切换启用/禁用
async function handleToggleEnabled(row: ProjectApi.ProjectDTO) {
  const newEnabled = !row.enabled;
  try {
    await updateProjectApi(row.id, { enabled: newEnabled });
    ElMessage.success(
      newEnabled
        ? $t('project.messages.enableSuccess')
        : $t('project.messages.disableSuccess'),
    );
    await fetchProjects();
  } catch (error) {
    // API 错误已由全局错误拦截器处理
  }
}

// 查看详情
function handleViewDetail(row: ProjectApi.ProjectDTO) {
  router.push({ name: 'ProjectDetail', params: { id: row.id } });
}

// 表单提交成功回调
function onFormSuccess() {
  fetchProjects();
}

// 格式化时间
function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

// 重置过滤
function handleResetFilter() {
  searchName.value = '';
  filterPlatform.value = '';
  filterEnabled.value = undefined;
}

// 平台标签颜色映射
function getPlatformTagType(
  platform: string,
): '' | 'danger' | 'info' | 'success' | 'warning' {
  switch (platform) {
    case 'GitHub': {
      return '';
    }
    case 'GitLab': {
      return 'warning';
    }
    case 'CodeCommit': {
      return 'info';
    }
    default: {
      return 'info';
    }
  }
}

onMounted(() => {
  fetchProjects();
});
</script>

<template>
  <Page :title="$t('project.list')">
    <FormDrawer @success="onFormSuccess" />

    <!-- 搜索工具栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <ElInput
        v-model="searchName"
        :placeholder="$t('project.search.namePlaceholder')"
        clearable
        class="!w-64"
      />
      <ElSelect
        v-model="filterPlatform"
        :placeholder="$t('project.search.platformPlaceholder')"
        clearable
        class="!w-40"
      >
        <ElOption label="GitHub" value="GitHub" />
        <ElOption label="GitLab" value="GitLab" />
        <ElOption label="CodeCommit" value="CodeCommit" />
      </ElSelect>
      <ElSelect
        v-model="filterEnabled"
        :placeholder="$t('project.search.statusPlaceholder')"
        clearable
        class="!w-36"
      >
        <ElOption :label="$t('project.status.enabled')" :value="true" />
        <ElOption :label="$t('project.status.disabled')" :value="false" />
      </ElSelect>
      <ElButton @click="handleResetFilter">
        {{ $t('common.reset') }}
      </ElButton>
      <div class="flex-1" />
      <ElButton type="primary" @click="handleCreate">
        {{ $t('project.create') }}
      </ElButton>
    </div>

    <!-- 项目表格 -->
    <ElTable
      v-loading="loading"
      :data="filteredProjects"
      stripe
      border
      style="width: 100%"
    >
      <ElTableColumn
        prop="name"
        :label="$t('project.fields.name')"
        min-width="160"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="gitPlatform"
        :label="$t('project.fields.gitPlatform')"
        width="130"
        align="center"
      >
        <template #default="{ row }">
          <ElTag :type="getPlatformTagType(row.gitPlatform)" size="small">
            {{ row.gitPlatform }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="repoUrl"
        :label="$t('project.fields.repoUrl')"
        min-width="260"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="enabled"
        :label="$t('project.fields.enabled')"
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
        :label="$t('project.fields.createdAt')"
        width="180"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="$t('project.fields.operations')"
        width="220"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <ElSpace>
            <ElButton size="small" @click="handleViewDetail(row)">
              {{ $t('project.actions.viewDetail') }}
            </ElButton>
            <ElButton size="small" type="primary" @click="handleEdit(row)">
              {{ $t('common.edit') }}
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
  </Page>
</template>
