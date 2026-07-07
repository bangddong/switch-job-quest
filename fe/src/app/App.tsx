import { useState, useCallback, useEffect } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult, JdAnalysisResult, ActClearReportResult, ResumeCheckResult, CodingQuestState, AppliedCompany, ApplicationStatus } from '@/types/api.types'
import type { Character } from '@/types/character.types'
import { QuestMap } from '@/features/quest-map'
import { QuestDetail, QuestBriefingView } from '@/features/quest-detail'
import { ActClearReportCard } from '@/features/ai-check'
import { CharacterCreate, OnboardingIntro } from '@/features/character'
import { InterviewCoach } from '@/features/interview-coach'
import { GrowthDashboard } from '@/features/growth'
import { SettingsPage } from '@/features/settings'
import { ResumeProfilePage } from '@/features/resume'
import { TechInterviewPage, TechInterviewDemoPage, DailyQuestionPage } from '@/features/tech-interview'
import { CodingQuestPage, CodingRoadmapPage } from '@/features/coding-quest'
import { useAuth } from '@/hooks/useAuth'
import { LoginPage } from '@/features/auth/components/LoginPage'
import { AuthCallback } from '@/features/auth/components/AuthCallback'
import { useCharacter } from '@/hooks/useCharacter'
import { fetchProgress, completeQuest, fetchActClearReport } from '@/lib/apiClient'
import { getCompanies, createCompany, updateCompanyStatus, deleteCompany, analyzeCompany } from '@/features/company-pipeline'
import { STORAGE_KEYS } from '@/lib/storageKeys'
import { ACTS } from '@/features/quest-map/constants/questData'

const PROGRESS_CACHE_KEY = STORAGE_KEYS.PROGRESS

interface ProgressCache {
  completed: Record<string, boolean>
  aiScores: Record<string, number>
  aiResults: Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult | ResumeCheckResult>
  lastCompletedAt: string | null
}

function loadProgressCache(): ProgressCache | null {
  try {
    const raw = localStorage.getItem(PROGRESS_CACHE_KEY)
    return raw ? (JSON.parse(raw) as ProgressCache) : null
  } catch {
    return null
  }
}

function saveProgressCache(cache: ProgressCache): void {
  try {
    localStorage.setItem(PROGRESS_CACHE_KEY, JSON.stringify(cache))
  } catch {
    // ignore
  }
}

// 모듈 로드 시 1회 읽음 (컴포넌트 리렌더와 무관)
const INITIAL_PROGRESS_CACHE = loadProgressCache()

type View =
  | { kind: 'map' }
  | { kind: 'briefing'; act: Act; quest: Quest }
  | { kind: 'detail'; act: Act; quest: Quest }
  | { kind: 'act-clear'; act: Act; report: ActClearReportResult }
  | { kind: 'interview-coach' }
  | { kind: 'growth' }
  | { kind: 'settings' }
  | { kind: 'resume' }
  | { kind: 'tech-interview' }
  | { kind: 'tech-interview-demo' }
  | { kind: 'coding-roadmap' }
  | { kind: 'coding-quest'; category: string }

export function App() {
  const { isLoggedIn } = useAuth()
  const { character, setCharacter } = useCharacter()
  const [view, setView] = useState<View>({ kind: 'map' })
  const [completed, setCompleted] = useState<Record<string, boolean>>(
    INITIAL_PROGRESS_CACHE?.completed ?? {}
  )
  const [lastCompletedAt, setLastCompletedAt] = useState<string | null>(
    INITIAL_PROGRESS_CACHE?.lastCompletedAt ?? null
  )
  const [aiScores, setAiScores] = useState<Record<string, number>>(
    INITIAL_PROGRESS_CACHE?.aiScores ?? {}
  )
  const [aiResult, setAiResult] = useState<AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult | ResumeCheckResult | null>(null)
  const [aiResults, setAiResults] = useState<Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult | ResumeCheckResult>>(
    INITIAL_PROGRESS_CACHE?.aiResults ?? {}
  )
  const [codingQuestState, setCodingQuestState] = useState<CodingQuestState | null>(null)
  const [companies, setCompanies] = useState<AppliedCompany[]>([])
  const [showForm, setShowForm] = useState(false)
  const [showIntro, setShowIntro] = useState(true)
  const [progressLoading, setProgressLoading] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) return

    const MAX_RETRIES = 6
    const RETRY_INTERVAL_MS = 5000

    const applyProgress = (progress: Awaited<ReturnType<typeof fetchProgress>>) => {
      const completedMap: Record<string, boolean> = {}
      const scoresMap: Record<string, number> = {}
      const aiResultsMap: Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult | ResumeCheckResult> = {}
      progress.completedQuests.forEach((id) => {
        completedMap[id] = true
      })
      Object.entries(progress.questDetails).forEach(([id, detail]) => {
        if (detail.score > 0) scoresMap[id] = detail.score
      })
      Object.entries(progress.questDetails).forEach(([id, detail]) => {
        if (detail.aiEvaluationJson) {
          try {
            const parsed = JSON.parse(detail.aiEvaluationJson)
            aiResultsMap[id] = parsed
          } catch { /* 파싱 실패 무시 */ }
        }
      })
      setCompleted(completedMap)
      setAiScores(scoresMap)
      setAiResults(aiResultsMap)
      setLastCompletedAt(progress.lastCompletedAt ?? null)
      saveProgressCache({
        completed: completedMap,
        aiScores: scoresMap,
        aiResults: aiResultsMap,
        lastCompletedAt: progress.lastCompletedAt ?? null,
      })
    }

    let cancelled = false

    const fetchWithRetry = async () => {
      setProgressLoading(true)
      for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
        try {
          const progress = await fetchProgress()
          if (!cancelled) {
            applyProgress(progress)
            setProgressLoading(false)
          }
          return
        } catch {
          if (cancelled) return
          if (attempt < MAX_RETRIES - 1) {
            await new Promise<void>((resolve) => setTimeout(resolve, RETRY_INTERVAL_MS))
          }
        }
      }
      // 6회 모두 실패 — 빈 상태로 계속 진행
      if (!cancelled) setProgressLoading(false)
    }

    fetchWithRetry()

    return () => {
      cancelled = true
    }
  }, [isLoggedIn])

  useEffect(() => {
    if (!isLoggedIn) return
    saveProgressCache({ completed, aiScores, aiResults, lastCompletedAt })
  }, [completed, aiScores, aiResults, lastCompletedAt, isLoggedIn])

  useEffect(() => {
    if (!isLoggedIn) {
      localStorage.removeItem(PROGRESS_CACHE_KEY)
      setCompanies([])
    }
  }, [isLoggedIn])

  useEffect(() => {
    if (!isLoggedIn) return
    getCompanies()
      .then(setCompanies)
      .catch(() => { /* 조회 실패 시 빈 배열 유지 */ })
  }, [isLoggedIn])

  useEffect(() => {
    if (isLoggedIn && view.kind === 'tech-interview-demo') {
      setView({ kind: 'tech-interview' })
    }
  }, [isLoggedIn, view.kind])

  const getActProgress = useCallback(
    (act: Act) => {
      const done = act.quests.filter((q) => completed[q.id]).length
      return act.quests.length > 0 ? Math.round((done / act.quests.length) * 100) : 0
    },
    [completed],
  )

  // OAuth callback route
  if (window.location.pathname === '/auth/callback') {
    return <AuthCallback />
  }

  // Daily question route (no auth required)
  if (window.location.pathname === '/daily-question') {
    return (
      <div style={{ background: '#060610', minHeight: '100vh' }}>
        <div style={{ maxWidth: 640, margin: '0 auto', padding: '0 20px 40px' }}>
          <DailyQuestionPage onLogin={() => { window.location.href = '/' }} />
        </div>
      </div>
    )
  }

  // Unauthenticated
  if (!isLoggedIn) {
    if (view.kind === 'tech-interview-demo') {
      return (
        <div style={{ background: '#060610', minHeight: '100vh' }}>
          <div style={{ maxWidth: 640, margin: '0 auto', padding: '0 20px 40px' }}>
            <button
              onClick={() => setView({ kind: 'map' })}
              style={{
                background: 'none', border: 'none', color: '#475569',
                cursor: 'pointer', fontSize: 13, padding: '16px 0 0',
                fontFamily: "'Courier New', monospace",
              }}
            >
              ← 로그인 페이지로
            </button>
            <TechInterviewDemoPage onLogin={() => setView({ kind: 'map' })} />
          </div>
        </div>
      )
    }
    return <LoginPage onTryDemo={() => setView({ kind: 'tech-interview-demo' })} />
  }

  const handleCharacterComplete = (c: Character) => {
    setCharacter(c)
    setView({ kind: 'map' })
  }

  const handleAddCompany = async (data: { companyName: string; position: string; jdUrl?: string; jobDescription?: string }) => {
    const added = await createCompany(data)
    setCompanies((prev) => [added, ...prev])
  }

  const handleAnalyzeCompany = async (id: number, skills: string[], experiences: string[]) => {
    return analyzeCompany(id, { userSkills: skills, userExperiences: experiences })
  }

  const handleCompanyStatusChange = async (id: number, status: ApplicationStatus) => {
    const updated = await updateCompanyStatus(id, status)
    setCompanies((prev) => prev.map((c) => (c.id === id ? updated : c)))
  }

  const handleDeleteCompany = async (id: number) => {
    await deleteCompany(id)
    setCompanies((prev) => prev.filter((c) => c.id !== id))
  }

  const handleSelectAct = (act: Act) => {
    if (act.quests.length > 0) {
      const quest = act.quests.find((q) => !completed[q.id]) ?? act.quests[act.quests.length - 1]!
      setAiResult(aiResults[quest.id] ?? null)
      setShowForm(false)
      setView({ kind: 'briefing', act, quest })
    }
  }

  const handleSelectQuest = (act: Act, quest: Quest) => {
    setAiResult(aiResults[quest.id] ?? null)
    setShowForm(false)
    setView({ kind: 'briefing', act, quest })
  }

  const handleBriefingStart = () => {
    if (view.kind === 'briefing') {
      setView({ kind: 'detail', act: view.act, quest: view.quest })
    }
  }

  const triggerActClearReport = (act: Act) => {
    fetchActClearReport(act.id, `${act.title} ${act.subtitle}`)
      .then((report) => setView({ kind: 'act-clear', act, report }))
      .catch(() => setView({ kind: 'map' }))
  }

  const isBossQuest = (questId: string) => questId.endsWith('-BOSS')

  const handleComplete = async (questId: string, xp: number, actId: number, act: Act) => {
    setCompleted((prev) => ({ ...prev, [questId]: true }))
    try {
      await completeQuest(questId, actId, xp)
      if (isBossQuest(questId)) triggerActClearReport(act)
    } catch {
      setCompleted((prev) => ({ ...prev, [questId]: false }))
      alert('퀘스트 완료 저장에 실패했습니다. 다시 시도해 주세요.')
    }
  }

  const handleAiResult = (result: AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult | ResumeCheckResult) => {
    setAiResult(result)
    setShowForm(false)
    if (view.kind === 'detail') {
      const { quest, act } = view
      setAiResults((prev) => ({ ...prev, [quest.id]: result }))
      const isJdAnalysis = 'overallMatchScore' in result && !('developerClass' in result) && quest.id !== '4-BOSS'
      const isBossPackage = quest.id === '4-BOSS'
      const isDeveloperClass = 'developerClass' in result
      const score = isJdAnalysis
        ? (result as JdAnalysisResult).overallMatchScore
        : isBossPackage || isDeveloperClass
          ? (result as BossPackageResult | DeveloperClassResult).overallScore
          : (result as AiEvaluationResult).score ?? (result as { overallScore?: number }).overallScore ?? 0
      const passed = (result as { passed: boolean }).passed
      if (passed) {
        setCompleted((prev) => ({ ...prev, [quest.id]: true }))
        setAiScores((prev) => ({ ...prev, [quest.id]: score }))
        if (isBossQuest(quest.id)) triggerActClearReport(act)
      }
    }
  }

  const handleNextQuest = (questId: string) => {
    for (const act of ACTS) {
      const quest = act.quests.find((q) => q.id === questId)
      if (quest) {
        setAiResult(aiResults[quest.id] ?? null)
        setShowForm(false)
        setView({ kind: 'detail', act, quest })
        return
      }
    }
  }

  const handleMockInterviewComplete = async (score: number) => {
    if (view.kind === 'detail') {
      const { quest, act } = view
      if (score >= 70) {
        setCompleted((prev) => ({ ...prev, [quest.id]: true }))
        setAiScores((prev) => ({ ...prev, [quest.id]: score }))
        try {
          await completeQuest(quest.id, act.id, quest.xp)
          if (isBossQuest(quest.id)) triggerActClearReport(act)
        } catch {
          setCompleted((prev) => ({ ...prev, [quest.id]: false }))
          alert('퀘스트 완료 저장에 실패했습니다. 다시 시도해 주세요.')
        }
      }
    }
  }

  if (character === null) {
    return (
      <div style={{ maxWidth: 640, margin: '0 auto', padding: '0 20px', minHeight: '100vh', fontFamily: "'Courier New', monospace", color: '#F8FAFC' }}>
        {showIntro
          ? <OnboardingIntro onComplete={() => setShowIntro(false)} />
          : <CharacterCreate onComplete={handleCharacterComplete} />
        }
      </div>
    )
  }

  if (progressLoading) {
    const hasCache = INITIAL_PROGRESS_CACHE !== null
    return (
      <div
        role="status"
        aria-live="polite"
        aria-busy="true"
        style={{
          maxWidth: 640,
          margin: '0 auto',
          padding: '0 20px',
          minHeight: '100vh',
          fontFamily: "'Courier New', monospace",
          color: '#F8FAFC',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
        }}
      >
        <p style={{ color: '#4ECDC4', fontSize: 14, margin: 0 }}>
          {hasCache ? '최신 데이터 동기화 중...' : '서버 기동 중...'}
        </p>
        {!hasCache && (
          <p style={{ color: '#475569', fontSize: 12, margin: 0 }}>진척 데이터를 불러오는 중입니다 (최대 25초)</p>
        )}
      </div>
    )
  }

  if (view.kind === 'coding-roadmap') {
    return (
      <CodingRoadmapPage
        onBack={() => setView({ kind: 'map' })}
        onSelectCategory={(category) => setView({ kind: 'coding-quest', category })}
      />
    )
  }

  if (view.kind === 'coding-quest') {
    return (
      <CodingQuestPage
        onBack={() => setView({ kind: 'coding-roadmap' })}
        savedState={codingQuestState}
        onStateChange={setCodingQuestState}
        category={view.category}
      />
    )
  }

  return (
    <div
      style={{
        maxWidth: 640,
        margin: '0 auto',
        padding: '0 20px 40px',
        minHeight: '100vh',
        fontFamily: "'Courier New', monospace",
        color: '#F8FAFC',
      }}
    >
      {(view.kind === 'detail' || view.kind === 'act-clear' || view.kind === 'interview-coach' || view.kind === 'growth' || view.kind === 'briefing' || view.kind === 'settings' || view.kind === 'tech-interview' || view.kind === 'resume') && (
        <button
          onClick={() => setView({ kind: 'map' })}
          style={{
            background: 'none',
            border: 'none',
            color: '#475569',
            cursor: 'pointer',
            fontSize: 13,
            padding: '16px 0 0',
            fontFamily: "'Courier New', monospace",
          }}
        >
          ← 퀘스트 맵으로
        </button>
      )}

      {view.kind === 'map' && (
        <>
          <QuestMap
            onSelectAct={handleSelectAct}
            onSelectQuest={handleSelectQuest}
            onOpenCoach={() => setView({ kind: 'interview-coach' })}
            completed={completed}
            getActProgress={getActProgress}
            character={character}
            lastCompletedAt={lastCompletedAt}
            companies={companies}
            onAddCompany={handleAddCompany}
            onCompanyStatusChange={handleCompanyStatusChange}
            onDeleteCompany={handleDeleteCompany}
            onAnalyzeCompany={handleAnalyzeCompany}
          />
          <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button
              onClick={() => setView({ kind: 'coding-roadmap' })}
              style={{
                background: 'rgba(167,139,250,0.1)',
                border: '1px solid rgba(167,139,250,0.3)',
                color: '#A78BFA',
                cursor: 'pointer',
                fontSize: 13,
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
                width: '100%',
              }}
            >
              💻 코딩 연습
            </button>
            <button
              onClick={() => setView({ kind: 'growth' })}
              style={{
                background: 'rgba(78,205,196,0.1)',
                border: '1px solid rgba(78,205,196,0.3)',
                color: '#4ECDC4',
                cursor: 'pointer',
                fontSize: 13,
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
                width: '100%',
              }}
            >
              📈 성장 기록
            </button>
            <button
              onClick={() => setView({ kind: 'tech-interview' })}
              style={{
                background: 'rgba(96,165,250,0.1)',
                border: '1px solid rgba(96,165,250,0.3)',
                color: '#60A5FA',
                cursor: 'pointer',
                fontSize: 13,
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
                width: '100%',
              }}
            >
              💻 기술 면접 연습
            </button>
            <button
              onClick={() => setView({ kind: 'settings' })}
              style={{
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                color: '#475569',
                cursor: 'pointer',
                fontSize: 13,
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
                width: '100%',
              }}
            >
              ⚙️ 설정
            </button>
            <button
              onClick={() => setView({ kind: 'resume' })}
              style={{
                background: 'rgba(245,158,11,0.1)',
                border: '1px solid rgba(245,158,11,0.3)',
                color: '#F59E0B',
                cursor: 'pointer',
                fontSize: 13,
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
                width: '100%',
              }}
            >
              📄 이력서 관리
            </button>
          </div>
        </>
      )}

      {view.kind === 'briefing' && (
        <QuestBriefingView
          quest={view.quest}
          act={view.act}
          onStart={handleBriefingStart}
        />
      )}

      {view.kind === 'detail' && (
        <QuestDetail
          key={view.quest.id}
          quest={view.quest}
          act={view.act}
          completed={completed}
          aiScores={aiScores}
          aiResult={aiResult}
          showForm={showForm}
          character={character}
          onShowForm={() => setShowForm(true)}
          onAiResult={handleAiResult}
          onComplete={handleComplete}
          onMockInterviewComplete={handleMockInterviewComplete}
          onNextQuest={handleNextQuest}
        />
      )}

      {view.kind === 'act-clear' && (
        <ActClearReportCard
          report={view.report}
          actColor={view.act.color}
          onContinue={() => setView({ kind: 'map' })}
        />
      )}

      {view.kind === 'interview-coach' && (
        <InterviewCoach />
      )}

      {view.kind === 'growth' && (
        <GrowthDashboard />
      )}

      {view.kind === 'settings' && (
        <SettingsPage />
      )}

      {view.kind === 'tech-interview' && (
        <TechInterviewPage />
      )}

      {view.kind === 'resume' && (
        <ResumeProfilePage />
      )}

    </div>
  )
}
