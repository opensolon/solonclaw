import { request } from '../client'

export interface McpServer {
  server_id: string
  name: string
  transport: string
  endpoint?: string
  command?: string
  args?: unknown
  auth?: unknown
  status: string
  tools?: unknown
  last_error?: string
  enabled: boolean
  created_at: number
  updated_at: number
  last_checked_at: number
}

export async function fetchMcpServers(): Promise<{ enabled: boolean; servers: McpServer[] }> {
  return request('/api/hermes/mcp')
}

export async function saveMcpServer(data: Record<string, unknown>) {
  return request('/api/hermes/mcp', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function checkMcpServer(serverId: string) {
  return request(`/api/hermes/mcp/${serverId}/check`, { method: 'POST' })
}

export async function deleteMcpServer(serverId: string) {
  return request(`/api/hermes/mcp/${serverId}`, { method: 'DELETE' })
}
