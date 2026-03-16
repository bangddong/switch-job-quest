interface ProgressBarProps {
  value: number
  color: string
  height?: number
}

export function ProgressBar({ value, color, height = 3 }: ProgressBarProps) {
  return (
    <div
      style={{
        background: '#0A0E1A',
        borderRadius: height,
        height,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          background: color,
          height: '100%',
          width: `${Math.min(Math.max(value, 0), 100)}%`,
          transition: 'width 0.5s ease',
          borderRadius: height,
        }}
      />
    </div>
  )
}
