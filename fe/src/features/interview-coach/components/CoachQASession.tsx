import { useState } from 'react'
import type { CoachQuestion, CoachAnswerResult, CoachAnswerHistory } from '../types/coach.types'
import { CoachBubble } from './CoachBubble'

interface CoachQASessionProps {
  questions: CoachQuestion[]
  onSubmitAnswer: (question: string, answer: string, index: number, total: number) => Promise<CoachAnswerResult>
  onComplete: (history: CoachAnswerHistory[]) => void
}

export function CoachQASession({ questions, onSubmitAnswer, onComplete }: CoachQASessionProps) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [answer, setAnswer] = useState('')
  const [feedback, setFeedback] = useState<CoachAnswerResult | null>(null)
  const [history, setHistory] = useState<CoachAnswerHistory[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const current = questions[currentIndex]!
  const isLast = currentIndex === questions.length - 1

  const handleSubmit = async () => {
    if (!answer.trim() || loading) return
    setLoading(true)
    setError(null)
    try {
      const result = await onSubmitAnswer(current.question, answer.trim(), currentIndex, questions.length)
      setFeedback(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 평가 중 오류가 발생했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleNext = () => {
    const newHistory: CoachAnswerHistory[] = [
      ...history,
      { question: current.question, answer: answer.trim(), feedback: feedback?.feedback ?? '' },
    ]
    setHistory(newHistory)

    if (isLast) {
      onComplete(newHistory)
    } else {
      setCurrentIndex((i) => i + 1)
      setAnswer('')
      setFeedback(null)
    }
  }

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 20,
        }}
      >
        <span style={{ fontSize: 12, color: '#475569' }}>면접 진행 중</span>
        <div style={{ display: 'flex', gap: 6 }}>
          {questions.map((_, i) => (
            <div
              key={i}
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background:
                  i < currentIndex
                    ? '#10B981'
                    : i === currentIndex
                      ? '#4ECDC4'
                      : 'rgba(255,255,255,0.1)',
              }}
            />
          ))}
        </div>
        <span style={{ fontSize: 12, color: '#4ECDC4' }}>
          {currentIndex + 1} / {questions.length}
        </span>
      </div>

      <CoachBubble message={current.question} />

      {!feedback && (
        <div style={{ marginTop: 8 }}>
          {loading && (
            <div style={{
              background: 'rgba(78,205,196,0.06)',
              border: '1px solid rgba(78,205,196,0.2)',
              borderRadius: 10,
              padding: '14px 16px',
              marginBottom: 12,
              textAlign: 'center',
            }}>
              <div style={{ fontSize: 12, color: '#4ECDC4', letterSpacing: 2, marginBottom: 6 }}>
                ⟳ 답변 검토 중
              </div>
              <div style={{ fontSize: 11, color: '#94A3B8' }}>
                약 30초 소요됩니다. 잠시만 기다려주세요.
              </div>
            </div>
          )}
          {error && (
            <div style={{
              background: 'rgba(239,68,68,0.1)',
              border: '1px solid rgba(239,68,68,0.3)',
              borderRadius: 8,
              padding: '10px 14px',
              marginBottom: 10,
              fontSize: 13,
              color: '#EF4444',
            }}>
              {error}
            </div>
          )}
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            disabled={loading}
            placeholder="답변을 입력해주세요. 구체적인 경험과 수치를 포함하면 더 좋은 평가를 받을 수 있습니다."
            rows={6}
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
              marginBottom: 12,
            }}
          />
          <button
            onClick={handleSubmit}
            disabled={!answer.trim() || loading}
            style={{
              width: '100%',
              background:
                answer.trim() && !loading
                  ? 'linear-gradient(135deg, #4ECDC4, #A78BFA)'
                  : 'rgba(255,255,255,0.05)',
              border: 'none',
              borderRadius: 8,
              padding: '12px 24px',
              color: answer.trim() && !loading ? '#060610' : '#475569',
              fontSize: 14,
              fontWeight: 700,
              fontFamily: "'Courier New', monospace",
              cursor: answer.trim() && !loading ? 'pointer' : 'not-allowed',
            }}
          >
            {loading ? '코치가 답변을 검토 중...' : '답변 제출'}
          </button>
        </div>
      )}

      {feedback && (
        <div style={{ marginTop: 8 }}>
          <div
            style={{
              background: '#0A0E1A',
              border: '1px solid rgba(255,255,255,0.06)',
              borderRadius: 8,
              padding: '10px 14px',
              marginBottom: 12,
              color: '#94A3B8',
              fontSize: 13,
              lineHeight: 1.6,
            }}
          >
            {answer}
          </div>

          <CoachBubble message={feedback.feedback} />

          {feedback.improvements.length > 0 && (
            <div
              style={{
                background: 'rgba(245,158,11,0.08)',
                border: '1px solid rgba(245,158,11,0.2)',
                borderRadius: 8,
                padding: 14,
                marginBottom: 12,
              }}
            >
              <p style={{ fontSize: 11, color: '#F59E0B', margin: '0 0 8px', letterSpacing: 1 }}>
                개선 포인트
              </p>
              {feedback.improvements.map((imp, i) => (
                <p key={i} style={{ fontSize: 13, color: '#F1F5F9', margin: '4px 0', lineHeight: 1.5 }}>
                  • {imp}
                </p>
              ))}
            </div>
          )}

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 16,
            }}
          >
            <span style={{ fontSize: 12, color: '#475569' }}>{feedback.encouragement}</span>
            <span
              style={{
                fontSize: 20,
                fontWeight: 700,
                color: feedback.score >= 80 ? '#10B981' : feedback.score >= 60 ? '#F59E0B' : '#EF4444',
              }}
            >
              {feedback.score}점
            </span>
          </div>

          <button
            onClick={handleNext}
            style={{
              width: '100%',
              background: 'linear-gradient(135deg, #4ECDC4, #A78BFA)',
              border: 'none',
              borderRadius: 8,
              padding: '12px 24px',
              color: '#060610',
              fontSize: 14,
              fontWeight: 700,
              fontFamily: "'Courier New', monospace",
              cursor: 'pointer',
            }}
          >
            {isLast ? '종합 평가 받기 →' : '다음 질문 →'}
          </button>
        </div>
      )}
    </div>
  )
}
