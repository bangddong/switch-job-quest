interface ScoreRingProps {
  score: number
  size?: number
}

const SCORE_COLORS = [
  { threshold: 90, color: '#F59E0B' },
  { threshold: 80, color: '#10B981' },
  { threshold: 70, color: '#4ECDC4' },
  { threshold: 60, color: '#A78BFA' },
  { threshold: 0, color: '#EF4444' },
] as const

function getScoreColor(score: number): string {
  const entry = SCORE_COLORS.find((e) => score >= e.threshold)
  return entry?.color ?? '#EF4444'
}

export function ScoreRing({ score, size = 80 }: ScoreRingProps) {
  const strokeWidth = 8
  const r = size / 2 - strokeWidth - 1
  const circumference = 2 * Math.PI * r
  const filled = (score / 100) * circumference
  const color = getScoreColor(score)

  return (
    <svg width={size} height={size} style={{ flexShrink: 0 }}>
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke="rgba(255,255,255,0.06)"
        strokeWidth={strokeWidth}
      />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeDasharray={`${filled} ${circumference}`}
        strokeLinecap="round"
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
        style={{ transition: 'stroke-dasharray 1.2s ease' }}
      />
      <text
        x={size / 2}
        y={size / 2 + 1}
        textAnchor="middle"
        dominantBaseline="middle"
        fill="#F1F5F9"
        fontSize={size * 0.22}
        fontWeight="bold"
        fontFamily="'Courier New', monospace"
      >
        {score}
      </text>
      <text
        x={size / 2}
        y={size / 2 + size * 0.2}
        textAnchor="middle"
        dominantBaseline="middle"
        fill="#475569"
        fontSize={size * 0.13}
        fontFamily="'Courier New', monospace"
      >
        /100
      </text>
    </svg>
  )
}
