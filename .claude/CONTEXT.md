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
| 진행 중인 트랙 | 🚧 **prod 평일 창 운영 (📌D-016, 2026-10-16 착수 — 계획 단계)**: 창 동안만 EKS 가 prod 서빙 · 그 외 Fly · **켜기 = 수동(`workflow_dispatch`)만 · 끄기 = 18시 cron + 수동**(사용자 Q-3). ✅ **선행 조건 Q-1 「학습 문서 정리」 = 튜토리얼 재현 검증 완료 2026-10-16**(Stage 4·5a~5f, 3세션 ≈ $0.35, 결함 8건 수정) → 클러스터를 prod 전용으로 전환해도 된다(간헐 학습 세션 종료). ✅ **순서표 1a(Flyway 공유 DB 안전 — BE+Fly) 2026-10-18 배포**: 마이그레이션은 Fly `release_command = "migrate"`(core-api jar 전용 모드), 앱 기동 시 OFF, **자동 `repair()` 제거**(`java -jar app.jar repair` 수동만). EKS 는 prod(external) 모드에서 기동 시 migrate **꺼짐**(5-B — Fly `release_command` 가 유일한 실행자, 1b 는 학습 in-cluster 모드에만). ✅ **순서표 2(이미지 공급망) 2026-10-19 (#461)**: ECR 엔 main 커밋 이미지만, `latest` 없음, IMMUTABLE. 배포 태그 정본 규칙은 `eks-tutorial-steps.md` 1-1(`--first-parent`). ✅ **순서표 3(CI 역할) 2026-10-19 (#464·#465·#466)**: 쓰기 = main 전용 역할, PR plan = 좁힌 읽기 전용(0-bootstrap state 의 `random_password` 평문 노출은 감수 — 쓰기 권한자 1명), access entry = `bootstrap-admin` + main 역할. 🔴 **PR 역할을 다시 넓히거나 entry 에 넣지 마라.** 가드 재정의(N-3)는 8번으로. 🚧 **순서표 5(DB 층) 진행 중**: 5-0 ✅ · 5-A ✅(#469, 플래그 꺼짐) · 5-B ✅(#470, `db_mode=external` + `apps-external`) · 다음 = **5-C 운영 문서·안전장치**(SOP §8b destroy 게이트 태그 필터, PERSISTENT-RESOURCES, 비용 재산정, D-004·D-016·`secrets.tf` 원칙 개정, 튜토리얼 db_mode 서술). 🔴 **`db_enabled=true` 머지 = 상시 과금 시작 — 6번 직전 별도 승인.** Fly `DB_HOST` 는 피어 IPv6 리터럴(이름 해석 불가). 4번(리퍼·마커)은 8번 전까지. 계정 밖 사본 = Neon 무료 콜드 대기(Q-2) · DB = Neon 이탈 → **EC2 t4g.micro 자가 운영 Postgres**(상시, Fly·EKS 공유). 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` — **복구 리허설 통과 전 첫 창 금지**, **01-15 전 탈출 경로**(Free Plan 종료 → 90일 뒤 계정·리소스 삭제). 📌D-015 → `❌폐기`(09-29 사용자 발화를 내가 "prod 안 옮김"으로 잘못 읽었을 수 있다 — 확인 없이 결정으로 적음). ／ EKS Stage 0~5f 전부 ✅(5f 2026-10-16). 🔴 **metrics-server 애드온은 `eksbuild.11` 고정**(`.12` = openapi protobuf 503). 세션 첫 관문 = ArgoCD 설치 전 protobuf `/openapi/v2` 200. 🔴 **tofu 는 `cd … && tofu apply` 형태로만, apply 직후 `cat .claude/eks-session/active` 로 시각 확인**(원장 L-70). 세션 종료 승인 대기는 **사용자가 지켜보는 시간에만 진행**(5c D15). ／ Fly 메타스페이스 160m 중 140.79 MiB(88%), swap 하루 ~5 MiB 증가(10-16 실측) — 192m 상향 대기(Fly 는 밤·주말 서빙을 계속하므로 유효) |

> 🔴 **열린 PR 이 나오면 `gh pr view <n>` 으로 내용을 열어본 뒤 판단한다 — 제목으로 분류하지 말 것.**
> (08-04 사고: 자매 레포의 열린 PR 을 목록에서 보고도 열지 않고 "무관"으로 라벨만 붙여 이틀 방치.
> 실제로는 내가 그 직후 고친 파일을 참조하는 계획서였고, 방치되는 동안 인용 행 번호가 전부 어긋났다.)
> ***목록을 조회한 것과 검토한 것은 다르다.***

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| **#467·#468** | **📌D-016 순서표 5 계획 + 5-0 실측.** Fly→DB = **Fly 6PN 에 EC2 를 WireGuard 피어로**(사용자 — 고정 IP $3.60 현금 기각), 비번 = SSM SecureString·EC2 생성(Secrets Manager 는 회전을 Fly write-only 시크릿 때문에 못 켜서 기각), TLS require. 실측: 인바운드 0개로 Fly→EC2 Postgres 접속, RTT 29.7ms. 🔴 **Java(musl)는 `._peer.internal` 을 못 푼다**(A 질의 NXDOMAIN) → `DB_HOST` = 피어 IPv6 리터럴. Neon = PG 17·싱가포르 | 10-19~20 |
| **#469** | **5-A — prod DB IaC, `db_enabled` 기본 false**(머지해도 생성 0). Ubuntu arm64 t4g.micro · 데이터 EBS 분리 · SG `vpc_cidr`:5432 만 · 비밀 0 인 멱등 user_data. **일회용 리허설에서 볼륨 재부착 실패 실측**(Ubuntu 는 설정을 루트 볼륨에 둔다) → 설정도 볼륨으로. QA 3라운드 HIGH 1 포함 전부 fixed | 10-20 |
| **#470** | **5-B — EKS 를 외부 prod DB 에 배선.** `db_mode=external`(기본 in-cluster 유지) · 좌표는 Secrets Manager, **비번은 SSM 에서만**(어떤 state 에도 없음) · 오버레이 `k8s/gitops/apps-external`(postgres·`postgres-tls` 삭제, **core-api 기동 migrate 끔** — prod migrate 는 Fly `release_command` 하나 ⇒ 1b 불필요 재판정). 🔴 tfsec ignore 를 `dynamic` 위에 달면 무시되고, 기본 모드에선 블록이 비어 **평가조차 안 한다** — `--tfvars-file` 로 검사 | 10-20 |
| **#458** | **EKS 튜토리얼 재현 검증 Stage 4·5a~5f — 문서만 보고 전부 통과.** 3세션 ≈ $0.35(계획 $1.2 의 30%), 결함 8건 수정(그중 2건은 문서만으로 진행 불가: daily-api 배포 단계 부재, LBC 가 1노드에서 core-api **선점** → 4-0b 신설). 5b-7(Karpenter 노드 살린 채 destroy → 정리 스크립트가 실제 EC2 종료) 첫 실물 확인. 📌D-016 선행 조건 Q-1 충족. 퀴즈 1차 부분 2·오답 1 → 재확인 3/3, 약점 *"조건 vs 원인"*·*"재현 = 문서 검증"* | 10-17 |

## 다음 작업

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
- **Spring 시작 시간 최적화** — cold start 2~3분에 사용자 503.
  ⚠️ `min_machines_running = 1` 은 **이미 적용됨**(`be/fly.toml:28`). 남은 것은
  `spring.main.lazy-initialization`(0건) · PgBouncer. 현상 자체는 그 이후 **재측정된 적이 없다**

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
