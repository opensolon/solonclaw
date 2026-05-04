import { request } from '../client'

export interface DisplayConfig {
  compact?: boolean
  personality?: string
  resume_display?: string
  busy_input_mode?: string
  bell_on_complete?: boolean
  show_reasoning?: boolean
  streaming?: boolean
  inline_diffs?: boolean
  skin?: string
}

export interface AgentConfig {
  max_turns?: number
  gateway_timeout?: number
  restart_drain_timeout?: number
  service_tier?: string
  tool_use_enforcement?: string
}

export interface MemoryConfig {
  memory_enabled?: boolean
  user_profile_enabled?: boolean
  memory_char_limit?: number
  user_char_limit?: number
}

export interface SessionResetConfig {
  mode?: string
  idle_minutes?: number
  at_hour?: number
}

export interface PrivacyConfig {
  redact_pii?: boolean
}

export interface AppConfig {
  display?: DisplayConfig
  agent?: AgentConfig
  memory?: MemoryConfig
  session_reset?: SessionResetConfig
  privacy?: PrivacyConfig
  wecom?: Record<string, any>
  feishu?: Record<string, any>
  dingtalk?: Record<string, any>
  weixin?: Record<string, any>
  platforms?: Record<string, any>
  [key: string]: any
}

interface RuntimeConfigInfo {
  is_set: boolean
  redacted_value?: string | null
}

function configPreview(config: Record<string, RuntimeConfigInfo>, key: string): string {
  const item = config[key]
  if (!item || !item.is_set) return ''
  return item.redacted_value || '已设置'
}

function configBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value !== 'string') return false
  const normalized = value.trim().toLowerCase()
  return normalized === 'true' || normalized === '1' || normalized === 'yes' || normalized === 'on'
}


export async function fetchRuntimeConfigItems(): Promise<Record<string, RuntimeConfigInfo>> {
  return request<Record<string, RuntimeConfigInfo>>('/api/runtime-config')
}

export async function setRuntimeConfigItem(key: string, value: string): Promise<void> {
  const text = (value || '').trim()
  if (!text) {
    await request(`/api/runtime-config?key=${encodeURIComponent(key)}`, { method: 'DELETE' })
    return
  }
  await request('/api/runtime-config', {
    method: 'PUT',
    body: JSON.stringify({ key, value: text }),
  })
}

export async function revealRuntimeConfigItem(key: string): Promise<string> {
  const data = await request<{ value: string }>('/api/runtime-config/reveal', {
    method: 'POST',
    body: JSON.stringify({ key }),
  })
  return data.value || ''
}

export async function fetchConfig(_sections?: string[]): Promise<AppConfig> {
  const [data, runtimeConfig] = await Promise.all([
    request<Record<string, any>>('/api/config'),
    request<Record<string, RuntimeConfigInfo>>('/api/runtime-config'),
  ])
  return {
    display: {
      show_reasoning: !!data.display?.showReasoning,
      streaming: !!data.llm?.stream,
    },
    agent: {
      max_turns: data.react?.maxSteps,
    },
    memory: {
      memory_enabled: true,
      user_profile_enabled: true,
    },
    session_reset: {
      mode: data.scheduler?.enabled ? 'manual' : 'off',
    },
    privacy: {
      redact_pii: false,
    },
    wecom: data.channels?.wecom || {},
    feishu: data.channels?.feishu || {},
    dingtalk: data.channels?.dingtalk || {},
    weixin: data.channels?.weixin || {},
    platforms: {
      feishu: {
        enabled: configBoolean(data.channels?.feishu?.enabled),
        extra: {
          app_id: configPreview(runtimeConfig, 'solonclaw.channels.feishu.appId'),
          app_secret: configPreview(runtimeConfig, 'solonclaw.channels.feishu.appSecret'),
        },
      },
      dingtalk: {
        enabled: configBoolean(data.channels?.dingtalk?.enabled),
        extra: {
          client_id: configPreview(runtimeConfig, 'solonclaw.channels.dingtalk.clientId'),
          client_secret: configPreview(runtimeConfig, 'solonclaw.channels.dingtalk.clientSecret'),
          robot_code: configPreview(runtimeConfig, 'solonclaw.channels.dingtalk.robotCode'),
        },
      },
      wecom: {
        enabled: configBoolean(data.channels?.wecom?.enabled),
        extra: {
          bot_id: configPreview(runtimeConfig, 'solonclaw.channels.wecom.botId'),
          secret: configPreview(runtimeConfig, 'solonclaw.channels.wecom.secret'),
        },
      },
      weixin: {
        enabled: configBoolean(data.channels?.weixin?.enabled),
        token: configPreview(runtimeConfig, 'solonclaw.channels.weixin.token'),
        extra: {
          account_id: configPreview(runtimeConfig, 'solonclaw.channels.weixin.accountId'),
        },
      },
    },
  }
}

export async function updateConfigSection(
  section: string,
  values: Record<string, any>,
): Promise<void> {
  const current = await request<Record<string, any>>('/api/config')
  const next = { ...current }

  if (section === 'display') {
    next.display = {
      ...(next.display || {}),
      showReasoning: values.show_reasoning ?? next.display?.showReasoning,
    }
    next.llm = {
      ...(next.llm || {}),
      stream: values.streaming ?? next.llm?.stream,
    }
  } else if (section === 'agent') {
    next.react = {
      ...(next.react || {}),
      maxSteps: values.max_turns ?? next.react?.maxSteps,
    }
  } else if (section === 'wecom' || section === 'feishu' || section === 'dingtalk' || section === 'weixin') {
    next.channels = {
      ...(next.channels || {}),
      [section]: {
        ...(next.channels?.[section] || {}),
        ...values,
      },
    }
  } else {
    return
  }

  await request('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ config: next }),
  })
}

export async function saveCredentials(
  platform: string,
  values: Record<string, any>,
): Promise<void> {
  const entries: Array<{ key: string; value: string | boolean | null | undefined }> = []

  if (platform === 'feishu') {
    if ('enabled' in values) entries.push({ key: 'solonclaw.channels.feishu.enabled', value: values.enabled })
    if (values.extra?.app_id !== undefined) entries.push({ key: 'solonclaw.channels.feishu.appId', value: values.extra.app_id })
    if (values.extra?.app_secret !== undefined) entries.push({ key: 'solonclaw.channels.feishu.appSecret', value: values.extra.app_secret })
  }

  if (platform === 'dingtalk') {
    if ('enabled' in values) entries.push({ key: 'solonclaw.channels.dingtalk.enabled', value: values.enabled })
    if (values.extra?.client_id !== undefined) entries.push({ key: 'solonclaw.channels.dingtalk.clientId', value: values.extra.client_id })
    if (values.extra?.client_secret !== undefined) entries.push({ key: 'solonclaw.channels.dingtalk.clientSecret', value: values.extra.client_secret })
    if (values.extra?.robot_code !== undefined) entries.push({ key: 'solonclaw.channels.dingtalk.robotCode', value: values.extra.robot_code })
  }

  if (platform === 'wecom') {
    if ('enabled' in values) entries.push({ key: 'solonclaw.channels.wecom.enabled', value: values.enabled })
    if (values.extra?.bot_id !== undefined) entries.push({ key: 'solonclaw.channels.wecom.botId', value: values.extra.bot_id })
    if (values.extra?.secret !== undefined) entries.push({ key: 'solonclaw.channels.wecom.secret', value: values.extra.secret })
  }

  if (platform === 'weixin') {
    if ('enabled' in values) entries.push({ key: 'solonclaw.channels.weixin.enabled', value: values.enabled })
    if (values.token !== undefined) entries.push({ key: 'solonclaw.channels.weixin.token', value: values.token })
    if (values.extra?.account_id !== undefined) entries.push({ key: 'solonclaw.channels.weixin.accountId', value: values.extra.account_id })
  }

  for (const entry of entries) {
    const raw = entry.value
    const text = typeof raw === 'boolean' ? String(raw) : (raw ?? '').toString().trim()
    if (!text) {
      await request(`/api/runtime-config?key=${encodeURIComponent(entry.key)}`, {
        method: 'DELETE',
      })
    } else {
      await request('/api/runtime-config', {
        method: 'PUT',
        body: JSON.stringify({ key: entry.key, value: text }),
      })
    }
  }
}

export interface WeixinQrCode {
  qrcode: string
  qrcode_url: string
}

export interface WeixinQrStatus {
  status: 'wait' | 'scaned' | 'scaned_but_redirect' | 'expired' | 'confirmed' | 'error'
  qrcode?: string
  qrcode_url?: string
  message?: string
  error_message?: string
  account_id?: string
  base_url?: string
}

export async function fetchWeixinQrCode(): Promise<WeixinQrCode> {
  const res = await request<any>('/api/gateway/setup/weixin/qr', { method: 'POST' })
  return {
    qrcode: res.ticket || '',
    qrcode_url: res.qr_image_url || res.qr_code || '',
  }
}

export async function pollWeixinQrStatus(qrcode: string): Promise<WeixinQrStatus> {
  const res = await request<any>(`/api/gateway/setup/weixin/qr/${encodeURIComponent(qrcode)}`)
  const statusMap: Record<string, WeixinQrStatus['status']> = {
    pending: 'wait',
    scanned: 'scaned',
    confirmed: 'confirmed',
    failed: res.error_code === 'qr_expired' || res.error_code === 'qr_timeout' ? 'expired' : 'error',
  }
  return {
    status: statusMap[res.status] || 'wait',
    qrcode: res.ticket || '',
    qrcode_url: res.qr_image_url || res.qr_code || '',
    message: res.message || '',
    error_message: res.error_message || '',
    account_id: res.account_id,
    base_url: res.base_url,
  }
}

export async function saveWeixinCredentials(_data: {
  account_id: string
  token: string
  base_url?: string
}): Promise<void> {
  throw new Error('当前后端未开放微信凭证保存接口')
}
