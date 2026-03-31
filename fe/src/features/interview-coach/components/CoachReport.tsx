import type { CoachReportResult } from '../types/coach.types'
import { CoachBubble } from './CoachBubble'

interface CoachReportProps {
  report: CoachReportResult
  targetRole: string
  onRestart: () => void
}

export function CoachReport({ report, targetRole, onRestart }: CoachReportProps) {
  const likelihood = report.passLikelihood
  const likelihoodColor =
    likelihood >= 80 ? '#10B981' : likelihood >= 60 ? '#F59E0B' : '#EF4444'

  return (
    <div>
      <CoachBubble
        message={`오늘 면접 준비 정말 수고하셨습니다! 🎉\n\n${report.finalAdvice}`}
      />

      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: 20,
          marginBottom: 16,
          textAlign: 'center',
        }}
      >
        <p style={{ fontSize: 11, color: '#475569', margin: '0 0 8px', letterSpacing: 1 }}>
          {targetRole} 합격 가능성
        </p>
        <p style={{ fontSize: 48, fontWeight: 700, color: likelihoodColor, margin: '0 0 4px' }}>
          {likelihood}%
        </p>
        <p style={{ fontSize: 12, color: '#475569', margin: 0 }}>
          종합 점수 {report.overallScore}점
        </p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <div
          style={{
            flex: 1,
            background: 'rgba(16,185,129,0.08)',
            border: '1px solid rgba(16,185,129,0.2)',
            borderRadius: 8,
            padding: 14,
          }}
        >
          <p style={{ fontSize: 11, color: '#10B981', margin: '0 0 10px', letterSpacing: 1 }}>
            강점
          </p>
          {report.strengths.map((s, i) => (
            <p key={i} style={{ fontSize: 12, color: '#F1F5F9', margin: '4px 0', lineHeight: 1.5 }}>
              ✓ {s}
            </p>
          ))}
        </div>

        <div
          style={{
            flex: 1,
            background: 'rgba(239,68,68,0.08)',
            border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: 8,
            padding: 14,
          }}
        >
          <p style={{ fontSize: 11, color: '#EF4444', margin: '0 0 10px', letterSpacing: 1 }}>
            보완점
          </p>
          {report.weaknesses.map((w, i) => (
            <p key={i} style={{ fontSize: 12, color: '#F1F5F9', margin: '4px 0', lineHeight: 1.5 }}>
              △ {w}
            </p>
          ))}
        </div>
      </div>

      <button
        onClick={onRestart}
        style={{
          width: '100%',
          background: 'rgba(255,255,255,0.05)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: '12px 24px',
          color: '#94A3B8',
          fontSize: 14,
          fontFamily: "'Courier New', monospace",
          cursor: 'pointer',
        }}
      >
        다시 시작하기
      </button>
    </div>
  )
}
