/**
 * useListPage - 通用列表页 composable
 *
 * 封装 VbenVxeGrid 的标准配置，提供：
 * - 完整工具栏（刷新、查询收起展开、最大化、列设置）
 * - 标准 proxyConfig（支持服务端分页 + 客户端全量两种模式）
 * - 统一响应格式（{ items: T[], total: number }）
 *
 * 使用示例：
 * ```typescript
 * const [Grid, gridApi] = useListPage({
 *   fetchFn: async (params) => {
 *     const data = await getItemsApi();
 *     return { items: data, total: data.length };
 *   },
 *   columns: useColumns(onActionClick),
 *   formSchema: useGridFormSchema(),
 * });
 * ```
 */

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

export interface ListPageOptions<T = any> {
  /** 数据获取函数，接受查询参数，返回 { items, total } */
  fetchFn: (params?: Record<string, any>) => Promise<{ items: T[]; total: number }>;
  /** VxeGrid 列定义 */
  columns: VxeTableGridOptions['columns'];
  /** 搜索表单字段（可选） */
  formSchema?: VbenFormSchema[];
  /** 覆盖默认 toolbarConfig（可选） */
  toolbarConfig?: {
    custom?: boolean;
    export?: boolean;
    refresh?: boolean;
    search?: boolean;
    zoom?: boolean;
  };
  /** 额外的 gridOptions（可选，会合并到默认配置） */
  gridOptions?: Partial<VxeTableGridOptions>;
}

/** 标准列表页工具栏配置 */
export const DEFAULT_TOOLBAR_CONFIG = {
  custom: true,   // 列设置
  export: false,
  refresh: true,  // 刷新
  search: true,   // 查询收起/展开
  zoom: true,     // 最大化
} as const;

/**
 * 通用列表页 composable
 * 封装 VbenVxeGrid 的标准配置
 */
export function useListPage<T = any>(options: ListPageOptions<T>) {
  const {
    fetchFn,
    columns,
    formSchema,
    toolbarConfig,
    gridOptions: extraGridOptions,
  } = options;

  const mergedToolbar = {
    ...DEFAULT_TOOLBAR_CONFIG,
    ...toolbarConfig,
  };

  const [Grid, gridApi] = useVbenVxeGrid({
    ...(formSchema && formSchema.length > 0
      ? {
          formOptions: {
            schema: formSchema,
            submitOnChange: true,
          },
        }
      : {}),
    gridOptions: {
      columns,
      height: 'auto',
      keepSource: true,
      proxyConfig: {
        ajax: {
          query: async (_params: any, formValues?: Record<string, any>) => {
            return await fetchFn(formValues || {});
          },
        },
      },
      rowConfig: {
        keyField: 'id',
      },
      toolbarConfig: mergedToolbar,
      ...extraGridOptions,
    } as VxeTableGridOptions,
  });

  return [Grid, gridApi] as const;
}
