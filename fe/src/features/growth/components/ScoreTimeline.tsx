import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'

interface ScoreTimelineProps {
  history: QuestHistoryItem[]
}

interface ChartDataPoint {
  date: string
  score: number
  questId: string
}

export function ScoreTimeline({ history }: ScoreTimelineProps) {
  const data: ChartDataPoint[] = history
    .slice()
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    .map((item) => {
      const d = new Date(item.createdAt)
      return {
        date: `${d.getMonth() + 1}/${d.getDate()}`,
        score: item.score,
        questId: item.questId,
      }
    })

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
        <LineChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
          <XAxis
            dataKey="date"
            tick={{ fill: '#475569', fontSize: 11, fontFamily: "'Courier New', monospace" }}
            axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
            tickLine={false}
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
            formatter={(value, _name, props) => [
              `${value ?? ''}점`,
              (props.payload as ChartDataPoint | undefined)?.questId ?? '',
            ]}
            labelStyle={{ color: '#475569' }}
          />
          <Line
            type="monotone"
            dataKey="score"
            stroke="#4ECDC4"
            strokeWidth={2}
            dot={{ fill: '#4ECDC4', r: 3, strokeWidth: 0 }}
            activeDot={{ r: 5, fill: '#4ECDC4' }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
