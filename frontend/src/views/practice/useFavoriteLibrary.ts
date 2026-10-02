import { onScopeDispose, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFavorites, removeFavorite, type FavoriteQuestionVO } from '@/api/favorite'
import { getFavoritePractice } from '@/api/practice'
import { useUserStore } from '@/stores/user'
import { onAuthSessionChange } from '@/utils/auth'
import { savePracticeSession } from '@/utils/practiceSession'
import { usePersonalCollection } from './usePersonalCollection'

export function useFavoriteLibrary() {
  const router = useRouter()
  const user = useUserStore()
  const collection = usePersonalCollection<FavoriteQuestionVO>((pageNum, pageSize) =>
    getFavorites({ pageNum, pageSize }, { errorDisplay: 'inline' }),
  )
  const practiceCount = ref(10)
  const startingId = ref<number | 'all' | null>(null)
  const removingId = ref<number | null>(null)
  const actionError = ref('')
  const notice = ref('')

  async function startPractice(questionId?: number) {
    if (startingId.value !== null || removingId.value !== null) return
    if (
      !questionId &&
      (!Number.isInteger(practiceCount.value) || practiceCount.value < 1 || practiceCount.value > 50)
    ) {
      actionError.value = '请输入 1 到 50 之间的题目数。'
      return
    }
    const userId = user.userInfo?.id
    if (!userId) return
    const current = collection.captureSession()
    startingId.value = questionId ?? 'all'
    actionError.value = ''
    notice.value = ''
    try {
      const result = await getFavoritePractice(
        { questionId, count: questionId ? 1 : practiceCount.value },
        { errorDisplay: 'inline' },
      )
      if (!current() || userId !== user.userInfo?.id) return
      if (result.code !== 0 || !result.data) throw new Error('Missing practice')
      if (!result.data.length) {
        notice.value = questionId ? '这道题暂不可练习，可先浏览其他收藏。' : '收藏中暂时没有可练习的题目。'
        return
      }
      if (savePracticeSession(userId, result.data, 'favorite')) await router.push({ name: 'PracticeSession' })
    } catch {
      if (current()) actionError.value = '练习暂时无法开始，请重试。'
    } finally {
      if (current()) startingId.value = null
    }
  }

  async function remove(questionId: number) {
    if (removingId.value !== null || startingId.value !== null) return
    const current = collection.captureSession()
    removingId.value = questionId
    actionError.value = ''
    notice.value = ''
    try {
      await removeFavorite(questionId, { errorDisplay: 'inline' })
      if (!current()) return
      notice.value = '已取消收藏。'
      await collection.load()
    } catch {
      if (current()) actionError.value = '取消收藏失败，题目仍保留在列表中。'
    } finally {
      if (current()) removingId.value = null
    }
  }

  onScopeDispose(
    onAuthSessionChange(() => {
      startingId.value = null
      removingId.value = null
      actionError.value = ''
      notice.value = ''
    }),
  )
  return { ...collection, practiceCount, startingId, removingId, actionError, notice, startPractice, remove }
}
