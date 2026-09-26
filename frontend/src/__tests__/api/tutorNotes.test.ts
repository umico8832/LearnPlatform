import { afterEach, expect, it } from 'vitest'
import request from '@/utils/request'
import {
  deleteTutorSessionNote,
  getTutorSessionNote,
  getTutorSessionNotes,
  saveTutorSessionNote,
} from '@/api/tutorNotes'

const originalAdapter = request.defaults.adapter
afterEach(() => {
  request.defaults.adapter = originalAdapter
})

it('uses the standard response envelope for current notes, writes, deletes and five-item pagination', async () => {
  const note = { sessionKey: 'session-a', revision: 2, note: '复盘', updatedAt: null, source: {} }
  const envelope = { code: 0, message: '成功', data: note }
  request.defaults.adapter = async (config) => {
    if (config.method === 'get' && config.url === '/my-courses/10/tutor-notes') {
      expect(config.params).toEqual({ page: 2 })
      return {
        data: { ...envelope, data: { records: [], total: 0, current: 2, size: 5 } },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
    expect(config.url).toBe('/my-courses/10/tutor-sessions/session-a/note')
    if (config.method === 'put') expect(JSON.parse(config.data)).toEqual({ revision: 2, note: '复盘' })
    if (config.method === 'delete') expect(config.params).toEqual({ revision: 2 })
    return { data: envelope, status: 200, statusText: 'OK', headers: {}, config }
  }

  expect(await getTutorSessionNotes(10, 2)).toEqual({
    ...envelope,
    data: { records: [], total: 0, current: 2, size: 5 },
  })
  expect(await getTutorSessionNote(10, 'session-a')).toEqual(envelope)
  expect(await saveTutorSessionNote(10, 'session-a', { revision: 2, note: '复盘' })).toEqual(envelope)
  expect(await deleteTutorSessionNote(10, 'session-a', 2)).toEqual(envelope)
})
