import type { ApiResponse, ProgressResult, ActClearReportResult, QuestHistoryItem, JourneyReportResult, UserEmailResult, TechInterviewResult, CodingProblem, CodingSubmissionResult, CodingLevelResult } from '@/types/api.types'
import { getToken, clearToken } from '@/hooks/useAuth'
import { STORAGE_KEYS } from '@/lib/storageKeys'

const API_BASE = '/api/v1'
let reloadScheduled = false

function authHeaders(): HeadersInit {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

function assertOk(res: Response): void {
  if (res.ok) return
  if ((res.status === 401 || res.status === 403) && !reloadScheduled) {
    reloadScheduled = true
    clearToken()
    localStorage.removeItem(STORAGE_KEYS.PROGRESS)
    window.location.reload()
  }
  throw new Error(`HTTP ${res.status}`)
}

export async function fetchProgress(): Promise<ProgressResult> {
  const res = await fetch(`${API_BASE}/progress`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<ProgressResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('진행 상황 조회 실패')
  return json.data
}

export async function completeQuest(questId: string, actId: number, earnedXp: number): Promise<void> {
  const res = await fetch(`${API_BASE}/progress/complete`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ questId, actId, earnedXp }),
  })
  assertOk(res)
}

export async function fetchActClearReport(actId: number, actTitle: string): Promise<ActClearReportResult> {
  const res = await fetch(`${API_BASE}/ai-check/act-clear-report`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ actId, actTitle }),
  })
  assertOk(res)
  const json: ApiResponse<ActClearReportResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('ACT 클리어 리포트 생성 실패')
  return json.data
}

export async function fetchHistory(): Promise<QuestHistoryItem[]> {
  const res = await fetch(`${API_BASE}/progress/history`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<QuestHistoryItem[]> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('히스토리 조회 실패')
  return json.data
}

export async function fetchQuestHistory(questId: string): Promise<QuestHistoryItem[]> {
  const res = await fetch(`${API_BASE}/progress/history/${encodeURIComponent(questId)}`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<QuestHistoryItem[]> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('퀘스트 히스토리 조회 실패')
  return json.data
}

export async function fetchJourneyReport(companyName: string, targetPosition: string): Promise<JourneyReportResult> {
  const res = await fetch(`${API_BASE}/ai-check/journey-report`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ companyName, targetPosition }),
  })
  assertOk(res)
  const json: ApiResponse<JourneyReportResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('여정 리포트 생성 실패')
  return json.data
}

export async function fetchUserEmail(): Promise<UserEmailResult> {
  const res = await fetch(`${API_BASE}/user/email`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<UserEmailResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('이메일 조회 실패')
  return json.data
}

export async function saveUserEmail(email: string): Promise<UserEmailResult> {
  const res = await fetch(`${API_BASE}/user/email`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ email }),
  })
  assertOk(res)
  const json: ApiResponse<UserEmailResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('이메일 저장 실패')
  return json.data
}

export async function fetchTechInterviewQuestion(techStack: string): Promise<TechInterviewResult> {
  const res = await fetch(`${API_BASE}/tech-interview/question?techStack=${encodeURIComponent(techStack)}`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<TechInterviewResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('질문 조회 실패')
  return json.data
}

export async function evaluateTechInterview(techStack: string, questions: string[], answers: string[]): Promise<TechInterviewResult> {
  const res = await fetch(`${API_BASE}/tech-interview/evaluate`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ techStack, questions, answers }),
  })
  assertOk(res)
  const json: ApiResponse<TechInterviewResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('평가 실패')
  return json.data
}

export async function fetchCodingProblem(language: string): Promise<CodingProblem> {
  const res = await fetch(`${API_BASE}/coding/problem?language=${encodeURIComponent(language)}`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<CodingProblem> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('코딩 문제 조회 실패')
  return json.data
}

export async function submitCode(problemId: number, language: string, userCode: string): Promise<CodingSubmissionResult> {
  const res = await fetch(`${API_BASE}/coding/submit`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ problemId, language, userCode }),
  })
  assertOk(res)
  const json: ApiResponse<CodingSubmissionResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('코드 제출 실패')
  return json.data
}

export async function fetchCodingLevel(): Promise<CodingLevelResult> {
  const res = await fetch(`${API_BASE}/coding/level`, { headers: authHeaders() })
  assertOk(res)
  const json: ApiResponse<CodingLevelResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('코딩 레벨 조회 실패')
  return json.data
}

export async function callAiCheck<T>(endpoint: string, body: Record<string, unknown>): Promise<T> {
  const res = await fetch(`${API_BASE}/ai-check/${endpoint}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    assertOk(res)
    let errorMessage = `HTTP ${res.status}`
    try {
      const errorJson: ApiResponse<unknown> = await res.json()
      if (errorJson.error?.message) errorMessage = errorJson.error.message
    } catch { /* response body not parseable */ }
    throw new Error(errorMessage)
  }

  const json: ApiResponse<T> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error(json.error?.message ?? 'AI 평가 오류')
  return json.data
}
