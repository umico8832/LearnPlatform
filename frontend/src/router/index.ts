import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import { learnerRoutes } from './routes'
import { learnerScrollBehavior } from './scrollBehavior'

const devRoutes: RouteRecordRaw[] = import.meta.env.DEV
  ? [
      {
        path: '/dev/pages',
        name: 'DevPages',
        component: () => import('@/views/dev/PageDirectoryView.vue'),
        meta: { requiresAuth: false, title: '全局页面预览' },
      },
    ]
  : []

export function createLearnerRoutes(includeDev = import.meta.env.DEV): RouteRecordRaw[] {
  return includeDev ? [...devRoutes, ...learnerRoutes] : learnerRoutes
}

const router = createRouter({
  history: createWebHistory(),
  routes: createLearnerRoutes(),
  scrollBehavior: learnerScrollBehavior,
})

/**
 * 路由守卫：检查登录状态
 */
router.beforeEach(async (to) => {
  const loggedIn = isAuthenticated()
  const isAuthPreview = import.meta.env.DEV && typeof to.query['auth-preview'] === 'string'

  if (loggedIn && !isAuthPreview && ['/login', '/register', '/forgot-password', '/reset-password'].includes(to.path)) {
    return { path: '/my-courses' }
  }

  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !loggedIn) return { path: '/login', query: { redirect: to.fullPath } }

  return true
})

router.afterEach((to, _from, failure) => {
  if (failure) return
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · LearnPlatform` : 'LearnPlatform'
})

export default router
