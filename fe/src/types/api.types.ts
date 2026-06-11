export interface ErrorMessage {
  code: string
  message: string
  data: unknown | null
}

export interface ApiResponse<T> {
  result: string
  data: T | null
  error: ErrorMessage | null
}

export interface QuestDetail {
  status: string
  score: number
  xp: number
  aiEvaluationJson?: string
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

export interface JourneyReportResult {
  companyName: string
  targetPosition: string
  totalXp: number
  completedQuestCount: number
  narrative: string
  lowestQuestId: string
  highestQuestId: string
  finalMessage: string
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
  questionId?: string
  question?: string
  userAnswer?: string
  improvements?: string
}

export interface DeveloperClassResult {
  overallScore: number
  passed: boolean
  developerClass: string
  classDescription: string
  strengths: string[]
  strategies: string[]
  overallFeedback: string
}

export interface ResumeImprovement {
  section: string
  original: string
  issue: string
  suggestion: string
}

export interface ResumeRewrite {
  original: string
  improved: string
  explanation: string
}

export interface ResumeCheckResult {
  overallScore: number
  passed: boolean
  starMethodScore: number
  quantificationScore: number
  keywordMatchScore: number
  improvements: ResumeImprovement[]
  rewrittenExamples: ResumeRewrite[]
}

export interface SkillRequirement {
  skill: string
  required: boolean
  userLevel: string
  importance: string
}

export interface UserEmailResult {
  email: string | null
}

export interface TechInterviewResult {
  questions: string[]
  overallScore: number
  feedback: string
  passed: boolean
  modelAnswer: string
}

export interface JdAnalysisResult {
  companyName: string
  requiredSkills: SkillRequirement[]
  hiddenRequirements: string[]
  overallMatchScore: number
  passed: boolean
  keyDifferentiators: string[]
  applicationStrategy: string
}

export interface TestCase {
  input: string
  expectedOutput: string
}

export interface CodingProblem {
  id: number
  title: string
  description: string
  difficulty: string
  language: string
  category?: string
  testCases: TestCase[]
}

export interface CategoryProgress {
  category: string
  displayName: string
  order: number
  solvedCount: number
  locked: boolean
}

export interface CodingSubmissionResult {
  problemId: number
  passed: boolean
  stdout: string
  stderr: string
  message: string
}

export interface CodingLevelResult {
  level: number
}

export interface CodingRankResult {
  totalScore: number
  tier: string
  nextTier: string | null
  nextTierScore: number | null
  easyCount: number
  mediumCount: number
  hardCount: number
  currentStreak: number
  categoryStats: Record<string, number>
}

export interface CodingQuestState {
  language: 'JAVA' | 'KOTLIN'
  problem: CodingProblem | null
  code: string
  result: CodingSubmissionResult | null
  showResult: boolean
  hints: string[]
  selectedCategory: string | null
}
