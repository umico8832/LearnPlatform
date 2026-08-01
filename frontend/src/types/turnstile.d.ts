type TurnstileTheme = 'auto' | 'light' | 'dark'

interface Window {
  turnstile?: {
    render: (container: HTMLElement, options: Record<string, unknown>) => string
    reset: (widgetId?: string) => void
    remove: (widgetId: string) => void
  }
}

interface ImportMetaEnv {
  readonly VITE_TURNSTILE_SITE_KEY?: string
}
