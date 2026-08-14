import { createRouter, createWebHistory } from 'vue-router'
import type { RouterHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

export function createAdminRouter(history: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/login',
        name: 'AdminLogin',
        component: () => import('./views/AdminLoginView.vue'),
        meta: { requiresAuth: false, title: '管理员登录' },
      },
      {
        path: '/',
        component: () => import('./AdminLayout.vue'),
        children: [
          {
            path: '',
            name: 'AdminDashboard',
            component: () => import('@/views/admin/AdminDashboard.vue'),
            meta: { title: '平台数据总览' },
          },
          {
            path: 'exams',
            name: 'AdminExamManage',
            component: () => import('@/views/admin/ExamManage.vue'),
            meta: { title: '试卷管理' },
          },
          {
            path: 'courses',
            name: 'AdminCourseManage',
            component: () => import('@/views/admin/CourseManage.vue'),
            meta: { title: '课程管理' },
          },
          {
            path: 'knowledge-points',
            name: 'AdminKPManage',
            component: () => import('@/views/admin/KnowledgePointManage.vue'),
            meta: { title: '知识点管理' },
          },
          {
            path: 'questions',
            name: 'AdminQuestionManage',
            component: () => import('@/views/admin/QuestionManage.vue'),
            meta: { title: '题目管理' },
          },
          {
            path: 'users',
            name: 'AdminUserManage',
            component: () => import('@/views/admin/UserManage.vue'),
            meta: { title: '用户管理' },
          },
          {
            path: 'submissions',
            name: 'AdminSubmissionManage',
            component: () => import('@/views/admin/SubmissionManage.vue'),
            meta: { title: '投稿管理' },
          },
          {
            path: 'ai-usage',
            name: 'AdminAiUsage',
            component: () => import('@/views/admin/AiUsageView.vue'),
            meta: { title: 'AI 调用分析' },
          },
          {
            path: 'ai-variant-reviews',
            name: 'AdminAiVariantReviews',
            component: () => import('@/views/admin/AiVariantReviewView.vue'),
            meta: { title: 'AI 变式题审查' },
          },
          {
            path: 'subjective-reviews',
            name: 'AdminSubjectiveReviews',
            component: () => import('@/views/admin/SubjectiveReviewView.vue'),
            meta: { title: '主观题批阅' },
          },
        ],
      },
      { path: '/:pathMatch(.*)*', redirect: { name: 'AdminDashboard' } },
    ],
  })

  router.beforeEach(async (to) => {
    if (to.meta.title) document.title = `${String(to.meta.title)} - LearnPlatform 管理系统`
    const loggedIn = isAuthenticated()
    if (to.name === 'AdminLogin') {
      if (!loggedIn) return true
      const userStore = useUserStore()
      if (!userStore.userInfo) await userStore.fetchUserInfo()
      if (userStore.userInfo?.role === 'ADMIN') return { name: 'AdminDashboard' }
      userStore.clearLoginInfo()
      return true
    }
    if (!loggedIn) {
      return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    }
    const userStore = useUserStore()
    if (!userStore.userInfo) await userStore.fetchUserInfo()
    if (userStore.userInfo?.role !== 'ADMIN') {
      userStore.clearLoginInfo()
      return { name: 'AdminLogin' }
    }
    return true
  })

  return router
}

export default createAdminRouter()
