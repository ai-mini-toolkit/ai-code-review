import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import { h } from 'vue';

import { setupVbenVxeTable, useVbenVxeGrid } from '@vben/plugins/vxe-table';

import { ElButton, ElImage, ElSpace, ElSwitch, ElTag } from 'element-plus';

import { useVbenForm } from './form';

setupVbenVxeTable({
  configVxeTable: (vxeUI) => {
    vxeUI.setConfig({
      grid: {
        align: 'center',
        border: false,
        columnConfig: {
          resizable: true,
        },
        minHeight: 180,
        formConfig: {
          // 全局禁用vxe-table的表单配置，使用formOptions
          enabled: false,
        },
        proxyConfig: {
          autoLoad: true,
          response: {
            result: 'items',
            total: 'total',
            list: 'items',
          },
          showActiveMsg: true,
          showResponseMsg: false,
        },
        round: true,
        showOverflow: true,
        size: 'small',
      } as VxeTableGridOptions,
    });

    // 表格配置项可以用 cellRender: { name: 'CellImage' },
    vxeUI.renderer.add('CellImage', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { column, row } = params;
        const src = row[column.field];
        return h(ElImage, { src, previewSrcList: [src], ...props });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellLink' },
    vxeUI.renderer.add('CellLink', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(
          ElButton,
          { size: 'small', link: true },
          { default: () => props?.text },
        );
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellSwitch', attrs: { beforeChange, field } }
    // beforeChange: (newVal: boolean, row: T) => Promise<boolean | undefined>
    vxeUI.renderer.add('CellSwitch', {
      renderTableDefault(renderOpts, params) {
        const { attrs } = renderOpts;
        const { row, column } = params;
        const field = attrs?.field || column.field;
        const currentValue = row[field];
        return h(ElSwitch, {
          modelValue: currentValue,
          onChange: async (newVal: boolean) => {
            if (attrs?.beforeChange) {
              const result = await attrs.beforeChange(newVal, row);
              if (result === false) return;
            }
            row[field] = newVal;
          },
        });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellTag', attrs: { colorMap } }
    // colorMap: Record<string, 'success'|'warning'|'danger'|'info'|''>
    vxeUI.renderer.add('CellTag', {
      renderTableDefault(renderOpts, params) {
        const { attrs } = renderOpts;
        const { row, column } = params;
        const value = row[column.field];
        const colorMap = attrs?.colorMap || {};
        const type = colorMap[value] ?? 'info';
        const labelMap = attrs?.labelMap || {};
        const label = labelMap[value] ?? String(value);
        return h(ElTag, { type, size: 'small' }, { default: () => label });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellOperation', attrs: { onClick, buttons } }
    // onClick: (params: { code: string; row: T }) => void
    // buttons: Array<{ code: string; label: string; type?: string; disabled?: (row) => boolean }>
    vxeUI.renderer.add('CellOperation', {
      renderTableDefault(renderOpts, params) {
        const { attrs } = renderOpts;
        const { row } = params;
        const onClick = attrs?.onClick;
        const buttons: Array<{
          code: string;
          label: string;
          type?: '' | 'primary' | 'success' | 'warning' | 'danger' | 'info';
          disabled?: (row: any) => boolean;
        }> = attrs?.buttons || [
          { code: 'edit', label: '编辑', type: 'primary' },
          { code: 'delete', label: '删除', type: 'danger' },
        ];
        return h(
          ElSpace,
          {},
          {
            default: () =>
              buttons.map((btn) =>
                h(
                  ElButton,
                  {
                    size: 'small',
                    type: btn.type,
                    disabled: btn.disabled ? btn.disabled(row) : false,
                    onClick: () => onClick?.({ code: btn.code, row }),
                  },
                  { default: () => btn.label },
                ),
              ),
          },
        );
      },
    });

    // 这里可以自行扩展 vxe-table 的全局配置，比如自定义格式化
    // vxeUI.formats.add
  },
  useVbenForm,
});

export { useVbenVxeGrid };

export type * from '@vben/plugins/vxe-table';
