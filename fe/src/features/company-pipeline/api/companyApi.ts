import type {
  ApiResponse,
  AppliedCompany,
  ApplicationStatus,
  JdAnalysisResult,
  CompanyResumeCheckResult,
  CompanyActivity,
} from '@/types/api.types'
import { getToken } from '@/hooks/useAuth'

const API_BASE = '/api/v1'

function authHeaders(): HeadersInit {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

function assertOk(res: Response): void {
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

async function parseErrorCode(res: Response): Promise<string> {
  try {
    const json: ApiResponse<unknown> = await res.json()
    if (json.error?.code) return json.error.code
    if (json.error?.message) return json.error.message
  } catch { /* response body not parseable */ }
  return `HTTP ${res.status}`
}

export async function createCompany(body: {
  companyName: string
  position: string
  jdUrl?: string
  jobDescription?: string
}): Promise<AppliedCompany> {
  const res = await fetch(`${API_BASE}/companies`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })
  assertOk(res)
  const json: ApiResponse<AppliedCompany> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('회사 등록 실패')
  return json.data
}

export async function getCompanies(): Promise<AppliedCompany[]> {
  const res = await fetch(`${API_BASE}/companies`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<AppliedCompany[]> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('회사 목록 조회 실패')
  return json.data
}

export async function updateCompanyStatus(
  id: number,
  status: ApplicationStatus,
  appliedAt?: string,
): Promise<AppliedCompany> {
  const res = await fetch(`${API_BASE}/companies/${id}/status`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ status, ...(appliedAt ? { appliedAt } : {}) }),
  })
  assertOk(res)
  const json: ApiResponse<AppliedCompany> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('상태 변경 실패')
  return json.data
}

export async function deleteCompany(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/companies/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  assertOk(res)
}

export async function analyzeCompany(
  id: number,
  body: { userSkills: string[]; userExperiences: string[] },
): Promise<JdAnalysisResult> {
  const res = await fetch(`${API_BASE}/companies/${id}/analyze`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseErrorCode(res))
  const json: ApiResponse<JdAnalysisResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error(json.error?.code ?? 'JD 분석 실패')
  return json.data
}

export async function resumeCheck(id: number): Promise<CompanyResumeCheckResult> {
  const res = await fetch(`${API_BASE}/companies/${id}/resume-check`, {
    method: 'POST',
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(await parseErrorCode(res))
  const json: ApiResponse<CompanyResumeCheckResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error(json.error?.code ?? '이력서 점검 실패')
  return json.data
}

export async function getActivities(id: number): Promise<CompanyActivity[]> {
  const res = await fetch(`${API_BASE}/companies/${id}/activities`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<CompanyActivity[]> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('점검 이력 조회 실패')
  return json.data
}
