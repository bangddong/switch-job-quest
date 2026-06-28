import { useState } from 'react'
import type { AppliedCompany, ApplicationStatus } from '@/types/api.types'
import { CompanyCard } from './CompanyCard'
import { AddCompanyModal } from './AddCompanyModal'

interface CompanyPipelinePanelProps {
  companies: AppliedCompany[]
  onAdd: (data: { companyName: string; position: string; jdUrl?: string }) => Promise<void>
  onStatusChange: (id: number, status: ApplicationStatus) => Promise<void>
  onDelete: (id: number) => Promise<void>
}

export function CompanyPipelinePanel({
  companies,
  onAdd,
  onStatusChange,
  onDelete,
}: CompanyPipelinePanelProps) {
  const [showModal, setShowModal] = useState(false)

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
              onStatusChange={(id, status) => { onStatusChange(id, status).catch(() => {}) }}
              onDelete={(id) => { onDelete(id).catch(() => {}) }}
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
