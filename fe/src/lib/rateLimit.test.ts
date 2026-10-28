import { describe, it, expect } from 'vitest'
import { RATE_LIMIT_DEFAULT_MESSAGE, RateLimitError, isRateLimited, extractRateLimitMessage } from './rateLimit'

describe('isRateLimited', () => {
  it('429 만 true', () => {
    expect(isRateLimited(429)).toBe(true)
    expect(isRateLimited(403)).toBe(false)
    expect(isRateLimited(200)).toBe(false)
  })
})

describe('extractRateLimitMessage', () => {
  it('error.message 가 있으면 그대로 쓴다', () => {
    const body = { result: 'ERROR', data: null, error: { code: 'RATE_LIMIT_EXCEEDED', message: '서버 문구' } }
    expect(extractRateLimitMessage(body)).toBe('서버 문구')
  })

  it('본문이 비었거나 형태가 다르면 기본 문구', () => {
    expect(extractRateLimitMessage(null)).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage(undefined)).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage('text')).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage({})).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage({ error: null })).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage({ error: { message: 123 } })).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
    expect(extractRateLimitMessage({ error: { message: '' } })).toBe(RATE_LIMIT_DEFAULT_MESSAGE)
  })

  it('기본 문구는 BE 현행 문구와 같다', () => {
    expect(RATE_LIMIT_DEFAULT_MESSAGE).toBe('오늘 사용 가능한 횟수를 모두 사용했습니다. 내일 다시 시도해주세요.')
  })
})

describe('RateLimitError', () => {
  it('message 를 보존하고 instanceof 로 구분된다', () => {
    const e = new RateLimitError('한도')
    expect(e).toBeInstanceOf(Error)
    expect(e).toBeInstanceOf(RateLimitError)
    expect(e.message).toBe('한도')
  })
})
