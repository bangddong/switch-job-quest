import type { ApiResponse } from '@/types/api.types'

const API_BASE = '/api/v1'

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
