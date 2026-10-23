export function createSubmissionIntent<T extends object>() {
  let pending: { key: string; body: T; fingerprint: string } | null = null
  return {
    prepare(body: T, identity: unknown = body) {
      const fingerprint = JSON.stringify(identity)
      if (!pending || pending.fingerprint !== fingerprint) {
        pending = { key: crypto.randomUUID(), body: structuredClone(body), fingerprint }
      }
      return { key: pending.key, body: pending.body }
    },
    reset() { pending = null },
  }
}
