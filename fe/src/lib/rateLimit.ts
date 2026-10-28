export const RATE_LIMIT_DEFAULT_MESSAGE = '오늘 사용 가능한 횟수를 모두 사용했습니다. 내일 다시 시도해주세요.'

/** 한도 초과로 거절된 요청. 메시지는 사용자에게 그대로 보여 줘도 된다. */
export class RateLimitError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'RateLimitError'
  }
}

export function isRateLimited(status: number): boolean {
  return status === 429
}

/** 429 응답 본문(`{ error: { message } }`)에서 사용자용 문구를 꺼낸다. 형태가 다르면 기본 문구. */
export function extractRateLimitMessage(body: unknown): string {
  if (typeof body === 'object' && body !== null && 'error' in body) {
    const error = body.error
    if (typeof error === 'object' && error !== null && 'message' in error) {
      const message = error.message
      if (typeof message === 'string' && message.length > 0) return message
    }
  }
  return RATE_LIMIT_DEFAULT_MESSAGE
}

/** 429 응답을 RateLimitError 로 바꾼다. 본문 파싱 실패 시 기본 문구. */
export async function toRateLimitError(res: Response): Promise<RateLimitError> {
  const body: unknown = await res.json().catch(() => null)
  return new RateLimitError(extractRateLimitMessage(body))
}
