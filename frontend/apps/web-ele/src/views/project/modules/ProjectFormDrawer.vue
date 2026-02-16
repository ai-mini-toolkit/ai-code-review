<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElMessage } from 'element-plus';

import {
  createProjectApi,
  type ProjectApi,
  updateProjectApi,
} from '#/api/project';
import { useVbenForm, z } from '#/adapter/form';

const emit = defineEmits<{
  success: [];
}>();

const formData = ref<ProjectApi.ProjectDTO>();
const isEdit = computed(() => !!formData.value?.id);

const gitPlatformOptions = [
  { label: 'GitHub', value: 'GitHub' },
  { label: 'GitLab', value: 'GitLab' },
  { label: 'CodeCommit', value: 'CodeCommit' },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('project.fields.name'),
      rules: z
        .string()
        .min(1, $t('common.inputRequired'))
        .max(255),
      componentProps: {
        placeholder: $t('project.form.namePlaceholder'),
      },
    },
    {
      component: 'Input',
      fieldName: 'description',
      label: $t('project.fields.description'),
      rules: z.string().max(2000).optional(),
      componentProps: {
        placeholder: $t('project.form.descriptionPlaceholder'),
        type: 'textarea',
        rows: 3,
      },
    },
    {
      component: 'Select',
      fieldName: 'gitPlatform',
      label: $t('project.fields.gitPlatform'),
      rules: z.string().min(1, $t('common.selectRequired')),
      componentProps: {
        options: gitPlatformOptions,
        placeholder: $t('project.search.platformPlaceholder'),
      },
    },
    {
      component: 'Input',
      fieldName: 'repoUrl',
      label: $t('project.fields.repoUrl'),
      rules: z
        .string()
        .min(1, $t('common.inputRequired'))
        .max(500)
        .regex(/^https?:\/\/.+/, $t('project.form.repoUrlInvalid')),
      componentProps: {
        placeholder: $t('project.form.repoUrlPlaceholder'),
      },
    },
    {
      component: 'Input',
      fieldName: 'webhookSecret',
      label: $t('project.fields.webhookSecret'),
      componentProps: {
        placeholder: $t('project.form.webhookSecretPlaceholder'),
        type: 'password',
        showPassword: true,
      },
      dependencies: {
        rules: (values) => {
          // 创建时必填，编辑时选填
          if (formData.value?.id) {
            return z.string().optional();
          }
          return z.string().min(1, $t('common.inputRequired'));
        },
        help: (values) => {
          // 编辑时显示提示，创建时不显示
          return formData.value?.id
            ? $t('project.form.webhookSecretEditHint')
            : undefined;
        },
        triggerFields: [],
      },
    },
  ],
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    const values = await formApi.getValues();
    drawerApi.lock();

    try {
      if (isEdit.value && formData.value?.id) {
        // 编辑模式：构建 UpdateProjectRequest，排除空的 webhookSecret
        const updateData: ProjectApi.UpdateProjectRequest = {};
        if (values.name !== undefined) updateData.name = values.name;
        if (values.description !== undefined)
          updateData.description = values.description;
        if (values.gitPlatform !== undefined)
          updateData.gitPlatform = values.gitPlatform;
        if (values.repoUrl !== undefined) updateData.repoUrl = values.repoUrl;
        if (values.webhookSecret)
          updateData.webhookSecret = values.webhookSecret;

        await updateProjectApi(formData.value.id, updateData);
        ElMessage.success($t('project.messages.updateSuccess'));
      } else {
        // 创建模式
        await createProjectApi(values as ProjectApi.CreateProjectRequest);
        ElMessage.success($t('project.messages.createSuccess'));
      }
      emit('success');
      drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<ProjectApi.ProjectDTO>();
      formApi.resetForm();

      if (data?.id) {
        formData.value = data;
        await formApi.setValues({
          name: data.name,
          description: data.description ?? '',
          gitPlatform: data.gitPlatform,
          repoUrl: data.repoUrl,
          webhookSecret: '',
        });
      } else {
        formData.value = undefined;
      }
    }
  },
});

const drawerTitle = computed(() =>
  isEdit.value ? $t('project.edit') : $t('project.create'),
);
</script>

<template>
  <Drawer :title="drawerTitle" class="w-full max-w-[600px]">
    <Form />
  </Drawer>
</template>
