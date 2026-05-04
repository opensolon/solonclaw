import { request } from '../client'

export interface ChannelMedia {
  media_id: string
  platform: string
  chat_id?: string
  message_id?: string
  kind?: string
  original_name?: string
  mime_type?: string
  local_path?: string
  remote_id?: string
  status: string
  error?: string
  size_bytes: number
  created_at: number
  updated_at: number
  expires_at: number
}

export async function fetchMedia(platform = '', limit = 50): Promise<ChannelMedia[]> {
  const suffix = platform ? `&platform=${encodeURIComponent(platform)}` : ''
  const res = await request<{ media: ChannelMedia[] }>(`/api/hermes/media?limit=${limit}${suffix}`)
  return res.media || []
}

export async function indexMedia(data: Record<string, unknown>) {
  return request('/api/hermes/media/index', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
