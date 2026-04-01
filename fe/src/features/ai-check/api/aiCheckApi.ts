import type { AiEvaluationResult, BossPackageResult, InterviewEvaluationResult } from '@/types/api.types'
import { callAiCheck } from '@/lib/apiClient'

export async function submitAiCheck(
  endpoint: string,
  body: Record<string, unknown>,
  userId: string,
): Promise<AiEvaluationResult> {
  return callAiCheck<AiEvaluationResult>(endpoint, body, userId)
}

export async function submitBossPackage(
  body: Record<string, unknown>,
  userId: string,
): Promise<BossPackageResult> {
  return callAiCheck<BossPackageResult>('boss-package', body, userId)
}

export async function submitMockInterview(
  params: {
    questId: string
    questionId: string
    question: string
    answer: string
    category: string
  },
  userId: string,
): Promise<InterviewEvaluationResult> {
  return callAiCheck<InterviewEvaluationResult>('mock-interview', params, userId)
}
