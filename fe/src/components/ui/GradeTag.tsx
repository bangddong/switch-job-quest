const GRADE_COLORS: Record<string, string> = {
  S: '#F59E0B',
  A: '#10B981',
  B: '#4ECDC4',
  C: '#A78BFA',
  D: '#EF4444',
}

interface GradeTagProps {
  grade: string
}

export function GradeTag({ grade }: GradeTagProps) {
  const color = GRADE_COLORS[grade] ?? '#64748B'

  return (
    <span
      style={{
        fontSize: 20,
        fontWeight: 'bold',
        color,
        fontFamily: "'Courier New', monospace",
        background: `${color}15`,
        border: `1px solid ${color}40`,
        padding: '3px 14px',
        borderRadius: 20,
      }}
    >
      {grade}
    </span>
  )
}
