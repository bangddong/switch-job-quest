import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'

interface HourlyActivityProps {
  history: QuestHistoryItem[]
}

function getCreatedAtHour(createdAt: string): number | null {
  const match = createdAt.match(/^\d{4}-\d{2}-\d{2}T(\d{2}):\d{2}(?::\d{2}(?:\.\d+)?)?$/)
  if (!match) {
    return null
  }
  const hour = Number(match[1])
  return Number.isInteger(hour) && hour >= 0 && hour <= 23 ? hour : null
}

export function HourlyActivity({ history }: HourlyActivityProps) {
  const counts = Array.from({ length: 24 }, (_, hour) => ({
    hour: `${String(hour).padStart(2, '0')}시`,
    count: history.filter((h) => getCreatedAtHour(h.createdAt) === hour).length,
  }))

  const maxCount = Math.max(...counts.map((c) => c.count), 1)

  return (
    <div style={{ width: '100%', height: 180 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={counts} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
          <XAxis
            dataKey="hour"
            tick={{ fill: '#475569', fontSize: 9, fontFamily: "'Courier New', monospace" }}
            axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
            tickLine={false}
            interval={2}
          />
          <YAxis
            allowDecimals={false}
            domain={[0, maxCount]}
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
            formatter={(value) => [`${value}회`, '시도']}
            labelStyle={{ color: '#475569' }}
          />
          <Bar dataKey="count" fill="#60A5FA" radius={[2, 2, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
