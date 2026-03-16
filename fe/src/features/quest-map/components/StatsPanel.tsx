import { ACTS } from '../constants/questData'

interface StatsPanelProps {
  completedCount: number
}

export function StatsPanel({ completedCount }: StatsPanelProps) {
  const totalQuests = ACTS.reduce((a, x) => a + x.quests.length, 0)
  const aiCheckCount = ACTS.reduce((a, x) => a + x.quests.filter((q) => q.aiCheck).length, 0)
  const totalXp = ACTS.reduce((a, x) => a + x.quests.reduce((b, q) => b + q.xp, 0), 0)

  const stats = [
    { label: '총 퀘스트', value: String(totalQuests), color: '#4ECDC4' },
    { label: '완료', value: String(completedCount), color: '#10B981' },
    { label: 'AI 검사', value: String(aiCheckCount), color: '#A78BFA' },
    { label: '총 XP', value: totalXp.toLocaleString(), color: '#F59E0B' },
  ]

  return (
    <div
      style={{
        marginTop: 20,
        padding: '14px 20px',
        background: 'rgba(10,14,26,0.6)',
        border: '1px solid rgba(255,255,255,0.04)',
        borderRadius: 10,
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr 1fr',
        gap: 16,
        textAlign: 'center',
      }}
    >
      {stats.map((s) => (
        <div key={s.label}>
          <div style={{ fontSize: 20, fontWeight: 'bold', color: s.color }}>{s.value}</div>
          <div style={{ fontSize: 10, color: '#334155', marginTop: 2 }}>{s.label}</div>
        </div>
      ))}
    </div>
  )
}
