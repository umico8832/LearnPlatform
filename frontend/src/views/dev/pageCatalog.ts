import type { RouteMeta, RouteRecordRaw } from 'vue-router'
import { adminRoutes } from '@/admin/routes'
import { learnerRoutes } from '@/router/routes'

export const pageGroups = [
  { id: 'public', label: '公开页面' },
  { id: 'auth', label: '认证页面' },
  { id: 'learner', label: '学习端' },
  { id: 'admin', label: '管理端' },
] as const

type PageGroupId = (typeof pageGroups)[number]['id']

export interface PageEntry {
  id: string
  app: 'learner' | 'admin'
  group: PageGroupId
  name: string
  title: string
  path: string
  access: 'public' | 'login' | 'admin'
  params: string[]
  hidden: boolean
  placeholder: boolean
  notice?: string
  entryPath?: string
}

const authPaths = new Set(['/login', '/register', '/forgot-password', '/reset-password', '/oauth/callback'])
const placeholderNames = new Set(['Product', 'Learning', 'Resources', 'Roadmap', 'About'])
const pageHints: Partial<Record<string, Pick<PageEntry, 'notice' | 'entryPath'>>> = {
  PracticeSession: { notice: '需先选择题目。', entryPath: '/practice' },
  TutorSession: { notice: '进入页面可能创建真实会话。', entryPath: '/my-courses' },
  ExamTake: { notice: '真实计时和提交。', entryPath: '/exams' },
  ExamLearning: { notice: '需要试卷学习会话。', entryPath: '/exams' },
  ExamResult: { notice: '需要考试记录。', entryPath: '/exams' },
  OAuthCallback: { notice: '从第三方登录回调进入。', entryPath: '/login' },
  ResetPassword: { notice: '需要邮件中的重置链接。', entryPath: '/forgot-password' },
}

function resolvePath(parentPath: string, path: string): string {
  if (path.startsWith('/')) return path
  if (!path) return parentPath || '/'
  return `${parentPath === '/' ? '' : parentPath}/${path}`
}

function pathParams(path: string): string[] {
  return [...path.matchAll(/:([A-Za-z0-9_]+)/g)].map((match) => match[1])
}

function appendPages(
  records: readonly RouteRecordRaw[],
  app: PageEntry['app'],
  parentPath = '',
  inheritedMeta: RouteMeta = {},
): PageEntry[] {
  return records.flatMap((record) => {
    const path = resolvePath(parentPath, record.path)
    const meta = { ...inheritedMeta, ...record.meta }
    const nested = record.children ? appendPages(record.children, app, path, meta) : []
    if (typeof record.name !== 'string' || record.redirect) return nested

    const group: PageGroupId =
      app === 'admin' ? 'admin' : authPaths.has(path) ? 'auth' : meta.requiresAuth === false ? 'public' : 'learner'
    const access: PageEntry['access'] =
      app === 'admin'
        ? record.name === 'AdminLogin'
          ? 'public'
          : 'admin'
        : meta.requiresAuth === false
          ? 'public'
          : 'login'
    const isNotFound = record.name === 'NotFound'
    const entry: PageEntry = {
      id: `${app}:${record.name}`,
      app,
      group,
      name: record.name,
      title: record.name === 'Home' ? '首页' : String(meta.title ?? record.name),
      path,
      access,
      params: isNotFound ? [] : pathParams(path),
      hidden: meta.hidden === true,
      placeholder: placeholderNames.has(record.name),
      ...pageHints[record.name],
    }
    return [entry, ...nested]
  })
}

export function createPageCatalog(
  learnerRouteRecords: readonly RouteRecordRaw[] = learnerRoutes,
  adminRouteRecords: readonly RouteRecordRaw[] = adminRoutes,
): PageEntry[] {
  return [...appendPages(learnerRouteRecords, 'learner'), ...appendPages(adminRouteRecords, 'admin')]
}

export const pages = createPageCatalog()
