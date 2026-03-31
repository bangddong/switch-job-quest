import type { CoachSessionResult } from '../types/coach.types'
import { CoachBubble } from './CoachBubble'

interface CoachAnalysisProps {
  session: CoachSessionResult
  targetRole: string
  onBegin: () => void
}

export function CoachAnalysis({ session, targetRole, onBegin }: CoachAnalysisProps) {
  return (
    <div>
      <CoachBubble
        message={`JD 분석을 완료했습니다!\n\n${session.jdSummary}`}
      />

      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: 16,
          marginBottom: 16,
        }}
      >
        <p style={{ fontSize: 11, color: '#4ECDC4', margin: '0 0 10px', letterSpacing: 1 }}>
          핵심 역량
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {session.keyCompetencies.map((c, i) => (
            <span
              key={i}
              style={{
                background: 'rgba(167,139,250,0.15)',
                border: '1px solid rgba(167,139,250,0.3)',
                borderRadius: 20,
                padding: '4px 12px',
                fontSize: 12,
                color: '#A78BFA',
              }}
            >
              {c}
            </span>
          ))}
        </div>
      </div>

      <CoachBubble
        message={`${targetRole} 직무에 맞는 면접 질문 ${session.questions.length}개를 준비했습니다.\n\n준비되셨으면 면접을 시작해볼까요? 각 질문에 성실하게 답변해주시면 즉시 피드백을 드리겠습니다.`}
      />

      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: 16,
          marginBottom: 20,
        }}
      >
        <p style={{ fontSize: 11, color: '#475569', margin: '0 0 12px', letterSpacing: 1 }}>
          오늘의 면접 질문 목록
        </p>
        {session.questions.map((q) => (
          <div
            key={q.index}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '6px 0',
              borderBottom: '1px solid rgba(255,255,255,0.04)',
            }}
          >
            <span
              style={{
                width: 22,
                height: 22,
                borderRadius: '50%',
                background: 'rgba(78,205,196,0.1)',
                border: '1px solid rgba(78,205,196,0.3)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 11,
                color: '#4ECDC4',
                flexShrink: 0,
              }}
            >
              {q.index + 1}
            </span>
            <span style={{ fontSize: 12, color: '#475569' }}>{q.competency} 관련 질문</span>
          </div>
        ))}
      </div>

      <button
        onClick={onBegin}
        style={{
          width: '100%',
          background: 'linear-gradient(135deg, #4ECDC4, #A78BFA)',
          border: 'none',
          borderRadius: 8,
          padding: '12px 24px',
          color: '#060610',
          fontSize: 14,
          fontWeight: 700,
          fontFamily: "'Courier New', monospace",
          cursor: 'pointer',
        }}
      >
        면접 시작하기 →
      </button>
    </div>
  )
}
