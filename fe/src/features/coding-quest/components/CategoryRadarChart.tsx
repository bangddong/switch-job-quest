interface CategoryRadarChartProps {
  categoryStats: Record<string, number>
  tierColor: string
}

const AXES = [
  { key: 'ARRAY',         label: '배열' },
  { key: 'HASH_MAP',      label: '해시맵' },
  { key: 'STACK_QUEUE',   label: '스택/큐' },
  { key: 'BINARY_SEARCH', label: '이진탐색' },
  { key: 'RECURSION',     label: '재귀' },
  { key: 'DP',            label: 'DP' },
  { key: 'GREEDY',        label: '그리디' },
  { key: 'GRAPH',         label: '그래프' },
  { key: 'TREE',          label: '트리' },
]

const CX = 140
const CY = 120
const R = 90
const N = AXES.length

function getPoint(index: number, radius: number): [number, number] {
  const angle = (index / N) * 2 * Math.PI - Math.PI / 2
  return [CX + radius * Math.cos(angle), CY + radius * Math.sin(angle)]
}

function getTextAnchor(x: number): 'end' | 'start' | 'middle' {
  if (x < CX - 5) return 'end'
  if (x > CX + 5) return 'start'
  return 'middle'
}

function getDominantBaseline(y: number): 'auto' | 'hanging' | 'middle' {
  if (y < CY - 5) return 'auto'
  if (y > CY + 5) return 'hanging'
  return 'middle'
}

export function CategoryRadarChart({ categoryStats, tierColor }: CategoryRadarChartProps) {
  const maxVal = Math.max(5, ...Object.values(categoryStats))

  const gridLevels = [0.25, 0.5, 0.75, 1.0]

  function polygonPoints(ratio: number): string {
    return AXES.map((_, i) => {
      const [x, y] = getPoint(i, R * ratio)
      return `${x},${y}`
    }).join(' ')
  }

  const dataPoints = AXES.map((axis, i) => {
    const val = categoryStats[axis.key] ?? 0
    const ratio = val / maxVal
    return getPoint(i, R * ratio)
  })

  const dataPolygon = dataPoints.map(([x, y]) => `${x},${y}`).join(' ')

  return (
    <svg
      width="100%"
      viewBox="0 0 280 240"
      style={{ display: 'block' }}
      role="img"
      aria-label={`카테고리별 풀이 수: ${AXES.map(({ key, label }) => `${label} ${categoryStats[key] ?? 0}문제`).join(', ')}`}
    >
      {/* 그리드 레벨 */}
      {gridLevels.map((ratio) => (
        <polygon
          key={ratio}
          points={polygonPoints(ratio)}
          fill="rgba(255,255,255,0.02)"
          stroke="rgba(255,255,255,0.06)"
          strokeWidth={1}
        />
      ))}

      {/* 축 선 */}
      {AXES.map((_, i) => {
        const [ex, ey] = getPoint(i, R)
        return (
          <line
            key={i}
            x1={CX}
            y1={CY}
            x2={ex}
            y2={ey}
            stroke="rgba(255,255,255,0.10)"
            strokeWidth={1}
          />
        )
      })}

      {/* 데이터 polygon */}
      <polygon
        points={dataPolygon}
        fill={`${tierColor}26`}
        stroke={tierColor}
        strokeWidth={1.5}
        opacity={0.9}
      />

      {/* 꼭짓점 원 */}
      {dataPoints.map(([x, y], i) => {
        const val = categoryStats[AXES[i]?.key ?? ''] ?? 0
        return (
          <circle
            key={i}
            cx={x}
            cy={y}
            r={val === 0 ? 2 : 3.5}
            fill={tierColor}
            stroke="#0F172A"
            strokeWidth={1.5}
            opacity={val === 0 ? 0.4 : 1}
          />
        )
      })}

      {/* 레이블 */}
      {AXES.map((axis, i) => {
        const [ex, ey] = getPoint(i, R + 14)
        return (
          <text
            key={i}
            x={ex}
            y={ey}
            textAnchor={getTextAnchor(ex)}
            dominantBaseline={getDominantBaseline(ey)}
            style={{
              fontSize: 10,
              fill: '#475569',
              fontFamily: "'Courier New', monospace",
            }}
          >
            {axis.label}
          </text>
        )
      })}
    </svg>
  )
}
