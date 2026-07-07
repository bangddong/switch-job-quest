import { useState } from 'react'
import type {
  AppliedCompany,
  ApplicationStatus,
  JdAnalysisResult,
  CompanyResumeCheckResult,
  CompanyActivity,
} from '@/types/api.types'

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

interface ResumeCheckPanelProps {
  result: CompanyResumeCheckResult
}

function ResumeCheckPanel({ result }: ResumeCheckPanelProps) {
  const [showAllImprovements, setShowAllImprovements] = useState(false)
  const overallStyle = getScoreStyle(result.overallScore)
  const visibleImprovements = showAllImprovements ? result.improvements : result.improvements.slice(0, 3)

  return (
    <div
      style={{
        marginTop: 10,
        background: 'rgba(245,158,11,0.04)',
        border: '1px solid rgba(245,158,11,0.2)',
        borderRadius: 8,
        padding: '12px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        fontFamily: "'Courier New', monospace",
      }}
    >
      {/* 점수 + 배지 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <span
          style={{
            fontSize: 11,
            fontWeight: 700,
            padding: '3px 10px',
            borderRadius: 4,
            ...overallStyle,
          }}
        >
          종합 {result.overallScore}점
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
          {result.passed ? '이력서 준비됨 ✓' : '보완 필요'}
        </span>
      </div>

      {/* 세부 점수 3종 (배점제: STAR /40, 정량화 /30, 키워드매칭 /30) */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 6 }}>
        {([
          { label: 'STAR', value: result.starMethodScore, max: 40 },
          { label: '정량화', value: result.quantificationScore, max: 30 },
          { label: '키워드매칭', value: result.keywordMatchScore, max: 30 },
        ] as const).map((item) => {
          const style = getScoreStyle((item.value / item.max) * 100)
          return (
            <div
              key={item.label}
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.06)',
                borderRadius: 6,
                padding: '8px 6px',
                textAlign: 'center',
              }}
            >
              <p style={{ fontSize: 10, color: '#475569', margin: '0 0 4px' }}>{item.label}</p>
              <p style={{ fontSize: 14, fontWeight: 700, color: style.color, margin: 0 }}>
                {item.value}/{item.max}
              </p>
            </div>
          )
        })}
      </div>

      {/* 개선 포인트 */}
      {result.improvements.length > 0 && (
        <div>
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#F59E0B', margin: '0 0 6px' }}>개선 포인트</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {visibleImprovements.map((item, i) => (
              <div
                key={i}
                style={{
                  background: '#0F172A',
                  border: '1px solid rgba(255,255,255,0.06)',
                  borderRadius: 6,
                  padding: '8px 10px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                }}
              >
                {item.section && (
                  <span
                    style={{
                      fontSize: 11,
                      color: '#F59E0B',
                      fontWeight: 600,
                      alignSelf: 'flex-start',
                      padding: '2px 6px',
                      borderRadius: 3,
                      background: 'rgba(245,158,11,0.1)',
                      border: '1px solid rgba(245,158,11,0.25)',
                    }}
                  >
                    {item.section}
                  </span>
                )}
                <p style={{ fontSize: 12, color: '#F1F5F9', margin: 0 }}>{item.issue}</p>
                <p style={{ fontSize: 12, color: '#10B981', margin: 0 }}>제안: {item.suggestion}</p>
              </div>
            ))}
          </div>
          {result.improvements.length > 3 && (
            <button
              onClick={() => setShowAllImprovements((v) => !v)}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: 0,
                marginTop: 6,
                fontFamily: "'Courier New', monospace",
                fontSize: 10,
                letterSpacing: 2,
                color: '#F59E0B',
              }}
            >
              {showAllImprovements ? '접기 ▲' : `더보기 (${result.improvements.length - 3}) ▼`}
            </button>
          )}
        </div>
      )}

      {/* 리라이트 예시 */}
      {result.rewrittenExamples.length > 0 && (
        <div>
          <p style={{ fontSize: 10, letterSpacing: 2, color: '#F59E0B', margin: '0 0 6px' }}>리라이트 예시</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {result.rewrittenExamples.map((ex, i) => (
              <div key={i}>
                <p
                  style={{
                    background: 'rgba(239,68,68,0.05)',
                    borderLeft: '2px solid rgba(239,68,68,0.3)',
                    padding: '6px 10px',
                    color: '#94A3B8',
                    fontSize: 12,
                    margin: 0,
                  }}
                >
                  {ex.original}
                </p>
                <p style={{ textAlign: 'center', color: '#475569', fontSize: 12, margin: '4px 0' }}>↓</p>
                <p
                  style={{
                    background: 'rgba(16,185,129,0.05)',
                    borderLeft: '2px solid rgba(16,185,129,0.4)',
                    padding: '6px 10px',
                    color: '#F1F5F9',
                    fontSize: 12,
                    margin: 0,
                  }}
                >
                  {ex.improved}
                </p>
                {ex.explanation && (
                  <p style={{ fontSize: 11, color: '#475569', margin: '4px 0 0' }}>{ex.explanation}</p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

const ACTIVITY_TYPE_STYLE: Record<CompanyActivity['activityType'], { label: string; color: string; background: string; border: string }> = {
  JD_ANALYSIS: { label: 'JD', color: '#A78BFA', background: 'rgba(167,139,250,0.12)', border: '1px solid rgba(167,139,250,0.3)' },
  RESUME_CHECK: { label: '이력서', color: '#F59E0B', background: 'rgba(245,158,11,0.12)', border: '1px solid rgba(245,158,11,0.3)' },
}

interface ActivityHistoryListProps {
  activities: CompanyActivity[]
}

function ActivityHistoryList({ activities }: ActivityHistoryListProps) {
  const recent = [...activities]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 3)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', marginTop: 4 }}>
      {recent.map((activity) => {
        const typeStyle = ACTIVITY_TYPE_STYLE[activity.activityType]
        const scoreStyle = getScoreStyle(activity.aiScore)
        return (
          <div
            key={activity.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '6px 0',
              borderBottom: '1px solid rgba(255,255,255,0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  padding: '2px 6px',
                  borderRadius: 3,
                  ...typeStyle,
                }}
              >
                {typeStyle.label}
              </span>
              <span style={{ fontSize: 11, color: '#475569' }}>
                {new Date(activity.createdAt).toLocaleDateString('ko-KR')}
              </span>
            </div>
            <span style={{ fontSize: 12, fontWeight: 700, color: scoreStyle.color }}>{activity.aiScore}</span>
          </div>
        )
      })}
    </div>
  )
}

interface CompanyCardProps {
  company: AppliedCompany
  onStatusChange: (id: number, status: ApplicationStatus) => void
  onDelete: (id: number) => void
  analysisResult?: JdAnalysisResult
  onAnalyze: (id: number, skills: string[], experiences: string[]) => Promise<JdAnalysisResult>
  hasResume: boolean
  resumeCheckResult?: CompanyResumeCheckResult
  onResumeCheck: (id: number) => Promise<CompanyResumeCheckResult>
  onNavigateToResume: () => void
  onFetchActivities: (id: number) => Promise<CompanyActivity[]>
}

export function CompanyCard({
  company,
  onStatusChange,
  onDelete,
  analysisResult,
  onAnalyze,
  hasResume,
  resumeCheckResult,
  onResumeCheck,
  onNavigateToResume,
  onFetchActivities,
}: CompanyCardProps) {
  const statusStyle = getStatusStyle(company.status)
  const [showAnalysisForm, setShowAnalysisForm] = useState(false)
  const [showResultPanel, setShowResultPanel] = useState(false)
  const [showResumePanel, setShowResumePanel] = useState(false)
  const [skillsInput, setSkillsInput] = useState('')
  const [experiencesInput, setExperiencesInput] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [checkingResume, setCheckingResume] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const [activitiesExpanded, setActivitiesExpanded] = useState(false)
  const [activities, setActivities] = useState<CompanyActivity[] | null>(null)
  const [activitiesLoading, setActivitiesLoading] = useState(false)

  const busy = analyzing || checkingResume

  const loadActivities = async () => {
    setActivitiesLoading(true)
    try {
      const result = await onFetchActivities(company.id)
      setActivities(result)
    } catch {
      setActivities([])
    } finally {
      setActivitiesLoading(false)
    }
  }

  // 새 점검/분석 성공 시 캐시 무효화 — 펼쳐진 상태면 즉시 refetch, 아니면 다음 펼침 시 재조회
  const invalidateActivities = () => {
    setActivities(null)
    if (activitiesExpanded) {
      loadActivities()
    }
  }

  const handleAnalysisButtonClick = async () => {
    if (analysisResult) {
      setShowResultPanel((v) => !v)
      return
    }
    if (hasResume) {
      setAnalyzing(true)
      setApiError(null)
      try {
        await onAnalyze(company.id, [], [])
        setShowResultPanel(true)
        invalidateActivities()
      } catch (e) {
        setApiError(e instanceof Error ? e.message : 'ANALYZE_FAILED')
      } finally {
        setAnalyzing(false)
      }
      return
    }
    setShowAnalysisForm((v) => !v)
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
      invalidateActivities()
    } catch {
      setAnalysisError('분석에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setAnalyzing(false)
    }
  }

  const handleResumeCheckClick = async () => {
    if (resumeCheckResult) {
      setShowResumePanel((v) => !v)
      return
    }
    setCheckingResume(true)
    setApiError(null)
    try {
      await onResumeCheck(company.id)
      setShowResumePanel(true)
      invalidateActivities()
    } catch (e) {
      setApiError(e instanceof Error ? e.message : 'RESUME_CHECK_FAILED')
    } finally {
      setCheckingResume(false)
    }
  }

  const handleToggleActivities = () => {
    const next = !activitiesExpanded
    setActivitiesExpanded(next)
    if (next && activities == null && !activitiesLoading) {
      loadActivities()
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

  const renderApiErrorBanner = () => {
    if (!apiError) return null
    if (apiError === 'COMPANY_JD_NOT_REGISTERED') {
      return (
        <div
          style={{
            background: 'rgba(239,68,68,0.06)',
            border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: 6,
            padding: '8px 10px',
          }}
        >
          <p style={{ fontSize: 12, color: '#EF4444', margin: 0 }}>
            JD를 먼저 등록하세요. 회사 카드에 JD 내용이 없으면 점검할 수 없어요.
          </p>
        </div>
      )
    }
    if (apiError === 'RESUME_NOT_REGISTERED') {
      return (
        <div
          style={{
            background: 'rgba(245,158,11,0.06)',
            border: '1px solid rgba(245,158,11,0.2)',
            borderRadius: 6,
            padding: '8px 10px',
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
          }}
        >
          <p style={{ fontSize: 12, color: '#F59E0B', margin: 0 }}>이력서를 먼저 등록하세요.</p>
          <button
            onClick={onNavigateToResume}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: 0,
              alignSelf: 'flex-start',
              fontFamily: "'Courier New', monospace",
              fontSize: 12,
              color: '#F59E0B',
              textDecoration: 'underline',
            }}
          >
            이력서 등록하러 가기
          </button>
        </div>
      )
    }
    return (
      <div
        style={{
          background: 'rgba(239,68,68,0.06)',
          border: '1px solid rgba(239,68,68,0.2)',
          borderRadius: 6,
          padding: '8px 10px',
        }}
      >
        <p style={{ fontSize: 12, color: '#EF4444', margin: 0 }}>점검에 실패했습니다. 다시 시도해주세요.</p>
      </div>
    )
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

      {/* 1행: 상태 select */}
      <select
        value={company.status}
        onChange={(e) => onStatusChange(company.id, e.target.value as ApplicationStatus)}
        disabled={busy}
        style={{
          background: '#0A0E1A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 6,
          color: '#F1F5F9',
          fontSize: 12,
          padding: '6px 8px',
          fontFamily: "'Courier New', monospace",
          cursor: busy ? 'not-allowed' : 'pointer',
        }}
      >
        {STATUS_OPTIONS.map((s) => (
          <option key={s} value={s}>{STATUS_LABELS[s]}</option>
        ))}
      </select>

      {/* 2행: 액션 그룹 */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {company.jobDescription && (
          <button
            onClick={handleAnalysisButtonClick}
            disabled={busy}
            style={{
              flex: '1 1 80px',
              minWidth: 0,
              background: analysisResult
                ? 'rgba(167,139,250,0.15)'
                : 'rgba(167,139,250,0.1)',
              border: '1px solid rgba(167,139,250,0.3)',
              borderRadius: 6,
              color: '#A78BFA',
              fontSize: 12,
              padding: '6px 10px',
              cursor: busy ? 'not-allowed' : 'pointer',
              fontFamily: "'Courier New', monospace",
              fontWeight: analysisResult ? 600 : 400,
            }}
          >
            {analyzing ? '분석 중...' : analysisResult ? 'AI 결과' : 'AI 분석'}
          </button>
        )}

        {company.jobDescription && (
          <button
            onClick={handleResumeCheckClick}
            disabled={busy}
            style={{
              flex: '1 1 80px',
              minWidth: 0,
              background: resumeCheckResult
                ? 'rgba(245,158,11,0.15)'
                : 'rgba(245,158,11,0.1)',
              border: '1px solid rgba(245,158,11,0.3)',
              borderRadius: 6,
              color: '#F59E0B',
              fontSize: 12,
              padding: '6px 10px',
              cursor: busy ? 'not-allowed' : 'pointer',
              fontFamily: "'Courier New', monospace",
              fontWeight: resumeCheckResult ? 600 : 400,
            }}
          >
            {checkingResume ? '점검 중...' : resumeCheckResult ? '점검 결과' : '이력서 점검'}
          </button>
        )}

        <button
          onClick={() => onDelete(company.id)}
          disabled={busy}
          style={{
            flex: '1 1 80px',
            minWidth: 0,
            background: 'rgba(239,68,68,0.08)',
            border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: 6,
            color: '#EF4444',
            fontSize: 12,
            padding: '6px 10px',
            cursor: busy ? 'not-allowed' : 'pointer',
            fontFamily: "'Courier New', monospace",
          }}
        >
          삭제
        </button>
      </div>

      {checkingResume && (
        <p style={{ fontSize: 11, color: '#475569', margin: '6px 0 0' }}>
          AI가 이력서를 분석하고 있어요 (최대 1분 소요)
        </p>
      )}

      {renderApiErrorBanner()}

      {/* 분석 입력 폼 */}
      {showAnalysisForm && !analysisResult && !hasResume && (
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
          <p style={{ fontSize: 11, color: '#475569', margin: 0 }}>
            이력서를 등록하면 이 입력을 생략할 수 있어요 →{' '}
            <button
              type="button"
              onClick={onNavigateToResume}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: 0,
                fontFamily: "'Courier New', monospace",
                fontSize: 11,
                color: '#F59E0B',
              }}
            >
              이력서 등록하기
            </button>
          </p>
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

      {/* 이력서 점검 결과 패널 */}
      {showResumePanel && resumeCheckResult && (
        <ResumeCheckPanel result={resumeCheckResult} />
      )}

      {/* 점검 이력 */}
      <div>
        <button
          onClick={handleToggleActivities}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: 0,
            fontFamily: "'Courier New', monospace",
            fontSize: 10,
            letterSpacing: 2,
            color: '#475569',
          }}
        >
          점검 이력 {activitiesExpanded ? '▲' : '▼'}
        </button>
        {activitiesExpanded && (
          activitiesLoading ? (
            <p style={{ fontSize: 11, color: '#475569', margin: '6px 0 0' }}>불러오는 중...</p>
          ) : activities && activities.length > 0 ? (
            <ActivityHistoryList activities={activities} />
          ) : (
            <p style={{ fontSize: 11, color: '#475569', margin: '6px 0 0' }}>아직 점검 이력이 없어요</p>
          )
        )}
      </div>
    </div>
  )
}
