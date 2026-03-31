export interface CoachQuestion {
  index: number
  question: string
  competency: string
}

export interface CoachSessionResult {
  jdSummary: string
  keyCompetencies: string[]
  questions: CoachQuestion[]
}

export interface CoachAnswerResult {
  feedback: string
  score: number
  improvements: string[]
  encouragement: string
}

export interface CoachAnswerHistory {
  question: string
  answer: string
  feedback: string
}

export interface CoachReportResult {
  overallScore: number
  passLikelihood: number
  strengths: string[]
  weaknesses: string[]
  finalAdvice: string
}

export type CoachStep = 'onboarding' | 'analyzing' | 'analysis' | 'qa' | 'reporting' | 'report'
