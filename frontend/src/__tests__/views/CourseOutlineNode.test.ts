import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CourseOutlineNode from '@/views/course/CourseOutlineNode.vue'
import type { KnowledgePointVO } from '@/api/knowledgePoint'
const node = {
  id: 1,
  name: '章节',
  contentKey: null,
  children: [{ id: 2, name: '已审核课节', contentKey: 'lesson', contentReviewStatus: 'REVIEWED', children: [] }],
} as unknown as KnowledgePointVO

describe('CourseOutlineNode', () => {
  it('uses a keyboard reachable native disclosure and forwards the nested lesson ID', async () => {
    const wrapper = mount(CourseOutlineNode, { props: { node, canStudy: true } })
    expect(wrapper.get('summary').text()).toContain('章节')
    expect(wrapper.get('details').attributes('open')).toBeUndefined()
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('study')).toEqual([[2]])
  })
  it('keeps nested study actions gated by course membership', () => {
    const wrapper = mount(CourseOutlineNode, { props: { node, canStudy: false } })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
