import { useState, useCallback, useEffect } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult, BossPackageResult, ActClearReportResult } from '@/types/api.types'
import type { Character } from '@/types/character.types'
import { QuestMap } from '@/features/quest-map'
import { QuestDetail } from '@/features/quest-detail'
import { ActClearReportCard } from '@/features/ai-check'
import { CharacterCreate } from '@/features/character'
import { InterviewCoach } from '@/features/interview-coach'
import { GrowthDashboard } from '@/features/growth'
import { useUserId } from '@/hooks/useUserId'
import { useCharacter } from '@/hooks/useCharacter'
import { fetchProgress, completeQuest, fetchActClearReport } from '@/lib/apiClient'
import { ACTS } from '@/features/quest-map/constants/questData'

type View = { kind: 'map' } | { kind: 'detail'; act: Act; quest: Quest } | { kind: 'act-clear'; act: Act; report: ActClearReportResult } | { kind: 'interview-coach' } | { kind: 'growth' }

export function App() {
  const userId = useUserId()
  const { character, setCharacter } = useCharacter()
  const [view, setView] = useState<View>({ kind: 'map' })
  const [completed, setCompleted] = useState<Record<string, boolean>>({})
  const [aiScores, setAiScores] = useState<Record<string, number>>({})
  const [aiResult, setAiResult] = useState<AiEvaluationResult | BossPackageResult | null>(null)
  const [showForm, setShowForm] = useState(false)

  useEffect(() => {
    fetchProgress(userId)
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
      })
      .catch(() => {
        // 서버 미응답 시 로컬 상태로 계속 진행
      })
  }, [userId])

  const getActProgress = useCallback(
    (act: Act) => {
      const done = act.quests.filter((q) => completed[q.id]).length
      return act.quests.length > 0 ? Math.round((done / act.quests.length) * 100) : 0
    },
    [completed],
  )

  const handleCharacterComplete = (c: Character) => {
    setCharacter(c)
    setView({ kind: 'map' })
  }

  const handleSelectAct = (act: Act) => {
    if (act.quests.length > 0) {
      const quest = act.quests[0]!
      setAiResult(null)
      setShowForm(false)
      setView({ kind: 'detail', act, quest })
    }
  }

  const triggerActClearReport = (act: Act) => {
    fetchActClearReport(userId, act.id, `${act.title} ${act.subtitle}`)
      .then((report) => setView({ kind: 'act-clear', act, report }))
      .catch(() => setView({ kind: 'map' }))
  }

  const isBossQuest = (questId: string) => questId.endsWith('-BOSS')

  const handleComplete = (questId: string, xp: number, actId: number, act: Act) => {
    setCompleted((prev) => ({ ...prev, [questId]: true }))
    completeQuest(userId, questId, actId, xp).catch(() => {})
    if (isBossQuest(questId)) triggerActClearReport(act)
  }

  const handleAiResult = (result: AiEvaluationResult | BossPackageResult) => {
    setAiResult(result)
    if (view.kind === 'detail') {
      const { quest, act } = view
      const isBoss = quest.id === '4-BOSS'
      const score = isBoss
        ? (result as BossPackageResult).overallScore
        : (result as AiEvaluationResult).score
      const passed = isBoss
        ? (result as BossPackageResult).overallScore >= 70
        : (result as AiEvaluationResult).passed
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
      <div
        style={{
          maxWidth: 480,
          margin: '0 auto',
          padding: '0 20px',
          minHeight: '100vh',
          fontFamily: "'Courier New', monospace",
          color: '#F8FAFC',
        }}
      >
        <CharacterCreate onComplete={handleCharacterComplete} />
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
      {(view.kind === 'detail' || view.kind === 'act-clear' || view.kind === 'interview-coach' || view.kind === 'growth') && (
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
            onOpenCoach={() => setView({ kind: 'interview-coach' })}
            completed={completed}
            getActProgress={getActProgress}
            character={character}
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
        <InterviewCoach userId={userId} />
      )}

      {view.kind === 'growth' && (
        <GrowthDashboard userId={userId} />
      )}
    </div>
  )
}
