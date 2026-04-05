import type { AiEvaluationResult, BossPackageResult, InterviewEvaluationResult } from '@/types/api.types'
import { callAiCheck } from '@/lib/apiClient'

export async function submitAiCheck(
  endpoint: string,
  body: Record<string, unknown>,
): Promise<AiEvaluationResult> {
  return callAiCheck<AiEvaluationResult>(endpoint, body)
}

export async function submitBossPackage(
  body: Record<string, unknown>,
): Promise<BossPackageResult> {
  return callAiCheck<BossPackageResult>('boss-package', body)
}

export async function submitMockInterview(params: {
  questId: string
  questionId: string
  question: string
  answer: string
  category: string
}): Promise<InterviewEvaluationResult> {
  return callAiCheck<InterviewEvaluationResult>('mock-interview', params)
}
