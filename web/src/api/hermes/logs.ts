import { request } from '../client'

export interface LogFileInfo {
  name: string
  size: string
  modified: string
}

export interface LogEntry {
  timestamp: string
  level: string
  logger: string
  message: string
  raw: string
}

const LOG_FILES = ['agent', 'errors', 'gateway']

function parseLine(line: string): LogEntry {
  const match = line.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([A-Z]+)\s+\[[^\]]+\]\s+([^\s]+)\s+-\s+(.*)$/)
  if (!match) {
    return {
      timestamp: '',
      level: 'INFO',
      logger: '',
      message: line,
      raw: line,
    }
  }

  return {
    timestamp: match[1],
    level: match[2],
    logger: match[3],
    message: match[4],
    raw: line,
  }
}

export async function fetchLogFiles(): Promise<LogFileInfo[]> {
  return LOG_FILES.map((name) => ({
    name,
    size: '-',
    modified: '',
  }))
}

export async function fetchLogs(name: string, params?: {
  lines?: number
  level?: string
  session?: string
  since?: string
}): Promise<LogEntry[]> {
  const query = new URLSearchParams()
  query.set('file', name)
  if (params?.lines) query.set('lines', String(params.lines))
  if (params?.level) query.set('level', params.level)
  const res = await request<{ file: string; lines: string[] }>(`/api/logs?${query}`)
  return (res.lines || []).map(parseLine)
}
