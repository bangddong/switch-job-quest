import { describe, expect, it } from 'vitest'
import { decideBackend } from './switch'

const NOW = Date.parse('2026-10-27T10:00:00Z')
const HOUR = 60 * 60 * 1000
const EKS_ORIGIN = 'https://eks.quest.dhbang.co.kr'

function lease(untilMs: number, target = 'eks'): string {
  return JSON.stringify({ target, until: new Date(untilMs).toISOString() })
}

describe('decideBackend — eks 로 가는 유일한 경로', () => {
  it('target=eks 이고 until 이 미래(12시간 이내)이면 eks', () => {
    expect(decideBackend(lease(NOW + 2 * HOUR), NOW, EKS_ORIGIN)).toBe('eks')
  })

  it('until 이 지금 + 12시간 정확히면 eks (경계 포함)', () => {
    expect(decideBackend(lease(NOW + 12 * HOUR), NOW, EKS_ORIGIN)).toBe('eks')
  })
})

describe('decideBackend — 안전 기본값은 모두 fly', () => {
  it('값이 없으면(null) fly', () => {
    expect(decideBackend(null, NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('JSON 이 아니면 fly', () => {
    expect(decideBackend('eks', NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend('{not json', NOW, EKS_ORIGIN)).toBe('fly')
  })

  it.each(['null', '"eks"', '123', 'true', '[]'])('객체가 아닌 JSON(%s)이면 fly', (raw) => {
    expect(decideBackend(raw, NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('target 이 eks 가 아니면 fly', () => {
    expect(decideBackend(lease(NOW + HOUR, 'fly'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(lease(NOW + HOUR, 'EKS'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(JSON.stringify({ until: new Date(NOW + HOUR).toISOString() }), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('until 이 없으면 fly', () => {
    expect(decideBackend(JSON.stringify({ target: 'eks' }), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('until 이 문자열이 아니거나 파싱 불가이면 fly', () => {
    expect(decideBackend(JSON.stringify({ target: 'eks', until: 12345 }), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(JSON.stringify({ target: 'eks', until: 'tomorrow' }), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(JSON.stringify({ target: 'eks', until: '' }), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('until 이 지금과 같거나 과거이면 fly (만료)', () => {
    expect(decideBackend(lease(NOW), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(lease(NOW - 1), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('until 이 지금 + 12시간을 넘으면 fly (비정상 미래값)', () => {
    expect(decideBackend(lease(NOW + 12 * HOUR + 1), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(lease(NOW + 24 * 365 * HOUR), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('EKS_ORIGIN 이 비어 있으면 fly', () => {
    expect(decideBackend(lease(NOW + HOUR), NOW, '')).toBe('fly')
    expect(decideBackend(lease(NOW + HOUR), NOW, '   ')).toBe('fly')
    expect(decideBackend(lease(NOW + HOUR), NOW, undefined)).toBe('fly')
  })

  it.each(['eks.quest.dhbang.co.kr', 'http://eks.quest.dhbang.co.kr', 'ftp://eks.quest.dhbang.co.kr', 'not a url', '://'])(
    'EKS_ORIGIN 이 https URL 이 아니면(%s) fly',
    (origin) => {
      expect(decideBackend(lease(NOW + HOUR), NOW, origin)).toBe('fly')
    },
  )
})

describe('decideBackend — until 은 타임존이 있는 ISO 8601 만 받는다', () => {
  function leaseRaw(until: string): string {
    return JSON.stringify({ target: 'eks', until })
  }

  it('Z 형식이면 eks (소수 초 포함)', () => {
    expect(decideBackend(leaseRaw('2026-10-27T12:00:00Z'), NOW, EKS_ORIGIN)).toBe('eks')
    expect(decideBackend(leaseRaw('2026-10-27T12:00:00.000Z'), NOW, EKS_ORIGIN)).toBe('eks')
  })

  it('±HH:MM 오프셋이면 eks 이고 같은 순간으로 판정된다', () => {
    // 2026-10-27T12:00:00Z == 2026-10-27T21:00:00+09:00 == 2026-10-27T07:00:00-05:00
    const asKst = leaseRaw('2026-10-27T21:00:00+09:00')
    const asUtc = leaseRaw('2026-10-27T12:00:00Z')
    const asUs = leaseRaw('2026-10-27T07:00:00-05:00')
    expect(decideBackend(asKst, NOW, EKS_ORIGIN)).toBe('eks')
    expect(decideBackend(asUs, NOW, EKS_ORIGIN)).toBe('eks')
    // 경계로 같은 순간임을 확인: 12h 창의 끝(NOW+12h = 22:00Z = 07:00+09:00 다음날)
    expect(decideBackend(leaseRaw('2026-10-27T22:00:00Z'), NOW, EKS_ORIGIN)).toBe('eks')
    expect(decideBackend(leaseRaw('2026-10-28T07:00:00+09:00'), NOW, EKS_ORIGIN)).toBe('eks')
    expect(decideBackend(leaseRaw('2026-10-28T07:00:01+09:00'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(asUtc, NOW, EKS_ORIGIN)).toBe('eks')
  })

  it('타임존 없는 ISO 는 로컬 시간으로 해석되므로 fly', () => {
    expect(decideBackend(leaseRaw('2026-10-27T12:00:00'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(leaseRaw('2026-10-27T12:00:00.000'), NOW, EKS_ORIGIN)).toBe('fly')
  })

  it('날짜만 있거나 비-ISO 문자열이면 fly', () => {
    expect(decideBackend(leaseRaw('2026-10-27'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(leaseRaw('Oct 27 2026 12:00:00 GMT'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(leaseRaw('Tue, 27 Oct 2026 12:00:00 GMT'), NOW, EKS_ORIGIN)).toBe('fly')
    expect(decideBackend(leaseRaw('2026/10/27 12:00:00Z'), NOW, EKS_ORIGIN)).toBe('fly')
  })
})
