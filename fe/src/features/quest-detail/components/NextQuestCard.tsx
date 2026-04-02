interface NextQuestCardProps {
  message: string
  onStart: () => void
}

export function NextQuestCard({ message, onStart }: NextQuestCardProps) {
  return (
    <div
      style={{
        marginTop: 16,
        padding: 20,
        background: 'rgba(78,205,196,0.05)',
        border: '1px solid rgba(78,205,196,0.35)',
        borderRadius: 12,
        animation: 'slideIn 0.5s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <span style={{ fontSize: 16 }}>➡️</span>
        <span
          style={{
            fontSize: 11,
            color: '#4ECDC4',
            letterSpacing: 3,
            fontWeight: 'bold',
          }}
        >
          다음 단계
        </span>
      </div>
      <p
        style={{
          margin: '0 0 16px',
          fontSize: 13,
          color: '#94A3B8',
          lineHeight: 1.6,
        }}
      >
        {message}
      </p>
      <button
        className="hov-btn"
        onClick={onStart}
        style={{
          width: '100%',
          padding: '11px',
          background: 'linear-gradient(135deg, #4ECDC4, #2DD4BF)',
          border: 'none',
          borderRadius: 10,
          color: '#060610',
          fontSize: 13,
          fontWeight: 'bold',
          cursor: 'pointer',
          fontFamily: "'Courier New', monospace",
          letterSpacing: 0.5,
        }}
      >
        다음 퀘스트 바로 시작 →
      </button>
    </div>
  )
}
