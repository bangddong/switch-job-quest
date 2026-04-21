import type { ApiResponse, ProgressResult, ActClearReportResult, QuestHistoryItem, JourneyReportResult, DeveloperClassResult } from '@/types/api.types'
import { getToken } from '@/hooks/useAuth'

const API_BASE = '/api/v1'

function authHeaders(): HeadersInit {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function fetchProgress(): Promise<ProgressResult> {
  const res = await fetch(`${API_BASE}/progress`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<ProgressResult> = await res.json()
  if (!json.success || json.data == null) throw new Error('진행 상황 조회 실패')
  return json.data
}

export async function completeQuest(
  questId: string,
  actId: number,
  earnedXp: number,
): Promise<void> {
  const res = await fetch(`${API_BASE}/progress/complete`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ questId, actId, earnedXp }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function fetchActClearReport(
  actId: number,
  actTitle: string,
): Promise<ActClearReportResult> {
  const res = await fetch(`${API_BASE}/ai-check/act-clear-report`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ actId, actTitle }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<ActClearReportResult> = await res.json()
  if (!json.success || json.data == null) throw new Error('ACT 클리어 리포트 생성 실패')
  return json.data
}

export async function fetchHistory(): Promise<QuestHistoryItem[]> {
  const res = await fetch(`${API_BASE}/progress/history`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<QuestHistoryItem[]> = await res.json()
  if (!json.success || json.data == null) throw new Error('히스토리 조회 실패')
  return json.data
}

export async function fetchQuestHistory(questId: string): Promise<QuestHistoryItem[]> {
  const res = await fetch(`${API_BASE}/progress/history/${encodeURIComponent(questId)}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<QuestHistoryItem[]> = await res.json()
  if (!json.success || json.data == null) throw new Error('퀘스트 히스토리 조회 실패')
  return json.data
}

export async function fetchJourneyReport(
  companyName: string,
  targetPosition: string,
): Promise<JourneyReportResult> {
  const res = await fetch(`${API_BASE}/ai-check/journey-report`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ companyName, targetPosition }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<JourneyReportResult> = await res.json()
  if (!json.success || json.data == null) throw new Error('여정 리포트 생성 실패')
  return json.data
}

export async function submitDeveloperClass(): Promise<DeveloperClassResult> {
  return callAiCheck<DeveloperClassResult>('developer-class', {})
}

export async function callAiCheck<T>(
  endpoint: string,
  body: Record<string, unknown>,
): Promise<T> {
  const res = await fetch(`${API_BASE}/ai-check/${endpoint}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    let errorMessage = `HTTP ${res.status}`
    try {
      const errorJson: ApiResponse<unknown> = await res.json()
      if (errorJson.message) errorMessage = errorJson.message
    } catch {
      // response body not parseable, use default
    }
    throw new Error(errorMessage)
  }

  const json: ApiResponse<T> = await res.json()

  if (json.result !== 'SUCCESS' || json.data == null) {
    throw new Error(json.message ?? 'AI 평가 오류')
  }

  return json.data
}
