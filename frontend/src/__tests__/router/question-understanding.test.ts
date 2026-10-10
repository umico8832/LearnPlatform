import { describe, expect, it } from 'vitest'
import router, { createLearnerRoutes } from '@/router'

describe('question understanding route', () => {
  it('accepts only numeric IDs and requires learner authentication', () => {
    const question = router.resolve('/questions/123')
    expect(question.name).toBe('QuestionUnderstanding')
    expect(question.meta.requiresAuth).toBe(true)
    expect(router.resolve('/questions/abc').name).toBe('NotFound')
    expect(router.resolve('/questions/123abc').name).toBe('NotFound')
  })

  it('does not register DevVizEmbed in the development routes', () => {
    expect(createLearnerRoutes(true).map((route) => route.name)).not.toContain('DevVizEmbed')
  })
})
