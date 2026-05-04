import { getApiKey, getBaseUrlValue, request } from '../client'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface UploadedChatFile {
  name: string
  path: string
  local_path: string
  kind: string
  mime_type: string
  size: number
}

export interface StartRunRequest {
  input: string
  instructions?: string
  conversation_history?: ChatMessage[]
  session_id?: string
  model?: string
  attachments?: UploadedChatFile[]
}

export interface StartRunResponse {
  run_id: string
  status: string
  session_id: string
}

export interface RunEvent {
  event: string
  run_id?: string
  session_id?: string
  delta?: string
  tool?: string
  name?: string
  preview?: string
  timestamp?: number
  error?: string
  agent_run_id?: string
  attempt_no?: number
  provider?: string
  model?: string
  status?: string
  reason?: string
  compressed?: boolean
  estimated_tokens?: number
  threshold_tokens?: number
  recovery_type?: string
  from_provider?: string
  to_provider?: string
  reasoning?: string
  usage?: {
    input_tokens: number
    output_tokens: number
    reasoning_tokens?: number
    total_tokens: number
  }
}

export async function uploadChatFiles(files: File[]): Promise<UploadedChatFile[]> {
  const formData = new FormData()
  for (const file of files) {
    formData.append('file', file, file.name)
  }

  const headers = new Headers()
  const apiKey = getApiKey()
  if (apiKey) {
    headers.set('Authorization', `Bearer ${apiKey}`)
  }

  const res = await fetch(`${getBaseUrlValue()}/api/chat/uploads`, {
    method: 'POST',
    body: formData,
    headers,
  })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`Upload failed: ${res.status} ${text || res.statusText}`)
  }

  const data = await res.json() as { files?: UploadedChatFile[] }
  return data.files || []
}

export async function startRun(body: StartRunRequest): Promise<StartRunResponse> {
  return request<StartRunResponse>('/api/chat/runs', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function cancelRun(runId: string): Promise<void> {
  await request(`/api/chat/runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
  })
}

export function streamRunEvents(
  runId: string,
  onEvent: (event: RunEvent) => void,
  onDone: () => void,
  onError: (err: Error) => void,
) {
  const controller = new AbortController()
  const headers = new Headers()
  const apiKey = getApiKey()
  if (apiKey) {
    headers.set('Authorization', `Bearer ${apiKey}`)
  }

  void (async () => {
    try {
      const res = await fetch(`${getBaseUrlValue()}/api/chat/runs/${encodeURIComponent(runId)}/events`, {
        method: 'GET',
        headers,
        signal: controller.signal,
      })

      if (!res.ok || !res.body) {
        const text = await res.text().catch(() => '')
        throw new Error(`SSE failed: ${res.status} ${text || res.statusText}`)
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const rawEvent = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          boundary = buffer.indexOf('\n\n')

          let eventName = ''
          const dataLines: string[] = []
          const lines = rawEvent.split(/\r?\n/)
          for (const line of lines) {
            if (line.startsWith(':')) continue
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
              continue
            }
            if (line.startsWith('data:')) {
              dataLines.push(line.slice(5).trim())
            }
          }

          if (!eventName && dataLines.length === 0) continue

          let payload: any = {}
          if (dataLines.length > 0) {
            const dataText = dataLines.join('\n')
            payload = dataText ? JSON.parse(dataText) : {}
          }
          onEvent({ event: eventName || 'message', ...payload })
        }
      }

      if (!controller.signal.aborted) {
        onDone()
      }
    } catch (err: any) {
      if (controller.signal.aborted) {
        return
      }
      onError(err instanceof Error ? err : new Error(String(err)))
    }
  })()

  return controller
}

export async function fetchModels(): Promise<{ data: Array<{ id: string }> }> {
  return { data: [] }
}
