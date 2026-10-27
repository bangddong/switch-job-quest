export type Backend = 'fly' | 'eks'

/** KV 에 스위치를 저장하는 키. eks-window 워크플로가 쓰고 Worker 는 읽기만 한다. */
export const SWITCH_KEY = 'api-target'

/** 창 임대(until)가 이보다 먼 미래이면 비정상 값으로 보고 Fly 로 간다 — 영구 EKS 고착 방지. */
export const MAX_LEASE_MS = 12 * 60 * 60 * 1000

const ISO_WITH_ZONE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$/

function isHttpsUrl(value: string): boolean {
  try {
    return new URL(value).protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * KV 에서 읽은 원문으로 중계할 백엔드를 정한다. EKS 로 가는 길은 단 하나다:
 * `{"target":"eks","until":"<ISO 8601>"}` 이고 now < until <= now + 12h 이며 EKS_ORIGIN 이 https URL 일 때.
 * 값 계약: `until` 은 타임존 지정자(`Z` 또는 `±HH:MM`)가 붙은 ISO 8601 날짜-시각이다. 그 외 형식은 Fly.
 * 그 밖의 모든 경우(값 없음·형식 오류·만료·비정상 미래값 포함)는 Fly 다 — 잘못된 값은 언제나 Fly 쪽으로 넘어진다.
 */
export function decideBackend(raw: string | null, now: number, eksOrigin: string | undefined): Backend {
  if (!eksOrigin || !isHttpsUrl(eksOrigin)) return 'fly'
  if (raw === null) return 'fly'

  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return 'fly'
  }
  if (typeof parsed !== 'object' || parsed === null) return 'fly'

  const { target, until } = parsed as { target?: unknown; until?: unknown }
  if (target !== 'eks') return 'fly'
  if (typeof until !== 'string') return 'fly'
  if (!ISO_WITH_ZONE.test(until)) return 'fly'

  const untilMs = Date.parse(until)
  if (Number.isNaN(untilMs)) return 'fly'
  if (untilMs <= now) return 'fly'
  if (untilMs > now + MAX_LEASE_MS) return 'fly'

  return 'eks'
}
