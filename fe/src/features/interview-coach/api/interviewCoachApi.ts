import type { ApiResponse } from '@/types/api.types'
import type { CoachSessionResult, CoachAnswerResult, CoachAnswerHistory, CoachReportResult } from '../types/coach.types'

const API_BASE = '/api/v1/coach'

async function callCoach<T>(path: string, body: Record<string, unknown>): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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
  userId: string,
  jdText: string,
  targetRole: string,
): Promise<CoachSessionResult> {
  return callCoach<CoachSessionResult>('/session/start', { userId, jdText, targetRole })
}

export async function submitCoachAnswer(
  userId: string,
  question: string,
  answer: string,
  questionIndex: number,
  totalQuestions: number,
): Promise<CoachAnswerResult> {
  return callCoach<CoachAnswerResult>('/session/answer', {
    userId,
    question,
    answer,
    questionIndex,
    totalQuestions,
  })
}

export async function generateCoachReport(
  userId: string,
  targetRole: string,
  jdSummary: string,
  answers: CoachAnswerHistory[],
): Promise<CoachReportResult> {
  return callCoach<CoachReportResult>('/session/report', { userId, targetRole, jdSummary, answers })
}
