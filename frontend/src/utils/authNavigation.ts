type AuthRedirectHandler = (redirect?: string) => void

let redirectHandler: AuthRedirectHandler | null = null

export function setAuthRedirectHandler(handler: AuthRedirectHandler) {
  redirectHandler = handler
}

export function redirectToLogin(redirect?: string) {
  if (redirectHandler) {
    redirectHandler(redirect)
    return
  }
  window.location.assign('/login')
}
