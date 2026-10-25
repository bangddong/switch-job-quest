import { afterEach, describe, expect, it, vi } from 'vitest'
import { handleRequest, type Env } from './proxy'

const API_ORIGIN = 'https://api.quest.dhbang.co.kr'

function makeEnv(assetsFetch = vi.fn()): Env {
  return {
    API_ORIGIN,
    ASSETS: { fetch: assetsFetch } as unknown as Fetcher,
  }
}

/** fetch 를 stub 한다. 프록시는 완성된 Request 하나만 fetch 에 넘긴다. */
function stubUpstream(response: Response | Error = new Response('ok')) {
  const upstream = vi.fn<(request: Request) => Promise<Response>>(async () => {
    if (response instanceof Error) throw response
    return response
  })
  vi.stubGlobal('fetch', upstream)
  return upstream
}

function sentRequest(upstream: ReturnType<typeof stubUpstream>): Request {
  const request = upstream.mock.calls[0]?.[0]
  if (!request) throw new Error('업스트림 fetch 가 호출되지 않았다')
  return request
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('handleRequest — /api 중계', () => {
  it('경로와 쿼리를 그대로 붙여 업스트림 URL 을 만든다', async () => {
    const upstream = stubUpstream()
    await handleRequest(new Request('https://quest.dhbang.co.kr/api/v1/x?y=1&z=2'), makeEnv())

    const sent = sentRequest(upstream)
    expect(sent.url).toBe('https://api.quest.dhbang.co.kr/api/v1/x?y=1&z=2')
  })

  it('API_ORIGIN 끝의 슬래시는 무시한다', async () => {
    const upstream = stubUpstream()
    const env = { ...makeEnv(), API_ORIGIN: `${API_ORIGIN}/` }
    await handleRequest(new Request('https://quest.dhbang.co.kr/api/v1/x'), env)

    expect(sentRequest(upstream).url).toBe('https://api.quest.dhbang.co.kr/api/v1/x')
  })

  it('method 와 body 를 전달한다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/ai-check/x', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ a: 1 }),
      }),
      makeEnv(),
    )

    const sent = sentRequest(upstream)
    expect(sent.method).toBe('POST')
    expect(sent.headers.get('content-type')).toBe('application/json')
    expect(await sent.text()).toBe('{"a":1}')
  })

  it('Authorization 헤더는 그대로 전달한다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/me', {
        headers: { Authorization: 'Bearer jwt-token' },
      }),
      makeEnv(),
    )

    expect(sentRequest(upstream).headers.get('authorization')).toBe('Bearer jwt-token')
  })

  it('클라이언트가 보낸 X-Forwarded-For 를 CF-Connecting-IP 값으로 덮어쓴다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/x', {
        headers: { 'X-Forwarded-For': '6.6.6.6, 7.7.7.7', 'CF-Connecting-IP': '203.0.113.9' },
      }),
      makeEnv(),
    )

    expect(sentRequest(upstream).headers.get('x-forwarded-for')).toBe('203.0.113.9')
  })

  it('CF-Connecting-IP 가 없으면 위조된 X-Forwarded-For 도 남기지 않는다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/x', {
        headers: { 'X-Forwarded-For': '6.6.6.6' },
      }),
      makeEnv(),
    )

    expect(sentRequest(upstream).headers.get('x-forwarded-for')).toBeNull()
  })

  it('클라이언트가 보낸 Fly-Client-IP 를 제거한다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/x', {
        headers: { 'Fly-Client-IP': '6.6.6.6' },
      }),
      makeEnv(),
    )

    expect(sentRequest(upstream).headers.get('fly-client-ip')).toBeNull()
  })

  it('X-Forwarded-Proto 는 https, X-Forwarded-Host 는 원래 호스트로 채운다', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/x', {
        headers: { 'X-Forwarded-Host': 'evil.example', 'X-Forwarded-Proto': 'http' },
      }),
      makeEnv(),
    )

    const sent = sentRequest(upstream)
    expect(sent.headers.get('x-forwarded-proto')).toBe('https')
    expect(sent.headers.get('x-forwarded-host')).toBe('quest.dhbang.co.kr')
  })

  it('원본 Host 헤더를 넘기지 않는다 (업스트림 URL 의 호스트가 쓰인다)', async () => {
    const upstream = stubUpstream()
    await handleRequest(
      new Request('https://quest.dhbang.co.kr/api/v1/x', {
        headers: { Host: 'quest.dhbang.co.kr' },
      }),
      makeEnv(),
    )

    const sent = sentRequest(upstream)
    expect(sent.headers.get('host')).toBeNull()
    expect(new URL(sent.url).host).toBe('api.quest.dhbang.co.kr')
  })

  it('redirect: manual 로 요청하고 302 를 따라가지 않고 그대로 돌려준다', async () => {
    const redirect = new Response(null, { status: 302, headers: { Location: 'https://elsewhere.example/' } })
    const upstream = stubUpstream(redirect)

    const res = await handleRequest(new Request('https://quest.dhbang.co.kr/api/v1/x'), makeEnv())

    expect(sentRequest(upstream).redirect).toBe('manual')
    expect(res.status).toBe(302)
    expect(res.headers.get('location')).toBe('https://elsewhere.example/')
  })

  it('업스트림 응답(상태·본문)을 그대로 돌려준다', async () => {
    stubUpstream(new Response('{"success":false}', { status: 429 }))

    const res = await handleRequest(new Request('https://quest.dhbang.co.kr/api/v1/x'), makeEnv())

    expect(res.status).toBe(429)
    expect(await res.text()).toBe('{"success":false}')
  })

  it('업스트림 fetch 가 예외를 던지면 502 와 짧은 텍스트를 돌려준다', async () => {
    stubUpstream(new Error('connection refused'))
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const res = await handleRequest(new Request('https://quest.dhbang.co.kr/api/v1/x'), makeEnv())

    expect(res.status).toBe(502)
    expect(res.headers.get('content-type')).toContain('text/plain')
    expect(await res.text()).toBe('Bad Gateway')
  })
})

describe('handleRequest — 정적 자산', () => {
  it('/api/ 가 아닌 경로는 ASSETS 로 넘기고 업스트림 fetch 를 하지 않는다', async () => {
    const upstream = stubUpstream()
    const assetResponse = new Response('<html></html>')
    const assetsFetch = vi.fn(async () => assetResponse)
    const request = new Request('https://quest.dhbang.co.kr/quest/abc')

    const res = await handleRequest(request, makeEnv(assetsFetch))

    expect(assetsFetch).toHaveBeenCalledWith(request)
    expect(res).toBe(assetResponse)
    expect(upstream).not.toHaveBeenCalled()
  })

  it('/apix 처럼 /api/ 로 시작하지 않는 경로는 중계하지 않는다', async () => {
    const upstream = stubUpstream()
    const assetsFetch = vi.fn(async () => new Response('x'))

    await handleRequest(new Request('https://quest.dhbang.co.kr/apix/v1'), makeEnv(assetsFetch))

    expect(assetsFetch).toHaveBeenCalledOnce()
    expect(upstream).not.toHaveBeenCalled()
  })
})
