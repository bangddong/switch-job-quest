# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | main |
| 열린 PR | 없음 |

## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #261 | 이력서 PDF 업로드 — pdfjs-dist 브라우저 파싱(dynamic import 지연 로드), 5MB 제한·스캔본 에러·50k자 자르기·덮어쓰기 confirm. **서버 파싱(PDFBox) 구현했다 폐기** — OOM 임계 상태라 서버 부하 0 방향 선택, BE 커밋은 로컬 `backup/be-pdf-parse` 보존. QA 2회, HIGH/MEDIUM 0. **머지 완료(2026-07-12), FE CD 배포 트리거됨** | 2026-07-11 |
| #259 | FE tech-debt LOW 3건 — onDelete/onStatusChange 에러 패턴 통일(Promise<void> 전환, swallow 제거), formatSavedAt invalid date 방어, 주석 보완. QA HIGH/MEDIUM 0. 머지·FE CD 배포 트리거됨 | 2026-07-10 |
| #257 | 데일리 질문 휘발형 학습(후속 질문) Phase A — POST /daily-question/explain(원본 Q/답변/피드백 컨텍스트, 단발·순수텍스트), explain 전용 레이트리밋(IP당 5회/일, 기존 2회/일 버킷과 분리 신설), DailyQuestionPage 후속질문 섹션. 게스트 허용. QA HIGH 0, MEDIUM 2 수정. CD 배포 트리거됨 | 2026-07-09 |
| #255 | 프롬프트 체계에 Finding Your Unknowns 기법 적용 — Deviations 로그(builder→QA 주입), Blindspot Pass(3.5단계), Design 다방향 모드, Gate 결정 테이블, Merge Quiz 스킬 | 2026-07-08 |

## 다음 작업

### 코드 작업
- [ ] **파이프라인 후속 (사용자 확인 대기)**: ① 모바일 실기기 확인 (데스크톱 시나리오는 Claude가
      prod 테스트 완료) ② 테스트 데이터 정리 — 회사 "테스트-토스" 삭제, 임시 이력서를 실제로 교체
- [ ] **Phase 4 후보 (실사용 후 판단)**: 면접 회고 메모(activity NOTE 타입), 같은 회사 카드
      그룹핑 뷰, JD 등록/수정 모달(현재 AddCompanyModal에서만 입력 가능), Phase 3c(JD URL 파싱)
- [ ] tech-debt(LOW): CompanyCard 삭제/상태변경 진행 중 busy 플래그(연타 중복 요청 가드, #259 QA LOW),
      FE 테스트 러너 미도입(vitest 등 — 인프라 도입 여부 별도 결정 필요, #259에서 확인),
      extractPdfText LOW 3건(#261 QA — destroy 실패 시 원 예외 덮어쓰기, `\r` 정규식 엣지,
      pdfjs 로딩/파싱 실패 메시지 미구분)
- [ ] **#261 후속**: 배포 후 실제 PDF 이력서로 추출 품질 확인(줄바꿈·표 레이아웃 깨짐 정도).
      BE 파싱(PDFBox) 구현은 로컬 `backup/be-pdf-parse` 브랜치 보존 — 스케일업 결정 시 재활용
- [ ] Phase 3a MEDIUM 보류: UserResumeAdapter upsert read-then-write 경합 — 다중 기기 동시
      사용 필요해지면 DB ON CONFLICT 전환
- [ ] **OOM 후속 관찰** (#245 swap 배포 후) — 07-10 3.3일차 실측: kill 0건(마지막 kill 07-07 00:01
      = 스왑 배포 직전, anon-rss 409MB), 스왑 소비 22~32MB/일 선형(배포 재시작 시 리셋),
      mem_available 12~47MB 바닥권 지속 → 무배포 8~10일 시 스왑 소진·재발 가능성 🟡.
      남은 확인: 4~5일차(07-11~12) creep 둔화 여부 → JVM 다이어트 조정폭 확정 후 PR 착수
- [ ] **JVM 메모리 다이어트 PR** (스왑 관찰 후 진행): 힙 상한 50%→35% + `ReservedCodeCacheSize=96m`
      + `MALLOC_ARENA_MAX=2` + `G1PeriodicGCInterval=300000` → RSS 천장 ~409→~300MB (512MB 내 근본 해결)
      리스크: 힙 피크 90MB 관측이 대표적이지 않으면(대형 AI 응답) JVM OOME — 단 컨테이너 kill보다 양성
- [ ] 에이전트 Disambiguation Gate / Closing Summary 미비점 보완 (Gate 횟수 상한, 트리거 기준 명시 — 실사용 경험 더 쌓은 뒤 결정)
- [ ] **#255 후속**: 다음 기능 작업에서 Blindspot Pass 실효성 확인 (Deviations→QA 집중검토 흐름은
      #259에서 1차 동작 확인. template 동기화는 07-10 완료 — orchestrator·clarify·quiz + 훅 스크립트 3종)
- [ ] 질문 뱅크 category 파라미터 — 현재 DailyMailScheduler가 항상 null로 호출해 카테고리 분기가
      죽은 경로 (QA MEDIUM, 의도적 보류). 향후 카테고리별 배분 쓸 계획 생기면 활성화
- [ ] 질문 뱅크 규모 확대 시(수백 건↑) `findAllBy...` 전체 로드 방식 재검토 — `ORDER BY RANDOM() LIMIT 1`
      native query 전환 고려 (단, `@DataJpaTest` 등 native query 검증 인프라 먼저 필요)

### 사용자 확인 필요
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획
- [ ] **#257 후속 — 휘발형 학습 실사용 확인**: 후속 질문 UX(단발형·5회/일 제한 적정성), AI 설명 품질.
      만족스러우면 **Phase B(축적형 복습노트)** 착수 판단 — 모르는 개념/오답 저장 → 나중에 복습(로그인·DB·간격 반복, RPG XP 연동). 지금은 보류.

### 백로그
- [ ] **Spring 시작 시간 최적화** — 현재 cold start 시 2~3분 소요, 사용자 503 경험
  - 원인: 512MB shared CPU + Neon DB cold start + Flyway 실행 겹침
  - 방향: `spring.main.lazy-initialization=true` / `min_machines_running=1`(비용) / Neon PgBouncer

### K8s 학습 진행 상태
- Stage 1 ✅ (Pod+Service e2e), Stage 2 ✅ (ConfigMap/Secret, PR #225) — 기록: `k8s/docs/stage*-learning.md`
- **다음: Stage 3** — H2 → PostgreSQL StatefulSet. 이후 Stage 4 (Ingress)
- WSL 클론: `~/switch-job-quest`, 이미지 `devquest-be:local` (`kind load docker-image`)
- local 프로파일 필수 env 3개: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET` (Secret `devquest-secrets`)
- 헬스 엔드포인트: `/health` (actuator 아님). 참고: `k8s/docs/env-requirements.md`

## 알아둬야 할 비자명적 결정

### OOM 진짜 원인 확정 — 순수 누적형 RSS 포화, kill 수위 anon-rss ~409MB (2026-07-07, #245)
#239 JVM 튜닝 후에도 재발. 커널 OOM 로그 7일 전수(8건)로 인과 최종 확정:
- **모든 kill에서 java anon-rss가 406~410MB로 동일** — 시각은 제각각(새벽·오전·오후·저녁).
  RSS가 시간당 ~3MB씩 차올라 ~409MB 도달하면 시각 무관 사망. **이벤트(메일 잡 등)는 무관** —
  8건 중 09시 메일 직후는 2건뿐, 상관관계 과대 해석 주의 (uptime 리셋 목록은 배포와 섞임)
- creep 정체: JVM이 커밋해둔 페이지(총 314MB)를 서서히 실제 터치 + native ~95MB
  → RSS 천장 = 커밋총량+native ≈ 409MB = kill 수위. JVM 지표(used/committed)로는 안 보임
- 부팅 직후 가용 마진 ~44MB뿐 (총 459MB). 사망 시점 HTTP/AI 요청 항상 0건 — 서비스 영향은
  회당 3~5분 503 창 + "AI 평가 도중 kill" 잠재 시나리오뿐
- 대응 1차: fly.toml `swap_size_mb = 256` (#245 머지·배포). creep이 스왑도 채우면(+3.5일) 재발
  가능 → **업타임 4~5일째 `fly_instance_memory_swap_free` 관찰**
- 대응 2차: JVM 메모리 다이어트 (다음 작업 참조). 최후 수단: 1GB 스케일업 (~$5.7/월)
- 조사 방법 메모 (재조사 시 그대로 재사용):
  - Grafana 스택/fly-metrics.net 페이지에서 `fetch('/api/datasources/proxy/uid/<uid>/api/v1/query_range?...')`
    — 스크린샷 없이 PromQL 수치 직접 획득. fly 쪽 uid `prometheus_on_fly`, 앱 스택 uid `grafanacloud-prom`
  - 머신 RSS: `fly_instance_memory_mem_available{app="devquest-api"}` (JVM 지표에 안 보이는 creep 관측)
  - 재시작 전수: `process_uptime_seconds` 리셋 감지 (단, 배포·autostop 섞임 — OOM 확정은 커널 로그로)
  - 커널 OOM 로그: fly-metrics.net `application_logs_vlog` datasource,
    LogsQL `"fly.app.name":"devquest-api" "Out of memory: Killed process" _time:7d` → anon-rss 수치 확인

### AI 메트릭 대시보드 "0으로 보임" — 버그 아님, `increase()` 콜드스타트 특성 (2026-07-01)
- 저트래픽 앱에서 `increase()` 기반 패널은 원래 0으로 보임 (신규 시리즈 콜드스타트 + 증가량 없음).
  raw 카운터를 Explore에서 `increase()` 없이 직접 쿼리하면 실제 값 확인 가능. **재발해도 코드 재조사 불필요**
- AI 카운터(`gen_ai_*`, `ai_*`)는 **첫 AI 호출 때 lazy 등록** — 재시작 후 호출 없으면 시리즈 자체가 소멸
  (JVM 메트릭은 기동 시 즉시 재등록되어 남음). 시리즈 부재 = "그 구간 AI 호출 0건"의 증거로 쓸 수 있음
- OTLP resourceAttributes에 instance 라벨 없음 — 멀티 머신 되면 시리즈 충돌 (1대라 무해, 스케일아웃 시 필수 수정)

### Grafana 대시보드 = 신형 v2 스키마 + table 패널의 `format: "table"` 필수 (2026-07-02)
- 라이브 대시보드는 신형 v2 스키마(`kind: Panel`, `elements` 맵, `RowsLayout`). IaC 소스는
  레포 `grafana/ai-metrics-dashboard.json` (신형으로 동기화됨)
- table 패널: `instant: true`만으론 부족, **`format: "table"`** 필수 — 빠지면 기본 `time_series`라
  라벨 컬럼이 안 나오고 merge/organize transform 깨짐
- 대시보드 편집 시 자동 새로고침 OFF 필수 (미저장 편집 삭제 + 렌더러 OOM/CDP 프리즈) —
  파일의 `timeSettings.autoRefresh`는 `""`(off) 유지

### Spring Boot 4.x Flyway 수동 구성
SB 4.x에서 Flyway auto-configuration 제거됨. `db-core`에 `FlywayConfig.kt`로 수동 구성:
- `@Profile("prod")` / `flyway.repair()` → `migrate()` 순차 호출 (flyway 11.x에서 repairOnMigrate 제거)
- `EntityManagerFactoryDependsOnPostProcessor("flyway")`로 JPA 초기화 순서 보장
- 패키지: `org.springframework.boot.jpa.autoconfigure` (SB 4.x 변경)
- ⚠️ 마이그레이션 작성 전 `core-api`·`db-core` **두 디렉토리 전부** 버전 확인 (V8 충돌 prod 다운 사고,
  상세는 archive — be-ci.yml 린트가 자동 차단)

### mneme wiki ↔ 앱 데이터 관계 (런타임 연동 아님)
`E:/development/wiki/`(mneme LLM wiki)는 로컬 개발머신 전용 — 앱 반영은 **빌드타임 시드**만
(사람 큐레이션 → Flyway 마이그레이션/정적 리소스). 런타임에 앱이 mneme 호출하는 구조 금지.
유사 패턴: `client-ai/support/ConferenceReferenceLoader` + `conference-references.json`

### Controller 테스트 패턴
`standaloneSetup` + `@AuthenticationPrincipal` 조합 시 반드시:
```kotlin
.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
// + @BeforeEach: SecurityContextHolder.getContext().authentication 설정
// + @AfterEach: SecurityContextHolder.clearContext()
```

### 동일 파일 수정 스프린트 — 직렬 순서 필수
두 스프린트가 같은 파일을 수정하면 병렬 브랜치 금지. 앞 PR 머지 후 다음 브랜치 생성.
(BE↔FE 다른 파일이면 병렬 OK)

### Observability 최종 상태
- Sentry: SB 4.x 미지원으로 포기 (PR #52)
- Grafana Cloud Loki: loki4j 1.6.0, `GRAFANA_LOKI_URL` 빈값이면 자동 비활성화
  - `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_INSTANCE_ID` fly secret 주입, `GRAFANA_API_KEY`는 메트릭과 공유
  - Grafana Cloud용 `maxBytes=65536`, `requestTimeoutMs=15000`

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control: `claude --agent orchestrator --remote-control` (직접 터미널 실행)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
