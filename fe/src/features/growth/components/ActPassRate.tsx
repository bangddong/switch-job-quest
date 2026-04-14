import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'

interface ActPassRateProps {
  history: QuestHistoryItem[]
}

const ACT_COLORS = ['#4ECDC4', '#A78BFA', '#F59E0B', '#10B981', '#EF4444']

export function ActPassRate({ history }: ActPassRateProps) {
  const data = [1, 2, 3, 4, 5].map((actId) => {
    const actHistory = history.filter((h) => h.actId === actId)
    const total = actHistory.length
    const passed = actHistory.filter((h) => h.passed).length
    return {
      name: `ACT ${actId}`,
      rate: total === 0 ? 0 : Math.round((passed / total) * 100),
      total,
    }
  })

  return (
    <div style={{ width: '100%', height: 180 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
          <XAxis
            dataKey="name"
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
              `${value}% (${(props.payload as { total: number } | undefined)?.total ?? 0}회 시도)`,
              '합격률',
            ]}
            labelStyle={{ color: '#475569' }}
          />
          <Bar dataKey="rate" radius={[4, 4, 0, 0]}>
            {data.map((_, i) => (
              <Cell key={i} fill={ACT_COLORS[i]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
