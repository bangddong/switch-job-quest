import type { Act } from '@/types/quest.types'
import { ProgressBar } from '@/components/ui/ProgressBar'

interface ActCardProps {
  act: Act
  progress: number
  locked: boolean
  onClick: () => void
}

export function ActCard({ act, progress, locked, onClick }: ActCardProps) {
  const completedCount = act.quests.filter((_, i) => i < Math.round((progress / 100) * act.quests.length)).length
  const totalXp = act.quests.reduce((sum, q) => sum + q.xp, 0)

  return (
    <div
      className="hov-act"
      onClick={() => !locked && onClick()}
      style={{
        background: 'rgba(10,14,26,0.9)',
        border: `1px solid ${locked ? 'rgba(255,255,255,0.03)' : act.color + '25'}`,
        borderRadius: 12,
        padding: '16px 20px',
        cursor: locked ? 'not-allowed' : 'pointer',
        opacity: locked ? 0.4 : 1,
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 3,
          background: locked ? '#0F172A' : act.color,
          borderRadius: '12px 0 0 12px',
        }}
      />
      <div
        style={{
          fontSize: 32,
          minWidth: 44,
          textAlign: 'center',
          animation: locked ? 'none' : 'float 3s ease-in-out infinite',
        }}
      >
        {locked ? '🔒' : act.icon}
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3 }}>
          <span style={{ fontSize: 9, letterSpacing: 4, color: act.color }}>{act.title}</span>
          <span style={{ fontSize: 12, color: '#334155' }}>{act.subtitle}</span>
          {locked && (
            <span style={{ fontSize: 10, color: '#EF4444', marginLeft: 'auto' }}>이전 ACT 75% 필요</span>
          )}
        </div>
        <div style={{ fontSize: 14, fontWeight: 'bold', color: '#E2E8F0', marginBottom: 8 }}>
          {act.quests
            .map((q) => q.title.replace('🐉 BOSS: ', '').replace('👑 FINAL BOSS: ', ''))
            .join(' → ')}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ flex: 1 }}>
            <ProgressBar value={progress} color={act.color} />
          </div>
          <span style={{ fontSize: 10, color: '#334155' }}>
            {progress}% · {completedCount}/{act.quests.length}
          </span>
          <span style={{ fontSize: 10, color: '#1E293B' }}>{totalXp.toLocaleString()} XP</span>
        </div>
      </div>
    </div>
  )
}
