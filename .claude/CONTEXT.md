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
| 진행 중인 트랙 | 🚧 **prod 평일 창 운영 (📌D-016, 2026-10-16 착수 — 계획 단계)**: 창 동안만 EKS 가 prod 서빙 · 그 외 Fly · **켜기 = 수동(`workflow_dispatch`)만 · 끄기 = 18시 EventBridge 예약 + 수동**(사용자 Q-3, B4-21 2026-10-23). ✅ **선행 조건 Q-1 「학습 문서 정리」 = 튜토리얼 재현 검증 완료 2026-10-16**(Stage 4·5a~5f, 3세션 ≈ $0.35, 결함 8건 수정) → 클러스터를 prod 전용으로 전환해도 된다(간헐 학습 세션 종료). ✅ **순서표 1a(Flyway 공유 DB 안전 — BE+Fly) 2026-10-18 배포**: 마이그레이션은 Fly `release_command = "migrate"`(core-api jar 전용 모드), 앱 기동 시 OFF, **자동 `repair()` 제거**(`java -jar app.jar repair` 수동만). EKS 는 prod(external) 모드에서 기동 시 migrate **꺼짐**(5-B — Fly `release_command` 가 유일한 실행자, 1b 는 학습 in-cluster 모드에만). ✅ **순서표 2(이미지 공급망) 2026-10-19 (#461)**: ECR 엔 main 커밋 이미지만, `latest` 없음, IMMUTABLE. 배포 태그 정본 규칙은 `eks-tutorial-steps.md` 1-1(`--first-parent`). ✅ **순서표 3(CI 역할) 2026-10-19 (#464·#465·#466)**: 쓰기 = main 전용 역할, PR plan = 좁힌 읽기 전용(0-bootstrap state 의 `random_password` 평문 노출은 감수 — 쓰기 권한자 1명), access entry = `bootstrap-admin` + main 역할. 🔴 **PR 역할을 다시 넓히거나 entry 에 넣지 마라.** 가드 재정의(N-3)는 8번으로. 🚧 **순서표 5(DB 층) 진행 중**: 5-0 ✅ · 5-A ✅(#469, 플래그 꺼짐) · 5-B ✅(#470) · 5-C ✅(#471) — **5번 코드·문서 완료, DB 는 아직 꺼짐**. ✅ **순서표 4a(리퍼·마커) 2026-10-20 (#472)**: 로컬 리퍼는 이 세션이 만든 클러스터만 destroy(createdAt 소유 판정), CI 클러스터 dead man's switch 는 8번. 🚧 **순서표 6(백업·복구) 진행 중** (#473 계획 · #474 6-A 도구): 리허설 원천 = Neon prod **읽기 전용** 덤프(7번 리허설 겸함), Neon 콜드 대기 매일 복원은 **7번 전환 후**, 실행 = EC2 systemd timer + SSM 설치. ✅ 6-0(Neon 읽기 전용 role) · ✅ 6-B **prod DB 가동 중 — 상시 과금(≈$12.9/월)**, 인스턴스 `Role=prod-db`, 백업 timer 매일 03:3x KST · ✅ 6-C 리허설 전부(#479 — Neon 대기 복원·격리 실측) · ✅ 6-D 감시(#477, 실패 메일은 **네이버 스팸함** — 수신 허용 #478). **6번 완료.** ✅ **7번 DB 전환 완료(2026-10-21 14:59 KST, #481·#482·#483·#486)**: **Fly prod 가 EC2 Postgres 를 쓴다**(피어 IPv6 `DB_HOST`, `CUTOVER_DONE=1`). Neon `production` = DB 단위 읽기 전용 동결 사본, `standby-rehearsal` = 매일 04:30 복원되는 콜드 대기. 🔴 **계정 폐쇄 = prod DB 소멸** — 탈출 경로 = Neon 대기(런북 「전환 뒤」). ✅ 7-C(#487): 서술 갱신·임시 자격증명(`neon-source-url`·`neon-app-url`) 삭제·**TASK-13 Fly 토큰 교체 완료**(app 범위 deploy 토큰, 노출 토큰 폐기). **7번 완료.** 🚧 **8번 계획(#488)**: **트래픽 없이 먼저** — 8-0 실측(1차 완료: api.quest = CNAME fly.dev·DNS only·TTL 300) → 8-A EKS prod 수준(#489 — `prod.auto.tfvars.json` 로 2-cluster 는 prod 창 전용, 앱 키 ExternalSecret 허용 목록, 이미지 = 마지막 성공 be-cd 커밋·없으면 같은 be/ 트리; 👤 TASK-17) → 8-B on/off·CI 안전장치(트래픽 0 — #490·#491·#492, **10-22 실측 왕복 통과** · GitHub cron 이 5h 지연·누락이라 **18:00 off·매시 watchdog 은 AWS EventBridge 가 `workflow_dispatch` 로 부른다**(#496, 10-23 실측 ≈10초) · 🔴 connection `devquest-eks-window-github` 이름 변경 금지(PAT 가 placeholder 로 돌아감) · PAT 만료 2027-01-15) → 8-C 트래픽 전환(~~방식 ③ 10-24~~ → **방식 ⓑ Worker 스위치 10-27** — `quest/api/*` 를 받는 Worker 가 KV 스위치 `{target, until}` 를 읽어 Fly/EKS(`eks.quest`, 기존 ACM) 로 보냄, 만료·오류 = Fly. ③ 은 Universal SSL 이 두 단계 호스트 api.quest 를 안 덮어 C-2 에서 붕괴 · C-0 ✅ 10-26 · C-1 ✅ 10-27(Worker 스위치·KV 바인딩) · C-2 ✅ #510 · C-3 ✅ 10-28(트래픽 0 왕복) · C-4 1차 10-28 ⚠️ **12분 만에 중단** — 스위치·배수·복귀는 성공, **404 의 진짜 원인 = FE 번들 `client_id=undefined`(V-3 이후 Workers 빌드 env 누락, 10-26~10-28 신규 로그인 전면 불가 — `fe-cd.yml` 빌드 env + 번들 단언으로 수정)** + EKS GitHub OAuth 자리표시(B7-16, 토큰 교환 500) + JWT 불일치 의심 로그아웃 → 🔴 **「10 직전」 선결 전 traffic=eks 금지** — OAuth ✅ #512 · JWT 통일 🚧(SSM 정본 + `jwt-rotate.yml`, 실행 = 1회 전원 로그아웃·사용자가 시점 결정) · B7-19 · B7-27 남음 · ⏳ 선행 = **Vercel 제거 → Cloudflare Workers**(✅ V-3 10-26 — prod FE 는 Worker 가 서빙, 남은 것 V-4 Vercel 삭제), 계획 `docs/superpowers/plans/2026-10-24-vercel-to-cloudflare-workers.md` · Cloudflare 토큰은 FE 배포/DNS 로 분리) — 10번 직전 과제: JWT 통일·레이트 리밋(B7-19)·EKS 풀 상한 25·B7-27(BE 트랜잭션 경계). 🔴 **`db_enabled=true` 머지 = 상시 과금 시작 — 6번 직전 별도 승인.** Fly `DB_HOST` 는 피어 IPv6 리터럴(이름 해석 불가). 계정 밖 사본 = Neon 무료 콜드 대기(Q-2) · DB = Neon 이탈 → **EC2 t4g.micro 자가 운영 Postgres**(상시, Fly·EKS 공유). 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` — **복구 리허설 통과 전 첫 창 금지**, **01-15 전 탈출 경로**(Free Plan 종료 → 90일 뒤 계정·리소스 삭제). 📌D-015 → `❌폐기`(09-29 사용자 발화를 내가 "prod 안 옮김"으로 잘못 읽었을 수 있다 — 확인 없이 결정으로 적음). ／ EKS Stage 0~5f 전부 ✅(5f 2026-10-16). 🔴 **metrics-server 애드온은 `eksbuild.11` 고정**(`.12` = openapi protobuf 503). 세션 첫 관문 = ArgoCD 설치 전 protobuf `/openapi/v2` 200. 🔴 **tofu 는 `cd … && tofu apply` 형태로만, apply 직후 `cat .claude/eks-session/active` 로 시각 확인**(원장 L-70). 세션 종료 승인 대기는 **사용자가 지켜보는 시간에만 진행**(5c D15). ／ Fly 메타스페이스 160m 중 140.79 MiB(88%), swap 하루 ~5 MiB 증가(10-16 실측) — 192m 상향 대기(Fly 는 밤·주말 서빙을 계속하므로 유효) |

> 🔴 **열린 PR 이 나오면 `gh pr view <n>` 으로 내용을 열어본 뒤 판단한다 — 제목으로 분류하지 말 것.**
> (08-04 사고: 자매 레포의 열린 PR 을 목록에서 보고도 열지 않고 "무관"으로 라벨만 붙여 이틀 방치.
> 실제로는 내가 그 직후 고친 파일을 참조하는 계획서였고, 방치되는 동안 인용 행 번호가 전부 어긋났다.)
> ***목록을 조회한 것과 검토한 것은 다르다.***

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| **#511·#512** | **로그인 복구 + EKS 창 OAuth.** #511: V-3 이후 Workers 빌드에 `VITE_GITHUB_CLIENT_ID` 누락 → prod 번들 `client_id=undefined`, 10-26~10-28 신규 로그인 전면 불가 — 빌드 env + 번들 단언 + 시크릿 지도(`.claude/docs/secrets-map.md`). #512(B7-16): prod OAuth 를 SSM→ESO, external 에서 자리표시 제거(닫힌 실패), apply 전 로그인 키 사전 점검. C-4 1차(13:21–13:33) 중단 원인 = 둘 다 | 10-28 |
| **#510 + C-3** | **8-C C-2 eks-window 배선 + 트래픽 0 왕복.** 입력 `traffic`(fly 기본·eks) — on 끝에 `eks.quest` → ALB(DNS only), eks 면 임대 표식 → KV `{eks, until=임대 만료}`. off 맨 앞: KV fly → (직전 eks 면) 배수 240s → 레코드 삭제. Cloudflare 불통은 eks 창+임대 유효일 때만 destroy 보류. C-3 실측: DNS 생성·삭제·KV 쓰기 첫 성공, 27분 ≈ $0.08. 다음 = C-4 첫 트래픽 창(🔴) | 10-28 |
| **#507·#508** | **8-C C-1 Worker 백엔드 스위치 배포.** Worker 가 KV `devquest-backend-switch` 키 `api-target` `{target, until}` 를 읽어 `/api/*` 를 Fly/EKS 로 — eks 는 `until` 이 미래·12h 이내·`EKS_ORIGIN` https 일 때만, 그 외(값 없음·만료·KV 오류) = Fly. 응답 헤더 `X-DevQuest-Backend`(B8-14 ✅). 키 없음 → prod 동작 불변(`fly` 실측). 토큰 둘: FE 배포(GitHub secret)·8-C(SSM) 둘 다 KV Edit | 10-27 |

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
