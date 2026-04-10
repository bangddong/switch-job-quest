import { ScoreRing } from '@/components/ui/ScoreRing'
import { GradeTag } from '@/components/ui/GradeTag'

interface ResultHeaderProps {
  score: number
  passed: boolean
  grade: string
  summary?: string
  developerType?: string
  passLabel?: string
  failLabel?: string
}

export function ResultHeader({
  score,
  passed,
  grade,
  summary,
  developerType,
  passLabel = '✓ 통과 — 퀘스트 완료!',
  failLabel = '✗ 미통과 — 재시도 가능',
}: ResultHeaderProps) {
  return (
    <div style={{ display: 'flex', gap: 18, alignItems: 'flex-start', marginBottom: 18 }}>
      <ScoreRing score={score} />
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
          <GradeTag grade={grade} />
          <span
            style={{ fontSize: 13, color: passed ? '#10B981' : '#EF4444', fontWeight: 'bold' }}
          >
            {passed ? passLabel : failLabel}
          </span>
        </div>
        {summary && (
          <p style={{ color: '#94A3B8', fontSize: 13, margin: 0, lineHeight: 1.6 }}>
            {summary}
          </p>
        )}
        {developerType && (
          <div style={{ marginTop: 6, fontSize: 12, color: '#A78BFA' }}>
            🧠 {developerType}
          </div>
        )}
      </div>
    </div>
  )
}
