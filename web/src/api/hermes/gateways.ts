import { request } from '../client'

export interface GatewayStatus {
  profile: string
  port: number
  host: string
  url: string
  running: boolean
  pid?: number
}

interface DashboardStatusResponse {
  gateway_pid?: number | null
  gateway_platforms?: Record<string, {
    state: string
    connection_mode?: string | null
    detail?: string | null
  }>
}

export async function fetchGateways(): Promise<GatewayStatus[]> {
  const res = await request<DashboardStatusResponse>('/api/status')
  return Object.entries(res.gateway_platforms || {}).map(([name, value]) => ({
    profile: name,
    port: 8080,
    host: value.connection_mode || 'local',
    url: '',
    running: value.state === 'connected',
    pid: res.gateway_pid || undefined,
  }))
}

export async function startGateway(_name: string): Promise<GatewayStatus> {
  throw new Error('当前后端未开放 dashboard 网关启停能力')
}

export async function stopGateway(_name: string): Promise<void> {
  throw new Error('当前后端未开放 dashboard 网关启停能力')
}

export async function checkGatewayHealth(name: string): Promise<GatewayStatus> {
  const gateways = await fetchGateways()
  const gateway = gateways.find((item) => item.profile === name)
  if (!gateway) throw new Error('Gateway not found')
  return gateway
}
