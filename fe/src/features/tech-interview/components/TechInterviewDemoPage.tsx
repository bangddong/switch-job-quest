import { useState } from 'react'
import type { ApiResponse, TechInterviewResult } from '@/types/api.types'
import { MarkdownRenderer } from '../../../components/MarkdownRenderer'

async function fetchDemoQuestion(techStack: string): Promise<TechInterviewResult> {
  const res = await fetch(`/api/v1/tech-interview/question?techStack=${encodeURIComponent(techStack)}`, {
    headers: { 'Content-Type': 'application/json' },
  })
  if (res.status === 429) {
    const json = await res.json().catch(() => ({}))
    throw new Error((json as { error?: { message?: string } })?.error?.message ?? '오늘 체험 횟수를 초과했습니다. 내일 다시 시도해주세요.')
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<TechInterviewResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('질문 조회 실패')
  return json.data
}

async function submitDemoEvaluation(techStack: string, questions: string[], answers: string[]): Promise<TechInterviewResult> {
  const res = await fetch('/api/v1/tech-interview/evaluate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ techStack, questions, answers }),
  })
  if (res.status === 429) {
    const json = await res.json().catch(() => ({}))
    throw new Error((json as { error?: { message?: string } })?.error?.message ?? '오늘 체험 횟수를 초과했습니다. 내일 다시 시도해주세요.')
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json: ApiResponse<TechInterviewResult> = await res.json()
  if (json.result !== 'SUCCESS' || json.data == null) throw new Error('평가 실패')
  return json.data
}

const TECH_STACKS = ['Java', 'Kotlin']

interface TechInterviewDemoPageProps {
  onLogin: () => void
}

export function TechInterviewDemoPage({ onLogin }: TechInterviewDemoPageProps) {
  const [techStack, setTechStack] = useState<string>('Java')
  const [questions, setQuestions] = useState<string[]>([])
  const [answers, setAnswers] = useState<string[]>([])
  const [result, setResult] = useState<TechInterviewResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleFetchQuestions = async () => {
    setLoading(true)
    setError(null)
    setResult(null)
    setAnswers([])
    try {
      const data = await fetchDemoQuestion(techStack)
      setQuestions(data.questions)
      setAnswers(data.questions.map(() => ''))
    } catch (e) {
      setError(e instanceof Error ? e.message : '질문을 불러오는 데 실패했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleAnswerChange = (index: number, value: string) => {
    setAnswers((prev) => {
      const next = [...prev]
      next[index] = value
      return next
    })
  }

  const handleSubmit = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const data = await submitDemoEvaluation(techStack, questions, answers)
      setResult(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : '평가 요청에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleReset = () => {
    setQuestions([])
    setAnswers([])
    setResult(null)
    setError(null)
  }

  return (
    <div style={{ paddingTop: 16 }}>
      {/* 안내 배너 */}
      <div
        style={{
          background: 'rgba(78,205,196,0.06)',
          border: '1px solid rgba(78,205,196,0.25)',
          borderRadius: 10,
          padding: '12px 16px',
          marginBottom: 24,
        }}
      >
        <p style={{ color: '#4ECDC4', fontSize: 13, fontFamily: "'Courier New', monospace", margin: 0, lineHeight: 1.6 }}>
          로그인 없이 체험 중입니다. 결과는 저장되지 않습니다.
        </p>
      </div>

      <h2 style={{ color: '#F8FAFC', fontSize: 20, marginBottom: 8, fontFamily: "'Courier New', monospace" }}>
        기술 면접 연습
      </h2>
      <p style={{ color: '#475569', fontSize: 13, marginBottom: 24, fontFamily: "'Courier New', monospace" }}>
        기술 스택을 선택하고 질문에 답변을 작성하세요.
      </p>

      {/* 기술 스택 선택 */}
      <div style={{ marginBottom: 20 }}>
        <p style={{ color: '#F1F5F9', fontSize: 13, marginBottom: 10, fontFamily: "'Courier New', monospace" }}>
          기술 스택
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          {TECH_STACKS.map((stack) => (
            <button
              key={stack}
              onClick={() => { setTechStack(stack); handleReset() }}
              style={{
                background: techStack === stack ? 'rgba(78,205,196,0.15)' : 'rgba(255,255,255,0.04)',
                border: `1px solid ${techStack === stack ? 'rgba(78,205,196,0.5)' : 'rgba(255,255,255,0.08)'}`,
                color: techStack === stack ? '#4ECDC4' : '#475569',
                cursor: 'pointer',
                fontSize: 13,
                padding: '8px 16px',
                borderRadius: 8,
                fontFamily: "'Courier New', monospace",
              }}
            >
              {stack}
            </button>
          ))}
        </div>
      </div>

      {/* 질문 받기 버튼 */}
      {questions.length === 0 && !result && (
        <button
          onClick={handleFetchQuestions}
          disabled={loading}
          style={{
            width: '100%',
            background: loading ? 'rgba(78,205,196,0.05)' : 'rgba(78,205,196,0.1)',
            border: '1px solid rgba(78,205,196,0.3)',
            color: '#4ECDC4',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontSize: 14,
            padding: '12px 0',
            borderRadius: 8,
            fontFamily: "'Courier New', monospace",
          }}
        >
          {loading ? '질문 불러오는 중...' : '질문 받기'}
        </button>
      )}

      {error && (
        <p style={{ color: '#EF4444', fontSize: 13, marginTop: 10, fontFamily: "'Courier New', monospace" }}>
          {error}
        </p>
      )}

      {/* 질문 목록 + 답변 입력 */}
      {questions.length > 0 && !result && (
        <div>
          {questions.map((q, i) => (
            <div
              key={i}
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 12,
                padding: 16,
                marginBottom: 16,
              }}
            >
              <p style={{ color: '#F1F5F9', fontSize: 14, marginBottom: 10, fontFamily: "'Courier New', monospace", lineHeight: 1.6 }}>
                Q{i + 1}. {q}
              </p>
              <textarea
                value={answers[i] ?? ''}
                onChange={(e) => handleAnswerChange(i, e.target.value)}
                placeholder="답변을 입력하세요..."
                rows={4}
                style={{
                  width: '100%',
                  background: '#0A0E1A',
                  border: '1px solid rgba(255,255,255,0.08)',
                  borderRadius: 8,
                  padding: '10px 12px',
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
          ))}

          <button
            onClick={handleSubmit}
            disabled={submitting}
            style={{
              width: '100%',
              background: submitting ? 'rgba(78,205,196,0.05)' : 'rgba(78,205,196,0.1)',
              border: '1px solid rgba(78,205,196,0.3)',
              color: '#4ECDC4',
              cursor: submitting ? 'not-allowed' : 'pointer',
              fontSize: 14,
              padding: '12px 0',
              borderRadius: 8,
              fontFamily: "'Courier New', monospace",
            }}
          >
            {submitting ? '평가 중...' : '제출'}
          </button>
        </div>
      )}

      {/* 평가 결과 */}
      {result && (
        <div>
          <div
            style={{
              background: result.passed ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
              border: `1px solid ${result.passed ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
              borderRadius: 12,
              padding: 20,
              marginBottom: 16,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <p style={{ color: '#F1F5F9', fontSize: 15, fontFamily: "'Courier New', monospace", margin: 0 }}>
                평가 결과
              </p>
              <span
                style={{
                  color: result.passed ? '#10B981' : '#EF4444',
                  background: result.passed ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                  border: `1px solid ${result.passed ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)'}`,
                  borderRadius: 6,
                  padding: '3px 10px',
                  fontSize: 13,
                  fontFamily: "'Courier New', monospace",
                }}
              >
                {result.passed ? 'PASS' : 'FAIL'}
              </span>
            </div>
            <p style={{ color: '#4ECDC4', fontSize: 28, fontFamily: "'Courier New', monospace", margin: '0 0 12px' }}>
              {result.overallScore}점
            </p>
            <MarkdownRenderer content={result.feedback} />
          </div>

          {/* 모범 답안 */}
          {result.modelAnswer && (
            <div
              style={{
                background: 'rgba(96,165,250,0.04)',
                border: '1px solid rgba(96,165,250,0.25)',
                borderRadius: 12,
                padding: 20,
                marginBottom: 16,
              }}
            >
              <p style={{ color: '#60A5FA', fontSize: 13, fontFamily: "'Courier New', monospace", margin: '0 0 10px' }}>
                💡 모범 답안
              </p>
              <MarkdownRenderer content={result.modelAnswer} />
            </div>
          )}

          {/* 로그인 CTA */}
          <div
            style={{
              background: 'rgba(78,205,196,0.04)',
              border: '1px solid rgba(78,205,196,0.2)',
              borderRadius: 12,
              padding: 20,
              marginBottom: 16,
              textAlign: 'center',
            }}
          >
            <p style={{ color: '#F1F5F9', fontSize: 14, fontFamily: "'Courier New', monospace", margin: '0 0 16px', lineHeight: 1.6 }}>
              결과가 저장되지 않았습니다.<br />로그인하면 XP를 적립하고 성장 기록을 남길 수 있습니다.
            </p>
            <button
              onClick={onLogin}
              style={{
                width: '100%',
                background: 'linear-gradient(135deg, #4ECDC4, #2DD4BF)',
                border: 'none',
                borderRadius: 8,
                color: '#060610',
                fontSize: 14,
                fontWeight: 'bold',
                padding: '12px 0',
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
                letterSpacing: 0.5,
              }}
            >
              GitHub로 로그인하고 XP 적립하기
            </button>
          </div>

          <button
            onClick={handleReset}
            style={{
              width: '100%',
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              color: '#475569',
              cursor: 'pointer',
              fontSize: 14,
              padding: '12px 0',
              borderRadius: 8,
              fontFamily: "'Courier New', monospace",
            }}
          >
            다시 연습하기
          </button>
        </div>
      )}
    </div>
  )
}
