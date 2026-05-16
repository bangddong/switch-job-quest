import { useState, useEffect } from 'react'
import { fetchUserEmail, saveUserEmail } from '@/lib/apiClient'

export function SettingsPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{ text: string; success: boolean } | null>(null)

  useEffect(() => {
    fetchUserEmail()
      .then((result) => {
        if (result.email) setEmail(result.email)
      })
      .catch(() => {/* 조회 실패 시 빈 상태 유지 */})
      .finally(() => setLoading(false))
  }, [])

  const handleSave = async () => {
    if (!email.trim()) {
      setMessage({ text: '이메일을 입력해주세요.', success: false })
      return
    }
    setSaving(true)
    setMessage(null)
    try {
      await saveUserEmail(email.trim())
      setMessage({ text: '저장되었습니다.', success: true })
    } catch {
      setMessage({ text: '저장에 실패했습니다. 다시 시도해주세요.', success: false })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ paddingTop: 24 }}>
      <h2 style={{ color: '#F8FAFC', fontSize: 20, marginBottom: 8, fontFamily: "'Courier New', monospace" }}>
        설정
      </h2>
      <p style={{ color: '#475569', fontSize: 13, marginBottom: 24, fontFamily: "'Courier New', monospace" }}>
        데일리 메일 수신을 위해 이메일을 등록하세요.
      </p>

      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 12,
          padding: 20,
        }}
      >
        <label
          htmlFor="email-input"
          style={{ display: 'block', color: '#F1F5F9', fontSize: 13, marginBottom: 8, fontFamily: "'Courier New', monospace" }}
        >
          이메일
        </label>
        {loading ? (
          <p style={{ color: '#475569', fontSize: 13, fontFamily: "'Courier New', monospace" }}>불러오는 중...</p>
        ) : (
          <>
            <input
              id="email-input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@email.com"
              style={{
                width: '100%',
                background: '#0A0E1A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 8,
                padding: '10px 12px',
                color: '#F8FAFC',
                fontSize: 14,
                fontFamily: "'Courier New', monospace",
                boxSizing: 'border-box',
                outline: 'none',
              }}
            />
            <button
              onClick={handleSave}
              disabled={saving}
              style={{
                marginTop: 12,
                width: '100%',
                background: saving ? 'rgba(78,205,196,0.05)' : 'rgba(78,205,196,0.1)',
                border: '1px solid rgba(78,205,196,0.3)',
                color: '#4ECDC4',
                cursor: saving ? 'not-allowed' : 'pointer',
                fontSize: 14,
                padding: '10px 0',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
              }}
            >
              {saving ? '저장 중...' : '저장'}
            </button>
            {message && (
              <p
                style={{
                  marginTop: 10,
                  fontSize: 13,
                  color: message.success ? '#10B981' : '#EF4444',
                  fontFamily: "'Courier New', monospace",
                }}
              >
                {message.text}
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
