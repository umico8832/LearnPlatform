import { afterEach, expect, it } from 'vitest'
import request from '@/utils/request'
import { deleteTutorMemory, getTutorMemory, saveTutorMemory } from '@/api/tutorMemory'

const originalAdapter = request.defaults.adapter
afterEach(() => {
  request.defaults.adapter = originalAdapter
})

it.each(['get', 'put', 'delete'] as const)(
  'preserves the API envelope after the real %s response interceptor',
  async (method) => {
    const memory = { revision: 2, explanationStyle: null, goal: '理解队列' }
    const envelope = { code: 0, message: '成功', data: memory }
    request.defaults.adapter = async (config) => {
      expect(config.method).toBe(method)
      expect(config.url).toBe('/my-courses/10/tutor-memory')
      if (method === 'put') expect(JSON.parse(config.data)).toEqual(memory)
      if (method === 'delete') expect(config.params).toEqual({ revision: 2 })
      return { data: envelope, status: 200, statusText: 'OK', headers: {}, config }
    }
    const result = await (method === 'get'
      ? getTutorMemory(10)
      : method === 'put'
        ? saveTutorMemory(10, memory)
        : deleteTutorMemory(10, 2))
    expect(result).toEqual(envelope)
  },
)
