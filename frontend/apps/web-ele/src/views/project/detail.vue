<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAppConfig } from '@vben/hooks';
import { $t } from '@vben/locales';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElMessage,
  ElTag,
} from 'element-plus';

import { useRoute, useRouter } from 'vue-router';

import { getProjectApi, type ProjectApi } from '#/api/project';

const route = useRoute();
const router = useRouter();

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

const project = ref<ProjectApi.ProjectDTO>();
const loading = ref(false);

const projectId = Number(route.params.id);

async function fetchProject() {
  loading.value = true;
  try {
    project.value = (await getProjectApi(projectId)) as ProjectApi.ProjectDTO;
  } finally {
    loading.value = false;
  }
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

function getWebhookUrl() {
  const platform = project.value?.gitPlatform?.toLowerCase() ?? 'github';
  return `${apiURL}/webhook/${platform}`;
}

async function handleCopyWebhookUrl() {
  try {
    await navigator.clipboard.writeText(getWebhookUrl());
    ElMessage.success($t('project.messages.webhookUrlCopied'));
  } catch {
    ElMessage.error('Failed to copy');
  }
}

function handleBack() {
  router.push({ name: 'ProjectList' });
}

onMounted(() => {
  fetchProject();
});
</script>

<template>
  <Page :title="$t('project.detail')" v-loading="loading">
    <template #extra>
      <ElButton @click="handleBack">
        {{ $t('common.back') }}
      </ElButton>
    </template>

    <ElDescriptions
      v-if="project"
      :column="2"
      border
      class="mb-6"
    >
      <ElDescriptionsItem :label="$t('project.fields.name')">
        {{ project.name }}
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.enabled')">
        <ElTag :type="project.enabled ? 'success' : 'danger'" size="small">
          {{
            project.enabled
              ? $t('project.status.enabled')
              : $t('project.status.disabled')
          }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.gitPlatform')">
        {{ project.gitPlatform }}
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.webhookSecretConfigured')">
        <ElTag
          :type="project.webhookSecretConfigured ? 'success' : 'warning'"
          size="small"
        >
          {{ project.webhookSecretConfigured ? 'Yes' : 'No' }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.repoUrl')" :span="2">
        <a :href="project.repoUrl" target="_blank" class="text-blue-500 hover:underline">
          {{ project.repoUrl }}
        </a>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.description')" :span="2">
        {{ project.description || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.createdAt')">
        {{ formatDate(project.createdAt) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="$t('project.fields.updatedAt')">
        {{ formatDate(project.updatedAt) }}
      </ElDescriptionsItem>
    </ElDescriptions>

    <!-- Webhook URL -->
    <div v-if="project" class="rounded border border-gray-200 p-4">
      <h3 class="mb-3 text-base font-medium">
        {{ $t('project.webhookUrl') }}
      </h3>
      <div class="flex items-center gap-3">
        <code class="flex-1 rounded bg-gray-100 px-3 py-2 text-sm dark:bg-gray-800">
          {{ getWebhookUrl() }}
        </code>
        <ElButton type="primary" size="small" @click="handleCopyWebhookUrl">
          {{ $t('project.actions.copyWebhookUrl') }}
        </ElButton>
      </div>
    </div>
  </Page>
</template>
