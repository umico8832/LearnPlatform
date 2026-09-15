import { describe, expect, it } from 'vitest'
import { splitSearchMatch } from '@/components/search/searchText'

describe('splitSearchMatch', () => {
  it('preserves untrusted text as text segments and only marks matching fragments', () => {
    expect(splitSearchMatch('<img src=x onerror=alert(1)> 数据结构', '数据')).toEqual([
      { text: '<img src=x onerror=alert(1)> ', match: false },
      { text: '数据', match: true },
      { text: '结构', match: false },
    ])
  })
})
