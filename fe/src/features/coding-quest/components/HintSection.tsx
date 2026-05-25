import { useState, useEffect } from 'react'
import type { CodingProblem } from '@/types/api.types'
import { fetchHint } from '../api/fetchHint'

interface HintSectionProps {
  problem: CodingProblem
}

export function HintSection({ problem }: HintSectionProps) {
  const [hints, setHints] = useState<string[]>([])
  const [loadingHint, setLoadingHint] = useState(false)
  const [hintError, setHintError] = useState<string | null>(null)

  useEffect(() => {
    setHints([])
    setLoadingHint(false)
    setHintError(null)
  }, [problem.id])

  const handleRequestHint = async () => {
    if (loadingHint || hints.length >= 3) return
    const nextLevel = (hints.length + 1) as 1 | 2 | 3
    setLoadingHint(true)
    setHintError(null)
    try {
      const result = await fetchHint(
        String(problem.id),
        problem.title,
        problem.description,
        nextLevel,
      )
      setHints((prev) => [...prev, result.hint])
    } catch {
      setHintError('힌트를 불러오는 데 실패했습니다.')
    } finally {
      setLoadingHint(false)
    }
  }

  const buttonText = loadingHint
    ? '불러오는 중...'
    : hints.length === 0
    ? '힌트 보기'
    : '다음 힌트'

  return (
    <div style={{ marginTop: 24 }}>
      <p
        style={{
          fontSize: 11,
          color: '#475569',
          margin: '0 0 8px',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
        }}
      >
        HINT
      </p>

      {hints.map((hint, i) => (
        <div
          key={i}
          style={{
            background: 'rgba(245,158,11,0.06)',
            border: '1px solid rgba(245,158,11,0.25)',
            borderRadius: 6,
            padding: '10px 12px',
            marginBottom: 8,
          }}
        >
          <span
            style={{
              fontSize: 11,
              color: '#F59E0B',
              background: 'rgba(245,158,11,0.15)',
              borderRadius: 3,
              padding: '2px 6px',
            }}
          >
            힌트 {i + 1}
          </span>
          <p
            style={{
              fontSize: 13,
              color: '#CBD5E1',
              lineHeight: 1.6,
              marginTop: 6,
              marginBottom: 0,
            }}
          >
            {hint}
          </p>
        </div>
      ))}

      {hints.length < 3 && (
        <button
          onClick={handleRequestHint}
          disabled={loadingHint}
          style={{
            width: '100%',
            padding: '8px 0',
            borderRadius: 6,
            background: 'rgba(245,158,11,0.1)',
            border: '1px solid rgba(245,158,11,0.35)',
            color: '#F59E0B',
            fontSize: 13,
            fontFamily: "'Courier New', monospace",
            cursor: loadingHint ? 'not-allowed' : 'pointer',
            opacity: loadingHint ? 0.5 : 1,
          }}
        >
          {buttonText}
        </button>
      )}

      {hintError && (
        <p style={{ fontSize: 12, color: '#EF4444', margin: '6px 0 0' }}>
          {hintError}
        </p>
      )}
    </div>
  )
}
