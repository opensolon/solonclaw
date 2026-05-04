import { request } from '../client'

export interface SessionSummary {
  id: string
  source: string
  model: string
  title: string | null
  preview?: string
  started_at: number
  ended_at: number | null
  last_active?: number
  message_count: number
  tool_call_count: number
  input_tokens: number
  output_tokens: number
  cache_read_tokens: number
  cache_write_tokens: number
  reasoning_tokens: number
  provider: string | null
  parent_session_id?: string | null
  branch_name?: string | null
  compressed_summary?: string | null
  last_compression_at?: number
  last_compression_input_tokens?: number
  compression_failure_count?: number
  active_agent_name?: string | null
}

export interface SessionDetail extends SessionSummary {
  messages: HermesMessage[]
}

export interface SessionSearchResult extends SessionSummary {
  matched_message_id: number | null
  snippet: string
  rank: number
}

export interface HermesMessage {
  id: number
  session_id: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  tool_call_id: string | null
  tool_calls: any[] | null
  tool_name: string | null
  timestamp: number
  token_count: number | null
  finish_reason: string | null
  reasoning: string | null
}

interface DashboardSessionSummary {
  id: string
  source: string | null
  model: string | null
  provider?: string | null
  title: string | null
  started_at: number
  ended_at: number | null
  last_active: number
  message_count: number
  tool_call_count: number
  input_tokens: number
  output_tokens: number
  reasoning_tokens?: number
  cache_read_tokens?: number
  cache_write_tokens?: number
  total_tokens?: number
  preview: string | null
  parent_session_id?: string | null
  branch_name?: string | null
  compressed_summary?: string | null
  last_compression_at?: number
  last_compression_input_tokens?: number
  compression_failure_count?: number
  active_agent_name?: string | null
}

interface DashboardSessionDetail {
  session_id: string
  model?: string | null
  provider?: string | null
  input_tokens: number
  output_tokens: number
  reasoning_tokens: number
  cache_read_tokens: number
  cache_write_tokens: number
  total_tokens: number
  last_total_tokens: number
  last_usage_at: number
  compressed_summary?: string | null
  last_compression_at?: number
  last_compression_input_tokens?: number
  compression_failure_count?: number
  active_agent_name?: string | null
  parent_session_id?: string | null
  branch_name?: string | null
  messages: Array<{
    role: 'user' | 'assistant' | 'system' | 'tool'
    content: string | null
    reasoning?: string | null
    tool_calls?: Array<{
      id: string
      function: { name: string; arguments: string }
    }>
    tool_name?: string
    tool_call_id?: string
    timestamp?: number
  }>
}

function mapSummary(s: DashboardSessionSummary): SessionSummary {
  return {
    id: s.id,
    source: s.source || 'local',
    model: s.model || '',
    title: s.title,
    preview: s.preview || '',
    started_at: s.started_at,
    ended_at: s.ended_at,
    last_active: s.last_active,
    message_count: s.message_count,
    tool_call_count: s.tool_call_count,
    input_tokens: s.input_tokens,
    output_tokens: s.output_tokens,
    cache_read_tokens: s.cache_read_tokens || 0,
    cache_write_tokens: s.cache_write_tokens || 0,
    reasoning_tokens: s.reasoning_tokens || 0,
    provider: s.provider || null,
    parent_session_id: s.parent_session_id || null,
    branch_name: s.branch_name || null,
    compressed_summary: s.compressed_summary || null,
    last_compression_at: s.last_compression_at || 0,
    last_compression_input_tokens: s.last_compression_input_tokens || 0,
    compression_failure_count: s.compression_failure_count || 0,
    active_agent_name: s.active_agent_name || 'default',
  }
}

function mapMessages(sessionId: string, messages: DashboardSessionDetail['messages']): HermesMessage[] {
  return messages.map((msg, index) => ({
    id: index + 1,
    session_id: sessionId,
    role: msg.role,
    content: msg.content || '',
    tool_call_id: msg.tool_call_id || null,
    tool_calls: msg.tool_calls || null,
    tool_name: msg.tool_name || null,
    timestamp: msg.timestamp || 0,
    token_count: null,
    finish_reason: null,
    reasoning: msg.reasoning || null,
  }))
}

export async function fetchSessions(source?: string, limit?: number): Promise<SessionSummary[]> {
  const params = new URLSearchParams()
  params.set('limit', String(limit || 200))
  params.set('offset', '0')
  const res = await request<{ sessions: DashboardSessionSummary[] }>(`/api/sessions?${params}`)
  return res.sessions
    .filter((session) => !source || (session.source || 'local') === source)
    .map(mapSummary)
}

export async function searchSessions(q: string, source?: string, limit?: number): Promise<SessionSearchResult[]> {
  const res = await request<{ results: Array<{
    session_id: string
    snippet: string
    role: string | null
    source: string | null
    model: string | null
    session_started: number | null
  }> }>(`/api/sessions/search?q=${encodeURIComponent(q)}`)

  const sessions = await fetchSessions(source, limit || 200)
  const map = new Map(sessions.map((session) => [session.id, session]))

  return res.results
    .filter((item) => !source || (item.source || 'local') === source)
    .slice(0, limit || 200)
    .map((item, index) => {
      const base = map.get(item.session_id)
      return {
        ...(base || {
          id: item.session_id,
          source: item.source || 'local',
          model: item.model || '',
          title: null,
          preview: '',
          started_at: item.session_started || 0,
          ended_at: null,
          last_active: item.session_started || 0,
          message_count: 0,
          tool_call_count: 0,
          input_tokens: 0,
          output_tokens: 0,
          cache_read_tokens: 0,
          cache_write_tokens: 0,
          reasoning_tokens: 0,
          provider: null,
          parent_session_id: null,
          branch_name: null,
          compressed_summary: null,
          last_compression_at: 0,
          last_compression_input_tokens: 0,
          compression_failure_count: 0,
        }),
        matched_message_id: null,
        snippet: item.snippet,
        rank: index + 1,
      }
    })
}

export async function fetchSession(id: string): Promise<SessionDetail | null> {
  try {
    const [sessionsRes, detail] = await Promise.all([
      request<{ sessions: DashboardSessionSummary[] }>('/api/sessions?limit=500&offset=0'),
      request<DashboardSessionDetail>(`/api/sessions/${id}/messages`),
    ])
    const summary = sessionsRes.sessions.find((session) => session.id === id)
    const base = summary ? mapSummary(summary) : {
      id,
      source: 'local',
      model: detail.model || '',
      title: null,
      preview: '',
      started_at: 0,
      ended_at: null,
      last_active: 0,
      message_count: detail.messages.length,
      tool_call_count: 0,
      input_tokens: detail.input_tokens,
      output_tokens: detail.output_tokens,
      cache_read_tokens: detail.cache_read_tokens,
      cache_write_tokens: detail.cache_write_tokens,
      reasoning_tokens: detail.reasoning_tokens,
      provider: detail.provider || null,
      parent_session_id: detail.parent_session_id || null,
      branch_name: detail.branch_name || null,
      compressed_summary: detail.compressed_summary || null,
      last_compression_at: detail.last_compression_at || 0,
      last_compression_input_tokens: detail.last_compression_input_tokens || 0,
      compression_failure_count: detail.compression_failure_count || 0,
      active_agent_name: detail.active_agent_name || 'default',
    }

    return {
      ...base,
      messages: mapMessages(id, detail.messages),
    }
  } catch {
    return null
  }
}

export async function deleteSession(id: string): Promise<boolean> {
  try {
    await request(`/api/sessions/${id}`, { method: 'DELETE' })
    return true
  } catch {
    return false
  }
}

export async function renameSession(_id: string, _title: string): Promise<boolean> {
  return false
}

export async function fetchSessionUsage(ids: string[]): Promise<Record<string, { input_tokens: number; output_tokens: number }>> {
  const results: Record<string, { input_tokens: number; output_tokens: number }> = {}
  const sessions = await fetchSessions(undefined, 500)
  for (const session of sessions) {
    if (ids.includes(session.id)) {
      results[session.id] = {
        input_tokens: session.input_tokens,
        output_tokens: session.output_tokens,
      }
    }
  }
  return results
}

export async function fetchSessionUsageSingle(id: string): Promise<{ input_tokens: number; output_tokens: number } | null> {
  const detail = await fetchSession(id)
  if (!detail) return null
  return {
    input_tokens: detail.input_tokens,
    output_tokens: detail.output_tokens,
  }
}

export async function fetchSessionTree(id: string): Promise<any> {
  return request(`/api/sessions/${id}/tree`)
}

export async function fetchSessionCheckpoints(id: string): Promise<any[]> {
  const res = await request<{ checkpoints: any[] }>(`/api/sessions/${id}/checkpoints`)
  return res.checkpoints || []
}

export async function fetchCheckpointPreview(id: string): Promise<any> {
  return request(`/api/checkpoints/${id}/preview`)
}

export async function rollbackCheckpoint(id: string): Promise<any> {
  return request(`/api/checkpoints/${id}/rollback`, { method: 'POST' })
}

export async function fetchContextLength(): Promise<number> {
  const res = await request<{ effective_context_length?: number; config_context_length?: number }>('/api/model/info')
  return res.effective_context_length || res.config_context_length || 128000
}
