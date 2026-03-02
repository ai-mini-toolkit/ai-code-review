import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { $t } from '@vben/locales';

export type OnActionClickFn<T = any> = (params: { code: string; row: T }) => void;

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('aiModel.fields.name'),
    },
    {
      component: 'Select',
      fieldName: 'providerType',
      label: $t('aiModel.fields.providerType'),
      componentProps: {
        allowClear: true,
        options: [
          { label: 'OpenAI', value: 'OPENAI' },
          { label: 'Anthropic', value: 'ANTHROPIC' },
          { label: $t('aiModel.provider.CUSTOM_OPENAPI'), value: 'CUSTOM_OPENAPI' },
        ],
      },
    },
    {
      component: 'Select',
      fieldName: 'enabled',
      label: $t('aiModel.fields.enabled'),
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('aiModel.status.enabled'), value: true },
          { label: $t('aiModel.status.disabled'), value: false },
        ],
      },
    },
  ];
}

export function useColumns(onActionClick: OnActionClickFn): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: $t('aiModel.fields.name'),
      minWidth: 160,
      showOverflow: true,
      sortable: true,
    },
    {
      field: 'providerType',
      title: $t('aiModel.fields.providerType'),
      width: 160,
      cellRender: {
        name: 'CellTag',
        attrs: {
          colorMap: {
            OPENAI: '',
            ANTHROPIC: 'success',
            CUSTOM_OPENAPI: 'info',
          },
          labelMap: {
            OPENAI: 'OpenAI',
            ANTHROPIC: 'Anthropic',
            CUSTOM_OPENAPI: $t('aiModel.provider.CUSTOM_OPENAPI'),
          },
        },
      },
    },
    {
      field: 'modelName',
      title: $t('aiModel.fields.modelName'),
      minWidth: 180,
      showOverflow: true,
    },
    {
      field: 'apiKeyConfigured',
      title: $t('aiModel.fields.apiKey'),
      width: 120,
    },
    {
      field: 'enabled',
      title: $t('aiModel.fields.enabled'),
      width: 100,
      cellRender: {
        name: 'CellSwitch',
        attrs: {
          field: 'enabled',
        },
      },
    },
    {
      field: 'createdAt',
      title: $t('aiModel.fields.createdAt'),
      width: 180,
      sortable: true,
      formatter: ({ cellValue }: { cellValue: string }) =>
        new Date(cellValue).toLocaleString(),
    },
    {
      field: 'operation',
      title: $t('aiModel.fields.operations'),
      width: 260,
      fixed: 'right',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          onClick: onActionClick,
          buttons: [
            { code: 'edit', label: $t('common.edit'), type: 'primary' },
            { code: 'test', label: $t('aiModel.actions.testConnection'), type: 'warning' },
            { code: 'delete', label: $t('common.delete'), type: 'danger' },
          ],
        },
      },
    },
  ];
}
