import { useState } from 'react'
import type { JourneyReportResult } from '@/types/api.types'
import { fetchJourneyReport } from '@/lib/apiClient'

const QUEST_LABEL: Record<string, string> = {
  '1-1': '기술 스택 진단',
  '1-2': '이직 동기 에세이',
  '1-BOSS': '개발자 클래스 판별',
  '2-2': '기술 블로그',
  '2-3': '시스템 설계',
  '2-BOSS': '모의 기술 면접',
  '3-2': 'JD 역분석',
  '4-1': '이력서 STAR 검토',
  '4-BOSS': '지원 패키지 점검',
  '5-1': '인성 면접 연습',
}

interface JourneyTimelineProps {
  lowestQuestId: string
  highestQuestId: string
  totalXp: number
  completedQuestCount: number
}

function JourneyTimeline({ lowestQuestId, highestQuestId, totalXp, completedQuestCount }: JourneyTimelineProps) {
  return (
    <div
      style={{
        background: 'rgba(10,14,26,0.8)',
        border: '1px solid rgba(255,255,255,0.06)',
        borderRadius: 12,
        padding: '18px 20px',
        marginBottom: 18,
      }}
    >
      <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 14 }}>
        📊 여정 통계
      </div>
      <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <div style={statCardStyle('#F59E0B')}>
          <div style={{ fontSize: 22, fontWeight: 'bold', color: '#F59E0B' }}>{totalXp.toLocaleString()}</div>
          <div style={{ fontSize: 10, color: '#64748B', marginTop: 2 }}>총 획득 XP</div>
        </div>
        <div style={statCardStyle('#4ECDC4')}>
          <div style={{ fontSize: 22, fontWeight: 'bold', color: '#4ECDC4' }}>{completedQuestCount}</div>
          <div style={{ fontSize: 10, color: '#64748B', marginTop: 2 }}>완료 퀘스트</div>
        </div>
      </div>

      {lowestQuestId && QUEST_LABEL[lowestQuestId] && (
        <div style={{ marginBottom: 8 }}>
          <span style={{ fontSize: 11, color: '#475569' }}>가장 힘들었던 퀘스트 </span>
          <span style={{ fontSize: 11, color: '#EF4444', fontWeight: 'bold' }}>
            ↓ {QUEST_LABEL[lowestQuestId] ?? lowestQuestId}
          </span>
        </div>
      )}
      {highestQuestId && QUEST_LABEL[highestQuestId] && (
        <div>
          <span style={{ fontSize: 11, color: '#475569' }}>가장 빛났던 퀘스트 </span>
          <span style={{ fontSize: 11, color: '#10B981', fontWeight: 'bold' }}>
            ↑ {QUEST_LABEL[highestQuestId] ?? highestQuestId}
          </span>
        </div>
      )}
    </div>
  )
}

function statCardStyle(color: string): React.CSSProperties {
  return {
    flex: 1,
    minWidth: 100,
    background: `${color}08`,
    border: `1px solid ${color}25`,
    borderRadius: 10,
    padding: '12px 16px',
    textAlign: 'center',
  }
}

interface NarrativeCardProps {
  report: JourneyReportResult
}

function NarrativeCard({ report }: NarrativeCardProps) {
  const paragraphs = report.narrative.split(/\n\n+/).filter(Boolean)

  return (
    <div
      style={{
        background: 'rgba(78,205,196,0.03)',
        border: '1px solid rgba(78,205,196,0.18)',
        borderRadius: 14,
        padding: '22px 20px',
        marginBottom: 18,
      }}
    >
      <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 3, marginBottom: 16 }}>
        📖 당신의 이야기
      </div>
      {paragraphs.map((para, i) => (
        <p
          key={i}
          style={{
            color: '#94A3B8',
            fontSize: 14,
            lineHeight: 1.85,
            margin: 0,
            marginBottom: i < paragraphs.length - 1 ? 16 : 0,
          }}
        >
          {para}
        </p>
      ))}
    </div>
  )
}

interface FinalBossViewProps {
  userId: string
  onComplete: (xp: number) => void
}

export function FinalBossView({ userId, onComplete }: FinalBossViewProps) {
  const [companyName, setCompanyName] = useState('')
  const [targetPosition, setTargetPosition] = useState('')
  const [loading, setLoading] = useState(false)
  const [report, setReport] = useState<JourneyReportResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async () => {
    if (!companyName.trim() || !targetPosition.trim()) return
    setLoading(true)
    setError(null)
    try {
      const result = await fetchJourneyReport(userId, companyName.trim(), targetPosition.trim())
      setReport(result)
      onComplete(2000)
    } catch {
      setError('리포트 생성에 실패했어요. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  if (report) {
    return (
      <div style={{ animation: 'slideIn 0.6s ease' }}>
        {/* 취뽀 타이틀 */}
        <div
          style={{
            textAlign: 'center',
            padding: '32px 0 24px',
          }}
        >
          <div style={{ fontSize: 52, marginBottom: 12 }}>🏆</div>
          <div
            style={{
              fontSize: 11,
              color: '#F59E0B',
              letterSpacing: 4,
              marginBottom: 8,
            }}
          >
            QUEST CLEAR — 취뽀 달성
          </div>
          <div style={{ fontSize: 22, fontWeight: 'bold', color: '#F8FAFC', marginBottom: 6 }}>
            {report.companyName}
          </div>
          <div style={{ fontSize: 14, color: '#64748B' }}>{report.targetPosition}</div>
        </div>

        {/* 여정 통계 */}
        <JourneyTimeline
          lowestQuestId={report.lowestQuestId}
          highestQuestId={report.highestQuestId}
          totalXp={report.totalXp}
          completedQuestCount={report.completedQuestCount}
        />

        {/* AI 회고 내러티브 */}
        <NarrativeCard report={report} />

        {/* 마지막 한 마디 */}
        <div
          style={{
            textAlign: 'center',
            padding: '20px 0',
            borderTop: '1px solid rgba(255,255,255,0.05)',
          }}
        >
          <div style={{ fontSize: 10, color: '#475569', letterSpacing: 3, marginBottom: 10 }}>
            FINAL MESSAGE
          </div>
          <div
            style={{
              fontSize: 16,
              color: '#4ECDC4',
              fontWeight: 'bold',
              fontStyle: 'italic',
            }}
          >
            "{report.finalMessage}"
          </div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ animation: 'slideIn 0.4s ease' }}>
      <div
        style={{
          textAlign: 'center',
          padding: '28px 0 20px',
        }}
      >
        <div style={{ fontSize: 44, marginBottom: 10 }}>🎯</div>
        <div style={{ fontSize: 11, color: '#F59E0B', letterSpacing: 3, marginBottom: 6 }}>
          FINAL BOSS
        </div>
        <div style={{ fontSize: 16, color: '#F8FAFC', fontWeight: 'bold', marginBottom: 6 }}>
          합격 신고하기
        </div>
        <div style={{ fontSize: 13, color: '#475569', lineHeight: 1.6 }}>
          어느 회사에 합격했나요?<br />
          당신의 여정을 AI가 회고해드릴게요.
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 18 }}>
        <div>
          <label style={{ fontSize: 11, color: '#64748B', letterSpacing: 2, display: 'block', marginBottom: 6 }}>
            합격 회사
          </label>
          <input
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            placeholder="예: 카카오"
            style={inputStyle}
          />
        </div>
        <div>
          <label style={{ fontSize: 11, color: '#64748B', letterSpacing: 2, display: 'block', marginBottom: 6 }}>
            합격 포지션
          </label>
          <input
            value={targetPosition}
            onChange={(e) => setTargetPosition(e.target.value)}
            placeholder="예: 시니어 백엔드 개발자"
            style={inputStyle}
          />
        </div>
      </div>

      {error && (
        <div style={{ fontSize: 12, color: '#EF4444', marginBottom: 12, textAlign: 'center' }}>
          {error}
        </div>
      )}

      <button
        className="hov-btn"
        onClick={handleSubmit}
        disabled={loading || !companyName.trim() || !targetPosition.trim()}
        style={{
          width: '100%',
          padding: '14px',
          background: loading
            ? 'rgba(245,158,11,0.05)'
            : 'linear-gradient(135deg, #F59E0B, #D97706)',
          border: loading ? '1px solid rgba(245,158,11,0.3)' : 'none',
          borderRadius: 10,
          color: loading ? '#F59E0B' : '#060610',
          fontSize: 14,
          fontWeight: 'bold',
          cursor: loading || !companyName.trim() || !targetPosition.trim() ? 'not-allowed' : 'pointer',
          fontFamily: "'Courier New', monospace",
          opacity: !companyName.trim() || !targetPosition.trim() ? 0.5 : 1,
          transition: 'all 0.2s ease',
        }}
      >
        {loading ? '🔮 여정을 돌아보는 중...' : '🏆 취뽀 달성 신고하기'}
      </button>
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '11px 14px',
  background: '#0A0E1A',
  border: '1px solid rgba(255,255,255,0.08)',
  borderRadius: 8,
  color: '#F1F5F9',
  fontSize: 14,
  fontFamily: "'Courier New', monospace",
  boxSizing: 'border-box',
  outline: 'none',
}
