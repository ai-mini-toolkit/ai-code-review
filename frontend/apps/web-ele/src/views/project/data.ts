import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { $t } from '@vben/locales';

export type OnActionClickFn<T = any> = (params: { code: string; row: T }) => void;

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('project.fields.name'),
    },
    {
      component: 'Select',
      fieldName: 'gitPlatform',
      label: $t('project.fields.gitPlatform'),
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('project.platform.github'), value: 'github' },
          { label: $t('project.platform.gitlab'), value: 'gitlab' },
          { label: $t('project.platform.codecommit'), value: 'codecommit' },
        ],
      },
    },
    {
      component: 'Select',
      fieldName: 'enabled',
      label: $t('project.fields.enabled'),
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('project.status.enabled'), value: true },
          { label: $t('project.status.disabled'), value: false },
        ],
      },
    },
  ];
}

export function useColumns(onActionClick: OnActionClickFn): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: $t('project.fields.name'),
      minWidth: 160,
      showOverflow: true,
      sortable: true,
    },
    {
      field: 'gitPlatform',
      title: $t('project.fields.gitPlatform'),
      width: 120,
      cellRender: {
        name: 'CellTag',
        attrs: {
          colorMap: {
            github: '',
            gitlab: 'warning',
            codecommit: 'info',
          },
          labelMap: {
            github: $t('project.platform.github'),
            gitlab: $t('project.platform.gitlab'),
            codecommit: $t('project.platform.codecommit'),
          },
        },
      },
    },
    {
      field: 'repoUrl',
      title: $t('project.fields.repoUrl'),
      minWidth: 200,
      showOverflow: true,
    },
    {
      field: 'enabled',
      title: $t('project.fields.enabled'),
      width: 100,
      cellRender: {
        name: 'CellSwitch',
        attrs: { field: 'enabled' },
      },
    },
    {
      field: 'createdAt',
      title: $t('project.fields.createdAt'),
      width: 180,
      sortable: true,
      formatter: ({ cellValue }: { cellValue: string }) =>
        new Date(cellValue).toLocaleString(),
    },
    {
      field: 'operation',
      title: $t('project.fields.operations'),
      width: 220,
      fixed: 'right',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          onClick: onActionClick,
          buttons: [
            { code: 'detail', label: $t('project.actions.viewDetail'), type: '' },
            { code: 'edit', label: $t('common.edit'), type: 'primary' },
            { code: 'delete', label: $t('common.delete'), type: 'danger' },
          ],
        },
      },
    },
  ];
}
