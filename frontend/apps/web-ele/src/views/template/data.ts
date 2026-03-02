import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { $t } from '@vben/locales';

export type OnActionClickFn<T = any> = (params: { code: string; row: T }) => void;

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('template.fields.name'),
    },
    {
      component: 'Select',
      fieldName: 'category',
      label: $t('template.fields.category'),
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('template.category.security'), value: 'security' },
          { label: $t('template.category.performance'), value: 'performance' },
          { label: $t('template.category.quality'), value: 'quality' },
          { label: $t('template.category.style'), value: 'style' },
          { label: $t('template.category.bug'), value: 'bug' },
          { label: $t('template.category.call-graph'), value: 'call-graph' },
        ],
      },
    },
  ];
}

export function useColumns(onActionClick: OnActionClickFn): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: $t('template.fields.name'),
      minWidth: 200,
      showOverflow: true,
      sortable: true,
    },
    {
      field: 'category',
      title: $t('template.fields.category'),
      width: 130,
      cellRender: {
        name: 'CellTag',
        attrs: {
          colorMap: {
            security: 'danger',
            performance: 'warning',
            quality: 'success',
            style: 'info',
            bug: 'danger',
            'call-graph': '',
          },
          labelMap: {
            security: $t('template.category.security'),
            performance: $t('template.category.performance'),
            quality: $t('template.category.quality'),
            style: $t('template.category.style'),
            bug: $t('template.category.bug'),
            'call-graph': $t('template.category.call-graph'),
          },
        },
      },
    },
    {
      field: 'version',
      title: $t('template.fields.version'),
      width: 90,
    },
    {
      field: 'isDefault',
      title: $t('template.fields.isDefault'),
      width: 100,
      cellRender: {
        name: 'CellTag',
        attrs: {
          colorMap: { true: 'success', false: 'info' },
          labelMap: {
            true: $t('template.status.default'),
            false: $t('template.status.custom'),
          },
        },
      },
    },
    {
      field: 'enabled',
      title: $t('template.fields.enabled'),
      width: 100,
      cellRender: {
        name: 'CellSwitch',
        attrs: { field: 'enabled' },
      },
    },
    {
      field: 'updatedAt',
      title: $t('template.fields.updatedAt'),
      width: 180,
      sortable: true,
      formatter: ({ cellValue }: { cellValue: string }) =>
        new Date(cellValue).toLocaleString(),
    },
    {
      field: 'operation',
      title: $t('template.fields.operations'),
      width: 220,
      fixed: 'right',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          onClick: onActionClick,
          buttons: [
            { code: 'edit', label: $t('template.actions.editTemplate'), type: 'primary' },
            { code: 'copy', label: $t('template.actions.copyTemplate'), type: '' },
            { code: 'delete', label: $t('common.delete'), type: 'danger' },
          ],
        },
      },
    },
  ];
}
