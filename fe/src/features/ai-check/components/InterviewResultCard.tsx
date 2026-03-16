import type { InterviewEvaluationResult } from '@/types/api.types'
import { ScoreRing } from '@/components/ui/ScoreRing'

interface InterviewResultCardProps {
  result: InterviewEvaluationResult
}

const SCORING_CRITERIA = [
  { label: '기술 정확성', key: 'technicalAccuracy' as const, max: 40, color: '#EF4444' },
  { label: '깊이/응용력', key: 'depthAndApplication' as const, max: 30, color: '#F59E0B' },
  { label: '실무 경험', key: 'practicalExperience' as const, max: 20, color: '#10B981' },
  { label: '커뮤니케이션', key: 'communicationClarity' as const, max: 10, color: '#4ECDC4' },
]

export function InterviewResultCard({ result }: InterviewResultCardProps) {
  return (
    <div
      style={{
        background: 'rgba(13,17,23,0.8)',
        border: '1px solid rgba(255,255,255,0.06)',
        borderRadius: 14,
        padding: 20,
        marginTop: 16,
        animation: 'slideIn 0.4s ease',
      }}
    >
      <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 18 }}>
        <ScoreRing score={result.score} size={72} />
        <div>
          <div
            style={{
              fontSize: 13,
              color: result.passed ? '#10B981' : '#EF4444',
              fontWeight: 'bold',
              marginBottom: 3,
            }}
          >
            {result.passed ? '✓ 통과' : '✗ 미통과'}
          </div>
          <div style={{ fontSize: 12, color: '#475569' }}>세부 채점 결과</div>
        </div>
      </div>

      {SCORING_CRITERIA.map((criteria) => {
        const val = result[criteria.key]
        return (
          <div key={criteria.label} style={{ marginBottom: 9 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 11,
                marginBottom: 3,
              }}
            >
              <span style={{ color: '#64748B' }}>{criteria.label}</span>
              <span style={{ color: criteria.color }}>
                {val}/{criteria.max}
              </span>
            </div>
            <div
              style={{ background: '#0F172A', borderRadius: 3, height: 5, overflow: 'hidden' }}
            >
              <div
                style={{
                  background: criteria.color,
                  height: '100%',
                  width: `${(val / criteria.max) * 100}%`,
                  transition: 'width 1s ease',
                  borderRadius: 3,
                }}
              />
            </div>
          </div>
        )
      })}

      {result.keyPointsMissed && result.keyPointsMissed.length > 0 && (
        <div style={{ marginTop: 14, marginBottom: 12 }}>
          <div style={{ fontSize: 10, color: '#EF4444', letterSpacing: 3, marginBottom: 6 }}>
            ⚠ MISSED
          </div>
          {result.keyPointsMissed.map((p, i) => (
            <div key={i} style={{ fontSize: 12, color: '#64748B', marginBottom: 3 }}>
              <span style={{ color: '#EF4444' }}>✗ </span>
              {p}
            </div>
          ))}
        </div>
      )}

      {result.correctAnswer && (
        <div
          style={{
            background: 'rgba(16,185,129,0.04)',
            border: '1px solid rgba(16,185,129,0.2)',
            borderRadius: 8,
            padding: 12,
          }}
        >
          <div style={{ fontSize: 10, color: '#10B981', letterSpacing: 3, marginBottom: 6 }}>
            📖 모범 답안
          </div>
          <p style={{ color: '#64748B', fontSize: 12, margin: 0, lineHeight: 1.6 }}>
            {result.correctAnswer}
          </p>
        </div>
      )}
    </div>
  )
}
