import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:file-code',
      order: 400,
      title: $t('template.title'),
    },
    name: 'TemplateManagement',
    path: '/templates',
    children: [
      {
        name: 'TemplateList',
        path: '/templates',
        component: () => import('#/views/template/index.vue'),
        meta: {
          icon: 'lucide:file-code',
          title: $t('template.list'),
        },
      },
      {
        name: 'TemplateEditor',
        path: '/templates/:id/edit',
        component: () => import('#/views/template/editor.vue'),
        meta: {
          hideInMenu: true,
          title: $t('template.editor'),
        },
      },
    ],
  },
];

export default routes;
