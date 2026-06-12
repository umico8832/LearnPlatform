import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/course/CourseListView.vue'),
        meta: { title: '课程列表' },
      },
      {
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/CourseDetailView.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'questions',
        name: 'QuestionList',
        component: () => import('@/views/course/QuestionListView.vue'),
        meta: { title: '题库' },
      },
      {
        path: 'practice',
        name: 'Practice',
        component: () => import('@/views/practice/PracticeView.vue'),
        meta: { title: '刷题练习' },
      },
      {
        path: 'practice/session',
        name: 'PracticeSession',
        component: () => import('@/views/practice/PracticeSessionView.vue'),
        meta: { title: '答题中' },
      },
      {
        path: 'practice/records',
        name: 'PracticeRecords',
        component: () => import('@/views/practice/PracticeRecordView.vue'),
        meta: { title: '刷题记录' },
      },
      {
        path: 'wrong-questions',
        name: 'WrongQuestions',
        component: () => import('@/views/practice/WrongQuestionView.vue'),
        meta: { title: '错题本' },
      },
      // 管理端路由
      {
        path: 'admin/courses',
        name: 'AdminCourseManage',
        component: () => import('@/views/admin/CourseManage.vue'),
        meta: { title: '课程管理', requiresAdmin: true },
      },
      {
        path: 'admin/knowledge-points',
        name: 'AdminKPManage',
        component: () => import('@/views/admin/KnowledgePointManage.vue'),
        meta: { title: '知识点管理', requiresAdmin: true },
      },
      {
        path: 'admin/questions',
        name: 'AdminQuestionManage',
        component: () => import('@/views/admin/QuestionManage.vue'),
        meta: { title: '题目管理', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 路由守卫：检查登录状态
 */
router.beforeEach((to, _from, next) => {
  // 设置页面标题
  const title = to.meta.title as string
  if (title) {
    document.title = `${title} - AI 题库与错题复习系统`
  }

  const loggedIn = isAuthenticated()

  // 已登录用户访问登录/注册页，跳转首页
  if (loggedIn && (to.path === '/login' || to.path === '/register')) {
    next({ path: '/' })
    return
  }

  // 未登录访问需认证页面，跳转登录页
  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !loggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router