interface ResultSectionProps {
  label: string
  color: string
  items: string[]
}

export function ResultSection({ label, color, items }: ResultSectionProps) {
  if (items.length === 0) return null

  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 10, color, letterSpacing: 3, marginBottom: 8 }}>
        {label}
      </div>
      {items.map((item, i) => (
        <div key={i} style={{ fontSize: 13, color: '#64748B', marginBottom: 4 }}>
          <span style={{ color }}>▸ </span>
          {item}
        </div>
      ))}
    </div>
  )
}
