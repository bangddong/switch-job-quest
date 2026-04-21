import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult } from '@/types/api.types'
import { getGrade, PASS_THRESHOLD } from '../../../utils/gradeUtils'
import { ResultHeader } from './ResultHeader'
import { ResultSection } from './ResultSection'

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
      <ResultHeader
        score={score}
        passed={passed}
        grade={grade}
        summary={result.summary}
        developerType={result.developerType}
      />

      {result.strengths && result.strengths.length > 0 && (
        <ResultSection label="✓ STRENGTHS" color="#10B981" items={result.strengths} />
      )}

      {result.improvements && (() => {
        const improvementItems = (
          Array.isArray(result.improvements) ? result.improvements : [result.improvements]
        ).map((imp) =>
          typeof imp === 'string'
            ? imp
            : (imp as { suggestion?: string; issue?: string }).suggestion ??
              (imp as { suggestion?: string; issue?: string }).issue ??
              JSON.stringify(imp),
        )
        return improvementItems.length > 0 ? (
          <ResultSection label="↑ IMPROVEMENTS" color="#F59E0B" items={improvementItems} />
        ) : null
      })()}

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

interface DeveloperClassResultCardProps {
  result: DeveloperClassResult
}

export function DeveloperClassResultCard({ result }: DeveloperClassResultCardProps) {
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
      <ResultHeader
        score={result.overallScore}
        passed={passed}
        grade={grade}
        passLabel="✓ 통과 — 개발자 클래스 판별 완료!"
        failLabel="✗ 미통과 — 더 많은 경험이 필요합니다"
      />

      {/* 개발자 클래스 강조 */}
      <div
        style={{
          background: 'rgba(167,139,250,0.06)',
          border: '1px solid rgba(167,139,250,0.2)',
          borderRadius: 12,
          padding: '18px 20px',
          marginBottom: 16,
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 10, color: '#A78BFA', letterSpacing: 3, marginBottom: 10 }}>
          🏷️ 개발자 클래스
        </div>
        <div
          style={{
            fontSize: 22,
            fontWeight: 'bold',
            color: '#A78BFA',
            marginBottom: 10,
            lineHeight: 1.3,
          }}
        >
          {result.developerClass}
        </div>
        <p style={{ color: '#94A3B8', fontSize: 13, margin: 0, lineHeight: 1.7 }}>
          {result.classDescription}
        </p>
      </div>

      {result.strengths.length > 0 && (
        <ResultSection label="✓ STRENGTHS" color="#10B981" items={result.strengths} />
      )}

      {result.strategies.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 10, color: '#F59E0B', letterSpacing: 3, marginBottom: 10 }}>
            🎯 맞춤 이직 전략
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {result.strategies.map((strategy, i) => (
              <div
                key={i}
                style={{
                  background: 'rgba(245,158,11,0.05)',
                  border: '1px solid rgba(245,158,11,0.18)',
                  borderRadius: 10,
                  padding: '12px 14px',
                  display: 'flex',
                  gap: 10,
                  alignItems: 'flex-start',
                }}
              >
                <span
                  style={{
                    fontSize: 12,
                    color: '#F59E0B',
                    fontWeight: 'bold',
                    minWidth: 20,
                    paddingTop: 1,
                  }}
                >
                  {i + 1}.
                </span>
                <span style={{ fontSize: 13, color: '#CBD5E1', lineHeight: 1.6 }}>{strategy}</span>
              </div>
            ))}
          </div>
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
  const passed = result.passed ?? result.overallScore >= PASS_THRESHOLD
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
      <ResultHeader
        score={result.overallScore}
        passed={passed}
        grade={grade}
        passLabel="✓ 통과 — 지원 패키지 완성!"
        failLabel="✗ 미통과 — 패키지 보완 필요"
      />

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

      <ResultSection label="✓ STRENGTHS" color="#10B981" items={result.strengths} />

      <ResultSection label="↑ IMPROVEMENTS" color="#F59E0B" items={result.improvements} />

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
