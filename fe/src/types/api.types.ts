export interface ApiResponse<T> {
  success: boolean
  result: string
  data: T | null
  message: string | null
}

export interface QuestDetail {
  status: string
  score: number
  xp: number
}

export interface ProgressResult {
  userId: string
  totalXp: number
  level: number
  completedQuests: string[]
  questDetails: Record<string, QuestDetail>
  lastCompletedAt?: string   // NEW — ISO datetime string, e.g. "2026-04-02T10:00:00"
}

export interface ActClearReportResult {
  actId: number
  actTitle: string
  overallScore: number
  grade: string
  developerClass: string
  achievements: string[]
  nextActHint: string
  encouragement: string
}

export interface AiEvaluationResult {
  score: number
  overallScore?: number
  passed: boolean
  grade: string
  summary?: string
  developerType?: string
  strengths?: string[]
  improvements?: Array<string | { suggestion?: string; issue?: string }>
  detailedFeedback?: string
  feedback?: string
  rewrittenExamples?: Array<{ original: string; improved: string }>
  suggestedFocus?: string[]
}

export interface QuestHistoryItem {
  id: number
  questId: string
  actId: number
  score: number
  grade: string
  passed: boolean
  earnedXp: number
  createdAt: string
}

export interface BossPackageResult {
  overallScore: number
  passed: boolean
  resumeImpactScore: number
  githubConsistencyScore: number
  technicalDepthScore: number
  positionFitScore: number
  differentiationScore: number
  strengths: string[]
  improvements: string[]
  overallFeedback: string
}

export interface InterviewEvaluationResult {
  score: number
  passed: boolean
  technicalAccuracy: number
  depthAndApplication: number
  practicalExperience: number
  communicationClarity: number
  keyPointsMissed?: string[]
  correctAnswer?: string
}
