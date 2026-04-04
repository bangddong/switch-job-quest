import { useState } from 'react'
import type { Act, Quest } from '@/types/quest.types'
import type { AiEvaluationResult, BossPackageResult } from '@/types/api.types'
import { QUEST_TYPE_CONFIG } from '@/features/quest-map'
import { AiCheckForm, AiResultCard, BossPackageResultCard, MockInterviewPanel } from '@/features/ai-check'
import { AI_FORMS } from '@/features/ai-check'
import { QUEST_NEXT } from '../constants/questConnections'
import { NextQuestCard } from './NextQuestCard'
import { RetryCoachCard } from './RetryCoachCard'
import { FinalBossView } from './FinalBossView'

interface QuestDetailProps {
  userId: string
  quest: Quest
  act: Act
  completed: Record<string, boolean>
  aiScores: Record<string, number>
  aiResult: AiEvaluationResult | BossPackageResult | null
  showForm: boolean
  onShowForm: () => void
  onAiResult: (result: AiEvaluationResult | BossPackageResult) => void
  onComplete: (questId: string, xp: number, actId: number, act: Act) => void
  onMockInterviewComplete: (score: number) => void
  onNextQuest?: (questId: string) => void
}

function extractImprovements(aiResult: AiEvaluationResult | BossPackageResult): string[] {
  if ('improvements' in aiResult && Array.isArray(aiResult.improvements)) {
    return (aiResult.improvements as Array<string | { suggestion?: string; issue?: string }>)
      .map((item) => {
        if (typeof item === 'string') return item
        return item.suggestion ?? item.issue ?? JSON.stringify(item)
      })
      .filter(Boolean)
  }
  return []
}

function isPassed(aiResult: AiEvaluationResult | BossPackageResult): boolean {
  return (aiResult as AiEvaluationResult).passed === true
}

export function QuestDetail({
  userId,
  quest,
  act,
  completed,
  aiScores,
  aiResult,
  showForm,
  onShowForm,
  onAiResult,
  onComplete,
  onMockInterviewComplete,
  onNextQuest,
}: QuestDetailProps) {
  const qc = QUEST_TYPE_CONFIG[quest.type]
  const done = !!completed[quest.id]
  const isFinalBoss = quest.id === '5-BOSS'
  const isMock = quest.id === '2-BOSS'
  const hasForm = quest.id in AI_FORMS
  const [lastSubmittedValues, setLastSubmittedValues] = useState<Record<string, unknown>>({})
  const [retryInitialValues, setRetryInitialValues] = useState<Record<string, unknown> | undefined>(undefined)

  const passed = aiResult ? isPassed(aiResult) : false
  const failed = aiResult !== null && !passed
  const improvements = aiResult ? extractImprovements(aiResult) : []
  const nextQuestInfo = QUEST_NEXT[quest.id]

  const handleRetry = () => {
    setRetryInitialValues(lastSubmittedValues)
    onShowForm()
  }

  if (isFinalBoss && !done) {
    return (
      <div style={{ animation: 'slideIn 0.4s ease' }}>
        <FinalBossView
          userId={userId}
          onComplete={(xp) => onComplete(quest.id, xp, act.id, act)}
        />
      </div>
    )
  }

  return (
    <div style={{ animation: 'slideIn 0.4s ease' }}>
      <div style={{ padding: '24px 0 14px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
          <div style={{ fontSize: 36 }}>{qc.icon}</div>
          <div>
            <div style={{ display: 'flex', gap: 7, marginBottom: 5 }}>
              <span
                style={{
                  fontSize: 9,
                  color: qc.border,
                  background: `${qc.border}12`,
                  padding: '2px 8px',
                  borderRadius: 4,
                }}
              >
                {quest.tag}
              </span>
              {quest.aiCheck && (
                <span
                  style={{
                    fontSize: 9,
                    color: '#A78BFA',
                    background: 'rgba(167,139,250,0.1)',
                    padding: '2px 8px',
                    borderRadius: 4,
                  }}
                >
                  🤖 Spring AI 검사
                </span>
              )}
            </div>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 'bold' }}>{quest.title}</h2>
            <p style={{ margin: '5px 0 0', fontSize: 13, color: '#475569', lineHeight: 1.5 }}>
              {quest.desc}
            </p>
          </div>
        </div>

        {/* XP Card */}
        <div
          style={{
            background: 'rgba(245,158,11,0.04)',
            border: '1px solid rgba(245,158,11,0.18)',
            borderRadius: 10,
            padding: '11px 15px',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <span style={{ fontSize: 18 }}>🎁</span>
          <div>
            <span style={{ fontSize: 14, color: '#F59E0B', fontWeight: 'bold' }}>+{quest.xp} XP</span>
            {quest.aiCheck && (
              <span style={{ fontSize: 11, color: '#64748B', marginLeft: 10 }}>
                · AI 통과 시 완료 처리
              </span>
            )}
          </div>
        </div>

        {/* Tasks */}
        <div
          style={{
            background: 'rgba(10,14,26,0.8)',
            border: `1px solid ${qc.border}18`,
            borderRadius: 10,
            padding: 16,
            marginBottom: 16,
          }}
        >
          <div style={{ fontSize: 9, letterSpacing: 4, color: '#1E293B', marginBottom: 10 }}>
            TASKS
          </div>
          {quest.tasks.map((task, i) => (
            <div
              key={i}
              style={{
                display: 'flex',
                gap: 9,
                marginBottom: i < quest.tasks.length - 1 ? 10 : 0,
              }}
            >
              <div
                style={{
                  minWidth: 20,
                  height: 20,
                  borderRadius: '50%',
                  background: `${qc.border}10`,
                  border: `1px solid ${qc.border}30`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 10,
                  color: qc.border,
                  fontWeight: 'bold',
                }}
              >
                {i + 1}
              </div>
              <div style={{ fontSize: 13, color: '#94A3B8', lineHeight: 1.6, paddingTop: 1 }}>
                {task}
              </div>
            </div>
          ))}
        </div>

        {/* AI Section */}
        {quest.aiCheck && !done && (
          <div>
            {isMock ? (
              <>
                <div style={{ fontSize: 10, color: '#EF4444', letterSpacing: 3, marginBottom: 12 }}>
                  ⚔️ BOSS BATTLE — AI 모의 기술 면접
                </div>
                <MockInterviewPanel onComplete={onMockInterviewComplete} />
              </>
            ) : hasForm ? (
              !showForm ? (
                <button
                  className="hov-btn"
                  onClick={onShowForm}
                  style={{
                    width: '100%',
                    padding: '12px',
                    background: 'rgba(167,139,250,0.08)',
                    border: '1px solid rgba(167,139,250,0.3)',
                    borderRadius: 10,
                    color: '#A78BFA',
                    fontSize: 14,
                    fontWeight: 'bold',
                    cursor: 'pointer',
                    fontFamily: "'Courier New', monospace",
                  }}
                >
                  🤖 AI 검사 시작하기
                </button>
              ) : (
                <AiCheckForm
                  questId={quest.id}
                  onResult={onAiResult}
                  initialValues={retryInitialValues}
                  onSubmit={(vals) => setLastSubmittedValues(vals)}
                />
              )
            ) : null}
          </div>
        )}

        {/* AI Result */}
        {aiResult && !isMock && (
          quest.id === '4-BOSS'
            ? <BossPackageResultCard result={aiResult as BossPackageResult} />
            : <AiResultCard result={aiResult as AiEvaluationResult} />
        )}

        {/* Feature E: Next quest card after passing */}
        {aiResult && !isMock && passed && nextQuestInfo && onNextQuest && (
          <NextQuestCard
            message={nextQuestInfo.message}
            onStart={() => onNextQuest(nextQuestInfo.questId)}
          />
        )}

        {/* Feature F: Retry coach after failing */}
        {aiResult && !isMock && failed && !showForm && (
          <RetryCoachCard
            improvements={improvements}
            onRetry={handleRetry}
          />
        )}

        {/* Manual complete */}
        {!quest.aiCheck && !done && (
          <button
            className="hov-btn"
            onClick={() => onComplete(quest.id, quest.xp, act.id, act)}
            style={{
              width: '100%',
              padding: '12px',
              background: `linear-gradient(135deg, ${act.color}, ${act.color}80)`,
              border: 'none',
              borderRadius: 10,
              color: '#060610',
              fontSize: 14,
              fontWeight: 'bold',
              cursor: 'pointer',
              fontFamily: "'Courier New', monospace",
            }}
          >
            🏆 완료로 표시하기
          </button>
        )}

        {/* Done state */}
        {done && (
          <div
            style={{
              textAlign: 'center',
              padding: 22,
              background: 'rgba(16,185,129,0.04)',
              border: '1px solid rgba(16,185,129,0.18)',
              borderRadius: 12,
            }}
          >
            <div style={{ fontSize: 30, marginBottom: 8 }}>✅</div>
            <div style={{ fontSize: 14, color: '#10B981', fontWeight: 'bold' }}>퀘스트 완료!</div>
            {aiScores[quest.id] != null && (
              <div style={{ fontSize: 12, color: '#475569', marginTop: 4 }}>
                AI 점수: {aiScores[quest.id]}/100
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
