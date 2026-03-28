import { useState } from 'react'
import type { Character, CharacterRole, CharacterYears } from '@/types/character.types'

interface CharacterCreateProps {
  onComplete: (character: Character) => void
}

const ROLES: CharacterRole[] = ['백엔드', '프론트엔드', '풀스택', '데이터/ML', 'DevOps']
const YEARS: CharacterYears[] = ['신입', '1-3년', '3-5년', '5년+']

const ROLE_ICONS: Record<CharacterRole, string> = {
  '백엔드': '⚙️',
  '프론트엔드': '🎨',
  '풀스택': '⚡',
  '데이터/ML': '🧠',
  'DevOps': '🔧',
}

export function CharacterCreate({ onComplete }: CharacterCreateProps) {
  const [nickname, setNickname] = useState('')
  const [role, setRole] = useState<CharacterRole | null>(null)
  const [years, setYears] = useState<CharacterYears | null>(null)

  const canSubmit = nickname.trim().length > 0 && role !== null && years !== null

  const handleSubmit = () => {
    if (!canSubmit || role === null || years === null) return
    onComplete({ nickname: nickname.trim(), role, years })
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '40px 0',
        animation: 'slideIn 0.4s ease',
      }}
    >
      {/* 헤더 */}
      <div style={{ textAlign: 'center', marginBottom: 40 }}>
        <div style={{ fontSize: 10, letterSpacing: 6, color: '#1E293B', marginBottom: 12 }}>
          CHARACTER CREATION
        </div>
        <div style={{ fontSize: 40, marginBottom: 16 }}>⚔️</div>
        <h1 style={{ fontSize: 22, fontWeight: 'bold', margin: '0 0 8px', color: '#F8FAFC', letterSpacing: 1 }}>
          새로운 모험가여
        </h1>
        <p style={{ fontSize: 13, color: '#475569', margin: 0, lineHeight: 1.6 }}>
          이직이라는 던전에 입장하기 전,<br />
          자신의 캐릭터를 설정하라
        </p>
      </div>

      {/* 닉네임 */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 10, letterSpacing: 4, color: '#4ECDC4', marginBottom: 10 }}>
          NICKNAME
        </div>
        <input
          type="text"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="개발자 닉네임을 입력하세요"
          maxLength={20}
          style={{
            width: '100%',
            padding: '12px 14px',
            background: '#0A0E1A',
            border: `1px solid ${nickname.trim() ? 'rgba(78,205,196,0.4)' : 'rgba(255,255,255,0.08)'}`,
            borderRadius: 8,
            color: '#F8FAFC',
            fontSize: 14,
            fontFamily: "'Courier New', monospace",
            outline: 'none',
            boxSizing: 'border-box',
          }}
        />
      </div>

      {/* 직군 */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 10, letterSpacing: 4, color: '#4ECDC4', marginBottom: 10 }}>
          CLASS
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {ROLES.map((r) => (
            <button
              key={r}
              onClick={() => setRole(r)}
              style={{
                padding: '9px 14px',
                background: role === r ? 'rgba(78,205,196,0.15)' : '#0A0E1A',
                border: `1px solid ${role === r ? 'rgba(78,205,196,0.5)' : 'rgba(255,255,255,0.08)'}`,
                borderRadius: 8,
                color: role === r ? '#4ECDC4' : '#64748B',
                fontSize: 13,
                fontFamily: "'Courier New', monospace",
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              {ROLE_ICONS[r]} {r}
            </button>
          ))}
        </div>
      </div>

      {/* 경력 */}
      <div style={{ marginBottom: 40 }}>
        <div style={{ fontSize: 10, letterSpacing: 4, color: '#4ECDC4', marginBottom: 10 }}>
          EXPERIENCE
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {YEARS.map((y) => (
            <button
              key={y}
              onClick={() => setYears(y)}
              style={{
                flex: 1,
                padding: '10px 8px',
                background: years === y ? 'rgba(78,205,196,0.15)' : '#0A0E1A',
                border: `1px solid ${years === y ? 'rgba(78,205,196,0.5)' : 'rgba(255,255,255,0.08)'}`,
                borderRadius: 8,
                color: years === y ? '#4ECDC4' : '#64748B',
                fontSize: 12,
                fontFamily: "'Courier New', monospace",
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              {y}
            </button>
          ))}
        </div>
      </div>

      {/* 시작 버튼 */}
      <button
        onClick={handleSubmit}
        disabled={!canSubmit}
        className={canSubmit ? 'hov-btn' : ''}
        style={{
          width: '100%',
          padding: '15px',
          background: canSubmit
            ? 'linear-gradient(135deg, #4ECDC4, #4ECDC480)'
            : 'rgba(255,255,255,0.04)',
          border: `1px solid ${canSubmit ? 'transparent' : 'rgba(255,255,255,0.06)'}`,
          borderRadius: 10,
          color: canSubmit ? '#060610' : '#334155',
          fontSize: 15,
          fontWeight: 'bold',
          fontFamily: "'Courier New', monospace",
          cursor: canSubmit ? 'pointer' : 'not-allowed',
          transition: 'all 0.2s ease',
          letterSpacing: 1,
        }}
      >
        ⚔️ 모험 시작
      </button>
    </div>
  )
}
