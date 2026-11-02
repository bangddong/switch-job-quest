import { runDailyJob } from './jobs'
import { handleRequest, type Env } from './proxy'

// Workers 모듈 형식은 default export 를 요구한다. 로직은 proxy.ts·jobs.ts 의 named export 에 있다.
export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env)
  },
  // 09:00 KST 일일 작업 트리거 (wrangler.jsonc 의 triggers.crons).
  // waitUntil 대신 await — 반환 Promise 가 작업 수명과 같아야 한다(최대 12분, Cron 벽시계 한도 15분).
  async scheduled(controller, env): Promise<void> {
    await runDailyJob(env, controller.scheduledTime)
  },
} satisfies ExportedHandler<Env>
