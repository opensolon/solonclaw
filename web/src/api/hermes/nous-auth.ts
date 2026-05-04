export interface NousStartResult {
  session_id: string
  user_code: string
  verification_url: string
  expires_in: number
}

export interface NousPollResult {
  status: 'pending' | 'approved' | 'denied' | 'expired' | 'error'
  error: string | null
}

export interface NousStatusResult {
  authenticated: boolean
}

export async function startNousLogin(): Promise<NousStartResult> {
  throw new Error('当前后端未开放 Nous 登录')
}

export async function pollNousLogin(_sessionId: string): Promise<NousPollResult> {
  return { status: 'error', error: '当前后端未开放 Nous 登录' }
}

export async function getNousAuthStatus(): Promise<NousStatusResult> {
  return { authenticated: false }
}
