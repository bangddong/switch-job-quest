import { useState, useEffect } from 'react'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'
import { loadResume, updateResume } from '../api/resumeApi'

const MAX_LENGTH = 50000
const PLACEHOLDER = '## 경력 요약\n- ...\n\n## 기술 스택\n- ...\n\n## 프로젝트 경험\n### 프로젝트명\n- 역할:\n- 성과:'

export function ResumeProfilePage() {
  const [content, setContent] = useState('')
  const [updatedAt, setUpdatedAt] = useState<string | null>(null)
  const [registered, setRegistered] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [message, setMessage] = useState<{ text: string; success: boolean } | null>(null)

  useEffect(() => {
    loadResume()
      .then((resume) => {
        if (resume) {
          setContent(resume.content)
          setUpdatedAt(resume.updatedAt)
          setRegistered(true)
        }
      })
      .catch(() => {/* 조회 실패 시 빈 상태 유지 */})
      .finally(() => setLoading(false))
  }, [])

  const handleSave = async () => {
    if (!content.trim() || saving) return
    setSaving(true)
    setMessage(null)
    try {
      const resume = await updateResume(content.trim())
      setContent(resume.content)
      setUpdatedAt(resume.updatedAt)
      setRegistered(true)
      setMessage({ text: '저장되었습니다.', success: true })
    } catch {
      setMessage({ text: '저장에 실패했습니다. 내용을 확인 후 다시 시도해주세요.', success: false })
    } finally {
      setSaving(false)
    }
  }

  const length = content.length
  const countColor = length >= MAX_LENGTH ? '#EF4444' : length >= 45000 ? '#F59E0B' : '#475569'
  const saveDisabled = saving || !content.trim()

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingTop: 24 }}>
      <h2 style={{ color: '#F8FAFC', fontSize: 20, marginBottom: 8, fontFamily: "'Courier New', monospace" }}>
        이력서 프로필
      </h2>
      <p style={{ color: '#475569', fontSize: 13, marginBottom: 24, fontFamily: "'Courier New', monospace" }}>
        한 번 등록해두면 회사별 AI 이력서 점검에 자동으로 사용됩니다.
      </p>

      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 12,
          padding: 20,
        }}
      >
        {loading ? (
          <p style={{ color: '#475569', fontSize: 13, fontFamily: "'Courier New', monospace" }}>불러오는 중...</p>
        ) : (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span style={{ color: '#475569', fontSize: 12, fontFamily: "'Courier New', monospace" }}>
                {registered && updatedAt ? `마지막 저장: ${updatedAt}` : '아직 등록된 이력서가 없습니다'}
              </span>
              <span style={{ color: countColor, fontSize: 12, fontFamily: "'Courier New', monospace" }}>
                {length} / {MAX_LENGTH}
              </span>
            </div>

            {previewing ? (
              <div
                style={{
                  minHeight: 320,
                  background: '#0A0E1A',
                  border: '1px solid rgba(255,255,255,0.08)',
                  borderRadius: 8,
                  padding: 12,
                  boxSizing: 'border-box',
                }}
              >
                <MarkdownRenderer content={content} fontSize={13} />
              </div>
            ) : (
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder={registered ? undefined : PLACEHOLDER}
                maxLength={MAX_LENGTH}
                style={{
                  width: '100%',
                  minHeight: 320,
                  background: '#0A0E1A',
                  border: '1px solid rgba(255,255,255,0.08)',
                  borderRadius: 8,
                  padding: 12,
                  color: '#F1F5F9',
                  fontSize: 14,
                  fontFamily: "'Courier New', monospace",
                  resize: 'vertical',
                  boxSizing: 'border-box',
                  outline: 'none',
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = 'rgba(245,158,11,0.4)' }}
                onBlur={(e) => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.08)' }}
              />
            )}

            {registered && (
              <button
                onClick={() => setPreviewing((prev) => !prev)}
                style={{
                  marginTop: 8,
                  background: 'none',
                  border: 'none',
                  color: '#F59E0B',
                  cursor: 'pointer',
                  fontSize: 13,
                  padding: 0,
                  fontFamily: "'Courier New', monospace",
                }}
              >
                {previewing ? '편집으로 돌아가기' : '미리보기'}
              </button>
            )}

            <button
              onClick={handleSave}
              disabled={saveDisabled}
              style={{
                marginTop: 12,
                width: '100%',
                background: saveDisabled ? 'rgba(245,158,11,0.05)' : 'rgba(245,158,11,0.1)',
                border: '1px solid rgba(245,158,11,0.3)',
                color: '#F59E0B',
                cursor: saveDisabled ? 'not-allowed' : 'pointer',
                fontSize: 14,
                padding: '12px 0',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
              }}
            >
              {saving ? '저장 중...' : registered ? '수정 사항 저장' : '이력서 등록하기'}
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
