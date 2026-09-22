interface AuthPreviewOption {
  value: string
  label: string
}

const options: Record<string, readonly AuthPreviewOption[]> = {
  Login: [{ value: 'default', label: '默认表单' }],
  Register: [
    { value: 'step-1', label: '第 1 步 · 账户信息' },
    { value: 'step-2', label: '第 2 步 · 邮箱验证' },
    { value: 'step-3', label: '第 3 步 · 设置密码' },
  ],
  ForgotPassword: [
    { value: 'form', label: '申请重置 · 填写邮箱' },
    { value: 'sent', label: '邮件已发送' },
    { value: 'resent', label: '邮件已重发' },
    { value: 'support', label: '联系支持 · 重发已达上限' },
  ],
  ResetPassword: [
    { value: 'checking', label: '验证链接 · 加载中' },
    { value: 'form', label: '设置新密码' },
    { value: 'success', label: '密码已重置' },
    { value: 'error', label: '链接失效' },
  ],
  OAuthCallback: [
    { value: 'loading', label: 'Google 登录 · 处理中' },
    { value: 'error', label: 'Google 登录 · 未完成' },
  ],
}

export function getAuthPreviewOptions(name: string): readonly AuthPreviewOption[] {
  return options[name] ?? []
}
