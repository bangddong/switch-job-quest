import { useState, useEffect } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'
import { fetchHistory } from '@/lib/apiClient'
import { ScoreTimeline } from './ScoreTimeline'
import { QuestHistoryList } from './QuestHistoryList'
import { ActPassRate } from './ActPassRate'
import { GradeDistribution } from './GradeDistribution'
import { StreakBadge } from './StreakBadge'
import { HourlyActivity } from './HourlyActivity'
import { QuestAttemptCount } from './QuestAttemptCount'

type ChartTab = 'score' | 'act' | 'grade' | 'hourly' | 'quest'

const CHART_TABS: { key: ChartTab; label: string }[] = [
  { key: 'score', label: '점수 추이' },
  { key: 'act', label: 'ACT 합격률' },
  { key: 'grade', label: '등급 분포' },
  { key: 'hourly', label: '시간대' },
  { key: 'quest', label: '퀘스트' },
]

interface BestScoreEntry {
  questId: string
  score: number
}

function buildBestScores(history: QuestHistoryItem[]): BestScoreEntry[] {
  const map = new Map<string, number>()
  for (const item of history) {
    const prev = map.get(item.questId) ?? 0
    if (item.score > prev) map.set(item.questId, item.score)
  }
  return Array.from(map.entries())
    .map(([questId, score]) => ({ questId, score }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 10)
}

function totalEarnedXp(history: QuestHistoryItem[]): number {
  return history.reduce((sum, item) => sum + item.earnedXp, 0)
}

export function GrowthDashboard() {
  const [history, setHistory] = useState<QuestHistoryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedChart, setSelectedChart] = useState<ChartTab>('score')

  useEffect(() => {
    setLoading(true)
    fetchHistory()
      .then((data) => {
        setHistory(data)
        setError(null)
      })
      .catch(() => {
        setHistory([])
        setError(null) // silent fallback per spec
      })
      .finally(() => setLoading(false))
  }, [])

  const bestScores = buildBestScores(history)
  const xpTotal = totalEarnedXp(history)

  return (
    <div style={{ fontFamily: "'Courier New', monospace", color: '#F8FAFC' }}>
      {/* 헤더 */}
      <div style={{ paddingTop: 16, marginBottom: 24 }}>
        <h2
          style={{
            fontSize: 20,
            fontWeight: 'bold',
            color: '#4ECDC4',
            margin: 0,
            marginBottom: 4,
          }}
        >
          성장 기록
        </h2>
        {!loading && (
          <div style={{ fontSize: 12, color: '#94A3B8' }}>
            총 {history.length}회 시도 · 획득 XP {xpTotal.toLocaleString()}
          </div>
        )}
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', color: '#94A3B8', fontSize: 13, padding: '40px 0' }}>
          로딩 중...
        </div>
      ) : (
        <>
          {/* 퀘스트별 최고점 바 차트 */}
          <section style={{ marginBottom: 28 }}>
            <div
              style={{
                fontSize: 12,
                color: '#94A3B8',
                marginBottom: 10,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              퀘스트별 최고점
            </div>
            <div
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 10,
                padding: '16px 8px 8px',
              }}
            >
              {bestScores.length === 0 ? (
                <div
                  style={{
                    padding: '32px 0',
                    textAlign: 'center',
                    color: '#64748B',
                    fontSize: 13,
                  }}
                >
                  아직 기록이 없습니다
                </div>
              ) : (
                <div style={{ width: '100%', height: 200 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={bestScores} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                      <XAxis
                        dataKey="questId"
                        tick={{ fill: '#64748B', fontSize: 9, fontFamily: "'Courier New', monospace" }}
                        axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
                        tickLine={false}
                        interval={0}
                        angle={-30}
                        textAnchor="end"
                        height={40}
                      />
                      <YAxis
                        domain={[0, 100]}
                        tick={{ fill: '#64748B', fontSize: 11, fontFamily: "'Courier New', monospace" }}
                        axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
                        tickLine={false}
                      />
                      <Tooltip
                        contentStyle={{
                          background: '#0F172A',
                          border: '1px solid rgba(255,255,255,0.08)',
                          borderRadius: 8,
                          fontFamily: "'Courier New', monospace",
                          fontSize: 12,
                          color: '#F8FAFC',
                        }}
                        formatter={(value) => [`${value ?? ''}점`, '최고점']}
                        labelStyle={{ color: '#94A3B8' }}
                      />
                      <Bar dataKey="score" fill="#A78BFA" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          </section>

          {/* 차트 탭 섹션 */}
          <section style={{ marginBottom: 28 }}>
            {/* 탭 바 */}
            <div
              style={{
                overflowX: 'auto',
                whiteSpace: 'nowrap',
                borderBottom: '1px solid rgba(255,255,255,0.08)',
                marginBottom: 12,
              }}
            >
              {CHART_TABS.map((tab) => {
                const isActive = selectedChart === tab.key
                return (
                  <button
                    key={tab.key}
                    onClick={() => setSelectedChart(tab.key)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      padding: '8px 12px',
                      background: 'none',
                      border: 'none',
                      borderBottom: isActive ? '2px solid #4ECDC4' : '2px solid transparent',
                      color: isActive ? '#4ECDC4' : '#64748B',
                      fontSize: 12,
                      fontFamily: "'Courier New', monospace",
                      cursor: 'pointer',
                      whiteSpace: 'nowrap',
                      marginBottom: -1,
                      transition: 'color 0.15s ease',
                    }}
                  >
                    {tab.label}
                  </button>
                )
              })}
            </div>

            {/* 선택된 차트 */}
            <div
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 10,
                padding: '16px 8px 8px',
              }}
            >
              {selectedChart === 'score' && <ScoreTimeline history={history} />}
              {selectedChart === 'act' && <ActPassRate history={history} />}
              {selectedChart === 'grade' && <GradeDistribution history={history} />}
              {selectedChart === 'hourly' && <HourlyActivity history={history} />}
              {selectedChart === 'quest' && <QuestAttemptCount history={history} />}
            </div>
          </section>

          {/* 연속 스트릭 (항상 표시) */}
          <section style={{ marginBottom: 28 }}>
            <div
              style={{
                fontSize: 12,
                color: '#94A3B8',
                marginBottom: 10,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              연속 학습 스트릭
            </div>
            <div
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 10,
                padding: '8px',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <StreakBadge history={history} />
            </div>
          </section>

          {/* 최근 시도 목록 (항상 표시) */}
          <section>
            <div
              style={{
                fontSize: 12,
                color: '#94A3B8',
                marginBottom: 10,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              최근 시도
            </div>
            <QuestHistoryList history={history} />
          </section>

          {error && (
            <div style={{ marginTop: 16, color: '#EF4444', fontSize: 12, textAlign: 'center' }}>
              {error}
            </div>
          )}
        </>
      )}
    </div>
  )
}
