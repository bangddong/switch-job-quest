import { decideBackend, SWITCH_KEY, type Backend } from './switch'

/** wrangler.jsonc 의 vars / assets.binding / kv_namespaces 와 이름이 같아야 한다. */
export interface Env {
  ASSETS: Fetcher
  API_ORIGIN: string
  /** EKS 원본. 비어 있으면 스위치가 있어도 Fly 로 간다. */
  EKS_ORIGIN: string
  /** 백엔드 스위치 KV. 바인딩이 없는 배포에서는 늘 Fly 로 간다. */
  SWITCH?: KVNamespace
}

const API_PREFIX = '/api/'
const BACKEND_HEADER = 'X-DevQuest-Backend'

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

/** KV 스위치를 읽어 백엔드를 정한다. 어떤 실패(바인딩 없음·KV 예외 포함)도 Fly 로 떨어진다. */
async function pickBackend(env: Env): Promise<Backend> {
  if (!env.SWITCH) return 'fly'
  try {
    const raw = await env.SWITCH.get(SWITCH_KEY, { type: 'text' })
    return decideBackend(raw, Date.now(), env.EKS_ORIGIN)
  } catch (error) {
    console.error('switch read failed', error instanceof Error ? error.message : String(error))
    return 'fly'
  }
}

/** 응답 헤더가 immutable 일 수 있어 복제본에 백엔드 표시를 붙인다. */
function withBackendHeader(response: Response, backend: Backend): Response {
  const tagged = new Response(response.body, response)
  tagged.headers.set(BACKEND_HEADER, backend)
  return tagged
}

export async function handleRequest(request: Request, env: Env): Promise<Response> {
  const { pathname } = new URL(request.url)

  if (!isApiPath(pathname)) {
    return env.ASSETS.fetch(request)
  }

  const backend = await pickBackend(env)
  const origin = backend === 'eks' ? env.EKS_ORIGIN : env.API_ORIGIN

  // 자동 장애 전환 없음 — EKS 실패를 Fly 로 재시도하면 POST(AI 평가)가 이중 처리될 수 있다.
  try {
    return withBackendHeader(await fetch(buildUpstreamRequest(request, origin)), backend)
  } catch (error) {
    console.error('upstream fetch failed', error instanceof Error ? error.message : String(error))
    return withBackendHeader(
      new Response('Bad Gateway', {
        status: 502,
        headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      }),
      backend,
    )
  }
}
