import { useState } from 'react'
import type { Character } from '@/types/character.types'
import type { InterviewEvaluationResult } from '@/types/api.types'
import { OracleLoadingModal } from '@/components/ui/OracleLoadingModal'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { FALLBACK_QUESTIONS } from '../constants/fallbackQuestions'
import { generateInterviewQuestions, submitMockInterview } from '../api/aiCheckApi'
import type { InterviewQuestion } from '../api/aiCheckApi'
import { InterviewResultCard } from './InterviewResultCard'
import { PASS_THRESHOLD } from '@/utils/gradeUtils'
import { MOCK_INTERVIEW_SAMPLE_ANSWERS } from '../constants/mockValues'

const PERSONALITY_FALLBACK: InterviewQuestion[] = [
  { id: 'p1', category: '인성', question: '가장 힘들었던 팀 프로젝트 경험과 어떻게 극복했는지 말씀해주세요.', difficulty: 'MEDIUM' },
  { id: 'p2', category: '인성', question: '이직을 결심한 이유와 새 회사에서 이루고 싶은 목표를 말씀해주세요.', difficulty: 'MEDIUM' },
  { id: 'p3', category: '인성', question: '기술적으로 가장 크게 성장한 경험을 구체적으로 설명해주세요.', difficulty: 'MEDIUM' },
]

const ROLE_DEFAULTS: Record<string, string> = {
  '백엔드': 'Java,Spring Boot,JPA,MySQL,Redis',
  '프론트엔드': 'React,TypeScript,Next.js',
  '풀스택': 'React,TypeScript,Node.js,PostgreSQL',
  '데이터/ML': 'Python,Pandas,TensorFlow,SQL',
  'DevOps': 'Docker,Kubernetes,AWS,Terraform',
}

interface MockInterviewPanelProps {
  character: Character
  onComplete: (score: number) => void
}

type Phase = 'onboarding' | 'loading' | 'interview' | 'done'

export function MockInterviewPanel({ character, onComplete }: MockInterviewPanelProps) {
  // onboarding state
  const [techStack, setTechStack] = useState(ROLE_DEFAULTS[character.role] ?? 'Kotlin,Spring Boot,JPA')
  const [techCount, setTechCount] = useState(8)
  const [personalityCount, setPersonalityCount] = useState(3)

  // interview state
  const [phase, setPhase] = useState<Phase>('onboarding')
  const [questions, setQuestions] = useState<InterviewQuestion[]>([])
  const [idx, setIdx] = useState(0)
  const [answers, setAnswers] = useState<string[]>([])
  const [answer, setAnswer] = useState('')
  const [results, setResults] = useState<InterviewEvaluationResult[]>([])
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [completionReported, setCompletionReported] = useState(false)

  const roleToTargetRole: Record<string, string> = {
    '백엔드': '백엔드 개발자',
    '프론트엔드': '프론트엔드 개발자',
    '풀스택': '풀스택 개발자',
    '데이터/ML': '데이터/ML 엔지니어',
    'DevOps': 'DevOps 엔지니어',
  }

  const handleStartInterview = async () => {
    setPhase('loading')
    setError(null)
    try {
      const fetched = await generateInterviewQuestions({
        techStack: techStack.split(',').map(s => s.trim()).filter(Boolean),
        targetRole: roleToTargetRole[character.role] ?? '개발자',
        yearsOfExperience: character.years,
        categories: 'Java,Spring,DB,JVM,인프라,네트워크,설계',
        techCount,
        personalityCount,
      })
      if (fetched.length === 0) throw new Error('빈 응답')
      setQuestions(fetched)
    } catch {
      // fallback
      const techFallback = FALLBACK_QUESTIONS.slice(0, techCount)
      const personalityFallback = PERSONALITY_FALLBACK.slice(0, personalityCount)
      setQuestions([...techFallback, ...personalityFallback])
    }
    setPhase('interview')
  }

  const q = questions[idx]
  const isLastQuestion = idx + 1 >= questions.length
  const isPersonality = q?.category === '인성'
  const questionAccent = isPersonality ? '#A78BFA' : '#4ECDC4'

  const totalScore = results.length
    ? Math.round(results.reduce((a, r) => a + r.score, 0) / results.length)
    : 0

  const handleNext = () => {
    if (!answer.trim()) return
    setAnswers(prev => { const next = [...prev]; next[idx] = answer; return next })
    setIdx(i => i + 1)
    setAnswer('')
  }

  const handleSubmitAll = async () => {
    if (!answer.trim()) return
    const allAnswers = [...answers]
    allAnswers[idx] = answer
    setLoading(true)
    setError(null)
    try {
      const allResults = await Promise.all(
        questions.map((question, i) =>
          submitMockInterview({
            questId: '2-BOSS',
            questionId: question.id,
            question: question.question,
            answer: allAnswers[i] ?? '',
            category: question.category,
            techStack: techStack.split(',').map(s => s.trim()).filter(Boolean),
            yearsOfExperience: character.years,
          })
        )
      )
      setResults(allResults)
      setDone(true)
      setPhase('done')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 평가 중 오류가 발생했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  // Onboarding phase
  if (phase === 'onboarding') {
    return (
      <div>
        <div style={{ fontSize: 10, color: '#A78BFA', letterSpacing: 3, marginBottom: 16 }}>
          면접 설정
        </div>

        <div style={{ marginBottom: 18 }}>
          <label style={{ fontSize: 12, color: '#94A3B8', display: 'block', marginBottom: 6 }}>
            기술 스택 (쉼표로 구분)
          </label>
          <textarea
            value={techStack}
            onChange={e => setTechStack(e.target.value)}
            rows={2}
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
              fontFamily: "'Courier New', monospace",
            }}
          />
          <div style={{ fontSize: 11, color: '#475569', marginTop: 4 }}>
            {character.role} 기본값 자동 적용됨
          </div>
        </div>

        <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
          <div style={{ flex: 1 }}>
            <label style={{ fontSize: 12, color: '#94A3B8', display: 'block', marginBottom: 6 }}>
              기술 질문 수 (5~12)
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <input
                type="range"
                min={5}
                max={12}
                value={techCount}
                onChange={e => setTechCount(Number(e.target.value))}
                style={{ flex: 1, accentColor: '#4ECDC4' }}
              />
              <span style={{ fontSize: 16, color: '#4ECDC4', fontWeight: 'bold', minWidth: 24, textAlign: 'right' }}>
                {techCount}
              </span>
            </div>
          </div>
          <div style={{ flex: 1 }}>
            <label style={{ fontSize: 12, color: '#94A3B8', display: 'block', marginBottom: 6 }}>
              인성 질문 수 (0~5)
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <input
                type="range"
                min={0}
                max={5}
                value={personalityCount}
                onChange={e => setPersonalityCount(Number(e.target.value))}
                style={{ flex: 1, accentColor: '#A78BFA' }}
              />
              <span style={{ fontSize: 16, color: '#A78BFA', fontWeight: 'bold', minWidth: 24, textAlign: 'right' }}>
                {personalityCount}
              </span>
            </div>
          </div>
        </div>

        <button
          onClick={handleStartInterview}
          disabled={!techStack.trim()}
          style={{
            width: '100%',
            padding: '13px',
            background: techStack.trim()
              ? 'linear-gradient(135deg, #EF4444, #DC2626)'
              : 'rgba(100,116,139,0.3)',
            border: 'none',
            borderRadius: 10,
            color: '#fff',
            fontSize: 14,
            fontWeight: 'bold',
            cursor: techStack.trim() ? 'pointer' : 'not-allowed',
            fontFamily: "'Courier New', monospace",
          }}
        >
          ⚔️ 면접 시작
        </button>
      </div>
    )
  }

  // Loading phase
  if (phase === 'loading') {
    return (
      <div>
        <OracleLoadingModal isOpen={true} />
        <div style={{ textAlign: 'center', padding: '40px 0', color: '#475569', fontSize: 13 }}>
          AI가 맞춤 질문을 생성하고 있습니다...
        </div>
      </div>
    )
  }

  // Done phase
  if (done) {
    const passed = totalScore >= PASS_THRESHOLD
    return (
      <div>
        <div style={{ textAlign: 'center', padding: '20px 0 16px' }}>
          <div style={{ fontSize: 44, marginBottom: 10 }}>{passed ? '🏆' : '📚'}</div>
          <div style={{ fontSize: 24, fontWeight: 'bold', color: passed ? '#10B981' : '#EF4444', marginBottom: 6 }}>
            최종 점수: {totalScore}/100
          </div>
          <div style={{ fontSize: 13, color: '#64748B', marginBottom: 20 }}>
            {passed ? '+800 XP 획득! 다음 Act 해금' : '70점 이상 필요. 재도전하세요'}
          </div>
        </div>

        <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 12 }}>
          📋 질문별 결과
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {results.map((r, i) => {
            const qItem = questions[i]
            const isP = qItem?.category === '인성'
            return (
              <div key={i}>
                <div style={{ fontSize: 12, marginBottom: 6 }}>
                  <span style={{ color: isP ? '#A78BFA' : '#4ECDC4' }}>[{qItem?.category}]</span>
                  <span style={{ color: '#475569', marginLeft: 6 }}>{qItem?.question}</span>
                </div>
                <InterviewResultCard result={r} />
              </div>
            )
          })}
        </div>
        {!completionReported && (
          <button
            type="button"
            onClick={() => {
              setCompletionReported(true)
              onComplete(totalScore)
            }}
            style={{
              width: '100%',
              padding: '12px',
              background: totalScore >= PASS_THRESHOLD
                ? 'linear-gradient(135deg, #10B981, #059669)'
                : 'rgba(78,205,196,0.1)',
              border: totalScore >= PASS_THRESHOLD ? 'none' : '1px solid rgba(78,205,196,0.3)',
              borderRadius: 10,
              color: totalScore >= PASS_THRESHOLD ? '#060610' : '#4ECDC4',
              fontSize: 14,
              fontWeight: 'bold',
              cursor: 'pointer',
              fontFamily: "'Courier New', monospace",
              marginTop: 20,
            }}
          >
            {totalScore >= PASS_THRESHOLD ? '🏆 완료 저장 & Act III 해금' : '📚 결과 확인 완료'}
          </button>
        )}
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
        <span
          style={{
            fontSize: 12,
            color: questionAccent,
            background: isPersonality ? 'rgba(167,139,250,0.1)' : 'rgba(78,205,196,0.1)',
            padding: '2px 8px',
            borderRadius: 4,
          }}
        >
          {q.category}
        </span>
      </div>

      <div style={{ marginBottom: 18 }}>
        <ProgressBar value={((idx + 1) / questions.length) * 100} color={questionAccent} height={4} />
      </div>

      <div
        style={{
          background: isPersonality ? 'rgba(167,139,250,0.04)' : 'rgba(78,205,196,0.04)',
          border: `1px solid ${isPersonality ? 'rgba(167,139,250,0.15)' : 'rgba(78,205,196,0.15)'}`,
          borderRadius: 12,
          padding: 18,
          marginBottom: 14,
        }}
      >
        <div style={{ fontSize: 10, color: questionAccent, letterSpacing: 3, marginBottom: 8 }}>
          QUESTION
        </div>
        <p style={{ fontSize: 14, color: '#F1F5F9', margin: 0, lineHeight: 1.7 }}>{q.question}</p>
      </div>

      <OracleLoadingModal isOpen={loading && isLastQuestion} />
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

      <button
        type="button"
        onClick={() => setAnswer(MOCK_INTERVIEW_SAMPLE_ANSWERS[q.id] ?? '')}
        style={{
          background: 'none',
          border: `1px solid ${isPersonality ? 'rgba(167,139,250,0.3)' : 'rgba(78,205,196,0.3)'}`,
          borderRadius: 6,
          color: questionAccent,
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
        onClick={isLastQuestion ? handleSubmitAll : handleNext}
        disabled={loading || !answer.trim()}
        style={{
          width: '100%',
          padding: '12px',
          background: loading
            ? 'rgba(239,68,68,0.2)'
            : isLastQuestion
              ? 'linear-gradient(135deg, #A78BFA, #7C3AED)'
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
        {loading ? '⟳ 채점 중...' : isLastQuestion ? '🤖 채점하기' : '다음 질문 →'}
      </button>
    </div>
  )
}
