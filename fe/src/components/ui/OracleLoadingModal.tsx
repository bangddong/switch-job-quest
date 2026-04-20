import { useState, useEffect } from 'react'

const ORACLE_MESSAGES = [
  '신탁이 운명의 서를 펼치는 중...',
  '예언자가 커피를 보충하는 중...',
  '현자가 Git 커밋 기록을 훔쳐보는 중...',
  '길드 마스터가 이력서를 정독하는 중...',
  '운명의 룬이 기술 스택을 정렬하는 중...',
  '드래곤이 코드 냄새를 맡는 중...',
  '신탁의 마법진이 과부하 직전인 중...',
  '예언자가 5년 후 비전을 스캔하는 중...',
  '고대의 알고리즘이 잠에서 깨어나는 중...',
  '마법사가 Spring Boot를 구동하는 중...',
  '현자가 기술 부채 총량을 계산하는 중...',
  '신탁이 LinkedIn을 몰래 열어보는 중...',
  '운명의 주사위가 굴러가는 중...',
  '길드 서버가 열심히 일하는 척하는 중...',
  '예언자가 스택오버플로우를 순례하는 중...',
  '마법진이 Docker 컨테이너를 소환하는 중...',
  '현자가 README를 몰래 수정하는 중...',
  '신탁의 GPU가 열을 식히는 중...',
  '길드 현인이 연봉 협상 시나리오를 짜는 중...',
  '운명의 저울이 기술 역량을 측정하는 중...',
  '예언자가 JD를 열두 번째 정독하는 중...',
  '마법사가 캐시를 비우고 다시 시도하는 중...',
  '현자가 커밋 메시지를 퇴고하는 중...',
  '신탁이 N+1 쿼리를 잡아내는 중...',
  '드래곤이 레거시 코드를 소각하는 중...',
  '길드 마법진이 PR 리뷰를 기다리는 중...',
  '예언자가 인프라 비용을 계산하는 중...',
  '운명의 서버가 재시작하는 중...',
  '마법사가 의존성 버전을 맞추는 중...',
  '현자가 면접 예상 질문을 뽑는 중...',
  '신탁이 당신의 포텐셜을 측정하는 중...',
  '길드 원로들이 회의실에서 논쟁하는 중...',
  '예언자가 기술 면접 채점표를 작성하는 중...',
  '마법진이 CI 파이프라인을 통과하는 중...',
  '현자가 5년치 성장 곡선을 그리는 중...',
  '드래곤이 이력서 클리셰를 불태우는 중...',
  '신탁이 당신의 운명을 컴파일하는 중...',
  '길드 서기가 판정문을 작성하는 중...',
  '예언자가 업계 연봉 데이터를 수집하는 중...',
  '마법사가 배포 직전 긴장하는 중...',
  '현자가 기술 부채를 묵묵히 감내하는 중...',
  '신탁의 신성한 LLM이 기도를 드리는 중...',
  '운명의 룬이 당신의 커리어를 각인하는 중...',
  '길드 마스터가 최종 인장을 찍으려는 중...',
  '예언자가 오라클 DB와 혼동되는 중...',
]

interface OracleLoadingModalProps {
  isOpen: boolean
}

export function OracleLoadingModal({ isOpen }: OracleLoadingModalProps) {
  const [msgIdx, setMsgIdx] = useState(() => Math.floor(Math.random() * ORACLE_MESSAGES.length))
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    if (!isOpen) return

    let cycleTimeout: ReturnType<typeof setTimeout> | undefined
    let transitionTimeout: ReturnType<typeof setTimeout> | undefined

    const scheduleNext = () => {
      cycleTimeout = setTimeout(() => {
        setVisible(false)
        transitionTimeout = setTimeout(() => {
          setMsgIdx((i) => (i + 1) % ORACLE_MESSAGES.length)
          setVisible(true)
          scheduleNext()
        }, 300)
      }, 3000)
    }

    scheduleNext()

    return () => {
      if (cycleTimeout) clearTimeout(cycleTimeout)
      if (transitionTimeout) clearTimeout(transitionTimeout)
    }
  }, [isOpen])

  if (!isOpen) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="AI 분석 중"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(6, 6, 16, 0.85)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
    >
      <div
        aria-busy="true"
        style={{
          background: '#0F172A',
          border: '1px solid rgba(78, 205, 196, 0.3)',
          borderRadius: 20,
          padding: '40px 48px',
          textAlign: 'center',
          width: 320,
          animation: 'oracleGlow 3s ease-in-out infinite',
        }}
      >
        {/* 파티클 레이어 */}
        <div style={{ position: 'relative', width: 80, height: 80, margin: '0 auto 24px' }}>
          {/* 파티클 1 */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            marginTop: -4,
            marginLeft: -4,
            animation: 'oracleOrbit1 4s linear infinite',
            fontSize: 8,
            color: '#4ECDC4',
            opacity: 0.7,
          }}>✦</div>
          {/* 파티클 2 */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            marginTop: -3,
            marginLeft: -3,
            animation: 'oracleOrbit2 6s linear infinite',
            fontSize: 6,
            color: '#A78BFA',
            opacity: 0.6,
          }}>✦</div>
          {/* 파티클 3 */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            marginTop: -5,
            marginLeft: -5,
            animation: 'oracleOrbit3 8s linear infinite',
            fontSize: 10,
            color: '#4ECDC4',
            opacity: 0.4,
          }}>✦</div>
          {/* 메인 아이콘 */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            fontSize: 40,
            color: '#4ECDC4',
            animation: 'oracleBreathe 2s ease-in-out infinite',
            lineHeight: 1,
          }}>✦</div>
        </div>

        {/* 멘트 */}
        <div aria-live="polite" style={{
          fontSize: 14,
          color: '#F1F5F9',
          marginBottom: 20,
          minHeight: 22,
          letterSpacing: 0.3,
          animation: visible ? 'oracleSlideDown 0.4s ease' : 'none',
          opacity: visible ? 1 : 0,
          transition: 'opacity 0.3s ease',
        }}>
          {ORACLE_MESSAGES[msgIdx]}
        </div>

        {/* Magic Energy Bar */}
        <div style={{
          position: 'relative',
          height: 3,
          background: 'rgba(78, 205, 196, 0.1)',
          borderRadius: 2,
          overflow: 'hidden',
          marginBottom: 16,
        }}>
          <div style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '50%',
            height: '100%',
            background: 'linear-gradient(90deg, transparent, #4ECDC4, #A78BFA, transparent)',
            animation: 'oracleShimmer 1.8s linear infinite',
          }} />
        </div>

        {/* 보조 문구 */}
        <div style={{ fontSize: 11, color: '#475569' }}>
          약 30초 소요됩니다
        </div>
      </div>
    </div>
  )
}
