# 배포 구성

## 인프라

| 대상 | 플랫폼 | 도메인 |
|------|--------|--------|
| FE | Vercel | `quest.dhbang.co.kr` |
| BE | Fly.io | `api.quest.dhbang.co.kr` |

## 환경변수 (Fly.io secrets)

| 변수 | 설명 |
|------|------|
| `ANTHROPIC_API_KEY` | Anthropic API 키 |
| `CORS_ALLOWED_ORIGINS` | `https://quest.dhbang.co.kr` |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `RESEND_API_KEY` | Resend 이메일 API 키 (PR #135) |
| `MAIL_ENABLED` | `true` 설정 시 실제 발송 활성화 |
| `JUDGE0_API_KEY` | RapidAPI Judge0 채점 키 (PR #136) |

## 배포 파일 위치

- `be/Dockerfile` — 멀티스테이지 빌드 (eclipse-temurin:21-jre-alpine)
- `be/fly.toml` — Fly.io 설정 (Tokyo 리전, 512MB)
- `fe/vercel.json` — API 프록시 (`/api/*` → BE) + SPA 라우팅

## BE 배포 명령

```bash
cd be
fly secrets set ANTHROPIC_API_KEY=sk-ant-... \
  CORS_ALLOWED_ORIGINS=https://quest.dhbang.co.kr \
  SPRING_PROFILES_ACTIVE=prod
fly deploy
```

## FE 배포 명령

```bash
cd fe
vercel --prod
```
