import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import type { QuestHistoryItem } from '@/types/api.types'
import { GRADE_COLORS } from '@/utils/gradeUtils'

interface GradeDistributionProps {
  history: QuestHistoryItem[]
}

const GRADES = ['S', 'A', 'B', 'C', 'D']

export function GradeDistribution({ history }: GradeDistributionProps) {
  const data = GRADES.map((grade) => ({
    grade,
    count: history.filter((h) => h.grade === grade).length,
  }))

  return (
    <div style={{ width: '100%', height: 180 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
          <XAxis
            dataKey="grade"
            tick={{ fill: '#475569', fontSize: 13, fontFamily: "'Courier New', monospace", fontWeight: 'bold' }}
            axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
            tickLine={false}
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
            formatter={(value) => [`${value}회`, '횟수']}
            labelStyle={{ color: '#475569' }}
          />
          <Bar dataKey="count" radius={[4, 4, 0, 0]}>
            {data.map((entry) => (
              <Cell key={entry.grade} fill={GRADE_COLORS[entry.grade] ?? '#475569'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
