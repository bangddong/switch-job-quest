# 미완료 작업 (사용자 직접 실행 필요)

## [Observability] Sentry + Logtail 설정 완료 (PR #48 머지 후)

### 1. Sentry 설정

1. [sentry.io](https://sentry.io) 가입 → New Project → **Java Spring**
2. DSN 복사 (형식: `https://xxx@o000.ingest.sentry.io/000`)
3. Fly.io secret 등록:
   ```bash
   flyctl secrets set SENTRY_DSN=<복사한 DSN> --app devquest-api
   ```

### 2. Logtail (Better Stack) 설정

1. [betterstack.com](https://betterstack.com) 가입 → Logs → **New Source**
   - Platform: **HTTP** 선택
   - Source 이름: `devquest-api`
2. Source token 복사
3. Fly.io log drain 등록:
   ```bash
   flyctl logs drain create https://in.logs.betterstack.com \
     --app devquest-api \
     -H "Authorization: Bearer <복사한 토큰>"
   ```
4. drain 확인:
   ```bash
   flyctl logs drain list --app devquest-api
   ```

### 완료 후 검증

- Fly.io에서 앱 재시작: `flyctl machine restart --app devquest-api`
- 로그인 시도 → Sentry에서 에러 캡처 확인
- Logtail 대시보드에서 실시간 로그 확인
