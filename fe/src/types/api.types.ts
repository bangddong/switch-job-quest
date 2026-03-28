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
