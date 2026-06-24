# 프로파일별 환경변수 요구사항

## local 프로파일 (`SPRING_PROFILES_ACTIVE=local`)

로컬 개발 / kind 클러스터 학습용. DB는 H2 in-memory, Loki·OTLP 비활성.

| 환경변수 | 기본값 | 필수 여부 | 비고 |
|---------|--------|---------|------|
| `GITHUB_CLIENT_ID` | 없음 | ✅ 필수 | `application.yml` — 기본값 없음 |
| `GITHUB_CLIENT_SECRET` | 없음 | ✅ 필수 | `application.yml` — 기본값 없음 |
| `JWT_SECRET` | 없음 | ✅ 필수 | `application.yml` — 기본값 없음, 256bit 이상 |
| `ANTHROPIC_API_KEY` | `""` (빈 문자열) | 선택 | AI 기능 호출 시에만 필요. 미설정 시 앱 기동은 가능 |
| `JUDGE0_API_KEY` | `""` | 선택 | 코드 실행 기능 |
| `RESEND_API_KEY` | `""` | 선택 | 메일 발송 기능 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 선택 | |
| `JWT_EXPIRATION_MS` | `2592000000` (30일) | 선택 | |
| `MAIL_ENABLED` | `false` | 선택 | |

### kind 클러스터 Secret 최소 구성 (3개만 필수)

```bash
kubectl create secret generic devquest-secrets \
  --from-literal=GITHUB_CLIENT_ID=dummy-client-id \
  --from-literal=GITHUB_CLIENT_SECRET=dummy-client-secret \
  --from-literal=JWT_SECRET=local-k8s-jwt-secret-must-be-at-least-256-bits-long-xxxxx
```

### deployment.yaml envFrom 설정

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "local"
envFrom:
  - secretRef:
      name: devquest-secrets
```

---

## prod 프로파일 (`SPRING_PROFILES_ACTIVE=prod`)

Fly.io 배포용. 외부 DB(Neon PostgreSQL) + Grafana Cloud(OTLP, Loki) 연동.

| 환경변수 | 필수 여부 | 비고 |
|---------|---------|------|
| `GITHUB_CLIENT_ID` | ✅ | |
| `GITHUB_CLIENT_SECRET` | ✅ | |
| `JWT_SECRET` | ✅ | |
| `DB_HOST` | ✅ | Neon PostgreSQL 호스트 |
| `DB_NAME` | ✅ | |
| `DB_USERNAME` | ✅ | |
| `DB_PASSWORD` | ✅ | |
| `ANTHROPIC_API_KEY` | ✅ | AI 기능 |
| `GRAFANA_API_KEY` | ✅ | `OtlpMetricsConfig` — prod에서 `grafana.otlp.enabled=true` 고정 |
| `GRAFANA_LOKI_URL` | ✅ | `logback-spring.xml` prod 프로파일 Loki Appender URI |
| `RESEND_API_KEY` | 선택 | 메일 발송 |
| `JUDGE0_API_KEY` | 선택 | 코드 실행 |

### prod 주의사항

- `grafana.otlp.enabled`는 `application-prod.yml`에 `true`로 **하드코딩**됨
  → `GRAFANA_API_KEY` 미설정 시 `PlaceholderResolutionException`으로 앱 기동 실패
- `GRAFANA_LOKI_URL`이 빈값이면 Logback 초기화 단계(Spring context 이전)에서 URI 파싱 오류 발생
  → 반드시 유효한 URL 형식 필요

---

## 의존 구조 요약

```
application.yml (항상 로드)
  ├── db-core.yml          → H2 기본값 내장 (local에선 외부 DB 불필요)
  ├── client-ai-anthropic.yml → ANTHROPIC_API_KEY 기본값 "" (선택)
  ├── logging.yml          → logback-spring.xml 참조
  └── monitoring.yml       → OtlpMetricsConfig (grafana.otlp.enabled 조건부 활성)

application-local.yml (local 프로파일 시 로드) — .gitignore 대상
application-prod.yml  (prod  프로파일 시 로드) — grafana.otlp.enabled=true 하드코딩
```
