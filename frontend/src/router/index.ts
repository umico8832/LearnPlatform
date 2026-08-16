import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false, title: '注册' },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { requiresAuth: false, title: '忘记密码' },
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { requiresAuth: false, title: '重置密码' },
  },
  {
    path: '/',
    redirect: '/my-courses',
    meta: { requiresAuth: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'my-courses',
        name: 'MyCourses',
        component: () => import('@/views/course/MyCoursesView.vue'),
        meta: { title: '我的课程' },
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/course/CourseListView.vue'),
        meta: { title: '课程库' },
      },
      {
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/CourseDetailView.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'my-courses/:id',
        name: 'CourseOverview',
        component: () => import('@/views/course/CourseOverviewView.vue'),
        meta: { title: '课程空间' },
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
        meta: { title: '练习' },
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
        meta: { title: '练习记录' },
      },
      {
        path: 'wrong-questions',
        name: 'WrongQuestions',
        component: () => import('@/views/practice/WrongQuestionView.vue'),
        meta: { title: '错题' },
      },
      {
        path: 'favorites',
        name: 'Favorites',
        component: () => import('@/views/practice/FavoriteView.vue'),
        meta: { title: '我的收藏' },
      },
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/practice/ReviewView.vue'),
        meta: { title: '复习' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/auth/ProfileView.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'learning-diagnosis',
        name: 'LearningDiagnosis',
        component: () => import('@/views/statistics/LearningDiagnosisView.vue'),
        meta: { title: '学习诊断', hidden: true },
      },
      {
        path: 'submit',
        name: 'QuestionSubmit',
        component: () => import('@/views/practice/QuestionSubmitView.vue'),
        meta: { title: '题目投稿', hidden: true },
      },
      {
        path: 'exams',
        name: 'ExamList',
        component: () => import('@/views/exam/ExamListView.vue'),
        meta: { title: '考试与试卷' },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/components/layout/FocusLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'my-courses/:id/tutor',
        name: 'TutorSession',
        component: () => import('@/views/course/TutorSessionView.vue'),
        meta: { title: 'AI 教学', focusTitle: 'AI 教学', focusSubtitle: '按步骤理解，服务端判分' },
      },
      {
        path: 'exams/take/:recordId',
        name: 'ExamTake',
        component: () => import('@/views/exam/ExamTakeView.vue'),
        meta: { title: '考试中', focusTitle: '考试进行中' },
      },
      {
        path: 'exams/learn/:sessionId',
        name: 'ExamLearning',
        component: () => import('@/views/exam/ExamLearningView.vue'),
        meta: { title: '试卷学习', focusTitle: '试卷学习' },
      },
      {
        path: 'exams/result/:recordId',
        name: 'ExamResult',
        component: () => import('@/views/exam/ExamResultView.vue'),
        meta: { title: '考试结果', focusTitle: '考试结果' },
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
router.beforeEach(async (to) => {
  // 设置页面标题
  const title = to.meta.title as string
  if (title) {
    document.title = `${title} · LearnPlatform`
  }

  const loggedIn = isAuthenticated()

  // 已登录用户访问登录/注册页，跳转我的课程
  if (loggedIn && ['/login', '/register', '/forgot-password', '/reset-password'].includes(to.path)) {
    return { path: '/my-courses' }
  }

  // 未登录访问需认证页面，跳转登录页
  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
