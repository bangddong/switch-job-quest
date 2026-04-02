import { useState } from 'react'

interface OnboardingIntroProps {
  onComplete: () => void
}

const SLIDES = [
  {
    icon: '💻',
    tag: '2026년 봄',
    title: '매일 같은 코드',
    body: '한 개발자가 퇴근 후 모니터를 멍하니 바라보고 있었다.\n3년째 같은 레거시를 고치며, 성장이 멈춘 느낌이 들었다.',
  },
  {
    icon: '😶',
    tag: '어느 날',
    title: '이게 맞는 건가...',
    body: '좋은 팀, 괜찮은 연봉. 그런데도 어딘가 허전했다.\n더 어려운 문제를 풀고 싶었다. 더 성장하고 싶었다.',
  },
  {
    icon: '☕',
    tag: '결심',
    title: '이직을 해보자',
    body: '커피 한 잔을 마시며 마음을 굳혔다.\n"지금이 아니면 언제?"',
  },
  {
    icon: '🤯',
    tag: '현실',
    title: '그런데... 어디서부터?',
    body: '기술 스택 정리, 이력서 작성, GitHub 정비, 모의 면접...\n뭐부터 해야 할지조차 모르겠다.',
  },
  {
    icon: '⚔️',
    tag: 'DEVQUEST',
    title: '당신의 이직 퀘스트',
    body: '단계별 퀘스트로 이직 준비의 모든 것을 완성하라.\nAI가 함께 검사하고, 피드백하고, 성장시킨다.',
    isCta: true,
  },
]

export function OnboardingIntro({ onComplete }: OnboardingIntroProps) {
  const [step, setStep] = useState(0)
  const slide = SLIDES[step]!
  const isLast = step === SLIDES.length - 1

  const handleNext = () => {
    if (isLast) {
      onComplete()
    } else {
      setStep(step + 1)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '40px 0' }}>
      {/* Progress dots */}
      <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginBottom: 48 }}>
        {SLIDES.map((_, i) => (
          <div key={i} style={{
            width: i === step ? 20 : 6,
            height: 6,
            borderRadius: 3,
            background: i === step ? '#4ECDC4' : i < step ? 'rgba(78,205,196,0.4)' : 'rgba(255,255,255,0.08)',
            transition: 'all 0.3s ease',
          }} />
        ))}
      </div>

      {/* Slide content — key triggers re-animation */}
      <div key={step} style={{ textAlign: 'center', animation: 'slideIn 0.3s ease', flex: 1 }}>
        <div style={{ fontSize: 52, marginBottom: 16 }}>{slide.icon}</div>
        <div style={{ fontSize: 10, letterSpacing: 5, color: '#334155', marginBottom: 8 }}>{slide.tag}</div>
        <h2 style={{ fontSize: 24, fontWeight: 'bold', color: '#F8FAFC', margin: '0 0 16px' }}>{slide.title}</h2>
        <p style={{ fontSize: 14, color: '#475569', lineHeight: 1.9, margin: 0, whiteSpace: 'pre-line' }}>{slide.body}</p>
      </div>

      {/* Buttons */}
      <div style={{ marginTop: 48 }}>
        {'isCta' in slide && slide.isCta ? (
          <button
            onClick={handleNext}
            className="hov-btn"
            style={{
              width: '100%',
              padding: '16px',
              background: 'linear-gradient(135deg, #4ECDC4, #4ECDC480)',
              border: 'none',
              borderRadius: 10,
              color: '#060610',
              fontSize: 16,
              fontWeight: 'bold',
              cursor: 'pointer',
              fontFamily: "'Courier New', monospace",
              letterSpacing: 1,
            }}
          >
            ⚔️ 여정 시작하기
          </button>
        ) : (
          <>
            <button
              onClick={handleNext}
              className="hov-btn"
              style={{
                width: '100%',
                padding: '14px',
                background: 'transparent',
                border: '1px solid rgba(78,205,196,0.3)',
                borderRadius: 10,
                color: '#4ECDC4',
                fontSize: 14,
                fontWeight: 'bold',
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
                marginBottom: 12,
              }}
            >
              다음 →
            </button>
            <button
              onClick={onComplete}
              style={{
                width: '100%',
                padding: '8px',
                background: 'transparent',
                border: 'none',
                color: '#1E293B',
                fontSize: 12,
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
              }}
            >
              건너뛰기
            </button>
          </>
        )}
      </div>
    </div>
  )
}
