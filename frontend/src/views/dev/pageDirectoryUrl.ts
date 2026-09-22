import type { PageEntry } from './pageCatalog'

export function normalizeAdminBase(value: string): string | undefined {
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      return undefined
    }
    if (url.pathname === '/') url.pathname = '/admin/'
    if (!url.pathname.endsWith('/')) url.pathname += '/'
    return url.href
  } catch {
    return undefined
  }
}

export function resolvePageUrl(
  page: PageEntry,
  params: Record<string, string>,
  learnerOrigin: string,
  adminBase: string,
): string | undefined {
  const base = page.app === 'admin' ? normalizeAdminBase(adminBase) : `${learnerOrigin}/`
  if (!base) return undefined
  let path = page.name === 'NotFound' ? '/__dev_page_not_found__' : page.path
  for (const param of page.params) {
    const value = params[param]?.trim() ?? ''
    if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) return undefined
    path = path.replace(`:${param}`, encodeURIComponent(value))
  }
  return new URL(path.replace(/^\//, ''), base).href
}
