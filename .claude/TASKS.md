# 미완료 작업

### TASK-3: BE 서버 다운 — PR #231 배포 실패 후 헬스체크 미통과 (2026-07-01)
`https://api.quest.dhbang.co.kr/health` 503 → 완전 타임아웃으로 악화 중. FE(`quest.dhbang.co.kr`)는 정상.
GH Actions `BE CD` 워크플로우(run 28488126339)가 "timeout reached waiting for health checks to pass"로
실패(빌드/이미지 push는 성공, 머신이 6분간 헬스체크 미통과 → rollback). flyctl 로컬 인증 토큰이 없어
이 세션에서 실제 런타임 로그 확인 불가.

**필요 작업 (사용자 직접 실행)**:
```bash
cd be
fly auth login
fly status
fly logs                  # 크래시 원인 확인 — V8 마이그레이션 이슈 / cold-start 지연 / Fly API 일시 장애 중 판별
fly machine restart <id>  # 필요시
fly deploy                # 원인 파악 후 필요시 재배포
```

## 완료된 항목

### TASK-1: BE AI Evaluator 캐시 메트릭 관측가능성 추가 (PR #123)
`CacheMetricsAdvisor` 추가 — 매 AI 평가 호출 후 cache_read_input_tokens / cache_creation_input_tokens INFO 로그 출력.

### TASK-2: Claude Code 세션 프롬프트 구조 최적화 (PR #124)
CONTEXT.md 고정 내용(비자명적 결정, 참조 문서) 상단 배치, 동적 내용(현재 상태, 최근 완료) 하단으로 분리.

### [Observability] Sentry → 포기, Logtail 연동 완료
- **Sentry**: Spring Boot 4.x 미지원으로 포기 (PR #52에서 의존성 제거)
- **Logtail (Better Stack)**: 연동 완료 (fly.io log drain 등록)
