import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { runDailyJob, type JobDeps } from './jobs'

const SECRET = 'super-secret-job-value-xyz'
const ORIGIN = 'https://api.quest.dhbang.co.kr'
const SCHEDULED = 1_793_000_000_000
const SEC = 1000
const MIN = 60 * SEC

type Step = Response | Error

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

interface Harness {
  deps: JobDeps
  fetchMock: ReturnType<typeof vi.fn>
  sleeps: number[]
  timeouts: number[]
  clock: { t: number }
}

/** steps 를 순서대로 돌려주는 가짜 환경. sleep 은 가짜 시계만 전진시키고, fetch 는 min(attemptCost, 시도 타임아웃)만큼 시계를 쓴다. */
function harness(steps: Step[], attemptCost = 0): Harness {
  const clock = { t: 1_000_000 }
  const sleeps: number[] = []
  const timeouts: number[] = []
  let i = 0
  const fetchMock = vi.fn(async () => {
    // 실제 fetch 처럼 시도 타임아웃을 넘겨 쓰지 못한다.
    clock.t += Math.min(attemptCost, timeouts[timeouts.length - 1] ?? attemptCost)
    const step = steps[Math.min(i, steps.length - 1)]
    i += 1
    if (step instanceof Error) throw step
    if (!step) throw new Error('no step')
    return step.clone()
  })
  const deps: JobDeps = {
    fetch: fetchMock as unknown as typeof fetch,
    sleep: async (ms) => {
      sleeps.push(ms)
      clock.t += ms
    },
    now: () => clock.t,
    timeoutSignal: (ms) => {
      timeouts.push(ms)
      return new AbortController().signal
    },
  }
  return { deps, fetchMock, sleeps, timeouts, clock }
}

const env = { API_ORIGIN: ORIGIN, DEVQUEST_JOB_SECRET: SECRET }

let logs: string[]
beforeEach(() => {
  logs = []
  for (const m of ['log', 'warn', 'error', 'info', 'debug'] as const) {
    vi.spyOn(console, m).mockImplementation((...args: unknown[]) => {
      logs.push(args.map((a) => (typeof a === 'string' ? a : JSON.stringify(a))).join(' '))
    })
  }
})
afterEach(() => vi.restoreAllMocks())

describe('runDailyJob — 요청 형태', () => {
  it('POST /internal/jobs/daily 에 비밀 헤더와 scheduledTime body 를 보낸다', async () => {
    const h = harness([json(200, { status: 'done', mailTargets: 3, mailSent: 3, expiredRateLimitRowsDeleted: 7 })])
    await runDailyJob(env, SCHEDULED, h.deps)

    expect(h.fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = h.fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe(`${ORIGIN}/internal/jobs/daily`)
    expect(init.method).toBe('POST')
    const headers = new Headers(init.headers)
    expect(headers.get('X-DevQuest-Job-Auth')).toBe(SECRET)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({ scheduledTime: SCHEDULED })
    expect(init.signal).toBeDefined()
  })

  it('API_ORIGIN 끝 슬래시가 있어도 경로가 겹치지 않는다', async () => {
    const h = harness([json(200, { status: 'done' })])
    await runDailyJob({ ...env, API_ORIGIN: `${ORIGIN}/` }, SCHEDULED, h.deps)
    expect((h.fetchMock.mock.calls[0] as unknown[])[0]).toBe(`${ORIGIN}/internal/jobs/daily`)
  })
})

describe('runDailyJob — 비밀 누락', () => {
  it.each([undefined, '', '   '])('비밀이 %j 이면 fetch 없이 misconfigured', async (secret) => {
    const h = harness([json(200, {})])
    const result = await runDailyJob({ API_ORIGIN: ORIGIN, DEVQUEST_JOB_SECRET: secret }, SCHEDULED, h.deps)
    expect(h.fetchMock).not.toHaveBeenCalled()
    expect(result.outcome).toBe('misconfigured')
    expect(result.attempts).toBe(0)
    expect(console.error).toHaveBeenCalled()
  })
})

describe('runDailyJob — 종료 분기', () => {
  it('200 은 done 이고 숫자 필드를 로그로 남긴다', async () => {
    const h = harness([json(200, { status: 'done', mailTargets: 5, mailSent: 5, expiredRateLimitRowsDeleted: 9 })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'done', attempts: 1, lastStatus: 200 })
    const text = logs.join('\n')
    expect(text).toContain('mailTargets')
    expect(text).toContain('5')
    expect(text).toContain('9')
    expect(console.log).toHaveBeenCalled()
  })

  it('200 이지만 body 가 JSON 이 아니어도 done', async () => {
    const h = harness([new Response('ok', { status: 200 })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('done')
  })

  it('409 는 재시도 없이 running + warn', async () => {
    const h = harness([json(409, { status: 'running' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'running', attempts: 1, lastStatus: 409 })
    expect(h.fetchMock).toHaveBeenCalledTimes(1)
    expect(console.warn).toHaveBeenCalled()
  })

  it('401 은 재시도 없이 unauthorized + error', async () => {
    const h = harness([json(401, { status: 'unauthorized' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'unauthorized', attempts: 1, lastStatus: 401 })
    expect(h.fetchMock).toHaveBeenCalledTimes(1)
    expect(console.error).toHaveBeenCalled()
  })

  it('503 + status=disabled 는 재시도 없이 disabled', async () => {
    const h = harness([json(503, { status: 'disabled' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'disabled', attempts: 1, lastStatus: 503 })
    expect(h.fetchMock).toHaveBeenCalledTimes(1)
    expect(console.error).toHaveBeenCalled()
  })

  it.each([400, 403, 404, 429])('그 밖의 4xx(%i)는 재시도 없이 failed', async (status) => {
    const h = harness([json(status, { error: 'x' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'failed', attempts: 1, lastStatus: status })
    expect(h.fetchMock).toHaveBeenCalledTimes(1)
    expect(console.error).toHaveBeenCalled()
  })
})

describe('runDailyJob — 재시도', () => {
  it('503(JSON 아님) -> 503 -> 200 이면 대기 [20s, 40s] 후 done', async () => {
    const h = harness([
      new Response('upstream connect error', { status: 503 }),
      new Response('', { status: 503 }),
      json(200, { status: 'done', mailTargets: 1, mailSent: 1, expiredRateLimitRowsDeleted: 0 }),
    ])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'done', attempts: 3, lastStatus: 200 })
    expect(h.sleeps).toEqual([20 * SEC, 40 * SEC])
  })

  it('503 이지만 status 가 disabled 가 아닌 JSON 이면 재시도', async () => {
    const h = harness([json(503, { status: 'starting' }), json(200, { status: 'done' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('done')
    expect(result.attempts).toBe(2)
  })

  it.each([500, 502, 504])('%i 도 재시도한다', async (status) => {
    const h = harness([json(status, { error: 'boom' }), json(200, { status: 'done' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('done')
    expect(h.sleeps).toEqual([20 * SEC])
  })

  it('네트워크 오류 후 재시도해 성공한다', async () => {
    const h = harness([new TypeError('fetch failed'), json(200, { status: 'done' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'done', attempts: 2, lastStatus: 200 })
  })

  it('타임아웃(TimeoutError) 후 재시도해 성공한다', async () => {
    const timeout = new DOMException('timed out', 'TimeoutError')
    const h = harness([timeout, json(200, { status: 'done' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('done')
    expect(result.attempts).toBe(2)
  })

  it('계속 실패하면 6회(대기 20,40,60,60,60) 후 failed 와 마지막 상태', async () => {
    const h = harness([json(503, {})])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'failed', attempts: 6, lastStatus: 503 })
    expect(h.fetchMock).toHaveBeenCalledTimes(6)
    expect(h.sleeps).toEqual([20 * SEC, 40 * SEC, 60 * SEC, 60 * SEC, 60 * SEC])
    const last = logs[logs.length - 1] ?? ''
    expect(last).toContain('6')
    expect(last).toContain('503')
  })

  it('네트워크 오류만 이어지면 lastStatus 는 null', async () => {
    const h = harness([new TypeError('fetch failed')])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'failed', attempts: 6, lastStatus: null })
  })
})

describe('runDailyJob — 시도 타임아웃과 전체 마감', () => {
  it('시도 타임아웃은 180초다', async () => {
    const h = harness([json(200, { status: 'done' })])
    await runDailyJob(env, SCHEDULED, h.deps)
    expect(h.timeouts).toEqual([180 * SEC])
  })

  it('마감까지 180초 미만 남으면 남은 시간으로 줄인다', async () => {
    // 시도 비용 170초: 1회 170, +20 대기, 2회 170 ... 남은 시간이 줄어드는 시도가 생긴다
    const h = harness([json(503, {})], 170 * SEC)
    await runDailyJob(env, SCHEDULED, h.deps)
    expect(Math.max(...h.timeouts)).toBeLessThanOrEqual(180 * SEC)
    expect(Math.min(...h.timeouts)).toBeLessThan(180 * SEC)
    expect(Math.min(...h.timeouts)).toBeGreaterThan(0)
  })

  it('시작 후 12분을 넘기는 시도를 하지 않는다', async () => {
    const h = harness([json(503, {})], 170 * SEC)
    const start = h.clock.t
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('failed')
    expect(result.attempts).toBeLessThan(6)
    expect(h.clock.t - start).toBeLessThanOrEqual(12 * MIN)
  })

  it('마지막 로그에 마감 초과를 남긴다', async () => {
    const h = harness([json(503, {})], 170 * SEC)
    await runDailyJob(env, SCHEDULED, h.deps)
    expect(console.error).toHaveBeenCalled()
  })
})

describe('runDailyJob — 409 로그 레벨 (F-3a)', () => {
  it('첫 시도의 409 는 warn 이고 error 는 없다', async () => {
    const h = harness([json(409, { status: 'running' })])
    await runDailyJob(env, SCHEDULED, h.deps)
    expect(console.warn).toHaveBeenCalled()
    expect(console.error).not.toHaveBeenCalled()
  })

  it('타임아웃 뒤 재시도에서 409 면 running 을 유지하고 error 로 남긴다', async () => {
    const h = harness([new DOMException('timed out', 'TimeoutError'), json(409, { status: 'running' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'running', attempts: 2, lastStatus: 409 })
    expect(console.error).toHaveBeenCalled()
  })

  it('네트워크 오류 뒤 재시도에서 409 도 error 로 남긴다', async () => {
    const h = harness([new TypeError('fetch failed'), json(409, { status: 'running' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('running')
    expect(console.error).toHaveBeenCalled()
  })

  it('503 뒤 재시도의 409 는 자기 타임아웃이 아니므로 warn 이다', async () => {
    const h = harness([new Response('', { status: 503 }), json(409, { status: 'running' })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('running')
    expect(console.warn).toHaveBeenCalled()
    expect(console.error).not.toHaveBeenCalledWith(expect.stringContaining('409'))
  })
})

describe('runDailyJob — 부분 발송 재시도 (F-3b)', () => {
  const partial = () => json(200, { status: 'done', mailTargets: 3, mailSent: 1, expiredRateLimitRowsDeleted: 0 })
  const full = () => json(200, { status: 'done', mailTargets: 3, mailSent: 3, expiredRateLimitRowsDeleted: 0 })

  it('mailSent < mailTargets 면 재시도하고 다음에 전부 보내면 done', async () => {
    const h = harness([partial(), full()])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'done', attempts: 2, lastStatus: 200 })
    expect(h.sleeps).toEqual([20_000])
  })

  it('같은 대기 배열을 따른다 (20,40,60,60,60) 그리고 소진되면 partial + error', async () => {
    const h = harness([partial()])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'partial', attempts: 6, lastStatus: 200 })
    expect(h.sleeps).toEqual([20_000, 40_000, 60_000, 60_000, 60_000])
    expect(console.error).toHaveBeenCalled()
  })

  it('마감에 걸리면 마지막 200 결과로 partial', async () => {
    const h = harness([partial()], 5 * MIN)
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('partial')
    expect(result.lastStatus).toBe(200)
    expect(h.clock.t - 1_000_000).toBeLessThanOrEqual(12 * MIN)
  })

  it('부분 발송 뒤 재시도가 5xx 로 실패해도 마지막 200 결과로 partial', async () => {
    const h = harness([partial(), new Response('', { status: 503 })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('partial')
    expect(result.lastStatus).toBe(200)
  })

  it('mailTargets 가 0 이면 바로 done', async () => {
    const h = harness([json(200, { status: 'done', mailTargets: 0, mailSent: 0 })])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result).toEqual({ outcome: 'done', attempts: 1, lastStatus: 200 })
  })

  it('mailSent === mailTargets 면 바로 done', async () => {
    const h = harness([full()])
    const result = await runDailyJob(env, SCHEDULED, h.deps)
    expect(result.outcome).toBe('done')
    expect(h.fetchMock).toHaveBeenCalledTimes(1)
  })

  it('숫자 필드가 없거나 숫자가 아니면 관대하게 done', async () => {
    for (const body of [
      { status: 'done' },
      { status: 'done', mailTargets: 3 },
      { status: 'done', mailSent: 1 },
      { status: 'done', mailTargets: '3', mailSent: '1' },
      { status: 'done', mailTargets: null, mailSent: null },
    ]) {
      const h = harness([json(200, body)])
      const result = await runDailyJob(env, SCHEDULED, h.deps)
      expect(result.outcome).toBe('done')
      expect(h.fetchMock).toHaveBeenCalledTimes(1)
    }
  })

  it('partial 로그에 비밀이 나오지 않는다', async () => {
    await runDailyJob(env, SCHEDULED, harness([partial()]).deps)
    expect(logs.join('\n')).not.toContain(SECRET)
  })
})

describe('runDailyJob — 비밀 비노출', () => {
  it('성공·실패·오류 어떤 경로에서도 비밀이 console 에 안 나온다', async () => {
    const scenarios: Step[][] = [
      [json(200, { status: 'done', mailTargets: 1 })],
      [json(409, { status: 'running' })],
      [json(401, { status: 'unauthorized' })],
      [json(503, { status: 'disabled' })],
      [json(500, { error: 'boom' })],
      [new TypeError('fetch failed')],
      [new DOMException('timed out', 'TimeoutError')],
    ]
    for (const steps of scenarios) {
      const h = harness(steps)
      await runDailyJob(env, SCHEDULED, h.deps)
    }
    await runDailyJob({ API_ORIGIN: ORIGIN }, SCHEDULED, harness([]).deps)
    expect(logs.length).toBeGreaterThan(0)
    expect(logs.join('\n')).not.toContain(SECRET)
  })
})
