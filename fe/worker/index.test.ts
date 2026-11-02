import { describe, expect, it, vi } from 'vitest'

vi.mock('./jobs', () => ({ runDailyJob: vi.fn() }))

import worker from './index'
import { runDailyJob } from './jobs'
import type { Env } from './proxy'

const SCHEDULED = 1_793_000_000_000

describe('scheduled 핸들러 배선', () => {
  it('controller.scheduledTime 을 그대로 넘기고 작업이 끝나기 전에는 resolve 하지 않는다', async () => {
    let finish: () => void = () => undefined
    const pending = new Promise<void>((resolve) => {
      finish = resolve
    })
    vi.mocked(runDailyJob).mockReturnValueOnce(
      pending.then(() => ({ outcome: 'done' as const, attempts: 1, lastStatus: 200 })),
    )
    const env = { API_ORIGIN: 'https://api.example', DEVQUEST_JOB_SECRET: 's' } as unknown as Env
    const controller = { scheduledTime: SCHEDULED, cron: '0 0 * * *' } as unknown as ScheduledController

    let resolved = false
    const returned = worker.scheduled(controller, env)
    const handlerDone = Promise.resolve(returned).then(() => {
      resolved = true
    })

    await new Promise((r) => setTimeout(r, 10))
    expect(runDailyJob).toHaveBeenCalledWith(env, SCHEDULED)
    expect(resolved).toBe(false)

    finish()
    await handlerDone
    expect(resolved).toBe(true)
  })
})
