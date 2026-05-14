import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult, InterviewEvaluationResult, JdAnalysisResult } from '@/types/api.types'
import { callAiCheck } from '@/lib/apiClient'
import { getToken } from '@/hooks/useAuth'

export interface InterviewQuestion {
  id: string
  category: string
  question: string
  difficulty: string
}

export async function generateInterviewQuestions(params: {
  techStack: string[]
  targetRole: string
  yearsOfExperience: string
  categories: string
  techCount: number
  personalityCount: number
}): Promise<InterviewQuestion[]> {
  const token = getToken()
  const headers: HeadersInit = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
  const query = new URLSearchParams({
    techStack: params.techStack.join(','),
    targetRole: params.targetRole,
    yearsOfExperience: params.yearsOfExperience,
    categories: params.categories,
    techCount: String(params.techCount),
    personalityCount: String(params.personalityCount),
  })
  const res = await fetch(`/api/v1/ai-check/mock-interview/questions?${query.toString()}`, { headers })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json = await res.json() as { result: string; data?: InterviewQuestion[]; error?: { message?: string } }
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error(json.error?.message ?? '질문 생성 실패')
  return json.data
}

export async function submitAiCheck(
  endpoint: string,
  body: Record<string, unknown>,
): Promise<AiEvaluationResult> {
  return callAiCheck<AiEvaluationResult>(endpoint, body)
}

export async function submitDeveloperClass(): Promise<DeveloperClassResult> {
  return callAiCheck<DeveloperClassResult>('developer-class', {})
}

export async function submitBossPackage(
  body: Record<string, unknown>,
): Promise<BossPackageResult> {
  return callAiCheck<BossPackageResult>('boss-package', body)
}

export async function submitJdAnalysis(body: Record<string, unknown>): Promise<JdAnalysisResult> {
  return callAiCheck<JdAnalysisResult>('jd-analysis', body)
}

export async function submitMockInterview(params: {
  questId: string
  questionId: string
  question: string
  answer: string
  category: string
  techStack?: string[]
  yearsOfExperience?: string
}): Promise<InterviewEvaluationResult> {
  return callAiCheck<InterviewEvaluationResult>('mock-interview', params)
}
