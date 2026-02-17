<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElSpace, ElTag } from 'element-plus';

import {
  createAIModelApi,
  testConnectionApi,
  type AIModelApi,
  updateAIModelApi,
} from '#/api/aiModel';
import { useVbenForm, z } from '#/adapter/form';
import type { ProviderType, TestConnectionResponse } from '#/types/aiModel';

const emit = defineEmits<{
  success: [];
}>();

const formData = ref<AIModelApi.AIModelDTO>();
const isEdit = computed(() => !!formData.value?.id);

const selectedProviderType = ref<ProviderType>('OPENAI');

const providerTypeOptions = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Anthropic (Claude)', value: 'ANTHROPIC' },
  { label: $t('aiModel.provider.CUSTOM_OPENAPI'), value: 'CUSTOM_OPENAPI' },
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
      label: $t('aiModel.fields.name'),
      rules: z.string().min(1, $t('common.inputRequired')).max(255),
      componentProps: {
        placeholder: $t('aiModel.form.namePlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'providerType',
      label: $t('aiModel.fields.providerType'),
      rules: z.string().min(1, $t('common.selectRequired')),
      componentProps: {
        options: providerTypeOptions,
        placeholder: $t('aiModel.form.providerTypePlaceholder'),
        onChange: (value: ProviderType) => {
          selectedProviderType.value = value;
          // M3 fix: clear apiEndpoint when switching away from CUSTOM_OPENAPI
          if (value !== 'CUSTOM_OPENAPI') {
            formApi.setFieldValue('apiEndpoint', '');
          }
        },
      },
    },
    {
      component: 'Input',
      fieldName: 'modelName',
      label: $t('aiModel.fields.modelName'),
      rules: z.string().min(1, $t('common.inputRequired')).max(255),
      componentProps: {
        placeholder: $t('aiModel.form.modelNamePlaceholder'),
      },
      help: $t('aiModel.form.modelNameHelp'),
    },
    {
      component: 'Input',
      fieldName: 'apiEndpoint',
      label: $t('aiModel.fields.apiEndpoint'),
      dependencies: {
        show: () => selectedProviderType.value === 'CUSTOM_OPENAPI',
        rules: () => {
          if (selectedProviderType.value === 'CUSTOM_OPENAPI') {
            return z
              .string()
              .min(1, $t('common.inputRequired'))
              .url($t('aiModel.form.apiEndpointInvalid'));
          }
          return z.string().optional();
        },
        triggerFields: ['providerType'],
      },
      componentProps: {
        placeholder: $t('aiModel.form.apiEndpointPlaceholder'),
      },
      help: $t('aiModel.form.apiEndpointHelp'),
    },
    {
      component: 'Input',
      fieldName: 'apiKey',
      label: $t('aiModel.fields.apiKey'),
      dependencies: {
        rules: () => {
          if (isEdit.value) {
            return z.string().optional();
          }
          return z.string().min(1, $t('common.inputRequired'));
        },
        help: () => {
          return isEdit.value ? $t('aiModel.form.apiKeyEditHint') : undefined;
        },
        triggerFields: [],
      },
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: $t('aiModel.form.apiKeyPlaceholder'),
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'timeoutSeconds',
      label: $t('aiModel.fields.timeoutSeconds'),
      rules: z.number().min(1).max(300).optional(),
      componentProps: {
        placeholder: '30',
        min: 1,
        max: 300,
      },
      help: $t('aiModel.form.timeoutSecondsHelp'),
    },
    {
      component: 'InputNumber',
      fieldName: 'maxTokens',
      label: $t('aiModel.fields.maxTokens'),
      rules: z.number().min(1).optional(),
      componentProps: {
        placeholder: '4000',
        min: 1,
        max: 100_000,
      },
      help: $t('aiModel.form.maxTokensHelp'),
    },
    {
      component: 'InputNumber',
      fieldName: 'temperature',
      label: $t('aiModel.fields.temperature'),
      rules: z.number().min(0).max(1).optional(),
      componentProps: {
        placeholder: '0.7',
        min: 0,
        max: 1,
        step: 0.1,
      },
      help: $t('aiModel.form.temperatureHelp'),
    },
  ],
  showDefaultActions: false,
});

// M1 fix: saving state for confirm button loading indicator
const saving = ref(false);

// Test connection state
const testing = ref(false);
const testResult = ref<TestConnectionResponse | null>(null);

async function handleTestConnection() {
  const { valid } = await formApi.validate();
  if (!valid) {
    ElMessage.warning($t('aiModel.messages.validateFirst'));
    return;
  }

  const values = await formApi.getValues();
  testing.value = true;
  testResult.value = null;

  try {
    const result = await testConnectionApi({
      providerType: values.providerType as ProviderType,
      modelName: values.modelName,
      apiEndpoint: values.apiEndpoint,
      apiKey: values.apiKey,
      timeoutSeconds: values.timeoutSeconds || 30,
    });
    testResult.value = result as TestConnectionResponse;

    if (testResult.value.success) {
      ElMessage.success(
        `${$t('aiModel.messages.testSuccess')} (${testResult.value.responseTimeMs}ms)`,
      );
    } else {
      ElMessage.error(
        `${$t('aiModel.messages.testFailed')}: ${testResult.value.message}`,
      );
    }
  } catch (error: any) {
    ElMessage.error(`${$t('aiModel.messages.testError')}: ${error.message}`);
  } finally {
    testing.value = false;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    const values = await formApi.getValues();
    drawerApi.lock();
    saving.value = true;

    try {
      if (isEdit.value && formData.value?.id) {
        const updateData: AIModelApi.UpdateAIModelRequest = {
          name: values.name,
          providerType: values.providerType as ProviderType,
          modelName: values.modelName,
          apiEndpoint: values.apiEndpoint,
          timeoutSeconds: values.timeoutSeconds,
          maxTokens: values.maxTokens,
          temperature: values.temperature,
        };
        if (values.apiKey) {
          updateData.apiKey = values.apiKey;
        }

        await updateAIModelApi(formData.value.id, updateData);
        ElMessage.success($t('aiModel.messages.updateSuccess'));
      } else {
        await createAIModelApi({
          name: values.name,
          providerType: values.providerType as ProviderType,
          modelName: values.modelName,
          apiEndpoint: values.apiEndpoint,
          apiKey: values.apiKey,
          timeoutSeconds: values.timeoutSeconds,
          maxTokens: values.maxTokens,
          temperature: values.temperature,
          enabled: true,
        });
        ElMessage.success($t('aiModel.messages.createSuccess'));
      }
      emit('success');
      drawerApi.close();
    } finally {
      saving.value = false;
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<AIModelApi.AIModelDTO>();
      formApi.resetForm();
      testResult.value = null;

      if (data?.id) {
        formData.value = data;
        selectedProviderType.value = data.providerType;
        await formApi.setValues({
          name: data.name,
          providerType: data.providerType,
          modelName: data.modelName,
          apiEndpoint: data.apiEndpoint ?? '',
          apiKey: '',
          timeoutSeconds: data.timeoutSeconds,
          maxTokens: data.maxTokens,
          temperature: data.temperature,
        });
      } else {
        formData.value = undefined;
        selectedProviderType.value = 'OPENAI';
      }
    }
  },
});

const drawerTitle = computed(() =>
  isEdit.value ? $t('aiModel.edit') : $t('aiModel.create'),
);
</script>

<template>
  <Drawer :title="drawerTitle" class="w-full max-w-[600px]">
    <Form />

    <!-- 测试连接区域 -->
    <div class="test-connection-section mt-4 flex items-center gap-3 rounded p-3">
      <ElButton type="warning" :loading="testing" @click="handleTestConnection">
        {{ $t('aiModel.actions.testConnection') }}
      </ElButton>
      <div v-if="testResult" class="flex items-center gap-2 text-sm">
        <ElTag
          :type="testResult.success ? 'success' : 'danger'"
          size="small"
        >
          {{ testResult.success ? $t('aiModel.messages.testSuccess') : $t('aiModel.messages.testFailed') }}
        </ElTag>
        <span>{{ testResult.message }}</span>
        <span v-if="testResult.responseTimeMs" class="text-gray-500">
          ({{ testResult.responseTimeMs }}ms)
        </span>
      </div>
    </div>

    <template #footer>
      <ElSpace>
        <ElButton @click="drawerApi.close()">
          {{ $t('common.cancel') }}
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="drawerApi.confirm()">
          {{ $t('common.confirm') }}
        </ElButton>
      </ElSpace>
    </template>
  </Drawer>
</template>

<style scoped lang="scss">
.test-connection-section {
  background: #f5f7fa;
}
</style>
