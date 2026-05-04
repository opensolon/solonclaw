export interface ConversationSummary {
  id: string
  source: string
  model: string
  title: string | null
  started_at: number
  ended_at: number | null
  last_active: number
  message_count: number
  tool_call_count: number
  input_tokens: number
  output_tokens: number
  cache_read_tokens: number
  cache_write_tokens: number
  reasoning_tokens: number
  provider: string | null
  preview: string
  is_active: boolean
  thread_session_count: number
}

export interface ConversationMessage {
  id: number | string
  session_id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

export interface ConversationDetail {
  session_id: string
  messages: ConversationMessage[]
  visible_count: number
  thread_session_count: number
}

export async function fetchConversationSummaries(_params: { humanOnly?: boolean; source?: string; limit?: number } = {}): Promise<ConversationSummary[]> {
  return []
}

export async function fetchConversationDetail(sessionId: string, _params: { humanOnly?: boolean; source?: string } = {}): Promise<ConversationDetail> {
  return {
    session_id: sessionId,
    messages: [],
    visible_count: 0,
    thread_session_count: 0,
  }
}
