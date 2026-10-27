# Vercel 제거 — FE 를 Cloudflare Workers 로 (2026-10-24)

> **목적**: 벤더 정리. Vercel 이 하던 일(정적 FE 서빙 + `/api` 중계)을 이미 앞단에 있는 Cloudflare 하나로 옮긴다.
> **순서**: 📌D-016 **8-C 보다 먼저** 한다(사용자 결정 2026-10-24). 8-C 가 시작될 때 경로가
> `브라우저 → Cloudflare(Worker) → api.quest` 로 이미 단순해져 있게 하려는 것이다.

## 현재 경로 (2026-10-24 실측)

| 도메인 | 경로 | 근거 |
|---|---|---|
| `quest.dhbang.co.kr` | Cloudflare 프록시 → **Vercel** → `vercel.json` rewrite `/api/(.*)` → `https://api.quest.dhbang.co.kr/api/$1` → Fly | 응답 헤더 `server: cloudflare` · `x-vercel-id` |
| `dhbang.co.kr`(블로그) | Cloudflare 프록시 → **GitHub Pages** | `x-github-request-id` · study-log 의 Pages 배포 워크플로 — **Vercel 무관** |

Vercel 이 하는 일은 `fe/vercel.json` 두 줄과 `fe-cd.yml` 의 `npx vercel --prod` 가 전부다.
- FE 는 `API_BASE = '/api/v1'`(상대 경로) → 같은 출처라 CORS 변경 없음
- JWT 는 localStorage + `Authorization` 헤더(쿠키 없음)
- OAuth 콜백은 `window.location.origin` 기준

## 단계

| 단계 | 내용 | 사용자 영향 | 게이트 |
|---|---|---|---|
| **V-0** | ✅ 2026-10-26 — Workers 배포 토큰 → GitHub secrets(TASK-20). 계정 API 토큰: 계정 정책 `Workers Scripts` + 존 정책 `Workers Routes`(V-3 용) | 없음 | 사용자(브라우저) |
| **V-1** | ✅ #501 (2026-10-25) — `fe/` 에 Worker 설정: 정적 자산(`dist/`) + SPA 처리 + `/api/*` 를 `api.quest` 로 중계하는 Worker 코드. `fe-cd.yml` 은 **Vercel 배포를 유지한 채** wrangler 배포를 추가(→ `*.workers.dev`) | 없음 | fe 빌더 → QA → PR |
| **V-2** | ✅ 2026-10-26 GET 스모크 통과(일지) — workers.dev 에서 스모크: 첫 화면 · 새로고침(SPA 경로) · `/api/v1/...` 응답 · 정적 자산 캐시 헤더 | 없음 | — |
| **V-3** | ✅ #503 (2026-10-26) — zone route `quest.dhbang.co.kr/*` 로 연결(기존 CNAME 유지, 복귀 = 대시보드 route 삭제 → routes 제거 PR, 그 사이 `fe/**` 머지 금지). 배포 토큰에 Zone Read 필요(`zone_name` 조회) | 🔴 prod FE 전환 | 🔴 사용자 승인 |
| **V-4** | 며칠 안정 → `fe-cd.yml` 의 Vercel 단계 · `vercel.json` · `VERCEL_*` secrets · Vercel 프로젝트 삭제. ⚠️ `quest` 레코드는 지금도 `CNAME cname.vercel-dns.com`(프록시) — route 가 가로채서 원본은 안 쓰이지만, Vercel 프로젝트를 지우면 **끊긴 원본을 가리키는 레코드**가 된다 → 원본 없는 프록시 레코드(`AAAA 100::`, Cloudflare 문서의 Worker 전용 패턴 🟡)로 교체 | 없음 | 사용자(Vercel 콘솔) |

> 리스크: V-3 에서 Cloudflare Worker 장애 = prod FE 장애. 지금도 Cloudflare 프록시 뒤라 **새로 편입되는 의존은 Workers 런타임 하나**다.
> 전제 조건: Worker 무료 플랜 한도(비자산 요청 = `/api/*` 호출 100k/일, CPU 10ms — `fetch` 대기 시간은 CPU 에 안 잡힘)가 현재 트래픽에 충분하다.
> 대안: Cloudflare Pages — `_redirects` 가 **외부 도메인 프록시를 지원하지 않아**(`/api` 중계 불가) Functions 를 따로 써야 한다. Workers 하나로 끝나는 쪽을 골랐다.

## V-3 연결 방식 — 착수 때 결정 (미확정)

| | Custom Domain | Route (`quest.dhbang.co.kr/*`) |
|---|---|---|
| 기존 CNAME(→ Vercel) | **지워야 한다**(문서: 기존 CNAME 이 있으면 생성 불가) → 짧은 공백 | 그대로 둔다(프록시 레코드면 Worker 가 가로챔) |
| 되돌리기 | Custom Domain 삭제 → CNAME 재생성 | Route 삭제(즉시) |
| Cloudflare 권장 | **Worker 가 origin 일 때 권장** | origin 이 따로 있을 때 권장 |
| 미확인 | — | 🟡 정적 자산이 Route 에서도 동일하게 서빙되는가 |

Route 가 무중단·즉시 복귀라 유리하지만 문서 권장과 어긋난다. V-2 이후 workers.dev 에서 동작을 본 뒤 정한다.

## 주의사항 (V-1 빌더 프롬프트에 주입)

1. 🟡 SPA 처리: `assets.not_found_handling = "single-page-application"` + `/api/*` 는 Worker 가 먼저 받아야 한다(`run_worker_first`) — 설정 키 이름은 V-1 에서 문서로 재확인.
2. **workers.dev 에서는 GitHub 로그인이 안 된다** — OAuth 앱의 콜백 URL 이 `quest.dhbang.co.kr` 로 등록돼 있다. V-2 스모크는 비로그인 경로 + `/api` 공개 엔드포인트로 한다. 로그인 확인은 V-3 직후.
2-1. 🔴 **workers.dev 에서는 쓰기 요청(POST/PUT/DELETE)이 BE CORS 에 막힌다 (V-1 QA F-1).** Worker 가 Host 를 `api.quest` 로 바꾸므로 브라우저 `Origin: …workers.dev` 는 BE 기준 교차 출처이고, `CORS_ALLOWED_ORIGINS` 에 없다 → 403. 브라우저는 같은 출처 GET 에는 Origin 을 대개 안 실어서 **GET 은 통과한다.** "같은 출처라 CORS 변경 없음" 은 **prod 도메인에서만** 참이다(Vercel 때도 같은 메커니즘으로 `quest.dhbang.co.kr` 이 허용 목록에 있어서 됐다). ⇒ V-2 = GET 스모크만. **body 중계(POST) 검증은 V-3 직후 첫 스모크 항목**이고 실패 시 즉시 복귀. 대안: V-2 동안 Fly `CORS_ALLOWED_ORIGINS` 에 workers.dev 를 임시 추가 — Fly 재시작이 따르므로 🔴 사용자 결정.
3. **실제 IP 헤더(B7-19)**: Worker → `api.quest` 요청에서 Fly 가 보는 클라이언트는 Cloudflare 이그레스다(지금 Vercel IP 를 보는 것과 같은 문제). Worker 에서 `X-Forwarded-For` 에 `CF-Connecting-IP` 를 실어 보낼 수는 있지만, BE 는 `Fly-Client-IP` 를 **먼저** 읽는다(`AbstractRateLimitInterceptor`) → BE 수정 없이는 효과가 없다. **V 범위 밖 — B7-19 로 둔다.** (Worker 가 헤더를 싣는 것까지는 V-1 에 넣어도 무해)
4. 🔴 `CLOUDFLARE_ACCOUNT_ID` 는 레포·`wrangler.toml` 에 적지 않는다 — GitHub secret 으로만.
5. Vercel 배포 단계는 V-4 까지 지우지 않는다(복귀 경로).

## 이번에 안 하는 것

| 항목 | 왜 지금 안 하나 | 언제 |
|---|---|---|
| BE 의 IP 헤더 우선순위 변경(B7-19) | BE 수정 + rate limit 동작 변경 → 별도 PR·QA. Vercel 제거와 독립 | 10번 전 (📌D-016 계획 「10번 전」 목록) |
| 8-C 용 DNS 토큰(TASK-18) | 8-C 착수 때 필요. FE 배포 토큰과 **분리**한다(아래) | 8-C 착수 때 |

### 토큰을 둘로 나누는 이유

| 토큰 | 권한 | 보관 | 읽는 주체 |
|---|---|---|---|
| FE 배포(TASK-20) | Account · Workers Scripts · Edit / Zone · Workers Routes · Edit | GitHub secret | `fe-cd` 워크플로 |
| 8-C DNS(TASK-18) | Zone · DNS · Edit 외 | SSM | eks-window 워크플로 |

DNS Edit 는 레코드 단위로 못 좁혀서 **블로그 레코드까지** 바꿀 수 있다(B8-13). 그 권한이 FE 배포 secret 에
섞이면, `fe/**` 를 건드리는 모든 main 배포가 존 전체 DNS 권한을 들고 돈다. 토큰 하나 더 만드는 비용은 콘솔 1분이다.
