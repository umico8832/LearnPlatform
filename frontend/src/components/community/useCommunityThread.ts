import { computed, onUnmounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  getCommunityPost,
  getCommunityComments,
  getCommunityReviews,
  setCommunityLike,
  deleteCommunityPost,
  deleteCommunityComment,
  addCommunityComment,
  downloadCommunityAttachment,
  type CommunityPost,
  type CommunityComment,
  type CommunityAttachment,
  type CommunityReview,
} from '@/api/community'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

export const COMMENT_PAGE_SIZE = 20

interface CommunityThreadOptions {
  id: () => number
  /** Asks before deleting; resolves false when the user cancels. */
  confirm: (kind: 'post' | 'comment') => Promise<boolean>
  /** Moves focus to the reply field after choosing a comment to answer. */
  focusReply?: () => void
  onDeleted: () => void
  onChanged?: () => void
}

/**
 * One community post with its comments, likes, attachments and review history.
 * Every request is tied to the post and the login session it started under, so a
 * late answer after switching posts, accounts or leaving the page is dropped.
 * Shared by the learner detail page and the admin review drawer.
 */
export function useCommunityThread(options: CommunityThreadOptions) {
  const user = useUserStore()
  const post = ref<CommunityPost | null>(null),
    comments = ref<CommunityComment[]>([]),
    reviews = ref<CommunityReview[]>([])
  const loading = ref(true),
    busy = ref(false),
    loadError = ref(''),
    actionError = ref(''),
    commentsError = ref(''),
    commentsLoading = ref(false),
    reply = ref('')
  const downloading = ref<number | null>(null),
    replyTo = ref<CommunityComment | null>(null)
  const commentPage = ref(1),
    commentTotal = ref(0)
  const canManage = computed(() => post.value?.userId === user.userInfo?.id || user.userInfo?.role === 'ADMIN')
  let generation = 0,
    commentGeneration = 0
  let alive = true
  let downloadController: AbortController | undefined
  function current(ticket: number, session: number) {
    return alive && ticket === generation && session === getAuthSessionVersion() && isAuthenticated()
  }
  async function loadComments() {
    const ticket = ++commentGeneration,
      id = options.id(),
      session = getAuthSessionVersion()
    commentsLoading.value = true
    commentsError.value = ''
    try {
      const res = await getCommunityComments(id, commentPage.value, { errorDisplay: 'inline' })
      if (ticket === commentGeneration && current(generation, session) && id === options.id()) {
        comments.value = res.data.records
        commentTotal.value = res.data.total
      }
    } catch (e) {
      if (ticket === commentGeneration && current(generation, session))
        commentsError.value = errorMessage(e, '回复加载失败')
    } finally {
      if (ticket === commentGeneration && current(generation, session)) commentsLoading.value = false
    }
  }
  async function load() {
    const ticket = ++generation,
      id = options.id(),
      session = getAuthSessionVersion()
    loading.value = true
    loadError.value = ''
    reviews.value = []
    try {
      const result = await getCommunityPost(id, { errorDisplay: 'inline' })
      if (!current(ticket, session)) return
      post.value = result.data
      await loadComments()
      if (!current(ticket, session)) return
      if (canManage.value) {
        const history = await getCommunityReviews(id, { errorDisplay: 'inline' })
        if (!current(ticket, session) || id !== options.id()) return
        if (current(ticket, session)) reviews.value = history.data
      }
    } catch (e) {
      if (current(ticket, session)) loadError.value = errorMessage(e, '内容加载失败或已不可访问')
    } finally {
      if (current(ticket, session)) loading.value = false
    }
  }
  async function action(fn: () => Promise<unknown>) {
    if (busy.value) return false
    const actionGeneration = generation
    const session = getAuthSessionVersion()
    const id = options.id()
    busy.value = true
    actionError.value = ''
    try {
      await fn()
      if (!current(actionGeneration, session) || id !== options.id()) return false
      await load()
      if (!current(generation, session) || id !== options.id()) return false
      options.onChanged?.()
      return true
    } catch (e) {
      if (current(actionGeneration, session) && id === options.id())
        actionError.value = errorMessage(e, '操作失败，请重试')
      return false
    } finally {
      if (current(generation, session) && id === options.id()) busy.value = false
    }
  }
  async function like(comment?: CommunityComment) {
    if (post.value)
      await action(() => setCommunityLike(options.id(), !(comment?.liked ?? post.value!.liked), comment?.id))
  }
  async function submitReply() {
    if (!reply.value.trim()) return
    const body = reply.value
    const parentId = replyTo.value?.id
    const submitted = await action(() => addCommunityComment(options.id(), body, parentId, { errorDisplay: 'inline' }))
    if (submitted && reply.value === body && replyTo.value?.id === parentId) {
      reply.value = ''
      replyTo.value = null
      commentPage.value = Math.max(1, Math.ceil(commentTotal.value / COMMENT_PAGE_SIZE))
      await loadComments()
    }
  }
  function beginReply(comment: CommunityComment) {
    replyTo.value = comment
    options.focusReply?.()
  }
  async function removeComment(comment: CommunityComment) {
    if (busy.value) return
    const id = options.id()
    const actionGeneration = generation
    const session = getAuthSessionVersion()
    busy.value = true
    const confirmed = await options.confirm('comment')
    if (!confirmed) {
      if (current(actionGeneration, session)) busy.value = false
      return
    }
    if (!current(actionGeneration, session) || id !== options.id()) return
    busy.value = false
    await action(() => deleteCommunityComment(comment.id, { errorDisplay: 'inline' }))
  }
  async function removePost() {
    if (busy.value) return
    const id = options.id()
    const actionGeneration = generation
    const session = getAuthSessionVersion()
    busy.value = true
    const confirmed = await options.confirm('post')
    if (!confirmed) {
      if (current(actionGeneration, session)) busy.value = false
      return
    }
    if (!current(actionGeneration, session) || id !== options.id()) return
    try {
      await deleteCommunityPost(id, { errorDisplay: 'inline' })
      if (current(actionGeneration, session) && id === options.id()) options.onDeleted()
    } catch (e) {
      if (current(actionGeneration, session) && id === options.id()) actionError.value = errorMessage(e, '删除失败')
    } finally {
      if (current(actionGeneration, session) && id === options.id()) busy.value = false
    }
  }
  async function download(file: CommunityAttachment) {
    const ticket = generation
    const session = getAuthSessionVersion()
    const controller = new AbortController()
    downloadController?.abort()
    downloadController = controller
    downloading.value = file.id
    try {
      await downloadCommunityAttachment(file, { errorDisplay: 'inline', signal: controller.signal })
    } catch (e) {
      if (current(ticket, session) && !controller.signal.aborted) actionError.value = errorMessage(e, '下载失败')
    } finally {
      if (current(ticket, session) && downloadController === controller) downloading.value = null
    }
  }
  function pageComments(page: number) {
    commentPage.value = page
    void loadComments()
  }
  watch(
    options.id,
    () => {
      downloadController?.abort()
      reply.value = ''
      replyTo.value = null
      post.value = null
      comments.value = []
      reviews.value = []
      busy.value = false
      downloading.value = null
      actionError.value = ''
      commentsError.value = ''
      loadError.value = ''
      commentPage.value = 1
      void load()
    },
    { immediate: true },
  )
  const unsubscribeAuth = onAuthSessionChange(() => {
    generation++
    commentGeneration++
    downloadController?.abort()
    post.value = null
    comments.value = []
    reviews.value = []
    reply.value = ''
    replyTo.value = null
    busy.value = false
    downloading.value = null
    actionError.value = ''
    commentsError.value = ''
    loadError.value = ''
    commentPage.value = 1
    commentsLoading.value = false
    if (isAuthenticated()) void load()
  })
  onUnmounted(() => {
    alive = false
    generation++
    commentGeneration++
    downloadController?.abort()
    unsubscribeAuth()
  })
  return {
    post,
    comments,
    reviews,
    loading,
    busy,
    loadError,
    actionError,
    commentsError,
    commentsLoading,
    reply,
    replyTo,
    downloading,
    commentPage,
    commentTotal,
    canManage,
    load,
    loadComments,
    like,
    submitReply,
    beginReply,
    removeComment,
    removePost,
    download,
    pageComments,
  }
}

export function formatBytes(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
