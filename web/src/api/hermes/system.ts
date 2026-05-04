import { request } from '../client'

export interface HealthResponse {
  status: string
  version?: string
  webui_version?: string
  webui_latest?: string
  webui_update_available?: boolean
  node_version?: string
}

export interface ModelInfo {
  id: string
  label: string
}

export interface ModelGroup {
  provider: string
  models: ModelInfo[]
}

export interface ConfigModelsResponse {
  default: string
  groups: ModelGroup[]
}

export interface ProviderRecord {
  providerKey: string
  name: string
  baseUrl: string
  defaultModel: string
  dialect: string
  hasApiKey: boolean
  isDefault: boolean
}

export interface FallbackProvider {
  provider: string
  model: string
}

export interface AvailableModelGroup {
  provider: string
  providerKey: string
  label: string
  base_url: string
  models: string[]
  dialect: string
  has_api_key: boolean
  isDefault: boolean
}

export interface AvailableModelsResponse {
  default: string
  default_provider: string
  groups: AvailableModelGroup[]
  allProviders: AvailableModelGroup[]
  fallbackProviders: FallbackProvider[]
}

export interface CustomProvider {
  providerKey: string
  name: string
  baseUrl: string
  apiKey?: string
  defaultModel: string
  dialect: string
}

interface DashboardStatus {
  version?: string
  update_available?: boolean
}

interface ProvidersPayload {
  providers: ProviderRecord[]
  defaultProviderKey: string
  defaultModel: string
  fallbackProviders: FallbackProvider[]
}

export interface DashboardModelInfo {
  model: string
  provider: string
  providerKey: string
  providerLabel: string
  dialect: string
  baseUrl: string
  fallbackProviders: FallbackProvider[]
  auto_context_length?: number
  config_context_length?: number
  effective_context_length?: number
}

export async function checkHealth(): Promise<HealthResponse> {
  const [health, status] = await Promise.all([
    request<{ ok?: boolean; service?: string }>('/health'),
    request<DashboardStatus>('/api/status'),
  ])

  return {
    status: health.ok ? 'ok' : 'error',
    version: status.version,
    webui_version: status.version,
    webui_latest: status.version,
    webui_update_available: !!status.update_available,
    node_version: '',
  }
}

export async function triggerUpdate(): Promise<{ success: boolean; message: string }> {
  return {
    success: false,
    message: '当前后端未开放在线更新',
  }
}

function toGroup(provider: ProviderRecord, defaultModel: string): AvailableModelGroup {
  const model = provider.defaultModel || defaultModel || ''
  return {
    provider: provider.providerKey,
    providerKey: provider.providerKey,
    label: provider.name || provider.providerKey,
    base_url: provider.baseUrl,
    models: model ? [model] : [],
    dialect: provider.dialect,
    has_api_key: provider.hasApiKey,
    isDefault: provider.isDefault,
  }
}

export async function fetchConfigModels(): Promise<ConfigModelsResponse> {
  const payload = await request<ProvidersPayload>('/api/providers')
  return {
    default: payload.defaultModel || '',
    groups: payload.providers.map(p => ({
      provider: p.providerKey,
      models: (p.defaultModel ? [p.defaultModel] : []).map(model => ({ id: model, label: model })),
    })),
  }
}

export async function fetchAvailableModels(): Promise<AvailableModelsResponse> {
  const payload = await request<ProvidersPayload>('/api/providers')
  const groups = payload.providers.map(p => toGroup(p, payload.defaultModel))
  return {
    default: payload.defaultModel || '',
    default_provider: payload.defaultProviderKey || '',
    groups,
    allProviders: groups,
    fallbackProviders: payload.fallbackProviders || [],
  }
}

export async function fetchModelInfo(): Promise<DashboardModelInfo> {
  return request<DashboardModelInfo>('/api/model/info')
}

export async function updateDefaultModel(data: {
  default: string
  provider?: string
}): Promise<void> {
  await request('/api/model/default', {
    method: 'PUT',
    body: JSON.stringify({
      providerKey: data.provider || '',
      model: data.default,
    }),
  })
}

export async function addCustomProvider(data: CustomProvider): Promise<void> {
  await request('/api/providers', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function fetchProviderModels(data: {
  providerKey?: string
  baseUrl: string
  apiKey?: string
  dialect: string
}): Promise<{ url: string; models: string[] }> {
  return request<{ url: string; models: string[] }>('/api/providers/models', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function removeCustomProvider(name: string): Promise<void> {
  await request(`/api/providers/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  })
}

export async function updateProvider(poolKey: string, data: {
  name?: string
  baseUrl?: string
  apiKey?: string
  defaultModel?: string
  dialect?: string
}): Promise<void> {
  await request(`/api/providers/${encodeURIComponent(poolKey)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function updateFallbackProviders(fallbackProviders: FallbackProvider[]): Promise<void> {
  await request('/api/model/fallbacks', {
    method: 'PUT',
    body: JSON.stringify({ fallbackProviders }),
  })
}
