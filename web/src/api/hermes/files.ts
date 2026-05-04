import { getApiKey, getBaseUrlValue, request } from '../client'

export interface FileEntry {
  name: string
  path: string
  isDir: boolean
  size: number
  modTime: string
}

export interface FileStat {
  name: string
  path: string
  isDir: boolean
  size: number
  modTime: string
  permissions?: string
}

interface WorkspaceFile {
  key: string
  name: string
  path: string
  exists: boolean
  content: string
}

const PATH_TO_KEY: Record<string, string> = {
  'AGENTS.md': 'agents',
  'SOUL.md': 'soul',
  'USER.md': 'user',
  'TOOLS.md': 'tools',
  'HEARTBEAT.md': 'heartbeat',
  'MEMORY.md': 'memory',
}

function normalizePath(path: string): string {
  return path.replace(/\\/g, '/').split('/').pop() || path
}

function keyForPath(path: string): string {
  const normalized = normalizePath(path)
  if (PATH_TO_KEY[normalized]) return PATH_TO_KEY[normalized]
  if (normalized.startsWith('memory/')) return 'memory_today'
  return Object.entries(PATH_TO_KEY).find(([, key]) => key === path)?.[1] || path
}

async function workspaceFiles(): Promise<WorkspaceFile[]> {
  const res = await request<{ files: WorkspaceFile[] }>('/api/workspace/files')
  return res.files || []
}

export async function listFiles(path: string = ''): Promise<{ entries: FileEntry[]; path: string }> {
  if (path) {
    return { entries: [], path }
  }

  const files = await workspaceFiles()
  return {
    path: '',
    entries: files.map((file) => ({
      name: file.name,
      path: file.name,
      isDir: false,
      size: file.content.length,
      modTime: '',
    })),
  }
}

export async function statFile(path: string): Promise<FileStat> {
  const files = await workspaceFiles()
  const file = files.find((item) => item.name === path || item.path === path)
  if (!file) throw new Error('File not found')
  return {
    name: file.name,
    path: file.name,
    isDir: false,
    size: file.content.length,
    modTime: '',
  }
}

export async function readFile(path: string): Promise<{ content: string; path: string; size: number }> {
  const key = keyForPath(path)
  const file = await request<WorkspaceFile>(`/api/workspace/files/${encodeURIComponent(key)}`)
  return {
    content: file.content || '',
    path,
    size: (file.content || '').length,
  }
}

export async function writeFile(path: string, content: string): Promise<void> {
  const key = keyForPath(path)
  await request(`/api/workspace/files/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ content }),
  })
}

function unsupported(): never {
  throw new Error('当前后端仅开放工作区文件的读取与保存')
}

export async function deleteFile(_path: string, _recursive: boolean = false): Promise<void> {
  unsupported()
}

export async function renameFile(_oldPath: string, _newPath: string): Promise<void> {
  unsupported()
}

export async function mkDir(_path: string): Promise<void> {
  unsupported()
}

export async function copyFile(_srcPath: string, _destPath: string): Promise<void> {
  unsupported()
}

export async function uploadFiles(_targetDir: string, _files: File[]): Promise<{ name: string; path: string }[]> {
  unsupported()
}

export function getFileDownloadUrl(relativePath: string, fileName?: string): string {
  const base = getBaseUrlValue()
  const params = new URLSearchParams({ path: relativePath })
  if (fileName) params.set('name', fileName)
  const token = getApiKey()
  if (token) params.set('token', token)
  return `${base}/api/hermes/download?${params.toString()}`
}
