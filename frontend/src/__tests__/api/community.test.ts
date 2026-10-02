import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
import request from '@/utils/request'
import { downloadCommunityAttachment } from '@/api/community'

describe('community attachment download', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() })
  })

  it('does not create or click an anchor when its signal is already cancelled', async () => {
    const controller = new AbortController()
    controller.abort()
    vi.mocked(request.get).mockResolvedValue({ data: new Blob(['private']) })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click')

    await downloadCommunityAttachment({ id: 1, name: 'private.txt', sizeBytes: 1 }, { signal: controller.signal })

    expect(click).not.toHaveBeenCalled()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
})
