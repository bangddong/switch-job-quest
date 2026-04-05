import { useState } from 'react'
import type { CoachStep, CoachSessionResult, CoachAnswerResult, CoachAnswerHistory, CoachReportResult } from '../types/coach.types'
import { startCoachSession, submitCoachAnswer, generateCoachReport } from '../api/interviewCoachApi'
import { CoachOnboarding } from './CoachOnboarding'
import { CoachAnalysis } from './CoachAnalysis'
import { CoachQASession } from './CoachQASession'
import { CoachReport } from './CoachReport'

export function InterviewCoach() {
  const [step, setStep] = useState<CoachStep>('onboarding')
  const [loading, setLoading] = useState(false)
  const [targetRole, setTargetRole] = useState('')
  const [session, setSession] = useState<CoachSessionResult | null>(null)
  const [report, setReport] = useState<CoachReportResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleStart = async (role: string, jdText: string) => {
    setError(null)
    setLoading(true)
    setStep('analyzing')
    setTargetRole(role)
    try {
      const result = await startCoachSession(jdText, role)
      setSession(result)
      setStep('analysis')
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류가 발생했습니다')
      setStep('onboarding')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmitAnswer = async (
    question: string,
    answer: string,
    index: number,
    total: number,
  ): Promise<CoachAnswerResult> => {
    return submitCoachAnswer(question, answer, index, total)
  }

  const handleQAComplete = async (history: CoachAnswerHistory[]) => {
    if (!session) return
    setStep('reporting')
    try {
      const result = await generateCoachReport(targetRole, session.jdSummary, history)
      setReport(result)
      setStep('report')
    } catch (e) {
      setError(e instanceof Error ? e.message : '리포트 생성 오류')
      setStep('qa')
    }
  }

  const handleRestart = () => {
    setStep('onboarding')
    setSession(null)
    setReport(null)
    setTargetRole('')
    setError(null)
  }

  return (
    <div style={{ paddingTop: 8 }}>
      <div style={{ marginBottom: 24 }}>
        <p style={{ fontSize: 11, color: '#4ECDC4', margin: '0 0 4px', letterSpacing: 2 }}>
          AI INTERVIEW COACH
        </p>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: '#F8FAFC', margin: 0 }}>
          전담 면접 코치
        </h2>
      </div>

      {error && (
        <div
          style={{
            background: 'rgba(239,68,68,0.1)',
            border: '1px solid rgba(239,68,68,0.3)',
            borderRadius: 8,
            padding: '10px 14px',
            marginBottom: 16,
            fontSize: 13,
            color: '#EF4444',
          }}
        >
          {error}
        </div>
      )}

      {(step === 'onboarding' || step === 'analyzing') && (
        <CoachOnboarding onStart={handleStart} loading={loading} />
      )}

      {step === 'analysis' && session && (
        <CoachAnalysis
          session={session}
          targetRole={targetRole}
          onBegin={() => setStep('qa')}
        />
      )}

      {step === 'qa' && session && (
        <CoachQASession
          questions={session.questions}
          onSubmitAnswer={handleSubmitAnswer}
          onComplete={handleQAComplete}
        />
      )}

      {step === 'reporting' && (
        <div style={{ textAlign: 'center', padding: '40px 0', color: '#475569', fontSize: 14 }}>
          코치가 종합 평가를 작성 중입니다...
        </div>
      )}

      {step === 'report' && report && (
        <CoachReport report={report} targetRole={targetRole} onRestart={handleRestart} />
      )}
    </div>
  )
}
