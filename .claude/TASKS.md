# 미완료 작업

## [ ] Fly.io secrets 업데이트 — Better Stack → Grafana Cloud Loki 전환

```bash
fly secrets set GRAFANA_LOKI_URL=https://logs-prod-ap-northeast-1.grafana.net/loki/v1/push \
               GRAFANA_LOKI_INSTANCE_ID=<your-loki-instance-id>
fly secrets unset LOGTAIL_SOURCE_TOKEN
```

Loki instance ID 확인: Grafana Cloud > Home > Stack > Loki > Details
GRAFANA_API_KEY는 기존 값 그대로 유지 (메트릭과 공유)

---

## 완료된 항목

### TASK-1: BE AI Evaluator 캐시 메트릭 관측가능성 추가 (PR #123)
`CacheMetricsAdvisor` 추가 — 매 AI 평가 호출 후 cache_read_input_tokens / cache_creation_input_tokens INFO 로그 출력.

### TASK-2: Claude Code 세션 프롬프트 구조 최적화 (PR #124)
CONTEXT.md 고정 내용(비자명적 결정, 참조 문서) 상단 배치, 동적 내용(현재 상태, 최근 완료) 하단으로 분리.

### [Observability] Sentry → 포기, Logtail 연동 완료
- **Sentry**: Spring Boot 4.x 미지원으로 포기 (PR #52에서 의존성 제거)
- **Logtail (Better Stack)**: 연동 완료 (fly.io log drain 등록)
