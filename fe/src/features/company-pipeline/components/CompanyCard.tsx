import { useState } from 'react'
import type { AppliedCompany, ApplicationStatus, JdAnalysisResult } from '@/types/api.types'

const STATUS_LABELS: Record<ApplicationStatus, string> = {
  INTERESTED: '관심',
  APPLIED: '지원',
  SCREENING_PASS: '서류 통과',
  SCREENING_FAIL: '서류 탈락',
  TECH_INTERVIEW: '기술 면접',
  HR_INTERVIEW: 'HR 면접',
  OFFERED: '합격',
  REJECTED: '불합격',
  WITHDRAWN: '취소',
}

const STATUS_OPTIONS: ApplicationStatus[] = [
  'INTERESTED',
  'APPLIED',
  'SCREENING_PASS',
  'SCREENING_FAIL',
  'TECH_INTERVIEW',
  'HR_INTERVIEW',
  'OFFERED',
  'REJECTED',
  'WITHDRAWN',
]

function getStatusStyle(status: ApplicationStatus): { color: string; background: string; border: string } {
  switch (status) {
    case 'OFFERED':
      return { color: '#10B981', background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)' }
    case 'SCREENING_PASS':
    case 'TECH_INTERVIEW':
    case 'HR_INTERVIEW':
      return { color: '#F59E0B', background: 'rgba(245,158,11,0.12)', border: '1px solid rgba(245,158,11,0.3)' }
    case 'APPLIED':
      return { color: '#60A5FA', background: 'rgba(96,165,250,0.12)', border: '1px solid rgba(96,165,250,0.3)' }
    case 'SCREENING_FAIL':
    case 'REJECTED':
      return { color: '#EF4444', background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.3)' }
    case 'INTERESTED':
    case 'WITHDRAWN':
    default:
      return { color: '#475569', background: 'rgba(71,85,105,0.12)', border: '1px solid rgba(71,85,105,0.3)' }
  }
}

function getScoreStyle(score: number): { color: string; background: string; border: string } {
  if (score >= 70) return { color: '#10B981', background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)' }
  if (score >= 50) return { color: '#F59E0B', background: 'rgba(245,158,11,0.12)', border: '1px solid rgba(245,158,11,0.3)' }
  return { color: '#EF4444', background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.3)' }
}

interface JdAnalysisCoachPanelProps {
  result: JdAnalysisResult
}

function JdAnalysisCoachPanel({ result }: JdAnalysisCoachPanelProps) {
  const [showHidden, setShowHidden] = useState(false)
  const scoreStyle = getScoreStyle(result.overallMatchScore)

  return (
    <div
      style={{
        marginTop: 10,
        background: 'rgba(167,139,250,0.04)',
        border: '1px solid rgba(167,139,250,0.2)',
        borderRadius: 8,
        padding: '12px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        fontFamily: "'Courier New', monospace",
      }}
    >
      {/* 점수 + 합격 뱃지 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <span
          style={{
            fontSize: 11,
            fontWeight: 700,
            padding: '3px 10px',
            borderRadius: 4,
            ...scoreStyle,
          }}
        >
          매칭 {result.overallMatchScore}점
        </span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 600,
            padding: '3px 10px',
            borderRadius: 4,
            ...(result.passed
              ? { color: '#10B981', background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.25)' }
              : { color: '#F59E0B', background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.25)' }),
          }}
        >
          {result.passed ? '합격 가능성 ✓' : 'Gap 있음'}
        </span>
      </div>

      {/* 전략 */}
      {result.applicationStrategy && (
        <div>
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#A78BFA', margin: '0 0 4px' }}>전략</p>
          <p style={{ fontSize: 12, color: '#F1F5F9', margin: 0, lineHeight: 1.6 }}>
            {result.applicationStrategy}
          </p>
        </div>
      )}

      {/* 핵심 차별화 포인트 */}
      {result.keyDifferentiators.length > 0 && (
        <div>
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#A78BFA', margin: '0 0 4px' }}>핵심 차별화</p>
          <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 2 }}>
            {result.keyDifferentiators.slice(0, 3).map((item, i) => (
              <li key={i} style={{ fontSize: 12, color: '#F1F5F9' }}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      {/* 스킬 요구사항 */}
      {result.requiredSkills.length > 0 && (
        <div>
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#A78BFA', margin: '0 0 6px' }}>스킬 분석</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {result.requiredSkills.map((s, i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 8,
                  background: '#0F172A',
                  border: '1px solid rgba(255,255,255,0.06)',
                  borderRadius: 6,
                  padding: '6px 10px',
                }}
              >
                <span style={{ fontSize: 12, color: '#F1F5F9', flex: 1, minWidth: 0, wordBreak: 'break-word' }}>
                  {s.skill}
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
                  {s.required && s.userLevel === '' && (
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        padding: '2px 6px',
                        borderRadius: 3,
                        color: '#EF4444',
                        background: 'rgba(239,68,68,0.12)',
                        border: '1px solid rgba(239,68,68,0.3)',
                      }}
                    >
                      Gap
                    </span>
                  )}
                  {s.importance && (
                    <span style={{ fontSize: 10, color: '#475569' }}>{s.importance}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 숨겨진 요구사항 (접을 수 있음) */}
      {result.hiddenRequirements.length > 0 && (
        <div>
          <button
            onClick={() => setShowHidden((v) => !v)}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: 0,
              fontFamily: "'Courier New', monospace",
              fontSize: 10,
              letterSpacing: 2,
              color: '#A78BFA',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            숨겨진 요구사항 {showHidden ? '▲' : '▼'}
          </button>
          {showHidden && (
            <ul style={{ margin: '6px 0 0', paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 2 }}>
              {result.hiddenRequirements.map((item, i) => (
                <li key={i} style={{ fontSize: 12, color: '#475569' }}>{item}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

interface CompanyCardProps {
  company: AppliedCompany
  onStatusChange: (id: number, status: ApplicationStatus) => void
  onDelete: (id: number) => void
  analysisResult?: JdAnalysisResult
  onAnalyze: (id: number, skills: string[], experiences: string[]) => Promise<JdAnalysisResult>
}

export function CompanyCard({ company, onStatusChange, onDelete, analysisResult, onAnalyze }: CompanyCardProps) {
  const statusStyle = getStatusStyle(company.status)
  const [showAnalysisForm, setShowAnalysisForm] = useState(false)
  const [showResultPanel, setShowResultPanel] = useState(false)
  const [skillsInput, setSkillsInput] = useState('')
  const [experiencesInput, setExperiencesInput] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [analysisError, setAnalysisError] = useState<string | null>(null)

  const handleAnalysisButtonClick = () => {
    if (analysisResult) {
      setShowResultPanel((v) => !v)
    } else {
      setShowAnalysisForm((v) => !v)
    }
  }

  const handleAnalysisSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const skills = skillsInput.split(',').map((s) => s.trim()).filter(Boolean)
    const experiences = experiencesInput.split('\n').map((s) => s.trim()).filter(Boolean)
    if (skills.length === 0 || experiences.length === 0) {
      setAnalysisError('스킬과 경험을 모두 입력해주세요.')
      return
    }
    setAnalyzing(true)
    setAnalysisError(null)
    try {
      await onAnalyze(company.id, skills, experiences)
      setShowAnalysisForm(false)
      setShowResultPanel(true)
    } catch {
      setAnalysisError('분석에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setAnalyzing(false)
    }
  }

  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#0A0E1A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 6,
    color: '#F1F5F9',
    fontSize: 12,
    padding: '8px 10px',
    fontFamily: "'Courier New', monospace",
    boxSizing: 'border-box',
    outline: 'none',
  }

  return (
    <div
      style={{
        background: '#0F172A',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 10,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        fontFamily: "'Courier New', monospace",
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <p style={{ fontSize: 14, fontWeight: 700, color: '#F8FAFC', margin: 0, wordBreak: 'break-word' }}>
            {company.companyName}
          </p>
          <p style={{ fontSize: 12, color: '#475569', margin: '3px 0 0' }}>
            {company.position}
          </p>
        </div>
        <span
          style={{
            fontSize: 10,
            fontWeight: 600,
            padding: '3px 8px',
            borderRadius: 4,
            whiteSpace: 'nowrap',
            flexShrink: 0,
            ...statusStyle,
          }}
        >
          {STATUS_LABELS[company.status]}
        </span>
      </div>

      {company.jdUrl && (
        <a
          href={company.jdUrl}
          target="_blank"
          rel="noopener noreferrer"
          style={{
            fontSize: 11,
            color: '#4ECDC4',
            textDecoration: 'none',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            display: 'block',
          }}
        >
          JD 링크 →
        </a>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <select
          value={company.status}
          onChange={(e) => onStatusChange(company.id, e.target.value as ApplicationStatus)}
          style={{
            flex: 1,
            background: '#0A0E1A',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: 6,
            color: '#F1F5F9',
            fontSize: 12,
            padding: '6px 8px',
            fontFamily: "'Courier New', monospace",
            cursor: 'pointer',
          }}
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{STATUS_LABELS[s]}</option>
          ))}
        </select>

        {company.jobDescription && (
          <button
            onClick={handleAnalysisButtonClick}
            style={{
              background: analysisResult
                ? 'rgba(167,139,250,0.15)'
                : 'rgba(167,139,250,0.1)',
              border: '1px solid rgba(167,139,250,0.3)',
              borderRadius: 6,
              color: '#A78BFA',
              fontSize: 12,
              padding: '6px 10px',
              cursor: 'pointer',
              fontFamily: "'Courier New', monospace",
              flexShrink: 0,
              fontWeight: analysisResult ? 600 : 400,
            }}
          >
            {analysisResult ? 'AI 결과' : 'AI 분석'}
          </button>
        )}

        <button
          onClick={() => onDelete(company.id)}
          style={{
            background: 'rgba(239,68,68,0.08)',
            border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: 6,
            color: '#EF4444',
            fontSize: 12,
            padding: '6px 10px',
            cursor: 'pointer',
            fontFamily: "'Courier New', monospace",
            flexShrink: 0,
          }}
        >
          삭제
        </button>
      </div>

      {/* 분석 입력 폼 */}
      {showAnalysisForm && !analysisResult && (
        <form
          onSubmit={handleAnalysisSubmit}
          style={{
            background: 'rgba(167,139,250,0.04)',
            border: '1px solid rgba(167,139,250,0.15)',
            borderRadius: 8,
            padding: '12px 14px',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#A78BFA', margin: 0 }}>JD AI 분석</p>
          <div>
            <label style={{ fontSize: 10, color: '#475569', display: 'block', marginBottom: 4 }}>
              보유 스킬 (쉼표 구분)
            </label>
            <input
              type="text"
              value={skillsInput}
              onChange={(e) => setSkillsInput(e.target.value)}
              placeholder="Kotlin, Spring Boot, JPA"
              style={inputStyle}
            />
          </div>
          <div>
            <label style={{ fontSize: 10, color: '#475569', display: 'block', marginBottom: 4 }}>
              주요 경험 (줄바꿈 구분)
            </label>
            <textarea
              value={experiencesInput}
              onChange={(e) => setExperiencesInput(e.target.value)}
              placeholder={'3년 백엔드 개발\nMSA 아키텍처 경험'}
              style={{ ...inputStyle, height: 80, resize: 'vertical' }}
            />
          </div>
          {analysisError && (
            <p style={{ fontSize: 11, color: '#EF4444', margin: 0 }}>{analysisError}</p>
          )}
          <div style={{ display: 'flex', gap: 6 }}>
            <button
              type="button"
              onClick={() => setShowAnalysisForm(false)}
              style={{
                flex: 1,
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 6,
                color: '#475569',
                fontSize: 12,
                padding: '7px',
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
              }}
            >
              취소
            </button>
            <button
              type="submit"
              disabled={analyzing}
              style={{
                flex: 2,
                background: analyzing ? 'rgba(167,139,250,0.05)' : 'rgba(167,139,250,0.15)',
                border: '1px solid rgba(167,139,250,0.3)',
                borderRadius: 6,
                color: '#A78BFA',
                fontSize: 12,
                padding: '7px',
                cursor: analyzing ? 'not-allowed' : 'pointer',
                fontFamily: "'Courier New', monospace",
                fontWeight: 600,
              }}
            >
              {analyzing ? '분석 중...' : '분석 시작'}
            </button>
          </div>
        </form>
      )}

      {/* 분석 결과 패널 */}
      {showResultPanel && analysisResult && (
        <JdAnalysisCoachPanel result={analysisResult} />
      )}
    </div>
  )
}
