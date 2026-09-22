import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import { learnerRoutes } from './routes'

const devRoutes: RouteRecordRaw[] = import.meta.env.DEV
  ? [
      {
        path: '/dev/auth-preview',
        name: 'AuthPreview',
        component: () => import('@/views/dev/AuthPreviewView.vue'),
        meta: { requiresAuth: false, title: '认证页面预览' },
      },
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
})

/**
 * 路由守卫：检查登录状态
 */
router.beforeEach(async (to) => {
  const title = to.meta.title as string
  if (title) document.title = `${title} · LearnPlatform`

  const loggedIn = isAuthenticated()
  const isAuthPreview = import.meta.env.DEV && typeof to.query['auth-preview'] === 'string'

  if (loggedIn && !isAuthPreview && ['/login', '/register', '/forgot-password', '/reset-password'].includes(to.path)) {
    return { path: '/my-courses' }
  }

  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !loggedIn) return { path: '/login', query: { redirect: to.fullPath } }

  return true
})

export default router
