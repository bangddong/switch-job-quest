import { useState, useCallback, useEffect } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult } from '@/types/api.types'
import { QuestMap } from '@/features/quest-map'
import { QuestDetail } from '@/features/quest-detail'
import { useUserId } from '@/hooks/useUserId'
import { fetchProgress, completeQuest } from '@/lib/apiClient'

type View = { kind: 'map' } | { kind: 'detail'; act: Act; quest: Quest }

export function App() {
  const userId = useUserId()
  const [view, setView] = useState<View>({ kind: 'map' })
  const [completed, setCompleted] = useState<Record<string, boolean>>({})
  const [aiScores, setAiScores] = useState<Record<string, number>>({})
  const [aiResult, setAiResult] = useState<AiEvaluationResult | null>(null)
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

  const handleSelectAct = (act: Act) => {
    if (act.quests.length > 0) {
      const quest = act.quests[0]!
      setAiResult(null)
      setShowForm(false)
      setView({ kind: 'detail', act, quest })
    }
  }

  const handleComplete = (questId: string, xp: number, actId: number) => {
    setCompleted((prev) => ({ ...prev, [questId]: true }))
    completeQuest(userId, questId, actId, xp).catch(() => {
      // 저장 실패해도 로컬 상태는 유지
    })
  }

  const handleAiResult = (result: AiEvaluationResult) => {
    setAiResult(result)
    if (view.kind === 'detail') {
      if (result.passed) {
        setCompleted((prev) => ({ ...prev, [view.quest.id]: true }))
        setAiScores((prev) => ({ ...prev, [view.quest.id]: result.score }))
      }
    }
  }

  const handleMockInterviewComplete = (score: number) => {
    if (view.kind === 'detail') {
      if (score >= 70) {
        setCompleted((prev) => ({ ...prev, [view.quest.id]: true }))
        setAiScores((prev) => ({ ...prev, [view.quest.id]: score }))
      }
    }
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
      {view.kind === 'detail' && (
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

      {view.kind === 'map' ? (
        <QuestMap
          onSelectAct={handleSelectAct}
          completed={completed}
          getActProgress={getActProgress}
        />
      ) : (
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
        />
      )}
    </div>
  )
}
