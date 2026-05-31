import { useState, useEffect } from 'react'
import type { CodingRankResult } from '@/types/api.types'
import { fetchCodingRank } from '@/lib/apiClient'
import { CategoryRadarChart } from './CategoryRadarChart'

const TIER_COLORS: Record<string, string> = {
  아이언: '#9CA3AF',
  브론즈: '#CD7F32',
  실버: '#94A3B8',
  골드: '#F59E0B',
  플래티넘: '#06B6D4',
  다이아: '#60A5FA',
  마스터: '#A78BFA',
  챌린저: '#4ECDC4',
}

function getTierColor(tier: string): string {
  return TIER_COLORS[tier] ?? '#94A3B8'
}

export function CodingRankCard() {
  const [rank, setRank] = useState<CodingRankResult | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchCodingRank()
      .then((data) => {
        setRank(data)
        setLoading(false)
      })
      .catch(() => {
        setLoading(false)
      })
  }, [])

  if (loading) {
    return (
      <div
        style={{
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 12,
          padding: '16px 20px',
          marginBottom: 16,
          fontFamily: "'Courier New', monospace",
          color: '#475569',
          fontSize: 13,
          textAlign: 'center',
        }}
      >
        랭크 정보 불러오는 중...
      </div>
    )
  }

  if (rank == null) return null

  const tierColor = getTierColor(rank.tier)
  const isChallenger = rank.nextTier == null
  const progressPct = isChallenger
    ? 100
    : rank.nextTierScore != null && rank.nextTierScore > 0
      ? Math.min(Math.round((rank.totalScore / rank.nextTierScore) * 100), 100)
      : 0

  return (
    <div
      style={{
        background: '#0F172A',
        border: `1px solid ${tierColor}40`,
        borderRadius: 12,
        padding: '16px 20px',
        marginBottom: 16,
        fontFamily: "'Courier New', monospace",
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* 좌측 티어 색상 바 */}
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 3,
          background: tierColor,
          borderRadius: '12px 0 0 12px',
        }}
      />

      {/* 상단: 티어 + 점수 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 16 }}>🏆</span>
          <span
            style={{
              fontSize: 16,
              fontWeight: 700,
              color: tierColor,
              fontFamily: "'Courier New', monospace",
            }}
          >
            {rank.tier}
          </span>
        </div>
        <span
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: '#F8FAFC',
            fontFamily: "'Courier New', monospace",
          }}
        >
          {rank.totalScore}점
        </span>
      </div>

      {/* 진행도 바 */}
      <div style={{ marginBottom: 6 }}>
        <div
          style={{
            background: 'rgba(255,255,255,0.08)',
            borderRadius: 4,
            height: 6,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${progressPct}%`,
              height: '100%',
              background: tierColor,
              borderRadius: 4,
              transition: 'width 0.4s ease',
            }}
          />
        </div>
        <div
          style={{
            marginTop: 4,
            fontSize: 11,
            color: '#475569',
            fontFamily: "'Courier New', monospace",
            textAlign: 'right',
          }}
        >
          {isChallenger
            ? '최고 티어 달성!'
            : `${rank.nextTier}까지 ${(rank.nextTierScore ?? 0) - rank.totalScore}점`}
        </div>
      </div>

      {/* 문제 수 행 */}
      <div
        style={{
          display: 'flex',
          gap: 16,
          marginTop: 12,
          paddingTop: 12,
          borderTop: '1px solid rgba(255,255,255,0.06)',
        }}
      >
        <span
          style={{
            fontSize: 12,
            fontFamily: "'Courier New', monospace",
            color: '#10B981',
          }}
        >
          EASY ×{rank.easyCount}
        </span>
        <span
          style={{
            fontSize: 12,
            fontFamily: "'Courier New', monospace",
            color: '#F59E0B',
          }}
        >
          MEDIUM ×{rank.mediumCount}
        </span>
        <span
          style={{
            fontSize: 12,
            fontFamily: "'Courier New', monospace",
            color: '#EF4444',
          }}
        >
          HARD ×{rank.hardCount}
        </span>
      </div>

      {/* 스트릭 행 (0이면 숨김) */}
      {rank.currentStreak > 0 && (
        <div
          style={{
            marginTop: 10,
            fontSize: 12,
            fontFamily: "'Courier New', monospace",
            color: '#F59E0B',
          }}
        >
          🔥 {rank.currentStreak}일 연속 풀이 중
        </div>
      )}

      {/* 카테고리 레이더 차트 */}
      <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
        <div style={{ fontSize: 11, color: '#475569', fontFamily: "'Courier New', monospace", marginBottom: 4, letterSpacing: '0.05em' }}>
          CATEGORY
        </div>
        <CategoryRadarChart categoryStats={rank.categoryStats} tierColor={tierColor} />
      </div>
    </div>
  )
}
