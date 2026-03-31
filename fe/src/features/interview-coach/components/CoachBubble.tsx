interface CoachBubbleProps {
  message: string
  type?: 'coach' | 'system'
}

export function CoachBubble({ message, type = 'coach' }: CoachBubbleProps) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 16 }}>
      {type === 'coach' && (
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #4ECDC4, #A78BFA)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 18,
            flexShrink: 0,
          }}
        >
          🎯
        </div>
      )}
      <div
        style={{
          background: type === 'coach' ? '#0F172A' : 'rgba(78,205,196,0.08)',
          border: `1px solid ${type === 'coach' ? 'rgba(255,255,255,0.08)' : 'rgba(78,205,196,0.2)'}`,
          borderRadius: type === 'coach' ? '4px 12px 12px 12px' : 8,
          padding: '12px 16px',
          color: '#F1F5F9',
          fontSize: 14,
          lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
        }}
      >
        {message}
      </div>
    </div>
  )
}
