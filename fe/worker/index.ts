import { handleRequest, type Env } from './proxy'

// Workers 모듈 형식은 default export 를 요구한다. 로직은 proxy.ts 의 named export 에 있다.
export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env)
  },
} satisfies ExportedHandler<Env>
