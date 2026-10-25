/** wrangler.jsonc 의 vars / assets.binding 과 이름이 같아야 한다. */
export interface Env {
  ASSETS: Fetcher
  API_ORIGIN: string
}

const API_PREFIX = '/api/'

// 클라이언트가 위조할 수 있는 IP 헤더 — 업스트림에 중계하지 않는다.
// BE(AbstractRateLimitInterceptor)는 Fly-Client-IP 를 먼저 믿고, 나머지는 현재 미사용이지만 방어적으로 제거한다.
// X-Forwarded-For 는 아래에서 CF-Connecting-IP 값으로 다시 쓴다.
const STRIPPED_CLIENT_IP_HEADERS = [
  'Fly-Client-IP',
  'X-Real-IP',
  'Forwarded',
  'True-Client-IP',
  'X-Client-IP',
] as const

export function isApiPath(pathname: string): boolean {
  return pathname.startsWith(API_PREFIX)
}

/**
 * 클라이언트 요청을 업스트림(Fly) 요청으로 바꾼다.
 * 클라이언트가 위조할 수 있는 IP/프록시 헤더는 신뢰하지 않고 Cloudflare 가 채운 값으로 다시 쓴다.
 */
export function buildUpstreamRequest(request: Request, apiOrigin: string): Request {
  const url = new URL(request.url)
  const upstreamUrl = `${apiOrigin.replace(/\/+$/, '')}${url.pathname}${url.search}`

  // redirect: 'manual' — 업스트림의 3xx 를 Worker 가 따라가지 않고 브라우저에 그대로 전달한다.
  const upstream = new Request(new Request(upstreamUrl, request), { redirect: 'manual' })
  const headers = upstream.headers

  // 원본 Host 를 넘기지 않는다 — fetch 가 업스트림 URL 의 호스트로 채운다.
  headers.delete('Host')

  for (const name of STRIPPED_CLIENT_IP_HEADERS) {
    headers.delete(name)
  }

  const clientIp = request.headers.get('CF-Connecting-IP')
  if (clientIp) {
    headers.set('X-Forwarded-For', clientIp)
  } else {
    headers.delete('X-Forwarded-For')
  }
  headers.set('X-Forwarded-Proto', 'https')
  headers.set('X-Forwarded-Host', url.host)

  return upstream
}

export async function handleRequest(request: Request, env: Env): Promise<Response> {
  const { pathname } = new URL(request.url)

  if (!isApiPath(pathname)) {
    return env.ASSETS.fetch(request)
  }

  try {
    return await fetch(buildUpstreamRequest(request, env.API_ORIGIN))
  } catch (error) {
    console.error('upstream fetch failed', error instanceof Error ? error.message : String(error))
    return new Response('Bad Gateway', {
      status: 502,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    })
  }
}
