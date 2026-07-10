import { useEffect, useState } from 'react'
import type {
  AppliedCompany,
  ApplicationStatus,
  JdAnalysisResult,
  CompanyResumeCheckResult,
  CompanyActivity,
} from '@/types/api.types'
import { CompanyCard } from './CompanyCard'
import { AddCompanyModal } from './AddCompanyModal'
import { fetchResume } from '@/lib/apiClient'
import { resumeCheck, getActivities } from '../api/companyApi'

interface CompanyPipelinePanelProps {
  companies: AppliedCompany[]
  onAdd: (data: { companyName: string; position: string; jdUrl?: string; jobDescription?: string }) => Promise<void>
  onStatusChange: (id: number, status: ApplicationStatus) => Promise<void>
  onDelete: (id: number) => Promise<void>
  onAnalyzeCompany: (id: number, skills: string[], experiences: string[]) => Promise<JdAnalysisResult>
  onNavigateToResume: () => void
}

export function CompanyPipelinePanel({
  companies,
  onAdd,
  onStatusChange,
  onDelete,
  onAnalyzeCompany,
  onNavigateToResume,
}: CompanyPipelinePanelProps) {
  const [showModal, setShowModal] = useState(false)
  const [analysisResults, setAnalysisResults] = useState<Record<number, JdAnalysisResult>>({})
  const [resumeCheckResults, setResumeCheckResults] = useState<Record<number, CompanyResumeCheckResult>>({})
  const [hasResume, setHasResume] = useState(false)

  useEffect(() => {
    fetchResume()
      .then((resume) => setHasResume(resume != null))
      .catch(() => setHasResume(false))
  }, [])

  const handleAnalyze = async (id: number, skills: string[], experiences: string[]): Promise<JdAnalysisResult> => {
    const result = await onAnalyzeCompany(id, skills, experiences)
    setAnalysisResults((prev) => ({ ...prev, [id]: result }))
    return result
  }

  const handleResumeCheck = async (id: number): Promise<CompanyResumeCheckResult> => {
    const result = await resumeCheck(id)
    setResumeCheckResults((prev) => ({ ...prev, [id]: result }))
    return result
  }

  const handleFetchActivities = async (id: number): Promise<CompanyActivity[]> => {
    return getActivities(id)
  }

  return (
    <div
      style={{
        marginTop: 20,
        background: 'rgba(78,205,196,0.03)',
        border: '1px solid rgba(78,205,196,0.12)',
        borderRadius: 12,
        padding: '16px 20px',
        fontFamily: "'Courier New', monospace",
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <div>
          <p style={{ fontSize: 11, letterSpacing: 3, color: '#4ECDC4', margin: 0 }}>PIPELINE</p>
          <h3 style={{ fontSize: 15, fontWeight: 700, color: '#F8FAFC', margin: '2px 0 0' }}>
            지원 현황
          </h3>
        </div>
        <button
          onClick={() => setShowModal(true)}
          style={{
            background: 'rgba(78,205,196,0.1)',
            border: '1px solid rgba(78,205,196,0.3)',
            borderRadius: 8,
            color: '#4ECDC4',
            fontSize: 12,
            padding: '7px 14px',
            cursor: 'pointer',
            fontFamily: "'Courier New', monospace",
            fontWeight: 600,
          }}
        >
          + 추가
        </button>
      </div>

      {companies.length === 0 ? (
        <div
          style={{
            textAlign: 'center',
            padding: '24px 0',
            color: '#334155',
            fontSize: 13,
          }}
        >
          <p style={{ margin: 0 }}>등록된 회사가 없습니다.</p>
          <p style={{ margin: '4px 0 0', fontSize: 11, color: '#1E293B' }}>
            관심 있는 회사를 추가해보세요.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {companies.map((company) => (
            <CompanyCard
              key={company.id}
              company={company}
              onStatusChange={onStatusChange}
              onDelete={onDelete}
              analysisResult={analysisResults[company.id]}
              onAnalyze={handleAnalyze}
              hasResume={hasResume}
              resumeCheckResult={resumeCheckResults[company.id]}
              onResumeCheck={handleResumeCheck}
              onNavigateToResume={onNavigateToResume}
              onFetchActivities={handleFetchActivities}
            />
          ))}
        </div>
      )}

      {showModal && (
        <AddCompanyModal
          onAdd={onAdd}
          onClose={() => setShowModal(false)}
        />
      )}
    </div>
  )
}
