import router from '@/router'

declare global {
  interface Window {
    __APP_SESSION_TOKEN__?: string
    __LOGIN_TOKEN__?: string
  }
}

const DEFAULT_BASE_URL = ''
const TOKEN_KEY = 'hermes_api_key'

function getBaseUrl(): string {
  return localStorage.getItem('hermes_server_url') || DEFAULT_BASE_URL
}

function getInjectedToken(): string {
  return window.__LOGIN_TOKEN__ || window.__APP_SESSION_TOKEN__ || ''
}

export function getApiKey(): string {
  return localStorage.getItem(TOKEN_KEY) || getInjectedToken()
}

export function setServerUrl(url: string) {
  localStorage.setItem('hermes_server_url', url)
}

export function setApiKey(key: string) {
  localStorage.setItem(TOKEN_KEY, key)
}

export function clearApiKey() {
  localStorage.removeItem(TOKEN_KEY)
}

export function hasApiKey(): boolean {
  return !!getApiKey()
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const base = getBaseUrl()
  const url = `${base}${path}`
  const headers = new Headers(options.headers || {})

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const apiKey = getApiKey()
  if (apiKey && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${apiKey}`)
  }

  const res = await fetch(url, { ...options, headers })

  if (res.status === 401) {
    clearApiKey()
    if (router.currentRoute.value.name !== 'login') {
      router.replace({ name: 'login' })
    }
    throw new Error('Unauthorized')
  }

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`API Error ${res.status}: ${text || res.statusText}`)
  }

  const contentType = res.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const json = await res.json()
    if (json && typeof json === 'object' && 'success' in json && 'data' in json) {
      return json.data as T
    }
    return json
  }

  return (await res.text()) as T
}

export function getBaseUrlValue(): string {
  return getBaseUrl()
}
