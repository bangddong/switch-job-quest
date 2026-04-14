import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'

interface QuestAttemptCountProps {
  history: QuestHistoryItem[]
}

export function QuestAttemptCount({ history }: QuestAttemptCountProps) {
  const countMap = new Map<string, number>()
  for (const item of history) {
    countMap.set(item.questId, (countMap.get(item.questId) ?? 0) + 1)
  }

  const data = Array.from(countMap.entries())
    .map(([questId, count]) => ({ questId, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 10)

  if (data.length === 0) {
    return (
      <div
        style={{
          padding: '32px 0',
          textAlign: 'center',
          color: '#475569',
          fontSize: 13,
          fontFamily: "'Courier New', monospace",
        }}
      >
        아직 기록이 없습니다
      </div>
    )
  }

  return (
    <div style={{ width: '100%', height: 200 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
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
            allowDecimals={false}
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
            formatter={(value) => [`${value}회`, '시도 횟수']}
            labelStyle={{ color: '#475569' }}
          />
          <Bar dataKey="count" fill="#F59E0B" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
