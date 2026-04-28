import { useState, useRef, useEffect } from 'react'
import type { InterviewEvaluationResult } from '@/types/api.types'
import { OracleLoadingModal } from '@/components/ui/OracleLoadingModal'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { FALLBACK_QUESTIONS } from '../constants/fallbackQuestions'
import { submitMockInterview } from '../api/aiCheckApi'
import { InterviewResultCard } from './InterviewResultCard'
import { PASS_THRESHOLD } from '@/utils/gradeUtils'
import { MOCK_INTERVIEW_SAMPLE_ANSWER } from '../constants/mockValues'

interface MockInterviewPanelProps {
  onComplete: (score: number) => void
}

export function MockInterviewPanel({ onComplete }: MockInterviewPanelProps) {
  const [questions] = useState(FALLBACK_QUESTIONS)
  const [idx, setIdx] = useState(0)
  const [answer, setAnswer] = useState('')
  const [results, setResults] = useState<InterviewEvaluationResult[]>([])
  const [loading, setLoading] = useState(false)
  const [lastResult, setLastResult] = useState<InterviewEvaluationResult | null>(null)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  const q = questions[idx]
  const totalScore = results.length
    ? Math.round(results.reduce((a, r) => a + r.score, 0) / results.length)
    : 0

  const handleSubmit = async () => {
    if (!q || !answer.trim()) return
    setLoading(true)
    setError(null)
    try {
      const result = await submitMockInterview(
        { questId: '2-BOSS', questionId: q.id, question: q.question, answer, category: q.category },
      )
      const newResults = [...results, result]
      setResults(newResults)
      setLastResult(result)

      if (idx + 1 >= questions.length) {
        const avg = Math.round(newResults.reduce((a, r) => a + r.score, 0) / newResults.length)
        setDone(true)
        onComplete(avg)
      } else {
        timerRef.current = setTimeout(() => {
          setIdx((i) => i + 1)
          setAnswer('')
          setLastResult(null)
        }, 2000)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 평가 중 오류가 발생했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    const passed = totalScore >= PASS_THRESHOLD
    return (
      <div style={{ textAlign: 'center', padding: '28px 0' }}>
        <div style={{ fontSize: 44, marginBottom: 12 }}>{passed ? '🏆' : '📚'}</div>
        <div
          style={{
            fontSize: 24,
            fontWeight: 'bold',
            color: passed ? '#10B981' : '#EF4444',
            marginBottom: 6,
          }}
        >
          최종 점수: {totalScore}/100
        </div>
        <div style={{ fontSize: 13, color: '#64748B', marginBottom: 20 }}>
          {passed ? '+800 XP 획득! 다음 Act 해금' : '70점 이상 필요. 재도전하세요'}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {results.map((r, i) => (
            <div
              key={i}
              style={{
                background: 'rgba(13,17,23,0.7)',
                borderRadius: 8,
                padding: '9px 14px',
                display: 'flex',
                justifyContent: 'space-between',
              }}
            >
              <span style={{ fontSize: 13, color: '#64748B' }}>
                Q{i + 1}. {questions[i]?.category}
              </span>
              <span
                style={{
                  fontSize: 13,
                  fontWeight: 'bold',
                  color: r.score >= PASS_THRESHOLD ? '#10B981' : '#EF4444',
                }}
              >
                {r.score}점
              </span>
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (!q) return null

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 12, color: '#475569' }}>
          Q {idx + 1} / {questions.length}
        </span>
        <span style={{ fontSize: 12, color: '#4ECDC4' }}>{q.category}</span>
      </div>

      <div style={{ marginBottom: 18 }}>
        <ProgressBar value={((idx + 1) / questions.length) * 100} color="#4ECDC4" height={4} />
      </div>

      <div
        style={{
          background: 'rgba(78,205,196,0.04)',
          border: '1px solid rgba(78,205,196,0.15)',
          borderRadius: 12,
          padding: 18,
          marginBottom: 14,
        }}
      >
        <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 8 }}>
          QUESTION
        </div>
        <p style={{ fontSize: 14, color: '#F1F5F9', margin: 0, lineHeight: 1.7 }}>{q.question}</p>
      </div>

      <OracleLoadingModal isOpen={loading} />
      {error && (
        <div
          style={{
            background: 'rgba(239,68,68,0.1)',
            border: '1px solid rgba(239,68,68,0.3)',
            borderRadius: 8,
            padding: '10px 14px',
            marginBottom: 10,
            fontSize: 13,
            color: '#EF4444',
          }}
        >
          {error}
        </div>
      )}

      {lastResult && <InterviewResultCard result={lastResult} />}
      {!lastResult && (
        <>
          <button
            onClick={() => setAnswer(MOCK_INTERVIEW_SAMPLE_ANSWER)}
            style={{
              background: 'none',
              border: '1px solid rgba(78,205,196,0.3)',
              borderRadius: 6,
              color: '#4ECDC4',
              fontSize: 11,
              padding: '4px 10px',
              cursor: 'pointer',
              fontFamily: "'Courier New', monospace",
              marginBottom: 8,
              display: 'block',
            }}
          >
            샘플 답변 채우기
          </button>
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            rows={5}
            placeholder="답변을 구체적으로 작성해주세요..."
            style={{
              width: '100%',
              background: '#0A0E1A',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: 8,
              padding: '10px 13px',
              color: '#F1F5F9',
              fontSize: 13,
              outline: 'none',
              resize: 'vertical',
              boxSizing: 'border-box',
              lineHeight: 1.6,
              fontFamily: "'Courier New', monospace",
              marginBottom: 10,
            }}
          />
          <button
            onClick={handleSubmit}
            disabled={loading || !answer.trim()}
            style={{
              width: '100%',
              padding: '12px',
              background: loading
                ? 'rgba(239,68,68,0.2)'
                : 'linear-gradient(135deg, #EF4444, #DC2626)',
              border: 'none',
              borderRadius: 10,
              color: '#fff',
              fontSize: 14,
              fontWeight: 'bold',
              cursor: loading ? 'not-allowed' : 'pointer',
              fontFamily: "'Courier New', monospace",
            }}
          >
            {loading ? '⟳ 채점 중...' : '답변 제출 →'}
          </button>
        </>
      )}
    </div>
  )
}
