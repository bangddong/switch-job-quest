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
| 진행 중인 트랙 | 🚧 **EKS Stage 5 (선택 과제) — 5a ✅ 2026-10-08 · 5b Karpenter ⬜ · 5c ArgoCD ⬜.** 🔴 5b 착수 전에 *세션 종료의 승인 의존*(계획서 `2026-10-07-eks-stage5a-hpa.md`)을 먼저 정한다. ⚠️ 아래 *"이관 트랙 종결"* 은 **prod 를 EKS 로 옮기는 계획**이 끝났다는 뜻이지 EKS 실습이 끝났다는 뜻이 아니다(2026-10-07 에 그렇게 잘못 옮겨 사용자가 바로잡았다). 독립 실습 클러스터는 Fly·Neon 없이 Stage 0~4b 가 전부 `✅` 다. ／ ✅ **EKS 이관 트랙 종결 — prod 이관을 하지 않는다 (2026-09-29, 📌D-015).** 간헐 세션형(apply→작업→destroy) 유지, prod 는 Fly+Neon 그대로. **📌D-002 는 반전이 아니라 확정**이고, 폐기된 것은 그 옆 「확정 전략」의 `잔액 활용` 예외 조항이다. 📌D-014(기간 한정 이관) → `❌폐기`. 🔑 **결정적 근거는 돈이 아니라 학습 잔량이었다** — Stage 0~4b 가 전부 `✅`(실클러스터 확인)이고 EKS **전용** 잔여는 **Karpenter 1개**(`선택`)뿐. 아직 못 배운 **상시 운영**은 distro 무관인데 ***정확히 그것만 예산이 못 산다***(상시는 어떤 듀티로도 불가 · 요청 베이스는 예산이 허용하는 유일한 상태가 **12~13분 콜드스타트**). **남은 것 = Stage 5(선택) · 면접 데모 며칠 · 블로그 원고.** 상세: `infra/aws-eks/README.md` D-015 · `docs/eks-cost-model.md` 「리소스 분해」「듀티 사이클」「요청 베이스」 |

> 🔴 **열린 PR 이 나오면 `gh pr view <n>` 으로 내용을 열어본 뒤 판단한다 — 제목으로 분류하지 말 것.**
> (08-04 사고: 자매 레포의 열린 PR 을 목록에서 보고도 열지 않고 "무관"으로 라벨만 붙여 이틀 방치.
> 실제로는 내가 그 직후 고친 파일을 참조하는 계획서였고, 방치되는 동안 인용 행 번호가 전부 어긋났다.)
> ***목록을 조회한 것과 검토한 것은 다르다.***

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| **#437** | **내가 #436 착수 근거로 쓴 *"재배포가 메타스페이스를 리셋한다"* 를 실측으로 반증.** **측정하지 않고 쓴 문장**이었다 — 갓 뜬 JVM(uptime 0.97일)이 **138.40 MiB = 86.5%** 라 `0` 으로 안 돌아간다. 되돌아간 양은 **1.71 MiB(8일치)**. 🔑 **숫자가 아니라 위험의 성질이 바뀐다 — creep 이 아니라 기준선이다.** 완화 ⓒ(배포 주기)는 **여전히 유효**하나(런웨이 96일) 진짜 위협은 배포 공백이 아니라 **기준선 상승**이고 그건 creep 없이 **기동 즉시** 반영된다 ⇒ 선택지 ⓐ(192m)가 creep 대응에서 **기준선 여유 확보**로 재해석. 🔴 **QA F-1(HIGH) — 정정 스윕이 반쪽. 계획서 3곳에 같은 거짓이 취소선 없이 생존**했고 ***내가 QA 프롬프트에 그 전례 3건(#427·#428 F-3·#434)을 직접 써 보내놓고 네 번째를 냈다.*** **전례를 인용하는 것과 그 전례를 피하는 것은 별개의 일이다.** 🔴 **F-2 — Δt 오류 세 번째**: `8.76일` 은 구 JVM 나이일 뿐 **신 JVM 자신의 나이 0.97일을 안 뺐다**. 정정하니 교차검증이 **오히려 강해졌다**(오차 15% → 2.5%) — 근거가 약해서가 아니라 **강한 근거를 잘못 계산**한 것. 📌 ***두 표본으로 기울기를 검증할 때는 각 표본의 나이를 먼저 적고 그 차를 Δt 로 쓴다.*** **F-3 — 내 해석도 과했다**: *"라이브러리 하나가 21.6 MiB 를 먹는다"* 는 유일 실측 전례(+4.2, confounded)의 **5배**라 삭제. **F-4 — 순서가 아니라 절을 잘못 찾았다**(메타스페이스 정정을 §6 RSS 절에). 머지된 #436 본문도 `gh pr edit` 으로 정정 — **원장에 쌓지 않고 출처를 고쳤다** | 09-27 |
| **#440** | **AWS 계정 인벤토리 정정 + 아키텍처 구성도(공식 AWS 아이콘).** 영속 리소스 합계 $1.08→**$1.14** 재검산 — 금액이 아니라 **표가 틀려 있던 것**이 문제였다(`ai-api`·`daily-api` 가 *"현재 0개"* 로 적혀 있었는데 실제 각 **2개**). 🔑 원인은 **§확인 명령이 ECR 의 개수·용량을 묻지 않아** 대조가 불가능했던 것 — ***표가 주장하는 것을 검사가 재지 않으면 그 표는 검증되지 않는다.*** 🔴 **`aws ecr list-images` 는 이미지가 아니라 *태그* 를 센다** — `core-api` 를 11개로 세어 *"lifecycle 10개 위반"* 이라고 보고할 뻔했다(`describe-images` 의 `length(imageDetails)` = 10). 표 밖에서 **고아 `/aws/lambda/test` 로그그룹**(2026-07-16, 600 B)을 발견해 **등재만** 했다. 구성도는 생성기(`docs/architecture/aws-architecture-gen.py`)를 커밋해 **재생성 바이트 동일** 확인 | 09-28 |
| **#448** | **EKS Stage 5a — metrics-server + HPA 실클러스터 확인.** Stage 5 를 5a/5b/5c 로 분리. 통과 6 · 조건부 1(늘어난 파드가 연결 재사용 탓에 80초간 유휴). 계획에 없던 발견: **콜드 JVM 이 같은 요청에 4~5배 CPU 를 써서 HPA 가 2 → 4 로 과확장.** 4번째는 `Insufficient memory` 로 Pending = 5b 출발점. 🔴 **비용 사고: 예상 $0.23 → 약 $0.59** — `tofu destroy` 호출이 승인 대기로 113분+37분 멈췄고 맥이 잠들어(🟡) 리퍼도 못 돌았다. ***세션 종료가 사람의 승인에 걸려 있다*** — 5b 전에 정할 것(계획서 `2026-10-07-eks-stage5a-hpa.md` 「이번에 하지 않는 것」). 퀴즈 1차 0/5 → 재확인 5/5, QA 3라운드 F-1~F-10 전부 문서 정확성 | 10-09 |

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
