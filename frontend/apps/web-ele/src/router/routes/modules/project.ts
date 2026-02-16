import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:folder-kanban',
      order: 100,
      title: $t('project.title'),
    },
    name: 'ProjectManagement',
    path: '/project',
    children: [
      {
        meta: {
          title: $t('project.list'),
        },
        name: 'ProjectList',
        path: '/project',
        component: () => import('#/views/project/index.vue'),
      },
      {
        meta: {
          hideInMenu: true,
          title: $t('project.detail'),
        },
        name: 'ProjectDetail',
        path: '/project/detail/:id',
        component: () => import('#/views/project/detail.vue'),
      },
    ],
  },
];

export default routes;
