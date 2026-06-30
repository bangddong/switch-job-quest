import { useState } from 'react'

interface AddCompanyModalProps {
  onAdd: (data: { companyName: string; position: string; jdUrl?: string; jobDescription?: string }) => Promise<void>
  onClose: () => void
}

export function AddCompanyModal({ onAdd, onClose }: AddCompanyModalProps) {
  const [companyName, setCompanyName] = useState('')
  const [position, setPosition] = useState('')
  const [jdUrl, setJdUrl] = useState('')
  const [jobDescription, setJobDescription] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!companyName.trim()) {
      setError('회사명을 입력해주세요.')
      return
    }
    setLoading(true)
    setError(null)
    try {
      await onAdd({
        companyName: companyName.trim(),
        position: position.trim(),
        ...(jdUrl.trim() ? { jdUrl: jdUrl.trim() } : {}),
        ...(jobDescription.trim() ? { jobDescription: jobDescription.trim() } : {}),
      })
      onClose()
    } catch {
      setError('회사 등록에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#0A0E1A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 6,
    color: '#F1F5F9',
    fontSize: 13,
    padding: '10px 12px',
    fontFamily: "'Courier New', monospace",
    boxSizing: 'border-box',
    outline: 'none',
  }

  const labelStyle: React.CSSProperties = {
    fontSize: 11,
    color: '#475569',
    marginBottom: 4,
    display: 'block',
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: '0 20px',
        fontFamily: "'Courier New', monospace",
      }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 12,
          padding: 24,
          width: '100%',
          maxWidth: 480,
        }}
      >
        <h2 style={{ fontSize: 16, fontWeight: 700, color: '#F8FAFC', margin: '0 0 20px' }}>
          회사 추가
        </h2>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>회사명 *</label>
            <input
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              placeholder="예: 카카오, 네이버"
              style={inputStyle}
              autoFocus
            />
          </div>

          <div>
            <label style={labelStyle}>직책 / 포지션</label>
            <input
              type="text"
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              placeholder="예: 백엔드 개발자"
              style={inputStyle}
            />
          </div>

          <div>
            <label style={labelStyle}>JD URL (선택)</label>
            <input
              type="url"
              value={jdUrl}
              onChange={(e) => setJdUrl(e.target.value)}
              placeholder="https://..."
              style={inputStyle}
            />
          </div>

          <div>
            <label style={labelStyle}>JD 내용 (선택 — AI 분석용)</label>
            <textarea
              value={jobDescription}
              onChange={(e) => setJobDescription(e.target.value)}
              placeholder="채용공고 본문을 붙여넣으세요"
              style={{
                ...inputStyle,
                height: 120,
                resize: 'vertical',
              }}
            />
          </div>

          {error && (
            <p style={{ fontSize: 12, color: '#EF4444', margin: 0 }}>{error}</p>
          )}

          <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                flex: 1,
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 8,
                color: '#475569',
                fontSize: 13,
                padding: '10px',
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
              }}
            >
              취소
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                flex: 1,
                background: loading ? 'rgba(78,205,196,0.05)' : 'rgba(78,205,196,0.1)',
                border: '1px solid rgba(78,205,196,0.3)',
                borderRadius: 8,
                color: '#4ECDC4',
                fontSize: 13,
                padding: '10px',
                cursor: loading ? 'not-allowed' : 'pointer',
                fontFamily: "'Courier New', monospace",
                fontWeight: 600,
              }}
            >
              {loading ? '등록 중...' : '추가'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
