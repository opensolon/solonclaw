import { request } from '../client'

export interface SkillInfo {
  name: string
  canonicalName: string
  description: string
  enabled?: boolean
}

export interface SkillCategory {
  name: string
  description: string
  skills: SkillInfo[]
}

export interface SkillListResponse {
  categories: SkillCategory[]
}

export interface SkillFileEntry {
  path: string
  name: string
  isDir: boolean
}

export interface MemoryData {
  memory: string
  user: string
  soul: string
  memory_mtime: number | null
  user_mtime: number | null
  soul_mtime: number | null
}

interface DashboardSkill {
  name: string
  description: string
  category: string
  enabled: boolean
}

interface SkillContentResponse {
  content: string
}

interface WorkspaceFile {
  key: string
  path: string
  content: string
}

function displayCategory(name: string): string {
  return name || 'general'
}

function bareSkillName(name: string, category: string): string {
  const prefix = category && category !== 'general' ? `${category}/` : ''
  return prefix && name.startsWith(prefix) ? name.slice(prefix.length) : name
}

export async function fetchSkills(): Promise<SkillCategory[]> {
  const skills = await request<DashboardSkill[]>('/api/skills')
  const groups = new Map<string, SkillInfo[]>()

  for (const skill of skills) {
    const key = displayCategory(skill.category)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push({
      name: bareSkillName(skill.name, key),
      canonicalName: skill.name,
      description: skill.description,
      enabled: skill.enabled,
    })
  }

  return Array.from(groups.entries()).map(([name, groupedSkills]) => ({
    name,
    description: name,
    skills: groupedSkills,
  }))
}

function canonicalSkillName(category: string, skill: string): string {
  if (skill.includes('/')) return skill
  return category && category !== 'general' ? `${category}/${skill}` : skill
}

export async function fetchSkillContent(category: string, skill: string, filePath?: string): Promise<string> {
  const params = new URLSearchParams({ name: canonicalSkillName(category, skill) })
  if (filePath) params.set('filePath', filePath)
  const res = await request<SkillContentResponse>(`/api/skills/view?${params.toString()}`)
  return res.content || ''
}

export async function fetchSkillFiles(category: string, skill: string): Promise<SkillFileEntry[]> {
  const params = new URLSearchParams({ name: canonicalSkillName(category, skill) })
  return request<SkillFileEntry[]>(`/api/skills/files?${params.toString()}`)
}

async function getWorkspaceFile(key: string): Promise<WorkspaceFile | null> {
  const res = await request<{ files: WorkspaceFile[] }>('/api/workspace/files')
  return (res.files || []).find((item) => item.key === key) || null
}

export async function fetchMemory(): Promise<MemoryData> {
  const [memory, user, soul] = await Promise.all([
    getWorkspaceFile('memory'),
    getWorkspaceFile('user'),
    getWorkspaceFile('soul'),
  ])

  return {
    memory: memory?.content || '',
    user: user?.content || '',
    soul: soul?.content || '',
    memory_mtime: null,
    user_mtime: null,
    soul_mtime: null,
  }
}

export async function saveMemory(section: 'memory' | 'user' | 'soul', content: string): Promise<void> {
  await request(`/api/workspace/files/${encodeURIComponent(section)}`, {
    method: 'PUT',
    body: JSON.stringify({ content }),
  })
}

export async function toggleSkill(category: string, skill: string, enabled: boolean): Promise<void> {
  await request('/api/skills/toggle', {
    method: 'PUT',
    body: JSON.stringify({ name: canonicalSkillName(category, skill), enabled }),
  })
}
