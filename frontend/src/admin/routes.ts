import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: RouteRecordRaw[] = [
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
        path: 'community',
        name: 'AdminCommunity',
        component: () => import('./views/CommunityManage.vue'),
        meta: { title: '社区管理' },
      },
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('./views/AdminDashboard.vue'),
        meta: { title: '平台数据总览' },
      },
      {
        path: 'exams',
        name: 'AdminExamManage',
        component: () => import('./views/ExamManage.vue'),
        meta: { title: '试卷管理' },
      },
      {
        path: 'courses',
        name: 'AdminCourseManage',
        component: () => import('./views/CourseManage.vue'),
        meta: { title: '课程管理' },
      },
      {
        path: 'knowledge-points',
        name: 'AdminKPManage',
        component: () => import('./views/KnowledgePointManage.vue'),
        meta: { title: '知识点管理' },
      },
      {
        path: 'knowledge',
        name: 'AdminKnowledgeManage',
        component: () => import('./views/KnowledgeManage.vue'),
        meta: { title: '知识快照审核' },
      },
      {
        path: 'questions',
        name: 'AdminQuestionManage',
        component: () => import('./views/QuestionManage.vue'),
        meta: { title: '题目管理' },
      },
      {
        path: 'users',
        name: 'AdminUserManage',
        component: () => import('./views/UserManage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'submissions',
        name: 'AdminSubmissionManage',
        component: () => import('./views/SubmissionManage.vue'),
        meta: { title: '投稿管理' },
      },
      {
        path: 'ai-usage',
        name: 'AdminAiUsage',
        component: () => import('./views/AiUsageView.vue'),
        meta: { title: 'AI 调用分析' },
      },
      {
        path: 'ai-variant-reviews',
        name: 'AdminAiVariantReviews',
        component: () => import('./views/AiVariantReviewView.vue'),
        meta: { title: 'AI 变式题审查' },
      },
      {
        path: 'subjective-reviews',
        name: 'AdminSubjectiveReviews',
        component: () => import('./views/SubjectiveReviewView.vue'),
        meta: { title: '主观题批阅' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'AdminDashboard' } },
]
