import { readFile } from './files'

export function getDownloadUrl(_filePath: string, _fileName?: string): string {
  return '#'
}

export async function downloadFile(filePath: string, fileName?: string): Promise<void> {
  const data = await readFile(filePath)
  const blob = new Blob([data.content], { type: 'text/plain;charset=utf-8' })
  const blobUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = fileName || filePath.split('/').pop() || 'download.txt'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(blobUrl)
}
