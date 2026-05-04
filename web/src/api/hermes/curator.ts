import { request } from '../client'

export interface CuratorReportSummary {
  report_id: string
  status: string
  summary?: string
  report_path?: string
  started_at: number
  finished_at: number
}

export async function fetchCuratorReports(limit = 20): Promise<CuratorReportSummary[]> {
  const res = await request<{ reports: CuratorReportSummary[] }>(`/api/hermes/curator?limit=${limit}`)
  return res.reports || []
}

export async function runCurator(force = true) {
  return request(`/api/hermes/curator/run?force=${force}`, { method: 'POST' })
}

export async function fetchCuratorReport(reportId: string) {
  return request(`/api/hermes/curator/${reportId}`)
}
