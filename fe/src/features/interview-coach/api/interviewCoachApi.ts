import type { ApiResponse } from '@/types/api.types'
import type { CoachSessionResult, CoachAnswerResult, CoachAnswerHistory, CoachReportResult } from '../types/coach.types'
import { getToken } from '@/hooks/useAuth'

const API_BASE = '/api/v1/coach'

function authHeaders(): HeadersInit {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

async function callCoach<T>(path: string, body: Record<string, unknown>): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const err: ApiResponse<unknown> = await res.json()
      if (err.message) message = err.message
    } catch { /* ignore */ }
    throw new Error(message)
  }
  const json: ApiResponse<T> = await res.json()
  if (!json.success || json.data == null) throw new Error(json.message ?? '코치 API 오류')
  return json.data
}

export async function startCoachSession(
  jdText: string,
  targetRole: string,
): Promise<CoachSessionResult> {
  return callCoach<CoachSessionResult>('/session/start', { jdText, targetRole })
}

export async function submitCoachAnswer(
  question: string,
  answer: string,
  questionIndex: number,
  totalQuestions: number,
): Promise<CoachAnswerResult> {
  return callCoach<CoachAnswerResult>('/session/answer', {
    question,
    answer,
    questionIndex,
    totalQuestions,
  })
}

export async function generateCoachReport(
  targetRole: string,
  jdSummary: string,
  answers: CoachAnswerHistory[],
): Promise<CoachReportResult> {
  return callCoach<CoachReportResult>('/session/report', { targetRole, jdSummary, answers })
}
