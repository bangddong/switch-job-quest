import { getToken, clearToken } from '@/hooks/useAuth'
import type { ApiResponse } from '@/types/api.types'
import { STORAGE_KEYS } from '@/lib/storageKeys'

export interface HintResult {
  hint: string
}

let reloadScheduled = false

export async function fetchHint(
  problemId: string,
  title: string,
  description: string,
  hintLevel: 1 | 2 | 3,
): Promise<HintResult> {
  const token = getToken()
  const res = await fetch('/api/v1/coding/hint', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ problemId, title, description, hintLevel }),
  })

  if (!res.ok) {
    if ((res.status === 401 || res.status === 403) && !reloadScheduled) {
      reloadScheduled = true
      clearToken()
      localStorage.removeItem(STORAGE_KEYS.PROGRESS)
      window.location.reload()
    }
    throw new Error(`HTTP ${res.status}`)
  }

  const json: ApiResponse<HintResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('힌트 조회 실패')
  return json.data
}
