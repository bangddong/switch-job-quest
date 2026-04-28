import { useState, useCallback, useEffect } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult, ActClearReportResult } from '@/types/api.types'
import type { Character } from '@/types/character.types'
import { QuestMap } from '@/features/quest-map'
import { QuestDetail, QuestBriefingView } from '@/features/quest-detail'
import { ActClearReportCard } from '@/features/ai-check'
import { CharacterCreate, OnboardingIntro } from '@/features/character'
import { InterviewCoach } from '@/features/interview-coach'
import { GrowthDashboard } from '@/features/growth'
import { useAuth } from '@/hooks/useAuth'
import { LoginPage } from '@/features/auth/components/LoginPage'
import { AuthCallback } from '@/features/auth/components/AuthCallback'
import { useCharacter } from '@/hooks/useCharacter'
import { fetchProgress, completeQuest, fetchActClearReport } from '@/lib/apiClient'
import { ACTS } from '@/features/quest-map/constants/questData'

const PROGRESS_CACHE_KEY = 'devquest-progress'

interface ProgressCache {
  completed: Record<string, boolean>
  aiScores: Record<string, number>
  aiResults: Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult>
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
  const [aiResult, setAiResult] = useState<AiEvaluationResult | BossPackageResult | DeveloperClassResult | null>(null)
  const [aiResults, setAiResults] = useState<Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult>>(
    INITIAL_PROGRESS_CACHE?.aiResults ?? {}
  )
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
      const aiResultsMap: Record<string, AiEvaluationResult | BossPackageResult | DeveloperClassResult> = {}
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
    }
  }, [isLoggedIn])

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

  // Unauthenticated
  if (!isLoggedIn) {
    return <LoginPage />
  }

  const handleCharacterComplete = (c: Character) => {
    setCharacter(c)
    setView({ kind: 'map' })
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

  const handleComplete = (questId: string, xp: number, actId: number, act: Act) => {
    setCompleted((prev) => ({ ...prev, [questId]: true }))
    completeQuest(questId, actId, xp).catch(() => {})
    if (isBossQuest(questId)) triggerActClearReport(act)
  }

  const handleAiResult = (result: AiEvaluationResult | BossPackageResult | DeveloperClassResult) => {
    setAiResult(result)
    setShowForm(false)
    if (view.kind === 'detail') {
      const { quest, act } = view
      setAiResults((prev) => ({ ...prev, [quest.id]: result }))
      const isBossPackage = quest.id === '4-BOSS'
      const isDeveloperClass = 'developerClass' in result
      const score = isBossPackage || isDeveloperClass
        ? (result as BossPackageResult | DeveloperClassResult).overallScore
        : (result as AiEvaluationResult).score
      const passed = result.passed
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

  const handleMockInterviewComplete = (score: number) => {
    if (view.kind === 'detail') {
      const { quest, act } = view
      if (score >= 70) {
        setCompleted((prev) => ({ ...prev, [quest.id]: true }))
        setAiScores((prev) => ({ ...prev, [quest.id]: score }))
        completeQuest(quest.id, act.id, quest.xp).catch(() => {})
        if (isBossQuest(quest.id)) triggerActClearReport(act)
      }
    }
  }

  if (character === null) {
    return (
      <div style={{ maxWidth: 480, margin: '0 auto', padding: '0 20px', minHeight: '100vh', fontFamily: "'Courier New', monospace", color: '#F8FAFC' }}>
        {showIntro
          ? <OnboardingIntro onComplete={() => setShowIntro(false)} />
          : <CharacterCreate onComplete={handleCharacterComplete} />
        }
      </div>
    )
  }

  if (progressLoading && INITIAL_PROGRESS_CACHE === null) {
    return (
      <div
        role="status"
        aria-live="polite"
        aria-busy="true"
        style={{
          maxWidth: 480,
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
        <p style={{ color: '#4ECDC4', fontSize: 14, margin: 0 }}>서버 기동 중...</p>
        <p style={{ color: '#475569', fontSize: 12, margin: 0 }}>진척 데이터를 불러오는 중입니다 (최대 25초)</p>
      </div>
    )
  }

  return (
    <div
      style={{
        maxWidth: 480,
        margin: '0 auto',
        padding: '0 20px 40px',
        minHeight: '100vh',
        fontFamily: "'Courier New', monospace",
        color: '#F8FAFC',
      }}
    >
      {(view.kind === 'detail' || view.kind === 'act-clear' || view.kind === 'interview-coach' || view.kind === 'growth' || view.kind === 'briefing') && (
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
          />
          <div style={{ marginTop: 16, textAlign: 'center' }}>
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
          quest={view.quest}
          act={view.act}
          completed={completed}
          aiScores={aiScores}
          aiResult={aiResult}
          showForm={showForm}
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
    </div>
  )
}
