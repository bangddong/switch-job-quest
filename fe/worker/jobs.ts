import type { Env } from './proxy'

const JOB_PATH = '/internal/jobs/daily'
const JOB_AUTH_HEADER = 'X-DevQuest-Job-Auth'

// 시도 사이 대기(ms). 길이가 곧 최대 시도 횟수다.
// Fly 가 완전 정지 상태면 JVM 콜드 스타트로 2~3분간 503/타임아웃이 난다 — 그 구간을 건너도록 총 대기를 늘린다.
const RETRY_DELAYS_MS = [0, 20_000, 40_000, 60_000, 60_000, 60_000]
const ATTEMPT_TIMEOUT_MS = 180_000
// Cron 핸들러 벽시계 한도(15분) 안쪽.
const TOTAL_DEADLINE_MS = 12 * 60_000

export type JobOutcome = 'done' | 'running' | 'unauthorized' | 'disabled' | 'misconfigured' | 'failed'

export interface JobResult {
  outcome: JobOutcome
  attempts: number
  /** 마지막으로 받은 HTTP 상태. 응답을 한 번도 못 받았으면 null. */
  lastStatus: number | null
}

export interface JobDeps {
  fetch: typeof fetch
  sleep: (ms: number) => Promise<void>
  now: () => number
  /** 시도당 타임아웃 신호. 기본은 AbortSignal.timeout. */
  timeoutSignal: (ms: number) => AbortSignal
}

export type JobEnv = Pick<Env, 'API_ORIGIN' | 'DEVQUEST_JOB_SECRET'>

const defaultDeps: JobDeps = {
  fetch: (input, init) => fetch(input, init),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  now: () => Date.now(),
  timeoutSignal: (ms) => AbortSignal.timeout(ms),
}

/** JSON 이 아닐 수 있는 body 를 객체로 읽는다. 실패하면 null. */
async function readJson(response: Response): Promise<Record<string, unknown> | null> {
  try {
    const parsed: unknown = JSON.parse(await response.text())
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null
  } catch {
    return null
  }
}

/** 로그에는 body 의 숫자 필드만 남긴다 — 임의 문자열을 통째로 찍지 않는다. */
function numericFields(body: Record<string, unknown> | null): Record<string, number> {
  const out: Record<string, number> = {}
  if (!body) return out
  for (const [key, value] of Object.entries(body)) {
    if (typeof value === 'number' && Number.isFinite(value)) out[key] = value
  }
  return out
}

/**
 * 09:00 KST Cron 이 부르는 일일 작업 트리거. Fly 가 일시정지/정지 상태일 수 있어 핸들러 안에서 재시도한다.
 * 작업은 BE 에서 멱등이므로 재시도해도 안전하다. 비밀 값은 로그에 남기지 않는다.
 */
export async function runDailyJob(
  env: JobEnv,
  scheduledTime: number,
  deps: Partial<JobDeps> = {},
): Promise<JobResult> {
  const d: JobDeps = { ...defaultDeps, ...deps }
  const secret = env.DEVQUEST_JOB_SECRET?.trim()
  if (!secret) {
    console.error('daily job skipped: DEVQUEST_JOB_SECRET is not configured')
    return { outcome: 'misconfigured', attempts: 0, lastStatus: null }
  }

  const url = `${env.API_ORIGIN.replace(/\/+$/, '')}${JOB_PATH}`
  const body = JSON.stringify({ scheduledTime })
  const deadline = d.now() + TOTAL_DEADLINE_MS
  let attempts = 0
  let lastStatus: number | null = null

  for (const delay of RETRY_DELAYS_MS) {
    if (delay > 0) {
      // 대기 후에는 시도할 시간이 남지 않으므로 마감이 지나기 전에 접는다.
      if (d.now() + delay >= deadline) break
      await d.sleep(delay)
    }
    const remaining = deadline - d.now()
    if (remaining <= 0) break

    attempts += 1
    let response: Response
    try {
      response = await d.fetch(url, {
        method: 'POST',
        headers: { [JOB_AUTH_HEADER]: secret, 'Content-Type': 'application/json' },
        body,
        signal: d.timeoutSignal(Math.min(ATTEMPT_TIMEOUT_MS, remaining)),
      })
    } catch (error) {
      const name = error instanceof Error ? error.name : 'unknown'
      console.warn(`daily job attempt ${attempts} failed: ${name}`)
      continue
    }

    const status = response.status
    lastStatus = status

    if (status === 200) {
      console.log('daily job done', JSON.stringify({ attempts, ...numericFields(await readJson(response)) }))
      return { outcome: 'done', attempts, lastStatus }
    }
    if (status === 409) {
      console.warn('daily job skipped: another run is in progress (409)')
      return { outcome: 'running', attempts, lastStatus }
    }
    if (status === 401) {
      console.error('daily job aborted: server rejected the job secret (401) — check DEVQUEST_JOB_SECRET on both sides')
      return { outcome: 'unauthorized', attempts, lastStatus }
    }
    if (status === 503) {
      const parsed = await readJson(response)
      if (parsed?.status === 'disabled') {
        console.error('daily job aborted: server job secret is not configured (503 disabled)')
        return { outcome: 'disabled', attempts, lastStatus }
      }
    } else {
      // 연결을 풀어주기만 한다. 완료를 기다리지 않는다.
      void response.body?.cancel().catch(() => undefined)
    }
    if (status >= 500) {
      console.warn(`daily job attempt ${attempts} got ${status}, will retry`)
      continue
    }
    // 그 밖의 4xx 와 예상 밖 상태(2xx/3xx)는 재시도해도 같다.
    console.error(`daily job aborted: unexpected status ${status}`)
    return { outcome: 'failed', attempts, lastStatus }
  }

  console.error(`daily job failed: attempts=${attempts} lastStatus=${lastStatus ?? 'none'} (retries exhausted or deadline reached)`)
  return { outcome: 'failed', attempts, lastStatus }
}
