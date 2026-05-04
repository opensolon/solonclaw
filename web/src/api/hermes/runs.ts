import { request } from '../client'

export interface AgentRun {
  run_id: string
  session_id: string
  source_key?: string
  run_kind?: string
  parent_run_id?: string
  status: string
  phase?: string
  busy_policy?: string
  backgrounded?: boolean
  input_preview?: string
  final_reply_preview?: string
  provider?: string
  model?: string
  agent_name?: string
  agent_snapshot?: Record<string, unknown>
  attempts: number
  context_estimate_tokens?: number
  context_window_tokens?: number
  compression_count?: number
  fallback_count?: number
  tool_call_count?: number
  subtask_count?: number
  input_tokens: number
  output_tokens: number
  total_tokens: number
  queued_at?: number
  started_at: number
  heartbeat_at?: number
  last_activity_at?: number
  finished_at: number
  exit_reason?: string
  recoverable?: boolean
  recovery_hint?: string
  error?: string
}

export interface AgentRunEvent {
  event_id: string
  run_id: string
  session_id?: string
  source_key?: string
  event_type: string
  phase?: string
  severity?: string
  attempt_no: number
  provider?: string
  model?: string
  summary?: string
  created_at: number
  metadata?: Record<string, unknown>
}

export interface ToolCall {
  tool_call_id: string
  run_id: string
  session_id?: string
  source_key?: string
  tool_name: string
  status: string
  args_preview?: string
  result_preview?: string
  result_ref?: string
  error?: string
  interruptible?: boolean
  side_effecting?: boolean
  started_at: number
  finished_at: number
  duration_ms: number
}

export interface SubagentRun {
  subagent_id: string
  parent_run_id?: string
  child_run_id?: string
  parent_source_key?: string
  child_source_key?: string
  session_id?: string
  name?: string
  goal_preview?: string
  status: string
  depth: number
  task_index: number
  output_tail?: unknown
  error?: string
  started_at: number
  finished_at: number
  heartbeat_at: number
}

export interface RunRecovery {
  recovery_id: string
  run_id: string
  session_id?: string
  source_key?: string
  recovery_type: string
  status: string
  summary?: string
  payload?: unknown
  created_at: number
  resolved_at: number
}

export interface RunDetail {
  run: AgentRun
  events: AgentRunEvent[]
  tools: ToolCall[]
  subagents: SubagentRun[]
  recoveries: RunRecovery[]
}

export async function fetchSessionRuns(sessionId: string, limit = 20): Promise<AgentRun[]> {
  const res = await request<{ runs: AgentRun[] }>(`/api/sessions/${sessionId}/runs?limit=${limit}`)
  return res.runs || []
}

export async function fetchRunEvents(runId: string): Promise<AgentRunEvent[]> {
  const res = await request<{ events: AgentRunEvent[] }>(`/api/hermes/runs/${runId}/events`)
  return res.events || []
}

export async function fetchRunDetail(runId: string): Promise<RunDetail> {
  return request<RunDetail>(`/api/hermes/runs/${runId}/detail`)
}

export async function fetchRunTools(runId: string): Promise<ToolCall[]> {
  const res = await request<{ tools: ToolCall[] }>(`/api/hermes/runs/${runId}/tools`)
  return res.tools || []
}

export async function fetchRunSubagents(runId: string): Promise<SubagentRun[]> {
  const res = await request<{ subagents: SubagentRun[] }>(`/api/hermes/runs/${runId}/subagents`)
  return res.subagents || []
}

export async function fetchRecoverableRuns(limit = 50): Promise<AgentRun[]> {
  const res = await request<{ runs: AgentRun[] }>(`/api/hermes/runs/recoverable?limit=${limit}`)
  return res.runs || []
}

export async function controlRun(runId: string, command: string, payload: Record<string, unknown> = {}) {
  return request(`/api/hermes/runs/${runId}/control`, {
    method: 'POST',
    body: JSON.stringify({ command, ...payload }),
  })
}
