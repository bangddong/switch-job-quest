interface RetryCoachCardProps {
  improvements: string[]
  onRetry: () => void
}

export function RetryCoachCard({ improvements, onRetry }: RetryCoachCardProps) {
  return (
    <div
      style={{
        marginTop: 16,
        padding: 20,
        background: 'rgba(245,158,11,0.05)',
        border: '1px solid rgba(245,158,11,0.35)',
        borderRadius: 12,
        animation: 'slideIn 0.4s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
        <span style={{ fontSize: 18 }}>💪</span>
        <span
          style={{
            fontSize: 14,
            color: '#F59E0B',
            fontWeight: 'bold',
          }}
        >
          조금만 더 다듬으면 돼요
        </span>
      </div>

      {improvements.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div
            style={{
              fontSize: 11,
              color: '#64748B',
              letterSpacing: 2,
              marginBottom: 10,
            }}
          >
            이 부분을 보완해봐요:
          </div>
          {improvements.map((tip, i) => (
            <div
              key={i}
              style={{
                display: 'flex',
                gap: 8,
                marginBottom: i < improvements.length - 1 ? 8 : 0,
                alignItems: 'flex-start',
              }}
            >
              <span style={{ color: '#F59E0B', fontSize: 12, marginTop: 2, flexShrink: 0 }}>▸</span>
              <span style={{ fontSize: 13, color: '#F59E0B', lineHeight: 1.6 }}>{tip}</span>
            </div>
          ))}
        </div>
      )}

      <button
        className="hov-btn"
        onClick={onRetry}
        style={{
          width: '100%',
          padding: '11px',
          background: 'rgba(245,158,11,0.12)',
          border: '1px solid rgba(245,158,11,0.4)',
          borderRadius: 10,
          color: '#F59E0B',
          fontSize: 13,
          fontWeight: 'bold',
          cursor: 'pointer',
          fontFamily: "'Courier New', monospace",
          letterSpacing: 0.5,
        }}
      >
        이전 답변 불러와서 수정하기
      </button>
    </div>
  )
}
