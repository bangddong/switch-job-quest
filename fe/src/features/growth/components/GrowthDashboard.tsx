import { useState, useEffect } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'
import { fetchHistory } from '@/lib/apiClient'
import { ScoreTimeline } from './ScoreTimeline'
import { QuestHistoryList } from './QuestHistoryList'

interface GrowthDashboardProps {
  userId: string
}

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

export function GrowthDashboard({ userId }: GrowthDashboardProps) {
  const [history, setHistory] = useState<QuestHistoryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    fetchHistory(userId)
      .then((data) => {
        setHistory(data)
        setError(null)
      })
      .catch(() => {
        setHistory([])
        setError(null) // silent fallback per spec
      })
      .finally(() => setLoading(false))
  }, [userId])

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
          ⚔️ 성장 기록
        </h2>
        {!loading && (
          <div style={{ fontSize: 12, color: '#475569' }}>
            총 {history.length}회 시도 · 획득 XP {xpTotal.toLocaleString()}
          </div>
        )}
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', color: '#475569', fontSize: 13, padding: '40px 0' }}>
          로딩 중...
        </div>
      ) : (
        <>
          {/* 전체 XP 성장 그래프 */}
          <section style={{ marginBottom: 28 }}>
            <div
              style={{
                fontSize: 12,
                color: '#475569',
                marginBottom: 10,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              점수 변화 추이
            </div>
            <div
              style={{
                background: '#0F172A',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 10,
                padding: '16px 8px 8px',
              }}
            >
              <ScoreTimeline history={history} />
            </div>
          </section>

          {/* 퀘스트별 최고점 바 차트 */}
          <section style={{ marginBottom: 28 }}>
            <div
              style={{
                fontSize: 12,
                color: '#475569',
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
                    color: '#475569',
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
                        tick={{ fill: '#475569', fontSize: 9, fontFamily: "'Courier New', monospace" }}
                        axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
                        tickLine={false}
                        interval={0}
                        angle={-30}
                        textAnchor="end"
                        height={40}
                      />
                      <YAxis
                        domain={[0, 100]}
                        tick={{ fill: '#475569', fontSize: 11, fontFamily: "'Courier New', monospace" }}
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
                        labelStyle={{ color: '#475569' }}
                      />
                      <Bar dataKey="score" fill="#A78BFA" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          </section>

          {/* 최근 시도 목록 */}
          <section>
            <div
              style={{
                fontSize: 12,
                color: '#475569',
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
