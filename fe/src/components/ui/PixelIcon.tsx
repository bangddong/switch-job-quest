import type { CSSProperties } from 'react'

/**
 * DevQuest pixel icon set.
 * Every icon is a grid of [x, y, w, h, alpha?] rects rendered as crisp SVG.
 * Colored via the `color` prop (defaults to currentColor) so icons inherit
 * from text color when embedded inline.
 */

type Rect = [number, number, number, number] | [number, number, number, number, number]
type IconDef = { grid: number; rects: Rect[] }
// PixelIconName is exported from @/types/icon.types — defined here only for internal use
type PixelIconName = keyof typeof PIXEL_ICONS

export const PIXEL_ICONS = {
  // ═══ ACT symbols ═══
  flask: {
    grid: 12,
    rects: [
      [4, 1, 4, 1], [4, 3, 1, 2], [7, 3, 1, 2],
      [3, 5, 1, 2], [8, 5, 1, 2], [2, 7, 1, 3], [9, 7, 1, 3],
      [2, 10, 8, 1],
      [4, 7, 4, 3, 0.35],
      [5, 8, 1, 1, 0.85], [7, 9, 1, 1, 0.85],
    ],
  },
  swords: {
    grid: 12,
    rects: [
      [2, 2, 1, 1], [3, 3, 1, 1], [4, 4, 1, 1], [5, 5, 1, 1], [6, 6, 1, 1], [7, 7, 1, 1], [8, 8, 1, 1],
      [9, 2, 1, 1], [8, 3, 1, 1], [7, 4, 1, 1], [6, 5, 1, 1], [5, 6, 1, 1], [4, 7, 1, 1], [3, 8, 1, 1],
      [1, 1, 2, 1], [1, 2, 1, 1],
      [9, 1, 2, 1], [10, 2, 1, 1],
      [2, 9, 2, 1], [1, 10, 1, 1],
      [8, 9, 2, 1], [10, 10, 1, 1],
    ],
  },
  map: {
    grid: 12,
    rects: [
      [1, 2, 10, 8],
      [1, 2, 10, 1, 0.6], [1, 9, 10, 1, 0.6],
      [4, 2, 1, 8, 0.3], [8, 2, 1, 8, 0.3],
      [5, 5, 1, 1], [6, 4, 1, 1], [7, 5, 1, 1], [6, 6, 1, 1],
    ],
  },
  shield: {
    grid: 12,
    rects: [
      [4, 1, 4, 1], [3, 2, 6, 1], [2, 3, 8, 3], [3, 6, 6, 2], [4, 8, 4, 1], [5, 9, 2, 1],
    ],
  },
  shieldCheck: {
    grid: 12,
    rects: [
      [4, 1, 4, 1], [3, 2, 6, 1], [2, 3, 8, 3], [3, 6, 6, 2], [4, 8, 4, 1], [5, 9, 2, 1],
      [4, 5, 1, 1], [5, 6, 1, 1], [7, 4, 1, 1], [6, 5, 1, 1],
    ],
  },
  crown: {
    grid: 12,
    rects: [
      [1, 5, 1, 4], [10, 5, 1, 4],
      [3, 3, 1, 4], [8, 3, 1, 4],
      [5, 4, 2, 3],
      [1, 8, 10, 2],
      [1, 4, 1, 1], [10, 4, 1, 1], [5, 3, 2, 1],
    ],
  },

  // ═══ Quest types ═══
  book: {
    grid: 8,
    rects: [
      [0, 1, 3, 6], [5, 1, 3, 6],
      [3, 2, 2, 5, 0.4],
      [0, 1, 3, 1, 0.7], [5, 1, 3, 1, 0.7],
      [1, 3, 1, 1], [1, 5, 1, 1],
      [6, 3, 1, 1], [6, 5, 1, 1],
    ],
  },
  pen: {
    grid: 8,
    rects: [
      [5, 0, 2, 1], [4, 1, 2, 1], [3, 2, 2, 1], [2, 3, 2, 1],
      [1, 4, 2, 1], [1, 5, 1, 1], [0, 6, 1, 1],
      [6, 1, 1, 1],
    ],
  },
  magnifier: {
    grid: 8,
    rects: [
      [1, 1, 4, 1], [0, 2, 1, 2], [5, 2, 1, 2], [1, 4, 4, 1],
      [2, 2, 2, 2, 0.35],
      [5, 5, 1, 1], [6, 6, 1, 1], [7, 7, 1, 1],
    ],
  },
  hammer: {
    grid: 8,
    rects: [
      [3, 0, 4, 2], [2, 1, 1, 2], [3, 2, 1, 1], [2, 3, 1, 1],
      [1, 4, 1, 1], [0, 5, 1, 2], [1, 6, 1, 1],
    ],
  },
  skull: {
    grid: 8,
    rects: [
      [2, 0, 4, 1],
      [1, 1, 6, 5],
      [0, 2, 1, 2], [7, 2, 1, 2],
      [2, 6, 1, 1], [4, 6, 1, 1], [6, 6, 1, 1],
      [3, 7, 1, 1], [5, 7, 1, 1],
    ],
  },

  // ═══ Utility / UI ═══
  lock: {
    grid: 8,
    rects: [
      [2, 3, 1, 1], [5, 3, 1, 1], [2, 2, 4, 1],
      [3, 1, 2, 1],
      [1, 4, 6, 4],
      [3, 5, 2, 2, 0.5],
    ],
  },
  check: {
    grid: 8,
    rects: [
      [1, 4, 1, 1], [2, 5, 1, 1], [3, 6, 1, 1], [4, 5, 1, 1], [5, 4, 1, 1], [6, 3, 1, 1], [7, 2, 1, 1],
    ],
  },
  xp: {
    grid: 8,
    rects: [
      [3, 0, 2, 1], [2, 1, 4, 1], [1, 2, 6, 1],
      [2, 3, 4, 2],
      [3, 5, 2, 1], [3, 6, 2, 1],
      [2, 2, 1, 1, 0.5], [5, 2, 1, 1, 0.5],
    ],
  },
  trophy: {
    grid: 8,
    rects: [
      [1, 1, 6, 1], [1, 1, 1, 3], [6, 1, 1, 3],
      [0, 2, 1, 1], [7, 2, 1, 1],
      [2, 2, 4, 3], [3, 5, 2, 1],
      [2, 6, 4, 1], [1, 7, 6, 1],
    ],
  },
  ai: {
    grid: 12,
    rects: [
      [5, 0, 2, 2], [5, 10, 2, 2], [0, 5, 2, 2], [10, 5, 2, 2],
      [1, 1, 2, 2], [9, 1, 2, 2], [1, 9, 2, 2], [9, 9, 2, 2],
      [4, 4, 4, 4],
      [5, 5, 2, 2, 0.5],
    ],
  },
  target: {
    grid: 8,
    rects: [
      [2, 0, 4, 1], [1, 1, 1, 1], [6, 1, 1, 1],
      [0, 2, 1, 4], [7, 2, 1, 4],
      [1, 6, 1, 1], [6, 6, 1, 1], [2, 7, 4, 1],
      [2, 2, 4, 1], [2, 5, 4, 1], [2, 2, 1, 4], [5, 2, 1, 4],
      [3, 3, 2, 2],
    ],
  },
  bars: {
    grid: 8,
    rects: [
      [1, 5, 1, 3], [3, 3, 1, 5], [5, 1, 1, 7],
      [0, 7, 8, 1, 0.5],
    ],
  },
  braces: {
    grid: 8,
    rects: [
      [2, 1, 1, 1], [1, 2, 1, 4], [2, 6, 1, 1],
      [0, 3, 1, 2, 0.5],
      [5, 1, 1, 1], [6, 2, 1, 4], [5, 6, 1, 1],
      [7, 3, 1, 2, 0.5],
      [3, 3, 1, 1], [4, 3, 1, 1],
    ],
  },
} as const satisfies Record<string, IconDef>

interface PixelIconProps {
  name: PixelIconName
  size?: number
  color?: string
  style?: CSSProperties
  'aria-label'?: string
}

/**
 * Render a pixel icon as crisp SVG. Color defaults to `currentColor` so the
 * icon inherits from surrounding text; pass `color` to override.
 */
export function PixelIcon({
  name,
  size = 16,
  color = 'currentColor',
  style,
  'aria-label': ariaLabel,
}: PixelIconProps) {
  const icon = PIXEL_ICONS[name]
  if (!icon) return null
  return (
    <svg
      viewBox={`0 0 ${icon.grid} ${icon.grid}`}
      width={size}
      height={size}
      fill={color}
      shapeRendering="crispEdges"
      role={ariaLabel ? 'img' : 'presentation'}
      aria-label={ariaLabel}
      aria-hidden={ariaLabel ? undefined : true}
      focusable={ariaLabel ? undefined : 'false'}
      style={{ imageRendering: 'pixelated', flexShrink: 0, display: 'inline-block', ...style }}
    >
      {icon.rects
        .filter((r) => r[4] === undefined || r[4] > 0)
        .map((rect, i) => {
          const [x, y, w, h, a] = rect
          return (
            <rect
              key={i}
              x={x}
              y={y}
              width={w}
              height={h}
              opacity={a != null ? a : 1}
            />
          )
        })}
    </svg>
  )
}

