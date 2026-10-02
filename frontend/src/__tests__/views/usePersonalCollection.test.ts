import { effectScope, nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { removeToken, setToken } from '@/utils/auth'
import { usePersonalCollection } from '@/views/practice/usePersonalCollection'

function response<T>(records: T[], total: number) {
  return { code: 0, data: { records, total } }
}

describe('usePersonalCollection', () => {
  afterEach(() => removeToken())

  it('ignores a late response from the previous authenticated session', async () => {
    let resolveFirst!: (value: ReturnType<typeof response<string>>) => void
    const fetchPage = vi
      .fn()
      .mockImplementationOnce(
        () => new Promise<ReturnType<typeof response<string>>>((resolve) => (resolveFirst = resolve)),
      )
      .mockResolvedValueOnce(response(['new-account'], 1))
    setToken('collection-a')
    const scope = effectScope()
    const collection = scope.run(() => usePersonalCollection(fetchPage))!

    const first = collection.load()
    setToken('collection-b')
    await nextTick()
    resolveFirst(response(['old-account'], 1))
    await first
    await Promise.resolve()

    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(collection.records.value).toEqual(['new-account'])
    scope.stop()
  })

  it('does not update collection state after its owning scope unmounts', async () => {
    let resolve!: (value: ReturnType<typeof response<string>>) => void
    const fetchPage = vi.fn().mockImplementation(() => new Promise((next) => (resolve = next)))
    setToken('collection-user')
    const scope = effectScope()
    const collection = scope.run(() => usePersonalCollection(fetchPage))!

    const pending = collection.load()
    scope.stop()
    resolve(response(['late'], 1))
    await pending

    expect(collection.records.value).toEqual([])
    expect(collection.total.value).toBeNull()
  })

  it('clears data on logout and does not fetch as an anonymous user', async () => {
    const fetchPage = vi.fn().mockResolvedValue(response(['private'], 1))
    setToken('collection-user')
    const scope = effectScope()
    const collection = scope.run(() => usePersonalCollection(fetchPage))!

    await collection.load()
    removeToken()
    await nextTick()

    expect(collection.records.value).toEqual([])
    expect(collection.total.value).toBeNull()
    expect(collection.loading.value).toBe(false)
    expect(fetchPage).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('returns from an emptied last page to the last available page', async () => {
    const fetchPage = vi
      .fn()
      .mockResolvedValueOnce(response<string>([], 10))
      .mockResolvedValueOnce(response(['remaining'], 10))
    setToken('collection-user')
    const scope = effectScope()
    const collection = scope.run(() => usePersonalCollection(fetchPage))!
    collection.page.value = 2

    await collection.load()

    expect(fetchPage.mock.calls.map(([page]) => page)).toEqual([2, 1])
    expect(collection.page.value).toBe(1)
    expect(collection.records.value).toEqual(['remaining'])
    scope.stop()
  })
})
