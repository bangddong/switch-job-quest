# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요. **여기에는 파생 불가능한 것만 둔다.**
> 전체 이력 `.claude/CONTEXT.archive.md` · 주제별 상세는 맨 아래 「참조 문서」.

## 현재 상태

> 🔴 **브랜치·열린 PR·크레딧 잔액을 여기 적지 않는다.** 파생 가능한 것을 복사하면 **반드시** 썩는다.
> PR 상태는 자기참조라 원리적으로 맞출 수 없었고(마지막에 적히는 `머지 대기` 가 머지 순간 거짓이 된다),
> 그 탓에 CONTEXT 수정만이 목적인 "클린 클로즈" PR 이 **24건** 쌓였다. 크레딧도 같은 이유로 뺐다 —
> `$2.936` 이라 적혀 있었는데 #426 미반영으로 이미 틀려 있었다(2026-09-18 발견).
> ```bash
> bash .claude/scripts/session-status.sh   # 브랜치·PR·원장·EKS 마커·영속 리소스
> aws budgets describe-budgets --account-id <id>   # 크레딧 (무료)
> ```

| 항목 | 내용 |
|------|------|
| 진행 중인 트랙 | 🛑 **없음 — 서비스 종료 (2026-11-06), 레포 보관.** 사용자: *"많은 시행착오 덕분에 너무 산으로 간 것 같아"* → 범위 = 「서비스 종료 + 레포 보관」. 삭제: Cloudflare Worker·KV·DNS 4건, Fly 앱·WireGuard, EC2 prod DB·EBS, 1-network 전체, 0-bootstrap 의 ACM·EventBridge·ECR·백업 S3·로그 그룹·학습 EBS, SSM `/devquest/prod/*`. **유지**(AWS 계정·크레딧은 후속 보일러플레이트 트랙이 쓴다): tfstate S3·DynamoDB, OIDC 역할, Budgets, Cost Anomaly. 최종 DB 덤프는 **레포 밖** 로컬 보관(개인정보 포함 — 레포 금지). 절차·실측은 `docs/eks-migration-log.md` 「2026-11-06 — 서비스 종료」, 남은 사용자 작업은 `.claude/TASKS.md` TASK-25. 직전 트랙(prod 평일 창, 📌D-016) 서술은 `CONTEXT.archive.md` 「종료 직전 트랙」 |

> 🔴 **열린 PR 이 나오면 `gh pr view <n>` 으로 내용을 열어본 뒤 판단한다 — 제목으로 분류하지 말 것.**
> (08-04 사고: 자매 레포의 열린 PR 을 목록에서 보고도 열지 않고 "무관"으로 라벨만 붙여 이틀 방치.
> 실제로는 내가 그 직후 고친 파일을 참조하는 계획서였고, 방치되는 동안 인용 행 번호가 전부 어긋났다.)
> ***목록을 조회한 것과 검토한 것은 다르다.***

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| **#524** | **B7-21 ⓐ OSIV OFF + prod DB 타임아웃(L-79 해소).** 실측: OSIV ON 이면 코딩 퀘스트 LLM·Judge0 대기 내내 커넥션 1개 점유(#516 뒤에도) → OFF 로 0, 기존 테스트 450 실패 0. prod `connection-timeout` 5s·`validation-timeout` 2s·`connectTimeout=5&socketTimeout=30` — 블랙홀 시 레이트 리밋 대체 30s→**5s**. 구조 가드(JPA 연관·엔티티 유출 0)를 CI 가 지킨다. 🔴 머지 = Fly 재시작 → 창 꺼진 시간에. 머지 후 Fly 스모크(suspend 재개 첫 요청 — L-82) | 11-03 |
| **#523** | **창 중 ai-api 실제 AI(B8-25 ⓐ).** 창 overlay 에서만 ai-api 스텁 해제(`test`+`replace`) + 전용 ES `ai-api-anthropic`(SSM EKS 전용 키) + 576Mi. 키 부재는 eks-window apply 전·on.sh 전환 전 이중 검사. D-008 🔄부분무효(base·학습 경로 스텁 유지). 🔴 EKS→Anthropic 실호출 0회 — 첫 창에서 채점 1회·노드 메모리(B8-25-1). 매니페스트만이라 Fly 재시작 없음 | 11-02 |
| **#522** | **Fly 유휴 일시정지(D-017) — Fly 10월 nrt 단가 인상(CPU +13%·RAM +20%)으로 상시 1대가 31일 달 $5.07 청구.** `min_machines_running=0`·suspend·**768MB·swap 제거**(suspend 조건). 09:00 메일은 앱 cron 제거 → **Cloudflare Worker Cron → Fly `POST /internal/jobs/daily`**(신규 비밀 `DEVQUEST_JOB_SECRET`, fail-closed). 🔴 **머지 후 TASK-24 rotate 안 하면 다음 09:00 메일 누락.** 대가: 쉬고 난 첫 요청 ≈3초 · stop 상태면 콜드 부팅 2~3분(기동 최적화 백로그 다시 유효) | 11-02 |

## 다음 작업

> 🛑 **서비스 종료(2026-11-06)로 아래 백로그는 전부 동결이다** — 기록으로만 남긴다.
> 후속은 **새 레포의 AI 연동 Kotlin 보일러플레이트**(하이브리드 학습: M0 골격 → M1 도메인·포트 → M2 AI 코어 TDD →
> M3 평가기 1개 → M4 관측·레이트리밋 → M5 ai-api 분리, 마일스톤마다 퀴즈). 재사용 후보 분석 = 2026-11-05 세션
> (A등급: `BaseAiEvaluator`·`AiCallExecutor`·`BaseAiHttpAdapter`·AiCallLog·`SharedRateLimiter`·스텁 패턴, `fe/worker/`).

### 제품 백로그 (이 파일이 유일 출처)

- **Phase 4 후보 (실사용 후 판단)**: 면접 회고 메모(activity `NOTE` 타입) · **같은 회사 카드 그룹핑 뷰** ·
  JD 등록/수정 모달(현재 `AddCompanyModal` 에서만 입력 가능) · **Phase 3c(JD URL 파싱)**
  (코드 확인: 전부 미구현. `.claude/docs/ux-retention-plan.md` 는 **다른 기능 세트**라 대체 출처가 아니다)
- 🔴 **메타스페이스 — 위험은 creep 이 아니라 기준선이다** (2026-09-27 실측)
  ```
  갓 뜬 JVM  138.40 MiB / MaxMetaspaceSize 160m = 86.5%   여유 21.60 MiB   런웨이 ≈96일
  동일 JVM 안 +0.225 MiB/일 · 배포가 되돌리는 양은 8일치(1.71 MiB)뿐
  ```
  **컨테이너 `limits` 로는 안 고쳐진다**(`-XX:MaxMetaspaceSize` 는 별개). 닿으면 2026-07-14 와 같은 경로
  (`Pause Full (Metadata GC Threshold)` 무한 반복 → `OOME: Metaspace` → prod 다운).
  진짜 위협은 배포 공백이 아니라 **의존성·빈 추가로 기준선이 오르는 것** — creep 없이 **기동 즉시** 닿는다.
  선택지 ⓐ상한 192m(= 기준선 여유 확보) ⓑ증가 원인 규명 ⓒ배포 주기 **보장**(지금 보장 장치 **없음**).
  **미결정.** ⚠️ ⓐ는 해결이 아니라 유예다.
  > 정정 이력 2건(*"재배포가 리셋한다"* · *"#361 이후 feat 0건"*)과 Δt 교차검증·표본 전문은
  > `docs/jvm-observability-notes.md` §1. **CONTEXT 80줄 규칙(원장 L-10) 때문에 여기서는 현재 상태만 둔다.**
- **#261 후속 — 실제 PDF 이력서로 추출 품질 확인** (줄바꿈·표 레이아웃 깨짐 정도).
  ⚠️ 종전 서술의 *"BE 파싱(PDFBox) 구현은 로컬 `backup/be-pdf-parse` 브랜치 보존"* 은 **무효다** —
  `git branch -a` 실측 **0건**(머신이 Windows→macOS 로 이동하며 소실). **재활용 계획의 전제가 없다.**
- **`CodingQuestService` 트랜잭션 재배치 보류** (#308 MEDIUM) — `generateProblem`/`submitCode` 의
  `@Transactional`(`:88`·`:160`)이 **AI 호출을 트랜잭션 안에 안는다**. 2026-09-18 코드 확인: 그대로.
  🔑 원장이 *"CONTEXT 소유"* 로 지정한 항목이다(`review-ledger.md` 「이미 다른 곳에」) — **여기서 지우면 고아가 된다**
- **`UserResumeAdapter` read-then-write** (Phase 3a MEDIUM) — `findByUserId` → 분기 → `save` 라
  동시 요청에 경합. `ON CONFLICT` 아님. 2026-09-18 코드 재확인, 원장·계획서 0건
- **Spring 시작 시간 최적화** — cold start 2~3분에 사용자 503. 🔴 **11-02 부터 다시 유효**: Fly 가
  `min_machines_running = 0`·suspend 로 바뀌었다(비용 — D-016 계획서 「Fly 유휴 일시정지」, D-017). suspend 복원은 빠르지만
  **stop 상태(배포·시크릿 교체 직후)면 콜드 부팅**. 남은 대책 `spring.main.lazy-initialization`(0건) · PgBouncer. 현상은 **재측정된 적이 없다** — 배포 후 첫 측정

### 🕒 이연 트랙 — AWS 신규 프리티어 계정 생성 시 k3s (2026-09-29, 📌D-015)

**EKS 에서 배운 것을 k3s 로 손수 해본다.** 사용자 결정 — *"추후 AWS 신규 프리티어 계정 생성시에는"*.

| | |
|---|---|
| 왜 매력적인가 | **컨트롤플레인이 $0** → t4g.small ×1 + IP = **$18.8/월 = 8.7개월** ⇒ **상시 가동이 예산에 들어온다.** 지금 EKS 로는 못 사는 **상시 운영 학습**(모니터링·롤아웃·장애 대응·가동률)과 **살아있는 포트폴리오 URL** 이 열린다 |
| 왜 지금 안 하나 | 돈이 아니라 **전환 비용** — `infra/aws-eks/`·`docs/eks-tutorial-steps.md`(2,400행)·`assert-eks-quiz.sh` 가 **전부 EKS 전제**라 재사용이 아니라 새 트랙이다 |
| 잃는 것 | IRSA · ALB Controller · 관리형 노드그룹 · EBS CSI · "EKS" 라는 이력서·블로그 문구 · HA(단일 EC2) |
| 얻는 것 | 컨트롤플레인을 **직접** 세우는 경험 · 상시 URL · distro 무관 지식의 재확인 |
| 미검증 전제 | HTTPS 를 cert-manager+Let's Encrypt 또는 Cloudflare 프록시로 붙이는 것(둘 다 $0, ⚪ 미검증) |

> ⚠️ **`kind` 07-16 폐기 사유가 *"돈이 제약이 아니다"* 였다 — 상시 운영에서는 뒤집힌다.**
> 착수 시 `design-change-procedure.md` 전 단계 (기각했던 선택지의 재채택).

### 질문 뱅크 — category 필터 보류 🔴 재조사 불필요 (2026-07-27 결정 · 09-18 코드 재확인)

***선행 조건 = 뱅크 보강. 그 전엔 활성화가 개선이 아니라 퇴보다.***

- **현상**: `TechQuestionBankPort.findUnused(exclude, category = null)` 의 category 가 프로덕션에서 항상 null.
  → `TechQuestionBankAdapter` 의 4분기 중 **category 2분기는 테스트만 밟는 죽은 경로**.
  실제 동작 = 전 카테고리 균등 랜덤 1개를 전 사용자에게 동일 발송.
  🔴 **호출부는 `DailyQuestionContentService.kt:91·118` 2곳이다** (2026-09-18 정정 —
  종전 서술 *"`DailyMailScheduler.kt:45` 한 곳"* 은 Task 2.1 이관 전 기준이라 **틀렸다**).
- 🔴 **보류 근거(실측): 데이터가 카테고리를 감당 못 한다.** 뱅크 총 **26개**(V10 5 + V11 21) —
  `java-spring` 8 · `system-design` 6 · `database` 6 · `concurrency` 5 · **`ai-llm` 1(4%)**.
  `ai-llm` 이 1개뿐이라 로테이션을 켜면 그날 소진 → `randomOrNull()`=null → **AI 폴백**.
  ***지금 켜면 AI 호출(비용)이 오히려 늘어난다.***
- **활성화 트리거**: **카테고리당 최소 10개** 확보(특히 `ai-llm`·`concurrency`).
  ⚠️ 종전의 *"V12 시드로"* 는 **무효** — V12 는 `create_user_resume` 로 이미 소진됐다(09-18 확인).
- **기각한 대안**: ①죽은 파라미터 제거 → 보강 계획이 살아 있어 재작업 유발 ②로테이션 즉시 도입 → 위 사유
- 🔑 **중복 방지 윈도우가 20일인 이유 = 뱅크 26개보다 작아야 AI 폴백이 안 돈다**(#333).
  **뱅크를 늘릴 때 이 결합을 반드시 함께 본다.**
- [ ] 뱅크가 수백 건이 되면 `findAllBy...` 전체 로드 → `ORDER BY RANDOM() LIMIT 1` 전환 검토
  (2026-09-18 확인: 어댑터가 여전히 `findAll()` → `randomOrNull()`)

## 알아둬야 할 비자명적 결정

### 🔴 `V11__seed_tech_question_bank_202607.sql`의 `E:/` 주석은 **건드리지 마라** (2026-08-09)

레포 전역의 `E:/development/wiki` 하드코딩을 걷어낼 때 이 파일 1행의 주석만 남겼다.
**Flyway 체크섬은 파일 내용(주석 포함)으로 계산**되므로 한 글자만 바꿔도 이미 V11이 적용된
DB에서 `validate`가 깨진다. prod에 적용돼 있고, #364 이후 CI도 실제 Postgres에 마이그레이션을
돌린다. **경로 정리를 하다 이 주석을 "마저 고치고 싶어지는" 순간이 반드시 오는데, 그게 함정이다.**

정 고치려면 마이그레이션 파일이 아니라 `question-bank-seed` 스킬 문서에 적을 것.

### mneme wiki ↔ 앱 데이터 관계 (런타임 연동 아님)
**mneme LLM wiki**(`$WIKI_DIR`, 이 기기 `~/Develop/Sources/llm-wiki`)는 로컬 개발머신 전용 — 앱 반영은 **빌드타임 시드**만
(사람 큐레이션 → Flyway 마이그레이션/정적 리소스). 런타임에 앱이 mneme 호출하는 구조 금지.
유사 패턴: `client-ai/support/ConferenceReferenceLoader` + `conference-references.json`

### Controller 테스트 패턴 → `.claude/agents/be-feature-builder.md:273`·`qa-reviewer.md:244`
### 동일 파일 수정 스프린트 — 직렬 순서 필수
두 스프린트가 같은 파일을 수정하면 병렬 브랜치 금지. 앞 PR 머지 후 다음 브랜치 생성.
(BE↔FE 다른 파일이면 병렬 OK)

### Observability 구성 → `.claude/TASKS.md`(Sentry/Logtail) · `k8s/README.md:316-330`(Loki 환경변수)
### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control: `claude --agent orchestrator --remote-control` (직접 터미널 실행)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원

### 아티팩트 (레포 밖 자원 — 지우면 복구 불가)

- Phase 1 브리핑 https://claude.ai/code/artifact/244a74dd-e7a4-4d62-a0e1-5eb5a4668e45
- Phase 0 회고 https://claude.ai/code/artifact/8d702047-0184-4743-b89d-4f085b8644bc
- 목표 아키텍처 https://claude.ai/code/artifact/ffe35a97-ee42-4412-b85c-2716e8b59a14

## 참조 문서

| 주제 | 위치 |
|------|------|
| **블로그 원고 개요** (형태 A/B/C — **형태 미정**) | `docs/superpowers/plans/2026-09-29-blog-outline.md` |
| **EKS 비용 상수·전략·Free Plan** | `docs/eks-cost-model.md` |
| **JVM·관측 현장 노트** (메타스페이스·GC·OOM·Grafana·flyctl) | `docs/jvm-observability-notes.md` |
| **EKS 결정 기록 (📌 D-001·D-002·D-004)** | `infra/aws-eks/README.md` 「결정 기록」 |
| **실패 6종 정의** (23곳이 번호로 참조) | `infra/aws-eks/PERSISTENT-RESOURCES.md` |
| 선행 조건 7건 · 착수 설계 | `docs/superpowers/plans/2026-09-11-prod-eks-migration-prereqs.md` |
| 서비스 분해 (D-003) | `docs/superpowers/specs/2026-07-20-service-decomposition-design.md` · `plans/…phase01.md` · `…phase02.md` |
| EKS 세션 절차 (과금) | `docs/eks-session-sop.md` |
| EKS 정답 경로 · 작업 일지 | `docs/eks-tutorial-steps.md` · `docs/eks-migration-log.md` |
| 미해결 QA 지적 | `.claude/review-ledger.md` |
| 사용자 직접 실행 | `.claude/TASKS.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
