# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 알아둬야 할 비자명적 결정

### mneme wiki ↔ 앱 데이터 관계 (런타임 연동 아님)
`E:/development/wiki/`(mneme LLM wiki)는 로컬 개발머신 전용(localhost:8080 MCP, SQLite) —
Fly.io 배포 앱에서 직접 조회 불가, 멀티테넌시 없음(개발자 개인 지식 저장소).
→ 앱 기능에 반영할 때는 **빌드타임 시드**로만 연계한다 (wiki 콘텐츠를 사람이 큐레이션 →
Flyway 마이그레이션/정적 리소스로 앱 DB에 넣음). 런타임에 앱이 mneme를 호출하는 구조 금지.
기존 유사 패턴: `client-ai/support/ConferenceReferenceLoader` + `conference-references.json`
(정적 classpath 리소스, 키워드 매칭 후 AI 프롬프트 주입).

### Controller 테스트 패턴
`standaloneSetup` + `@AuthenticationPrincipal` 조합 시 반드시:
```kotlin
.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
// + @BeforeEach: SecurityContextHolder.getContext().authentication 설정
// + @AfterEach: SecurityContextHolder.clearContext()
```

### 동일 파일 수정 스프린트 — 직렬 순서 필수
두 스프린트가 같은 파일을 수정하면 병렬 브랜치 금지.
앞 PR 머지 완료 후 다음 브랜치 생성. (BE↔FE 다른 파일이면 병렬 OK)

### Observability 최종 상태
- Sentry: Spring Boot 4.x 미지원으로 포기 (PR #52)
- Grafana Cloud Loki: loki4j 1.6.0 사용, `GRAFANA_LOKI_URL` 빈값이면 자동 비활성화
  - `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_INSTANCE_ID` fly secret으로 주입
  - `GRAFANA_API_KEY`는 메트릭(Prometheus)과 공유
  - Grafana Cloud용 `maxBytes=65536`, `requestTimeoutMs=15000` 설정
  - 이전: Logtail (Better Stack) `LogtailHttpAppender` → 제거됨 (chore/better-stack-to-loki)

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control 조합: `claude --agent orchestrator --remote-control` (직접 터미널 실행 필요)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원 (일반 세션만 생성)

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |

---

## 알아둬야 할 비자명적 결정 (추가)

### Spring Boot 4.x Flyway 수동 구성
Spring Boot 4.x에서 Flyway auto-configuration 제거됨 (spring-boot-autoconfigure AutoConfiguration.imports에 없음).
`db-core` 모듈에 `FlywayConfig.kt`로 수동 구성:
- `@Profile("prod")`로 prod 전용 활성화
- `flyway.repair()` → `flyway.migrate()` 순차 호출 (flyway 11.x에서 repairOnMigrate API 제거됨)
- `EntityManagerFactoryDependsOnPostProcessor("flyway")`로 JPA가 Flyway 완료 후 초기화되도록 설정
- 패키지: `org.springframework.boot.jpa.autoconfigure` (Spring Boot 4.x 변경)

### Flyway 마이그레이션 디렉토리 분산 — V8 버전 충돌로 prod 다운 사고 (2026-07-01)
마이그레이션 파일이 `be/core/core-api/.../db/migration/`과 `be/storage/db-core/.../db/migration/`
두 곳에 나뉘어 있고 런타임 클래스패스에서 합쳐짐. PR #231에서 `db-core`만 보고 V8을 새로
만들었는데 `core-api`에 이미 V8(company_pipeline)·V9(company_jd_analysis)가 있어 버전 중복 →
Flyway가 부팅 단계에서 예외 던지고 앱 전체 크래시(prod BE 완전 다운, `BE CD` 배포 실패).
- 수정: `V8__create_tech_question_bank.sql` → `V10__create_tech_question_bank.sql`로 rename (fix/flyway-v8-version-collision)
- 재발 방지: `be-ci.yml`에 두 디렉토리 합산 버전 중복 검사 린트 스텝 추가 — PR 단계에서 자동 차단
- `systematic-debugging.md` 스킬에 "마이그레이션 작성 전 두 디렉토리 전부 확인" 규칙 추가

### AI 메트릭 소멸 진짜 원인 — 반복 OOM 재시작 (2026-07-03, #239)
아래 #234 결론(increase 콜드스타트)은 반쪽이었음. 실측으로 확정한 전체 인과:
- Fly 512MB 머신이 **매일 1~2회 OOM 킬**(exit 137) — Machine Events 7/1·7/2·7/3(2회) 확인
- JVM 재시작 → 카운터 리셋. JVM 메트릭은 기동 시 즉시 재등록되지만 `gen_ai_*`/AI 카운터는
  **첫 AI 호출 때 lazy 등록** → 재시작 후 호출 없으면 시리즈 자체가 Grafana에서 소멸
- 원인: `MaxRAMPercentage=75`(힙 384MB)로 비힙 예산 부족 → #239에서 50%/Metaspace 160m/Xss512k로 재배분
- 주의: `spring.threads.virtual.enabled=true`라서 `server.tomcat.threads.max`는 **죽은 설정** (QA 지적으로 제외)
- OTLP resourceAttributes에 instance 라벨 없음 — 멀티 머신 되면 시리즈 충돌 (현재 1대라 무해, 스케일아웃 시 필수 수정)
- 배포 후 관찰: 힙/Metaspace 사용률, GC pause, OOM 재발 여부

### AI 메트릭 대시보드 "0으로 보임" — 버그 아님, `increase()` 콜드스타트 특성 (2026-07-01)
증상: Grafana `DevQuest - AI Metrics` 대시보드의 "Input/Output Tokens by Evaluator" 등 패널이
실제 AI 호출 이후에도 0으로 보임. BE 코드/OTLP 파이프라인 조사했으나 원인 아니었음 — **정상 동작**.
- 근거: raw 쿼리 `ai_tokens_input_total`(increase 없이)는 실제 값(1474 등)을 정상적으로 보여줌 —
  카운터 증가·OTLP push 다 정상. WARN 로그도 없음(예외 없이 정상 실행 확인).
- 원인: 패널이 `increase(metric[$__rate_interval])` 사용 — 이 프로젝트는 트래픽이 하루 몇 건뿐이라,
  (1) 새로 태어난 metric series는 첫 샘플에 비교할 이전 샘플이 없어 델타 계산 불가(콜드스타트),
  (2) 그 이후 새 호출이 없으면 "증가량"은 진짜 0 (누적값은 유지되지만 패널은 "증가량"만 표시).
- 결론: **같은 증상 재발해도 코드 재조사 불필요** — 트래픽이 뜨문뜨문한 저사용량 앱에서
  `increase()` 기반 레이트 패널은 원래 이렇게 보임. raw 카운터 값으로 확인하려면 Explore에서
  `increase()` 없이 순수 metric명으로 직접 쿼리.

### Grafana 대시보드 = 신형 v2 스키마 + table 패널의 `format: "table"` 필수 (2026-07-02)
- 라이브 대시보드는 **신형 v2 스키마**(`kind: Panel`, `elements` 맵, `RowsLayout`, Transformation은
  `{group, kind, spec.options}` 형태)로 마이그레이션됨. 레포의 `grafana/ai-metrics-dashboard.json`은
  구형(v10 classic, `__inputs`/`panels[]`)이라 divergent였음 → 신형으로 교체해 동기화함(IaC 소스는 이 파일).
- **table 패널 핵심**: Prometheus 쿼리에 `instant: true`만으론 부족하고 **`format: "table"`**을 반드시
  명시해야 `evaluatorType/model` 라벨 컬럼 + `Value #A/#B` 형태가 나옴. 빠지면 기본값 `time_series`라
  값 컬럼이 라벨셋 이름으로 지어지고 A·B가 충돌해 merge가 2행으로 쌓이고 organize rename도 실패.
  (UI Format 드롭다운의 "Table" = JSON `format: "table"`)
- 편집 시 **자동 새로고침(`refresh=1m`) OFF** 필수 — 켜져 있으면 미저장 편집이 리로드로 삭제되고
  브라우저 렌더러 OOM/CDP 프리즈 유발. 그래서 파일의 `timeSettings.autoRefresh`는 `""`(off)로 둠.

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | `chore/question-bank-seed-202607` |
| 열린 PR | #239 — OOM 재시작 대응 JVM 메모리 예산 튜닝 (머지 대기) / #240 — 질문 뱅크 wiki 시드 2026-07, 21건 (검수 대기) |

### K8s 학습 진행 상태

- **Stage 1 완료 ✅** — Pod Running + Service→Pod end-to-end 검증(`curl /health` 성공)
- 학습 기록: `k8s/docs/stage1-learning.md` (단계별 명령어 + 용어 심화)
- **Stage 2 완료 ✅** — ConfigMap + Secret 분리, `envFrom` 병용 구조
- 학습 기록: `k8s/docs/stage2-learning.md` (PR #225 머지 완료)
- **다음: Stage 3** — H2 → PostgreSQL StatefulSet
- 이후: Stage 3 (H2 → PostgreSQL StatefulSet), Stage 4 (Ingress)
- WSL 클론 위치: `~/switch-job-quest` (Linux 네이티브, Gradle fast)
- 이미지: `devquest-be:local` (`kind load docker-image`로 로드)
- local 프로파일 필수 env 3개: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET` (Secret `devquest-secrets`로 주입)
- 헬스 엔드포인트: `/health` (actuator 아님, 자체 HealthController)
- 참고: `k8s/docs/env-requirements.md`










## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #240 | 질문 뱅크 wiki 시드 2026-07 — 면접 concept 페이지 12건 wiki 생성(DB·JPA·JVM·인프라) + V11 질문 21건 시드 (검수 대기) | 2026-07-04 |
| #237 | 질문 뱅크 wiki 시드 반자동화 — /question-bank-seed 스킬 + SessionStart 훅(미처리 5건↑ 알림) | 2026-07-03 |
| #236 | Grafana AI Metrics 토큰 패널 table 전환(모델별 표) + 대시보드 v2 스키마 IaC 동기화 (format:table 핵심) | 2026-07-02 |
| #231 | 기술면접 질문 뱅크 DB — TechQuestionBank 테이블(V8 마이그레이션, 시드 5건) + DailyMailScheduler 뱅크 우선 조회(랜덤)→AI 폴백 | 2026-07-01 |
| #230 | repo 정리 — daily 로그 24건 커밋, be/.claude/qa-cache gitignore 보강, 스트레이 스크린샷 제거 | 2026-07-01 |
| #229 | 지원 파이프라인 Phase 2 — JD 분석 코칭 연동 (company_activity 연결, AI 분석 endpoint, CoachPanel UI) | 2026-06-30 |
| #228 | K8s Stage 3 학습 인덱스 — PostgreSQL StatefulSet + PV/PVC 예습 | 2026-06-29 |
| #227 | 회사별 지원 파이프라인 Phase 1 — AppliedCompany CRUD + 지원 현황 UI | 2026-06-28 |
| #226 | 데일리 기술면접 참고자료 — 국내 컨퍼런스 발표 카테고리 주입 | 2026-06-27 |
| #224 | K8s Stage 2 학습 인덱스 — ConfigMap/Secret 패턴 예습 노트 | 2026-06-24 |
| #223 | K8s Stage 1 학습 기록 — 단계별 명령어 + 용어 심화 설명 | 2026-06-24 |
| #222 | 데일리 질문 꼬리질문 제거 → 모범답안 실무 포인트 섹션 추가 | 2026-06-24 |
| #221 | K8s 초기 매니페스트 — BE Deployment(local 프로파일), Service, env-requirements.md | 2026-06-24 |
| #220 | modelAnswer 길이 축소 — 핵심 서술 + 참고 링크 구조 (output 토큰 ~6000 → ~2000 목표) | 2026-06-22 |
| #217 | OtlpMeterRegistry 중복 start() 제거 + push 성공 로그 추가 (3-arg 생성자 + publish() 오버라이드) | 2026-06-20 |
| #216 | OTLP auto-config 명시 비활성화 — management.otlp.metrics.export.enabled: false | 2026-06-20 |
| #215 | prompt injection 방어 — BaseAiEvaluator callAi/wrapUserContent, 전체 17개 Evaluator 적용 | 2026-06-18 |
| #214 | TechInterview max-tokens 4000→8000 (JSON 잘림 수정) + 대시보드 카운터 round() 추가 | 2026-06-17 |
| #213 | OTLP 메트릭 push keep-alive stale connection 수정 — Connection:close 헤더 + http.keepAlive=false | 2026-06-17 |
| #212 | OTLP auto-config @SpringBootApplication excludeName으로 제외 | 2026-06-17 |
| #208 | 기술면접 평가 면접관 페르소나 수정 — 5년차 기준 피드백 명시 | 2026-06-16 |
| #206 | 평가 결과 마크다운 렌더링 + 모바일 가시성 개선 — MarkdownRenderer 컴포넌트, maxWidth 640 | 2026-06-15 |
| #203 | qa-reviewer 버전 업그레이드 작업 시 deprecated API 전수 확인 체크리스트 추가 | 2026-06-13 |
| #202 | 전체 Evaluator .entity() → parseContent() 마이그레이션 — Spring AI RC2 500 에러 수정 | 2026-06-13 |
| #191 | qa-reviewer + orchestrator severity HIGH/MEDIUM/LOW 기준 통일 | 2026-06-10 |
| #190 | 기술면접 비로그인 체험 + IP rate limiting (Bucket4j, 하루 2회, 자정 reset) | 2026-06-10 |
| #189 | PR 리뷰 훅 개선 — HIGH/MEDIUM/LOW 3단계 + 차단 기준 명확화 | 2026-06-10 |
| #187 | AI 캐시 토큰 메트릭 추가 + Grafana 대시보드 (cache_read/creation 카운터, 5개 섹션 대시보드) | 2026-06-09 |
| #186 | Better Stack 제거 — Grafana Cloud Loki 전환 (loki4j 1.6.0) | 2026-06-09 |
| #185 | QA 강제화 훅 — gh pr create 전 qa-reviewer 실행 여부 차단 | 2026-06-09 |
| #184 | 모의면접 Java/인프라 카테고리 추가 및 질문 다양성 강화 + 모범 답안 상세화 | 2026-06-09 |
| #183 | AI 호출 메트릭 수집 — AiMetricsRecorder + Spring AI Observation 활성화 | 2026-06-08 |
| #180 | 데일리 메일 deepLink URL 오타 수정 (devquest.kr → quest.dhbang.co.kr) | 2026-06-08 |
| #178 | fe-feature-builder·design-reviewer ultrathink 제거 + assert-pr-reviewed.sh 개선 | 2026-06-08 |
| #176 | Grafana instance ID 수정 (3284556 → 1680166) | 2026-06-08 |
| #177 | CI claude-review 폐기 → PreToolUse 훅 기반 사전 리뷰로 전환 (gh pr create 시 자동 차단) | 2026-06-08 |
| #174 | Copilot 리뷰 → Claude 리뷰 전환 — claude-review.yml 추가, Anthropic API 직접 호출 | 2026-06-07 |
| #173 | 데일리 기술 질문 중복 발송 방지 — 최근 30일 질문 제외 로직 추가 (커밋 2026-06-06) | 2026-06-07 |
| #172 | Grafana Alloy 제거 + Micrometer OTLP push 전환 — OtlpConfig headers() 오버라이드로 401 수정 | 2026-06-07 |
| #165 | 모바일 코딩 에디터 CodeMirror 6 교체 (문법 강조·괄호 자동 닫기·Tab 들여쓰기) + orchestrator 훅 절대경로 수정 | 2026-06-04 |
| #163 | 모바일 코딩 에디터 스크롤·코드 잘림·키보드 미표시 수정 | 2026-06-02 |
| #162 | 메일 HTML 템플릿 개선 + dhbang.co.kr 도메인 인증 + AI 질문 프롬프트 강화 | 2026-06-01 |
| #161 | 데일리 메일 AI 질문 생성 + 발송 이력 중복 방지 | 2026-05-31 |
| #160 | 카테고리별 코딩 풀이 레이더 차트 (SVG, 9축) | 2026-05-31 |
| #158 | 코딩 로드맵 해금 기준 — 고유 문제 수(DISTINCT) 기준으로 수정 | 2026-05-30 |
| #157 | 코딩 문제 로드맵 — 카테고리별 단계적 문제 풀기 (9개 카테고리, 잠금/해금) | 2026-05-29 |
| #156 | 모바일 UX 개선 — AI 폼 다단계화, 성장 차트 탭 네비게이션, 코딩 에디터 최적화 | 2026-05-28 |
| #155 | PR 절차 문서화 — git-strategy.md Copilot 리뷰 처리 절차 추가, orchestrator 9단계 git-strategy 참조로 교체 | 2026-05-27 |
| #154 | orchestrator tools 와일드카드 변경 및 mcpServers 제거 (머지 완료) | 2026-05-27 |
| #153 | .claude 구조 개편 — skills universal/project 분리, pair-programmer 에이전트 추가, orchestrator 브랜치 규칙 명시 | 2026-05-26 |

## 다음 작업

### 코드 작업
- [x] **Grafana AI Metrics 토큰 패널 table 전환 완료** (chore/grafana-ai-metrics-dashboard-iac)
      - Input/Output Tokens + Cache Tokens 패널을 Time series → **table**로 전환,
        `sum by (evaluatorType, model) (increase(metric[$__range]))` instant 쿼리 + merge/organize transform.
        모델별 토큰 사용량 한 행으로 표시 (Input 44.8K / Output 8.40K 실측 확인)
      - Overview stat 패널 `$__range` 연동은 보류 (현행 고정 윈도우 유지 — 필요 시 별도 작업)
- [ ] client-ai Jackson 2/3 혼재 정리 (CompanyFitEvaluator, MockInterviewEvaluator → tools.jackson 마이그레이션)
- [ ] 에이전트 Disambiguation Gate / Closing Summary 미비점 보완 (Gate 횟수 상한, 트리거 기준 명시 — 실사용 경험 더 쌓은 뒤 결정)
- [ ] 기술면접 질문 뱅크(#231) 콘텐츠 확충 — `/question-bank-seed` 스킬로 반자동화됨.
      SessionStart 훅이 wiki 미처리 페이지 5건↑이면 알림 → 스킬 실행 → PR 리뷰가 큐레이션.
      (런타임 연동 아님, 빌드타임 시드만. 처리 이력: `.claude/state/question-bank-seeded.txt`)
- [ ] 질문 뱅크 category 파라미터 — 현재 DailyMailScheduler가 항상 null로 호출해 카테고리 분기가
      죽은 경로 (QA MEDIUM, 의도적 보류). 향후 카테고리별 배분 쓸 계획 생기면 활성화
- [ ] 질문 뱅크 규모 확대 시(수백 건↑) `findAllBy...` 전체 로드 방식 재검토 — `ORDER BY RANDOM() LIMIT 1`
      native query 전환 고려 (단, `@DataJpaTest` 등 native query 검증 인프라 먼저 필요)

### 사용자 확인 필요
- [x] **RESEND_API_KEY** 발급 완료 → application-local.yml 기입, Fly.io secret 세팅 완료
- [x] **JUDGE0_API_KEY** 발급 완료 → application-local.yml 기입, Fly.io secret 세팅 완료
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

### 백로그
- [ ] **Spring 시작 시간 최적화** — 현재 cold start 시 2~3분 소요, 사용자 503 경험
  - 원인: 512MB shared CPU + Neon DB cold start + Flyway 실행 겹침
  - 방향 1: `spring.main.lazy-initialization=true` (필요한 빈만 즉시 초기화)
  - 방향 2: `min_machines_running = 1` (machine 항상 warm, Fly.io 비용 발생)
  - 방향 3: Neon connection pooler (PgBouncer) 활성화 → DB cold start 제거
  - 방향 4: LogtailHttpAppender 초기화 비동기화 (현재 startup 중 60s timeout 블로킹)
- [x] devquest-log-shipper 제거 — 커스텀 Logback HTTP 어펜더 구현 완료
- [x] Disambiguation Gate + Closing Summary 에이전트 패턴 도입 (#127)
