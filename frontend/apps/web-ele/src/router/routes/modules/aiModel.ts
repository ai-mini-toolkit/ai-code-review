import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:bot',
      order: 300,
      title: $t('aiModel.title'),
    },
    name: 'AIModelManagement',
    path: '/models',
    component: () => import('#/views/aiModel/index.vue'),
  },
];

export default routes;
