import { runDailyJob } from './jobs'
import { handleRequest, type Env } from './proxy'

// Workers 모듈 형식은 default export 를 요구한다. 로직은 proxy.ts·jobs.ts 의 named export 에 있다.
export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env)
  },
  // 09:00 KST 일일 작업 트리거 (wrangler.jsonc 의 triggers.crons).
  scheduled(controller, env, ctx): void {
    ctx.waitUntil(runDailyJob(env, controller.scheduledTime))
  },
} satisfies ExportedHandler<Env>
