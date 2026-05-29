import { useState, useEffect } from 'react'
import type { CategoryProgress } from '@/types/api.types'
import { fetchCodingRoadmap } from '@/lib/apiClient'
import { ProgressBar } from '@/components/ui/ProgressBar'

export interface CodingRoadmapPageProps {
  onBack: () => void
  onSelectCategory: (category: string) => void
}

interface CategoryCardProps {
  item: CategoryProgress
  onSelect: (category: string) => void
}

function CategoryCard({ item, onSelect }: CategoryCardProps) {
  const [hovered, setHovered] = useState(false)
  const progressValue = Math.min(item.solvedCount, 3) / 3 * 100
  const isComplete = item.solvedCount >= 3

  return (
    <div
      onClick={() => !item.locked && onSelect(item.category)}
      onMouseEnter={() => !item.locked && setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        position: 'relative',
        background: hovered && !item.locked
          ? 'rgba(10,14,26,1)'
          : 'rgba(10,14,26,0.9)',
        border: `1px solid ${item.locked ? 'rgba(255,255,255,0.03)' : 'rgba(78,205,196,0.25)'}`,
        borderRadius: 12,
        padding: '16px 20px',
        cursor: item.locked ? 'default' : 'pointer',
        opacity: item.locked ? 0.4 : 1,
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        transition: 'background 0.15s',
        overflow: 'hidden',
      }}
    >
      {/* 좌측 액센트 바 */}
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 3,
          background: item.locked ? '#0F172A' : '#4ECDC4',
          borderRadius: '12px 0 0 12px',
        }}
      />

      {/* 아이콘 영역 */}
      <div
        style={{
          width: 36,
          height: 36,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: item.locked ? 'rgba(255,255,255,0.03)' : 'rgba(78,205,196,0.1)',
          color: item.locked ? '#334155' : '#4ECDC4',
          borderRadius: 8,
          fontSize: item.locked ? 16 : 15,
          fontWeight: 700,
          fontFamily: "'Courier New', monospace",
        }}
      >
        {item.locked ? '🔒' : item.order}
      </div>

      {/* 텍스트 영역 */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: '#F8FAFC',
            fontFamily: "'Courier New', monospace",
            marginBottom: item.locked ? 4 : 6,
          }}
        >
          {item.displayName}
        </div>
        {item.locked ? (
          <div
            style={{
              fontSize: 11,
              color: '#334155',
              fontFamily: "'Courier New', monospace",
            }}
          >
            앞 카테고리 3문제 완료 후 해금
          </div>
        ) : (
          <>
            <div
              style={{
                fontSize: 12,
                color: isComplete ? '#4ECDC4' : '#475569',
                fontFamily: "'Courier New', monospace",
                marginBottom: 6,
              }}
            >
              {isComplete ? '✓ 3문제 완료' : `${item.solvedCount} / 3 문제 해결`}
            </div>
            <ProgressBar value={progressValue} color='#4ECDC4' height={3} />
          </>
        )}
      </div>
    </div>
  )
}

export function CodingRoadmapPage({ onBack, onSelectCategory }: CodingRoadmapPageProps) {
  const [roadmap, setRoadmap] = useState<CategoryProgress[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  useEffect(() => {
    fetchCodingRoadmap()
      .then((data) => {
        setRoadmap(data)
        setLoading(false)
      })
      .catch(() => {
        setError('로드맵을 불러오는 데 실패했습니다.')
        setLoading(false)
      })
  }, [])

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: '#060610',
        fontFamily: "'Courier New', monospace",
        color: '#F8FAFC',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* TopBar */}
      <div
        style={{
          height: 48,
          flexShrink: 0,
          background: '#0A0E1A',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
        }}
      >
        {/* 좌: 뒤로가기 */}
        <button
          onClick={onBack}
          style={{
            background: 'none',
            border: 'none',
            color: '#475569',
            cursor: 'pointer',
            fontSize: 13,
            fontFamily: "'Courier New', monospace",
            padding: '4px 8px',
          }}
          onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.color = '#F8FAFC' }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.color = '#475569' }}
        >
          ← 뒤로
        </button>

        {/* 중앙: 제목 */}
        <span
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: '#F8FAFC',
            fontFamily: "'Courier New', monospace",
            position: 'absolute',
            left: '50%',
            transform: 'translateX(-50%)',
          }}
        >
          코딩 로드맵
        </span>

        {/* 우: 균형 dummy */}
        <div style={{ width: 60 }} />
      </div>

      {/* 본문 */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: isMobile ? 16 : 24,
        }}
      >
        <div
          style={{
            maxWidth: 640,
            margin: '0 auto',
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          {loading ? (
            <p
              style={{
                color: '#475569',
                textAlign: 'center',
                marginTop: 40,
                fontSize: 14,
                fontFamily: "'Courier New', monospace",
              }}
            >
              로드맵 불러오는 중...
            </p>
          ) : error ? (
            <div
              style={{
                background: 'rgba(239,68,68,0.04)',
                border: '1px solid rgba(239,68,68,0.25)',
                borderRadius: 8,
                padding: '14px 16px',
                fontSize: 13,
                color: '#EF4444',
                fontFamily: "'Courier New', monospace",
              }}
            >
              {error}
            </div>
          ) : (
            roadmap.map((item) => (
              <CategoryCard
                key={item.category}
                item={item}
                onSelect={onSelectCategory}
              />
            ))
          )}
        </div>
      </div>
    </div>
  )
}
