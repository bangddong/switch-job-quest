import type { AiEvaluationResult, BossPackageResult } from '@/types/api.types'
import { ScoreRing } from '@/components/ui/ScoreRing'
import { GradeTag } from '@/components/ui/GradeTag'
import { PASS_THRESHOLD } from '@/constants/scoring'

function getGrade(score: number): string {
  if (score >= 90) return 'S'
  if (score >= 80) return 'A'
  if (score >= 70) return 'B'
  if (score >= 60) return 'C'
  return 'D'
}

interface AiResultCardProps {
  result: AiEvaluationResult
}

export function AiResultCard({ result }: AiResultCardProps) {
  const score = result.score ?? result.overallScore ?? 0
  const passed = result.passed ?? score >= PASS_THRESHOLD
  const grade = result.grade ?? getGrade(score)

  return (
    <div
      style={{
        background: passed ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
        border: `1px solid ${passed ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
        borderRadius: 14,
        padding: 22,
        marginTop: 18,
        animation: 'slideIn 0.5s ease',
      }}
    >
      <div style={{ display: 'flex', gap: 18, alignItems: 'flex-start', marginBottom: 18 }}>
        <ScoreRing score={score} />
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <GradeTag grade={grade} />
            <span
              style={{ fontSize: 13, color: passed ? '#10B981' : '#EF4444', fontWeight: 'bold' }}
            >
              {passed ? '✓ 통과 — 퀘스트 완료!' : '✗ 미통과 — 재시도 가능'}
            </span>
          </div>
          {result.summary && (
            <p style={{ color: '#94A3B8', fontSize: 13, margin: 0, lineHeight: 1.6 }}>
              {result.summary}
            </p>
          )}
          {result.developerType && (
            <div style={{ marginTop: 6, fontSize: 12, color: '#A78BFA' }}>
              🧠 {result.developerType}
            </div>
          )}
        </div>
      </div>

      {result.strengths && result.strengths.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: '#10B981', letterSpacing: 3, marginBottom: 8 }}>
            ✓ STRENGTHS
          </div>
          {result.strengths.map((s, i) => (
            <div key={i} style={{ fontSize: 13, color: '#64748B', marginBottom: 4 }}>
              <span style={{ color: '#10B981' }}>▸ </span>
              {s}
            </div>
          ))}
        </div>
      )}

      {result.improvements && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: '#F59E0B', letterSpacing: 3, marginBottom: 8 }}>
            ↑ IMPROVEMENTS
          </div>
          {(Array.isArray(result.improvements) ? result.improvements : [result.improvements]).map(
            (imp, i) => (
              <div key={i} style={{ fontSize: 13, color: '#64748B', marginBottom: 4 }}>
                <span style={{ color: '#F59E0B' }}>▸ </span>
                {typeof imp === 'string'
                  ? imp
                  : (imp as { suggestion?: string; issue?: string }).suggestion ??
                    (imp as { suggestion?: string; issue?: string }).issue ??
                    JSON.stringify(imp)}
              </div>
            ),
          )}
        </div>
      )}

      {(result.detailedFeedback ?? result.feedback) && (
        <div
          style={{
            background: 'rgba(15,23,42,0.7)',
            borderRadius: 10,
            padding: '14px 16px',
            borderLeft: '3px solid #4ECDC4',
          }}
        >
          <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 8 }}>
            💬 AI FEEDBACK
          </div>
          <p style={{ color: '#94A3B8', fontSize: 13, margin: 0, lineHeight: 1.7 }}>
            {result.detailedFeedback ?? result.feedback}
          </p>
        </div>
      )}

      {result.rewrittenExamples && result.rewrittenExamples.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <div style={{ fontSize: 10, color: '#A78BFA', letterSpacing: 3, marginBottom: 10 }}>
            ✏️ AI REWRITES
          </div>
          {result.rewrittenExamples.map((ex, i) => (
            <div
              key={i}
              style={{
                background: 'rgba(13,17,23,0.6)',
                borderRadius: 8,
                padding: 12,
                marginBottom: 8,
              }}
            >
              <div style={{ fontSize: 11, color: '#EF4444', marginBottom: 3 }}>Before</div>
              <div style={{ fontSize: 12, color: '#475569', marginBottom: 8 }}>{ex.original}</div>
              <div style={{ fontSize: 11, color: '#10B981', marginBottom: 3 }}>After</div>
              <div style={{ fontSize: 12, color: '#CBD5E1' }}>{ex.improved}</div>
            </div>
          ))}
        </div>
      )}

      {result.suggestedFocus && result.suggestedFocus.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <div style={{ fontSize: 10, color: '#F59E0B', letterSpacing: 3, marginBottom: 8 }}>
            🎯 추천 회사 유형
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {result.suggestedFocus.map((f, i) => (
              <span
                key={i}
                style={{
                  background: 'rgba(245,158,11,0.08)',
                  border: '1px solid rgba(245,158,11,0.25)',
                  color: '#F59E0B',
                  padding: '3px 10px',
                  borderRadius: 20,
                  fontSize: 11,
                }}
              >
                {f}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

interface BossPackageResultCardProps {
  result: BossPackageResult
}

const SCORE_ITEMS: Array<{ key: keyof BossPackageResult; label: string; color: string }> = [
  { key: 'resumeImpactScore', label: '이력서 임팩트', color: '#4ECDC4' },
  { key: 'githubConsistencyScore', label: 'GitHub 일관성', color: '#A78BFA' },
  { key: 'technicalDepthScore', label: '기술 깊이', color: '#60A5FA' },
  { key: 'positionFitScore', label: '포지션 적합도', color: '#F59E0B' },
  { key: 'differentiationScore', label: '차별화 점수', color: '#10B981' },
]

export function BossPackageResultCard({ result }: BossPackageResultCardProps) {
  const passed = result.passed ?? result.overallScore >= 70
  const grade = getGrade(result.overallScore)

  return (
    <div
      style={{
        background: passed ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
        border: `1px solid ${passed ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
        borderRadius: 14,
        padding: 22,
        marginTop: 18,
        animation: 'slideIn 0.5s ease',
      }}
    >
      <div style={{ display: 'flex', gap: 18, alignItems: 'flex-start', marginBottom: 18 }}>
        <ScoreRing score={result.overallScore} />
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <GradeTag grade={grade} />
            <span
              style={{ fontSize: 13, color: passed ? '#10B981' : '#EF4444', fontWeight: 'bold' }}
            >
              {passed ? '✓ 통과 — 지원 패키지 완성!' : '✗ 미통과 — 패키지 보완 필요'}
            </span>
          </div>
        </div>
      </div>

      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 10 }}>
          📊 SCORE BREAKDOWN
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {SCORE_ITEMS.map(({ key, label, color }) => {
            const score = result[key] as number
            return (
              <div key={key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 12, color: '#64748B', minWidth: 110 }}>{label}</span>
                <div
                  style={{
                    flex: 1,
                    background: 'rgba(255,255,255,0.05)',
                    borderRadius: 4,
                    height: 6,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      width: `${score}%`,
                      height: '100%',
                      background: color,
                      borderRadius: 4,
                    }}
                  />
                </div>
                <span style={{ fontSize: 12, color, minWidth: 30, textAlign: 'right' }}>
                  {score}
                </span>
              </div>
            )
          })}
        </div>
      </div>

      {result.strengths.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: '#10B981', letterSpacing: 3, marginBottom: 8 }}>
            ✓ STRENGTHS
          </div>
          {result.strengths.map((s, i) => (
            <div key={i} style={{ fontSize: 13, color: '#64748B', marginBottom: 4 }}>
              <span style={{ color: '#10B981' }}>▸ </span>
              {s}
            </div>
          ))}
        </div>
      )}

      {result.improvements.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: '#F59E0B', letterSpacing: 3, marginBottom: 8 }}>
            ↑ IMPROVEMENTS
          </div>
          {result.improvements.map((imp, i) => (
            <div key={i} style={{ fontSize: 13, color: '#64748B', marginBottom: 4 }}>
              <span style={{ color: '#F59E0B' }}>▸ </span>
              {imp}
            </div>
          ))}
        </div>
      )}

      <div
        style={{
          background: 'rgba(15,23,42,0.7)',
          borderRadius: 10,
          padding: '14px 16px',
          borderLeft: '3px solid #4ECDC4',
        }}
      >
        <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 8 }}>
          💬 OVERALL FEEDBACK
        </div>
        <p style={{ color: '#94A3B8', fontSize: 13, margin: 0, lineHeight: 1.7 }}>
          {result.overallFeedback}
        </p>
      </div>
    </div>
  )
}
