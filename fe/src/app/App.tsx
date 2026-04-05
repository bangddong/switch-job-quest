import { useState, useCallback, useEffect } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult, BossPackageResult, ActClearReportResult } from '@/types/api.types'
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
  const [completed, setCompleted] = useState<Record<string, boolean>>({})
  const [lastCompletedAt, setLastCompletedAt] = useState<string | null>(null)
  const [aiScores, setAiScores] = useState<Record<string, number>>({})
  const [aiResult, setAiResult] = useState<AiEvaluationResult | BossPackageResult | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [showIntro, setShowIntro] = useState(true)

  useEffect(() => {
    if (!isLoggedIn) return
    fetchProgress()
      .then((progress) => {
        const completedMap: Record<string, boolean> = {}
        const scoresMap: Record<string, number> = {}
        progress.completedQuests.forEach((id) => {
          completedMap[id] = true
        })
        Object.entries(progress.questDetails).forEach(([id, detail]) => {
          if (detail.score > 0) scoresMap[id] = detail.score
        })
        setCompleted(completedMap)
        setAiScores(scoresMap)
        if (progress.lastCompletedAt) setLastCompletedAt(progress.lastCompletedAt)
      })
      .catch(() => {
        // 서버 미응답 시 로컬 상태로 계속 진행
      })
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
      setAiResult(null)
      setShowForm(false)
      setView({ kind: 'briefing', act, quest })
    }
  }

  const handleSelectQuest = (act: Act, quest: Quest) => {
    setAiResult(null)
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

  const handleAiResult = (result: AiEvaluationResult | BossPackageResult) => {
    setAiResult(result)
    setShowForm(false)
    if (view.kind === 'detail') {
      const { quest, act } = view
      const isBoss = quest.id === '4-BOSS'
      const score = isBoss
        ? (result as BossPackageResult).overallScore
        : (result as AiEvaluationResult).score
      const passed = (result as AiEvaluationResult | BossPackageResult).passed
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
        setAiResult(null)
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
