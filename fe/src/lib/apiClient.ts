import type { ApiResponse, ProgressResult } from '@/types/api.types'

const API_BASE = '/api/v1'

export async function fetchProgress(userId: string): Promise<ProgressResult> {
  const res = await fetch(`${API_BASE}/progress/${userId}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<ProgressResult> = await res.json()
  if (!json.success || json.data == null) throw new Error('진행 상황 조회 실패')
  return json.data
}

export async function completeQuest(
  userId: string,
  questId: string,
  actId: number,
  earnedXp: number,
): Promise<void> {
  const res = await fetch(`${API_BASE}/progress/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, questId, actId, earnedXp }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function callAiCheck<T>(
  endpoint: string,
  body: Record<string, unknown>,
  userId: string,
): Promise<T> {
  const res = await fetch(`${API_BASE}/ai-check/${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, userId }),
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

  if (!json.success || json.data == null) {
    throw new Error(json.message ?? 'AI 평가 오류')
  }

  return json.data
}
