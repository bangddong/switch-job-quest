import type { ActClearReportResult } from '@/types/api.types'
import { GRADE_COLORS } from '../../../utils/gradeUtils'

interface ActClearReportCardProps {
  report: ActClearReportResult
  actColor: string
  onContinue: () => void
}

export function ActClearReportCard({ report, actColor, onContinue }: ActClearReportCardProps) {
  const gradeColor = GRADE_COLORS[report.grade] ?? '#475569'

  return (
    <div
      style={{
        background: `linear-gradient(160deg, ${actColor}08, rgba(6,6,16,0.9))`,
        border: `1px solid ${actColor}30`,
        borderRadius: 16,
        padding: 24,
        marginTop: 16,
        animation: 'slideIn 0.5s ease',
      }}
    >
      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        <div style={{ fontSize: 36, marginBottom: 8 }}>🏆</div>
        <div style={{ fontSize: 10, letterSpacing: 4, color: actColor, marginBottom: 6 }}>
          ACT {report.actId} CLEAR
        </div>
        <div style={{ fontSize: 20, fontWeight: 'bold', color: '#F8FAFC' }}>{report.actTitle}</div>
      </div>

      {/* Score & Grade */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          gap: 24,
          marginBottom: 20,
          padding: '16px',
          background: 'rgba(0,0,0,0.3)',
          borderRadius: 12,
        }}
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 11, color: '#475569', marginBottom: 4 }}>종합 점수</div>
          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#F8FAFC' }}>{report.overallScore}</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 11, color: '#475569', marginBottom: 4 }}>등급</div>
          <div style={{ fontSize: 28, fontWeight: 'bold', color: gradeColor }}>{report.grade}</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 11, color: '#475569', marginBottom: 4 }}>클래스</div>
          <div style={{ fontSize: 13, fontWeight: 'bold', color: actColor, maxWidth: 100 }}>
            {report.developerClass}
          </div>
        </div>
      </div>

      {/* Achievements */}
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 10, letterSpacing: 3, color: '#475569', marginBottom: 10 }}>
          ACHIEVEMENTS
        </div>
        {report.achievements.map((achievement, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              gap: 8,
              marginBottom: 8,
              fontSize: 13,
              color: '#94A3B8',
              lineHeight: 1.5,
            }}
          >
            <span style={{ color: actColor }}>✦</span>
            {achievement}
          </div>
        ))}
      </div>

      {/* Next Act Hint */}
      <div
        style={{
          background: 'rgba(0,0,0,0.3)',
          border: '1px solid rgba(255,255,255,0.06)',
          borderRadius: 10,
          padding: '12px 14px',
          marginBottom: 16,
        }}
      >
        <div style={{ fontSize: 10, letterSpacing: 3, color: '#475569', marginBottom: 6 }}>
          NEXT ACT HINT
        </div>
        <div style={{ fontSize: 13, color: '#CBD5E1', lineHeight: 1.6 }}>{report.nextActHint}</div>
      </div>

      {/* Encouragement */}
      <div
        style={{
          fontSize: 13,
          color: '#64748B',
          fontStyle: 'italic',
          textAlign: 'center',
          marginBottom: 20,
          lineHeight: 1.6,
        }}
      >
        "{report.encouragement}"
      </div>

      {/* Continue Button */}
      <button
        className="hov-btn"
        onClick={onContinue}
        style={{
          width: '100%',
          padding: '13px',
          background: `linear-gradient(135deg, ${actColor}, ${actColor}80)`,
          border: 'none',
          borderRadius: 10,
          color: '#060610',
          fontSize: 14,
          fontWeight: 'bold',
          cursor: 'pointer',
          fontFamily: "'Courier New', monospace",
        }}
      >
        퀘스트 맵으로 →
      </button>
    </div>
  )
}
