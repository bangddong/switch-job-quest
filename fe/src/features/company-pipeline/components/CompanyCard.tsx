import type { AppliedCompany, ApplicationStatus } from '@/types/api.types'

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

interface CompanyCardProps {
  company: AppliedCompany
  onStatusChange: (id: number, status: ApplicationStatus) => void
  onDelete: (id: number) => void
}

export function CompanyCard({ company, onStatusChange, onDelete }: CompanyCardProps) {
  const statusStyle = getStatusStyle(company.status)

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
    </div>
  )
}
