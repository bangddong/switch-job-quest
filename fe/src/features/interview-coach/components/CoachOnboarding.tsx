import { useState } from 'react'
import { OracleLoadingModal } from '@/components/ui/OracleLoadingModal'
import { CoachBubble } from './CoachBubble'

interface CoachOnboardingProps {
  onStart: (targetRole: string, jdText: string) => void
  loading: boolean
}

export function CoachOnboarding({ onStart, loading }: CoachOnboardingProps) {
  const [targetRole, setTargetRole] = useState('')
  const [jdText, setJdText] = useState('')

  const canSubmit = targetRole.trim().length > 0 && jdText.trim().length > 0 && !loading

  return (
    <div>
      <CoachBubble
        message={`안녕하세요! 저는 당신의 전담 면접 코치입니다. 🎯\n\n지원할 직무와 채용공고(JD)를 알려주시면, 처음부터 끝까지 함께 면접을 준비해드리겠습니다.\n\n먼저 아래 정보를 입력해주세요.`}
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 8 }}>
        <div>
          <label style={{ display: 'block', fontSize: 12, color: '#4ECDC4', marginBottom: 6 }}>
            목표 직무
          </label>
          <input
            type="text"
            value={targetRole}
            onChange={(e) => setTargetRole(e.target.value)}
            placeholder="예: 백엔드 개발자 (5년차)"
            style={{
              width: '100%',
              background: '#0A0E1A',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: 8,
              padding: '10px 14px',
              color: '#F8FAFC',
              fontSize: 14,
              fontFamily: "'Courier New', monospace",
              boxSizing: 'border-box',
              outline: 'none',
            }}
          />
        </div>

        <div>
          <label style={{ display: 'block', fontSize: 12, color: '#4ECDC4', marginBottom: 6 }}>
            채용공고 (JD) 붙여넣기
          </label>
          <textarea
            value={jdText}
            onChange={(e) => setJdText(e.target.value)}
            placeholder="채용공고 내용을 그대로 붙여넣어 주세요. 직무 설명, 자격 요건, 우대사항 등을 포함하면 더 정확한 질문이 생성됩니다."
            rows={8}
            style={{
              width: '100%',
              background: '#0A0E1A',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: 8,
              padding: '10px 14px',
              color: '#F8FAFC',
              fontSize: 13,
              fontFamily: "'Courier New', monospace",
              boxSizing: 'border-box',
              resize: 'vertical',
              outline: 'none',
              lineHeight: 1.6,
            }}
          />
        </div>

        <OracleLoadingModal isOpen={loading} />
        <button
          onClick={() => onStart(targetRole.trim(), jdText.trim())}
          disabled={!canSubmit}
          style={{
            background: canSubmit ? 'linear-gradient(135deg, #4ECDC4, #A78BFA)' : 'rgba(255,255,255,0.05)',
            border: 'none',
            borderRadius: 8,
            padding: '12px 24px',
            color: canSubmit ? '#060610' : '#475569',
            fontSize: 14,
            fontWeight: 700,
            fontFamily: "'Courier New', monospace",
            cursor: canSubmit ? 'pointer' : 'not-allowed',
            transition: 'opacity 0.2s',
          }}
        >
          {loading ? '코치가 JD를 분석 중입니다...' : '코칭 시작하기 →'}
        </button>
      </div>
    </div>
  )
}
