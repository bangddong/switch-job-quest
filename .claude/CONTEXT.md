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
| 진행 중인 트랙 | **`prod → EKS 상시 운영` 선행 조건** (**D-013**, `plans/2026-09-11-prod-eks-migration-prereqs.md`). 구축(Stage 0~4b)은 끝났다. **1 백업·복구 ✅ · 2a 시크릿 환경 축 ✅ · 2b 재판정 해소 ✅ · 3 HTTPS ✅(#426) · 4 RSS creep ✅$0 실측(#428)**. 🔴 **5 는 선행 조건에서 빠졌다 (09-19)** — *실 AI 경로 검증(스텁 해제)* 이 **📌D-008(`✅유효`, *"ANTHROPIC_API_KEY 를 학습 클러스터에 넣지 않는다"*)과 정면 충돌**한다. 클러스터는 하나이고 선행 조건은 이관 **전**에 태워야 하므로 그 클러스터는 학습용 → 원문 실행 = D-008 반전. 우회로(*별도 예산 키*)는 D-008 이 **이미 기각**해뒀다. 🔑 ***D-008 의 근거는 이관 전에는 해소 불가, 이관 후에는 적용되지 않는다*** → **이관 직후 첫 작업**으로 이동(B-6·B-11·B-16 과 같은 취급). → 🔴 **다음 = 6 EKS CD 파이프라인**($0~0.1). 그다음 7 안전장치 개정($0) → **블로그 원고($0)**. ⚠️ **항목 4 잔여($0)**: creep 기울기 재측정(`fly ssh console` 며칠 반복 — **스케줄러 발화 전후로 갈라 찍을 것**, 메타스페이스 +4.2 MiB 가 알려진 일일 점프와 크기가 같다) + 대응 선택지 결정(①limits 768Mi ②NodeSwap ③creep 해결 ④1GB 인스턴스). ⚠️ Stage 5(HPA·ArgoCD)는 README 가 "선택". 🔑 **규모 감각**: 첫 답(기술 2~3주)이 틀렸고 Blindspot 22건 중 6건이 *"이 계획은 성립하지 않는다"* 수준이었다 — 실제 **2~3개월**. |

> 🔴 **열린 PR 이 나오면 `gh pr view <n>` 으로 내용을 열어본 뒤 판단한다 — 제목으로 분류하지 말 것.**
> (08-04 사고: 자매 레포의 열린 PR 을 목록에서 보고도 열지 않고 "무관"으로 라벨만 붙여 이틀 방치.
> 실제로는 내가 그 직후 고친 파일을 참조하는 계획서였고, 방치되는 동안 인용 행 번호가 전부 어긋났다.)
> ***목록을 조회한 것과 검토한 것은 다르다.***

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| **#429** | **선행 조건 5 사전 조사 — $0 으로 2건을 종결했다.** 항목 4 의 교훈(*"$0 확인을 먼저 하니 항목 정의가 틀려 있었다"*)을 절차로 굳혀 **착수 전에** 조사했다. 🟢 **메일 안전**: `MAIL_ENABLED:false` 기본값 + `JavaMailSender` 사용처 1곳 + 호출자 1곳, 둘 다 가드 → *"학습 클러스터가 실사용자에게 메일을 보낸다"* 는 **현재 코드에서 발생 불가**. 🟢 **egress 종결**: NetworkPolicy 전수 1개이고 `policyTypes: [Ingress]` 뿐 → **아웃바운드 제약 없음**. 🔴 ***내가 착수 설계에서 "default-deny 라 이건 진짜 새 질문" 이라고 쓴 것이 틀렸다 — 그 default-deny 는 Ingress 방향이다. 방향을 안 보고 단어만 가져왔다.*** 🔴 **QA F-1 — 세는 방법이 틀렸다**: 무스텁 AI 포트를 `find -name '*Evaluator*'` 로 세어 **15**라 했는데 실제 **16**이다. `JourneyReportGenerator.kt` 가 이름 규약을 벗어나 있어 안 걸렸다(진짜 Anthropic 호출자다). 올바른 법은 **타입으로 세는 것**: `grep -rl ': AiEvaluatorPort'` → 17, 스텁 1 제외 16. 🔑 ***이름 기반 검색은 "이름이 다른 구현체가 없다"를 전혀 배제하지 못하고, 규약을 벗어난 항목이야말로 누락되기 쉬운 것이다*** | 09-19 |
| **#428** | **선행 조건 4 재정의 — 메타스페이스가 아니라 RSS creep 이었다. $0 실측.** 항목 이름이 틀렸다: 메타스페이스 누수는 **2026-07-15 근거 3종으로 종결**이고 같은 문서가 *"두 리스크는 별개"*(0.73 vs 3 MB/h)로 명시 판정해뒀다. `README:24` 의 **조사 이전 프레이밍**을 계획서가 현재형으로 옮긴 것 — **2b 와 같은 형태**. 💰 **$7~10 을 $0 으로 바꿨다**: *"2.3일 뒤 OOMKill"* 실측엔 2~3일 과금이 필요했는데 **prod 가 이미 답을 갖고 있었다**(`fly ssh console`, 업타임 70.8h, 실트래픽) — RSS 386.7 · swap 32.6/256 사용 · 가용 RAM 26.6 MiB. **kill 이 안 난 이유 확인**: RAM 이 바닥나자 swap 으로 나갔다 = `swap_size_mb=256`(#245)이 **유일한 방어선**이고 이관하면 사라진다. 🔑 **B-5 근거 교체**: *"swap 이 없다"* → **"Fly 앱 상한 409 < K8s limits 576 이라 swap 을 뺀 총량이 역전"**(665 vs 576 = 89 MiB 적다). 🔴 **QA F-1 이 내 방법론 오류를 잡았다** — 처음엔 `459+256=715 vs 576 = 139` 으로 썼는데 **459 는 VM 총량, 576 은 컨테이너 한계로 층위가 달랐다.** ***같은 문서에 앱 상한(anon-rss 409, 커널 로그 8건)이 있었는데 옆 칸을 봤다. 실측값은 맞았고 비교 대상이 틀렸다.*** 🔴 **F-3·F-7 은 #427 이 만든 내 부채**였다(깨진 포인터 4곳 + 탈락 문장 1줄) — *"#427 유실을 고쳤다"* 고 보고했는데 **하나만 고친 것**이었다. 🔑 **QA 부수 발견**: 메타스페이스 상승분 **4.2 MiB** 가 이미 기록된 *"DailyMailScheduler 일일 점프 +4.2 MiB"* 와 **크기가 정확히 같다** → *"작동점이 올랐다"* 와 *"알려진 점프의 재관측"* 을 **구분할 근거가 없다**(재측정 시 스케줄러 전후로 갈라 찍을 것). 📉 **라운드 추이 6→1→0** — L-40 의 원인(*"설명은 곧 새 검증 대상"*)을 의식해 **주장을 줄이는 방향**으로 고쳤다. ⚠️ **남은 것**: creep 기울기 재측정($0) + 대응 선택지 결정. ④1GB 인스턴스(Fly $5.7/월)가 **prod 를 Fly 에 두는 한 가장 싸서 이관 동기를 약화시킨다** — ***선행 조건을 파니 "이관을 왜 하는가"로 되돌아왔다. 목적은 비용이 아니라 운영 경험이다*** | 09-18 |
| **#427** | **이 파일을 1017 → 121줄로 압축**(원장 L-10 종결). 아카이브만으론 ~200줄에서 멈춘다 — **유일 정보가 180줄**이라 주제별 재배치가 필요했다(`docs/eks-cost-model.md`·`docs/jvm-observability-notes.md` 신설, 실패 6종 → `PERSISTENT-RESOURCES.md`, 결정 블록 → `README.md`·`plans/…phase02.md`). 🔴 **결정 블록을 새 `docs/` 로 옮길 뻔했다** — `check-design-integrity.sh` 의 `DOCS` 가 고정 목록이라 감시 밖이 된다. 🔴 **정리가 문서 오류 8건을 드러냈다**(가드레일·크레딧·Stage4 모순·SecurityConfig·호출부·삭제된 브랜치 + **이미 해소됐는데 아무도 모르던 2건**) — ***포인터만 남은 항목은 이렇게 썩는다.*** 🔴 **QA 가 내 검증의 한계를 드러냈다**: 내 grep 16종은 *"유실 0"* 이라 했는데 삭제분 977줄 전수 대조로 **3건(HIGH 1)** 이 나왔다 — `D-003` 을 통째로 날렸다. ***완료 처리도 '지우기'가 아니라 '상태 갱신'이다.*** ⚠️ **121줄은 80줄 규칙을 여전히 초과한다** — 더 줄이면 유일 출처를 잘라야 한다. **규칙 자체가 다음 검토 대상** | 09-18 |

## 다음 작업

### 제품 백로그 (이 파일이 유일 출처)

- **Phase 4 후보 (실사용 후 판단)**: 면접 회고 메모(activity `NOTE` 타입) · **같은 회사 카드 그룹핑 뷰** ·
  JD 등록/수정 모달(현재 `AddCompanyModal` 에서만 입력 가능) · **Phase 3c(JD URL 파싱)**
  (코드 확인: 전부 미구현. `.claude/docs/ux-retention-plan.md` 는 **다른 기능 세트**라 대체 출처가 아니다)
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
