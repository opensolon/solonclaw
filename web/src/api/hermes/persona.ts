import { request } from '../client'

export interface PersonaFileData {
  key: string
  title: string
  fileName: string
  path: string
  exists: boolean
  content: string
}

export interface PersonaDiaryEntry {
  name: string
  relativePath: string
  path: string
}

const META: Record<string, { title: string; fileName: string }> = {
  agents: { title: '规则', fileName: 'AGENTS.md' },
  memory: { title: '记忆', fileName: 'MEMORY.md' },
  memory_today: { title: '日记', fileName: 'memory/YYYY-MM-DD.md' },
  soul: { title: '灵魂', fileName: 'SOUL.md' },
  identity: { title: '身份', fileName: 'IDENTITY.md' },
  user: { title: '用户', fileName: 'USER.md' },
  tools: { title: '工具', fileName: 'TOOLS.md' },
  heartbeat: { title: '心跳', fileName: 'HEARTBEAT.md' },
}

interface WorkspaceFile {
  key: string
  name: string
  path: string
  exists: boolean
  content: string
}

export function personaMeta(key: string) {
  return META[key] || { title: key, fileName: key }
}

export async function fetchPersonaFile(key: string): Promise<PersonaFileData> {
  const file = await request<WorkspaceFile>(`/api/workspace/files/${encodeURIComponent(key)}`)
  const meta = personaMeta(key)
  return {
    key,
    title: meta.title,
    fileName: key === 'memory_today' ? file.name : meta.fileName,
    path: file.path,
    exists: file.exists,
    content: file.content || '',
  }
}

export async function savePersonaFile(key: string, content: string): Promise<void> {
  await request(`/api/workspace/files/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ content }),
  })
}

export async function fetchPersonaDiaries(): Promise<PersonaDiaryEntry[]> {
  const res = await request<{ files: PersonaDiaryEntry[] }>('/api/workspace/diaries')
  return res.files || []
}

export async function fetchPersonaDiary(relativePath: string): Promise<{
  name: string
  relativePath: string
  path: string
  content: string
}> {
  return request(`/api/workspace/diaries/read?path=${encodeURIComponent(relativePath)}`)
}
