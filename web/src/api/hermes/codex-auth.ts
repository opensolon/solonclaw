export interface CodexStartResult {
  session_id: string
  user_code: string
  verification_url: string
  expires_in: number
}

export interface CodexPollResult {
  status: 'pending' | 'approved' | 'expired' | 'error'
  error: string | null
}

export interface CodexStatusResult {
  authenticated: boolean
  last_refresh?: string
}

export async function startCodexLogin(): Promise<CodexStartResult> {
  throw new Error('当前后端未开放 Codex 登录')
}

export async function pollCodexLogin(_sessionId: string): Promise<CodexPollResult> {
  return { status: 'error', error: '当前后端未开放 Codex 登录' }
}

export async function getCodexAuthStatus(): Promise<CodexStatusResult> {
  return { authenticated: false }
}
