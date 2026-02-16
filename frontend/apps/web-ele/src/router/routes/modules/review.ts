/**
 * 审查历史管理路由模块
 */
import type { RouteRecordRaw } from 'vue-router';
import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:clipboard-list',
      order: 200,
      title: $t('review.title'),
    },
    name: 'ReviewManagement',
    path: '/reviews',
    children: [
      {
        meta: {
          title: $t('review.history'),
        },
        name: 'ReviewHistory',
        path: '/reviews',
        component: () => import('#/views/review/ReviewHistory.vue'),
      },
      {
        meta: {
          title: $t('review.detail'),
          hideInMenu: true,
        },
        name: 'ReviewDetail',
        path: '/reviews/:id',
        component: () => import('#/views/review/ReviewDetail.vue'),
      },
    ],
  },
];

export default routes;
