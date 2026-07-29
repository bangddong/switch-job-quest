# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | `main` (clean) |
| 열린 PR | 없음 |

> **🌙 다음 세션 시작점**: main clean · 미커밋 0 · 열린 PR 0 · **AWS 잔존물 0 (비용 $0)** ·
> 크레딧 ≈ $199.6/$200 · 세션 마커 없음.
>
> **🛡️ 가드레일이 이제 실제로 작동한다 (07-29 실측 확인)**:
> 예산 `$10/$50/$150 ABSOLUTE_VALUE` + 이상탐지 `devquest-eks-anomaly-alerts / DAILY / 임계값 $5`.
> **이전엔 AWS 기본값 `$100 AND 40%`라 크레딧 절반이 날아간 뒤에야 울렸다** — 콘솔에는 "켜짐"으로
> 보였다는 점에서 꺼진 것보다 나빴다. 코드화하지 않았으면 몰랐을 것.
> **➡️ 다음 = EKS Stage 3** (사용자가 "조금 이따" 진행하기로 함, 07-28).
> RDS → **in-cluster Postgres StatefulSet** 스왑으로 EBS CSI·PVC·StorageClass 학습 +
> "관리형 ↔ 자체운영" 비교(앱 코드 변경 0로 가능 — `application-prod.yml`이 100% 환경변수 기반).
> 브랜치는 `stage/eks-3-*`(퀴즈 게이트 대상). 착수 전 `docs/eks-session-sop.md` §1부터.
>
> **Stage 3 첫 작업으로 딱 좋은 것**: PR #339의 CI가 만든 이미지
> **`e74147f8094d35238e83470672b31fbd4fe7a35b`** 로 배포해 **`GRAFANA_*` 3키 없이 뜨는지** 확인.
> 이게 통과하면 logback 조건부화(`5cf76da`)의 실환경 검증이 끝난다. 검증됨: ECR 태그 == PR 머지 커밋
> SHA, `5cf76da`가 조상, 해당 트리 `logback-spring.xml`에 조건부 처리 존재.
> (3키를 제거하라는 뜻이 아니다 — prod도 시크릿으로 주입하는 값이라 자리는 유지. **의존하지 않음**을 확인하는 것)

> ✅ **EKS Stage 2 완료 — PR #339 머지 (07-28, ★과금 26분 35초 ≈ $0.06).** apply→검증→teardown→퀴즈 전부 끝.
> **현재 AWS에 아무것도 안 떠 있음 = 비용 $0, 고아 0건.** 퀴즈 통과(`docs/eks-quizzes/stage-eks-2-rds-secrets.md`).
>
> **핵심 성과 — `/health` 200.** Stage 1에서 CrashLoopBackOff로 끝났던 core-api가 **코드 변경 0으로**
> 정상 기동했다(바뀐 건 환경변수 주입 경로뿐 — `application-prod.yml`이 100% 환경변수 기반인 설계의 배당금).
> `jdbc:postgresql://devquest-eks-db.<...>.rds.amazonaws.com/devquest?sslmode=require (PostgreSQL 17.10)`,
> Flyway 12개 마이그레이션 적용, 26.3초 기동. 시크릿은 **손으로 안 만들었다** — ESO가 AWS Secrets
> Manager에서 K8s Secret 2개(10키)를 자동 생성.
>
> 🔴 **가장 값진 실패: 한글 description으로 apply가 2개 리소스에서 깨졌다.**
> `tofu validate`·`plan`·`tfsec`이 **셋 다 통과시킨다** — AWS API를 호출하지 않으니까.
> 게다가 OpenTofu는 에러를 **apply 종료 시점에 몰아서** 출력해 진행 중엔 "Creating..."에서 멈춘 것처럼
> 보인다 → **CloudTrail 이벤트 원문**으로 확정했다(`lookup-events` 요약의 `ErrorCode`는 `None`으로 나와 오독 유발).
> 제약은 **서비스마다 다르다**: EC2 보안그룹·IAM = 한글 ❌ / Secrets Manager·ECR lifecycle = ✅.
> `resource` 블록의 description만 위험하고 `variable`/`output`은 로컬 메타데이터라 무관.
>
> ✅ **미검증 5건 전부 해소** — ①kubectl 서버측 스키마 검증 통과 ②ESO CRD는 `v1`만 served
> (**v1beta1은 served조차 false** — 예상과 다름) ③IRSA `sub` 일치 확인 ④**RDS 관리형 시크릿
> (`rds!db-...`)은 인스턴스 삭제와 함께 완전 자동 정리**(복구창 좀비 없음) ⑤RDS 생성 4분 50초·삭제 3분 53초.
>
> 🔑 **IRSA 두 실패 모드 구분(실측)** — 이번 세션 최대 학습:
> `Not authorized to perform sts:AssumeRoleWithWebIdentity` = **인증**(신뢰정책 sub/aud 불일치, 최다 실패) /
> `AccessDeniedException ... no identity-based policy allows` = **인가**(권한 정책). 후자는 주체가
> `assumed-role/<롤>/<세션>`으로 찍히는데, 그 자체가 **assume는 성공했다는 증거**다.
> 부수 교훈: ExternalSecret이 실패로 보일 때 권한을 의심하기 전에 **마지막 시도 시각**을 먼저 봐라 —
> ESO 백오프가 16s→32s→64s→128s로 벌어져 정책 부착 후에도 한동안 실패 표시가 남는다
> (`kubectl annotate es <name> force-sync=$(date +%s) --overwrite`로 즉시 당김).
>
> 🔴 **착수 전 Blindspot Pass가 잡았던 과금 안전장치 구멍 3개가 전부 실전에서 값을 했다**:
> ①`skip_final_snapshot`(없었으면 destroy 실패 → 리퍼 벽돌) ②리퍼 생존 판정 **EKS OR RDS**
> (teardown 후 마커 자가청소 정상 동작 확인) ③RDS를 `2-cluster` 안에 배치(리퍼 사정권).
>
> **부수 발견 2건 (둘 다 문서에 반영됨)**:
> ⓐ **ECR 이미지는 태그를 여러 개 갖고 `imageTags[0]`은 순서 보장이 없다** — 금지된 `latest`로
> 배포될 뻔했다. 40자리 hex만 골라야 한다(`k8s/README.md` §1).
> ⓑ **`kubectl delete secret`만으로는 ESO 관리 Secret을 못 지운다** — `creationPolicy: Owner`라
> 8초 만에 **다른 UID로 부활**한다(실측). 소유자인 ExternalSecret을 먼저 지워야 하고, 그러면
> K8s Secret도 함께 GC된다. teardown 순서가 SOP §8과 `k8s/README.md` §5에 확정 기록됨.
>
> **⚠️ 이 PR은 prod에도 배포됐다** — `be/` 변경(logback 조건부화 `5cf76da`)이 포함돼 머지가 BE CD를
> 트리거했다. prod에는 `GRAFANA_LOKI_URL`이 설정돼 있어 `<if>`가 참 → **Loki 어펜더가 기존과 동일하게
> 동작**(동작 무변경). 이 변경의 값은 "환경변수가 **없어도** 앱이 죽지 않는다"는 안전성 확보다.

> **🧹 tech-debt 정리 세션 완료 (07-27) — 9 PR 머지, 전부 CI 그린.**
> #326 죽은 설정 · #327 core-api Jackson3 · #328 db-core Jackson3(+회귀테스트) · #329 FE(CompanyCard 가드·extractPdfText)
> · #330 클린클로즈 · **#331 FE 테스트 러너(vitest) 도입 + CI 게이트** · #332 질문뱅크 category 보류 결정
> · **#333 질문 중복방지 윈도우 버그 수정** · #334 FE 테스트 문서 동기화.
> **성과: be/ 소스 Jackson 2 잔재 0건 · FE 테스트 인프라 확보(그동안 불가능했던 순수함수 단위테스트) · 실사용 버그 1건 제거.**
>
> **결정필요 4항목 처리 결과**: ~~①FE 테스트 러너~~ **→ #331 도입 완료** ·
> ~~②질문뱅크 category 활성화~~ **→ 🔴 보류 확정(#332): 뱅크 26개 중 `ai-llm`이 1개뿐이라 지금 켜면
> AI 폴백↑ = 퇴보. 선행조건 = 카테고리당 10개 보강. 근거 기록 완료, 재조사 불필요** ·
> **③질문뱅크 ORDER BY RANDOM(미착수 — `@DataJpaTest` 인프라 선행) ④원장 L-8(미착수 — 전역 J2 kotlin 모듈, blast radius 큼).**
>
> 🔴 **원장 L-9 (zone 불일치)는 오진이었다 — #337에서 실측으로 반증.** prod는 `TZ=Asia/Seoul`(#210)로
> **처음부터 KST**였고 저장·조회가 어긋난 적이 없다. 주장했던 "데일리 메일 마진 0"·"스트릭 과소 계산"은
> **둘 다 사실이 아니다.** 실측: `eclipse-temurin:21-*-alpine`에서 TZ 미설정→`GMT`, **`TZ=Asia/Seoul`→`Asia/Seoul`**
> (이 이미지엔 tzdata가 **포함**돼 있다 — "Alpine엔 tzdata 없음" 통설이 여기 해당 안 됨).
> **오진 경로**: ①루트 `fly.toml`(존재하지 않는 경로)을 grep → `||` 폴백의 "없음"을 사실로 보고
> ②QA가 TZ 존재를 찾은 뒤 **이미지를 재보는 대신 웹 검색 일반론을 채택**(기존 결론을 살려주는 방향이라 더 위험).
> **교훈: 일반론으로 특정 환경의 사실을 대체하지 말 것 — `docker run` 한 줄이면 30초.**
> **✅ #336 코드는 유지**(원장 L-9-c): `build.gradle.kts` 테스트 zone 인자는 **실재하던 CI≢prod 갭**
> (CI는 UTC, prod는 KST)을 닫았고, `TimezoneConsistencyTest`가 회귀 가드. Dockerfile `-Duser.timezone`은
> 배포 설정 유실 대비 이중 안전장치로 유지.
> **🐳 이 세션에서 colima+docker CLI 로컬 설치** — 이제 이미지 빌드/실행을 로컬에서 검증할 수 있다
> (`colima start` 필요, 안 쓸 땐 `colima stop`). 이번 오진을 잡아낸 게 정확히 이 도구다.
>
> **🌙 다음 세션 시작점 (07-28 세션 종료 시점 갱신)**: main clean, 미커밋 0, 열린 PR 0, EKS 잔존물 0(비용 $0).
> colima는 **정지 상태** — 컨테이너 검증이 필요하면 `colima start`(안 쓸 땐 `colima stop`).
> **작은 것부터 집으려면**: ⓐ**L-8** 전역 J2 kotlin 모듈 제거(blast radius 큼)
> ⓑ질문뱅크 `ORDER BY RANDOM`(`@DataJpaTest` 인프라 선행) ⓒ질문뱅크 시드 보강(카테고리당 10개 → 그래야
> category 활성화가 의미를 가짐, #332 참조). **큰 트랙은 아래 "다음 = 택1".**
> ~~L-9 zone 불일치~~ → **오진으로 종결(#337).**
> - **서비스 분해 트랙**: Phase 0+1 완료(#295·#297·#298·#300 / #304·#305·#306·#307·#308). ai-api가 AI 포트
>   24개를 REST로 노출, core는 HTTP 어댑터로 호출 가능. ⚠️ prod 기본값은 `transport=inprocess` 유지.
> - **EKS 트랙**: Task 8 왕복 실증(#316) → #318 퀴즈 게이트 · #320 과금 안전장치(dead man's switch,
>   하트비트 2h stale→launchd 리퍼 자동 `tofu destroy`, 이 맥에 설치됨) · #322 ECR 0-bootstrap 편입 ·
>   #323 ECR push 워크플로 · **#324 Stage 1 완료 — core-api가 EKS에서 실제로 떴다**(ECR→노드 pull 성공,
>   imagePullSecret 없이 노드 IAM·arm64, CrashLoop 3단계 Loki→JWT→DB, 클린 teardown 고아 0·ECR 생존).
>   크레딧 ~$199.8(만료 2027-01-15). 실습 SOP 단일 출처 = `docs/eks-session-sop.md`(apply 전 필독).
> **다음 = 택1:**
> - **① EKS Stage 3 (Postgres StatefulSet + EBS CSI + PVC)** ← **사용자가 다음으로 지목(07-28)**.
>   ~~Stage 2~~는 **#339로 완료**(RDS+IRSA+ESO, `/health` 200, 고아 0). 브랜치 `stage/eks-3-*`(퀴즈 게이트).
>   **Stage 2의 RDS를 in-cluster Postgres로 스왑** → StorageClass·동적 EBS 프로비저닝을 배우면서
>   "관리형 ↔ 자체운영"을 **앱 코드 변경 0**으로 비교(`application-prod.yml`이 100% 환경변수 기반).
>   ⚠️ **PVC가 처음 생기는 Stage** → destroy 전 `kubectl delete ingress,pvc --all -A` **필수**
>   (K8s가 만든 EBS는 tofu state 밖 = 고아 과금). SOP §8에 이미 반영돼 있음.
>   ⚠️ 노드 파드 상한 **11 실측**(t4g.small) — 시스템 4 + ESO 3 + 앱 1 = 8. StatefulSet까지 얹으면 여유가 적다.
> - **② Phase 2 (daily-service 추출 + 경량 무로그인 FE)** — 설계 `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`.
>   ⚠️ **착수 전 반드시**: ai-api를 **실제 네트워크에 처음 올리는 시점**이므로 `/internal/ai/**` **무인증**
>   문제를 먼저 해결할 것(현재는 Fly가 core-api만 배포해서 노출 안 됨). `include-message: always`도 켜져 있다.
> - **③ `transport=http` 실전 검증** — 로컬에서 ai-api 띄우고 수동 e2e → 문제없으면 prod 기본값 전환 검토.
>   Phase 1은 "전환 가능"까지만 했고 "전환 완료"는 아니다.
>
> **📋 Phase 1 회고 — Phase 2 착수 전 반드시 볼 것:**
> - **가장 큰 교훈: 가짜 서버 테스트는 계약을 증명하지 못한다.** Task 1.4a의 테스트 68개가 전부 그린이었는데도
>   `MockRestServiceServer`/가짜 HTTP 서버라 **ai-api의 진짜 Jackson을 한 번도 안 거쳤다.** Task 1.5에서
>   진짜 ai-api를 띄우자마자 **`server.error.include-message`가 Boot 3 키였다는 버그**가 드러났다
>   (Boot 4는 `spring.web.error.include-message` — AI 실패 원인이 core로 **한 번도 전달된 적 없었음**).
>   → **경계를 넘는 계약은 반드시 양쪽 실물을 붙여서 검증한다.**
> - **테스트가 거짓 안심을 준 사례 2건**: ①`jsonPath("$")`는 json-smart **permissive 모드**라 따옴표 없는
>   raw text도 통과시킨다 → String 반환 엔드포인트의 wire format 불일치를 못 잡았다(#305).
>   ②`produces=APPLICATION_JSON_VALUE`를 붙여도 `StringHttpMessageConverter`가 `*/*`를 지원해 Jackson보다
>   먼저 선택된다 → 헤더는 JSON인데 바디는 raw text. **실측(바이트 확인) 전엔 믿지 마라.**
> - **QA가 HIGH를 낸 건 1건(#305), 그리고 그게 맞았다.** 추측으로 고치지 않고 `@SpringBootTest(RANDOM_PORT)`로
>   실제 바이트를 재고 나서 수정한 절차가 유효했다. **"추측 결론으로 코드 수정 금지" 규칙이 실제로 작동함.**
> - **계획 전파는 매 태스크 첫 커밋으로**: QA 발견을 다음 태스크 브랜치의 첫 커밋으로 계획 문서에 반영하는
>   패턴을 썼다(#305→1.2 브랜치, #304→1.1 브랜치). Phase 0의 "계획 stale" 문제가 재발하지 않았고
>   **문서 전용 PR도 0건**(Phase 0은 10PR 중 6개가 문서였다).
> - **큰 태스크는 쪼갠다**: Task 1.4를 1.4a(기계적·무행동)와 1.4b(동작 변경)로 분리했다. 회귀가 났을 때
>   원인 범위가 절반으로 줄어든다. Phase 2의 daily-service 추출에도 같은 분리를 적용할 것.
> - **시각화**: **Phase 1 브리핑 https://claude.ai/code/artifact/244a74dd-e7a4-4d62-a0e1-5eb5a4668e45**
>   (경계 다이어그램·실행 순서·검출된 버그·트랜잭션 재배치·이월 항목) ·
>   Phase 0 회고 https://claude.ai/code/artifact/8d702047-0184-4743-b89d-4f085b8644bc ·
>   목표 아키텍처 https://claude.ai/code/artifact/ffe35a97-ee42-4412-b85c-2716e8b59a14
> - **배포 타겟 열린 결정(Phase 3 전 확정)**: "최종 prod"를 EKS 완전체 vs Fly 3서비스 vs Fly 단일+EKS 학습전용 —
>   미정. EKS 상시는 컨트롤플레인 $73/mo 고정비(destroy-after-use 전제).
> - **메모(리뷰 CI)**: OCR(alibaba)·roborev 검토 완료 → **도입 보류.** 솔로라 안 아픔 + OCR은 **API 종량제(Claude 구독 불가)**. 현 qa-reviewer로 충분. **협업자 생기거나 PR이 3서비스로 늘면** 그때 OCR 파일럿. 나중 카드.
> - **메모(DB)**: **Neon→RDS 전환 = 폐기(07-21).** 무료 사용량 부족 시점에만 재고. RDS는 상시 과금이라 destroy-after-use(EKS 실습)·Fly fallback 전략과 배치. 상세는 "백로그 › DB".
>
> **✅ Task 8 완료 — 2-cluster apply 왕복 실증 (2026-07-24, ★첫 과금)**: `tofu apply`(14 added, ~10분)
> → `kubectl get nodes` **노드 Ready**(v1.36.2·arm64·공인IP) → `tofu destroy`(14 destroyed) → **고아 리소스 0**
> (EBS·SG·LB·NAT 전수 확인). **2-cluster IaC가 실제로 동작함이 검증됨.** 비용 **~$0.1 이하**(벽시계 ~50분,
> 컨트롤플레인 40분×$0.10). **현재 AWS에 아무것도 안 떠 있음 = 비용 $0, 크레딧 $199.81 유지.**
> teardown 명령·개념은 `docs/eks-tutorial-steps.md` Step 8(검증된 정답 경로), 실측은 `docs/eks-migration-log.md`.
> - **✅ Stage 1 완료(#324, 07-27)**: ECR 편입(#322)·CI 빌드(#323)·core-api 배포 실증. ECR→노드 pull 성공.
>   **✅ Stage 2 완료(#339, 07-28)**: RDS + Secrets Manager + IRSA + ESO. `/health` 200, 고아 0, ~$0.06.
>   **➡️ 다음: Stage 3 = Postgres StatefulSet + EBS CSI + PVC**(RDS를 in-cluster로 스왑, 관리형↔자체운영 비교).
>   ⚠️ **학습용 DB 별도 필요**(prod Neon 연결 금지 확정). Stage 3부터 ALB/PVC 생기면 **destroy 전 `kubectl delete
>   ingress,pvc --all -A` 필수**(K8s 생성 AWS 리소스는 tofu state 밖 = 고아 과금).
> - kubectl 이 머신에 설치 완료(`brew install kubectl` v1.36.3, 07-24).
> - K8s 1.36 핀 유효 재확인(07-24 실측: 표준지원 종료 27-08-02, 1.33은 07-29 종료).
>
> **🖼️ 아키텍처 다이어그램 상시 유지 (07-20 도입)**: 매 레이어/Stage마다 갱신. ① mermaid 소스
> `docs/architecture/eks-2-cluster.md`(repo·PR·블로그용, GitHub 자동 렌더) ② 라이브 아티팩트
> (줌·전체화면·과금 색구분) https://claude.ai/code/artifact/0d4a3aa3-74eb-46c3-a598-96228686b311
> **둘 다 07-28 Stage 2 기준으로 갱신됨** — 다이어그램 3종(인프라 전경 / IRSA 시퀀스 / 시크릿 2갈래),
> 리소스 26개 표, 세션별 실측 비용. 아티팩트는 3개 다이어그램 각각 줌·전체화면 가능.
> ⚠️ **Stage 3 착수 시 두 벌 모두 갱신할 것**(RDS 제거 → StatefulSet+PVC, EBS CSI 추가).
>
> **🔒 CI 가드 (07-20 #287)**: `infra-ci.yml`의 `guard-local-layers` 잡이 `infra-deploy.yml` 매트릭스에
> `2-cluster` 진입 시 CI 실패시킴 — 로컬 전용 레이어가 CI 자동 apply로 과금 새는 것 기계 차단.

## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| **#342 · #343** | **★ IaC-first 정합화 — 그리고 가드레일이 사실은 꺼져 있었다는 발견 (07-29, 비용 $0).** 사용자 지적("IaC 기반인데 캡처가 필요하냐")에서 출발했으나 밑에 더 큰 부채가 있었다: 튜토리얼 G-1이 **콘솔 클릭으로 예산 만드는 법**을 가르치는데 `budget.tf`가 **이미 그 절차를 대체**한 상태 → 캡처는 죽은 절차의 부속품. 캡처 체계 전면 폐기 + 콘솔 절차를 IaC로 교체 + **미작성이던 레이어 스캐폴딩(재현 검증 첫 구멍) 작성**. 이상탐지를 `cost-anomaly.tf`로 코드화(TASKS는 "코드로 처리"라 적었지만 **실제 .tf가 없었다**). **🔴 머지 후 CI apply 실패 — `Limit exceeded on dimensional spend monitor creation`**: AWS가 신규 계정에 `Default-Services-Monitor`를 미리 만들어 두고 DIMENSIONAL은 **계정당 1개** → 생성이 아니라 **인수(import)**가 정답. plan은 내 코드만 보므로 못 잡는다(**plan이 못 잡는 실패 3번째** — ①한글 description ②EMAIL+IMMEDIATE ③계정당 한도). **진짜 수확**: 인수해 보니 AWS 기본 임계값이 **$100 AND 40%** — 크레딧 $200 계정에서 **절반 날아간 뒤에야 울림**. 콘솔엔 "켜짐"으로 보여 **꺼진 것보다 나빴다**. $5로 하향, 실측 확인 완료. 부수 발견: **크레딧 만료일이 문서마다 달랐고**(01-15 vs 07-15) 사용자 확인으로 **2027-01-15 확정** — 07-16의 "가입 +1년" **추론이 콘솔 값을 덮어쓴 오독**이었다. **크레딧 수명이 1년이 아니라 6개월.** | 2026-07-29 |
| **#345** | **★ 설계 드리프트 방지 장치 (07-29).** 사용자 지적(*"설계는 바뀌는데 대비가 없어 계속 틀어진다"*)에서 출발. **근본 원인**: 결정은 `CONTEXT.md`에, 재판정은 일지에 따로 산다 — 일지는 append-only 시계열이라 "지금 유효한 결정"을 못 알려준다 → **CONTEXT만 읽으면 뒤집힌 결정을 유효한 것으로 읽는다.** 07-29 RDS 오답이 정확히 이것(07-28 재판정이 일지에만 존재). **1차 전수 점검 3건**: D-3 🔴 RDS 재탈락 사유①이 무효화됐는데 역참조 없음(→ **사유별 재판정 표**로 교체 — 통째로 지웠다면 ③이 살아있어 결론이 뒤집혔을 것) / D-1·D-2 경로 축약형. 정합 확인된 축: K8s 1.36·t4g.small·db.t4g.micro 코드값, 매니페스트 4종, 만료일, 비용 모델. **장치 4개**: ①결정 메타 줄(`📌 D-NNN · 상태 · 영향 · 재판정`) — **관측 사실엔 안 붙인다**, 대상은 다르게 고를 수 있었던 전략 결정뿐 ②`<!-- verify: <경로> -->` 마커(이상탐지 갭을 잡았을 장치) ③`design-integrity.yml` **경로 필터 없음**(코드 삭제가 문서를 깨뜨리는 게 핵심이라 문서를 안 건드린 PR이 더 위험) ④`design-change-procedure.md` + 빨간 깃발 3줄. **검사기 반증 테스트에서 버그 발견** — `\| while read`가 서브셸이라 에러는 찍히는데 `exit 0`이었다. **통과했다고 믿게 만드는 검사기는 없는 것보다 나쁘다.** | 2026-07-29 |
| #341 | **EKS 게시용 자료 부채 상환 (+1,429/−94).** 튜토리얼 2스테이지·다이어그램 3스테이지 뒤처진 것을 Stage 1~2로 갱신. 라이브 아티팩트 줌/전체화면 보존(덮어쓸 뻔한 것을 도구가 차단). | 2026-07-28 |

## 다음 작업

### 🎯 서비스 분해 에픽 (신규 대방향, #289 설계 확정) — 여러 세션짜리
> 📌 **D-003** · 상태 `🚧진행중` · 영향 `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`, `docs/superpowers/plans/2026-07-21-service-decomposition-phase01.md`, `be/core/ai-api`, `infra/aws-eks/2-cluster/addons.tf`, `.claude/review-ledger.md`, Stage 3~5

> ⚠️ **가장 많이 바뀔 결정이다** — Phase 0~1만 구현됐고 2~3은 계획 상태다. 계획과 구현이 갈라지기
> 가장 쉬운 지점이므로, **Phase를 넘길 때마다 이 블록을 갱신**한다(완료 표시가 아니라 *계획이 바뀌었는지*).
> 특히 아래 두 가지는 **아직 코드가 없는 약속**이라 드리프트 1순위다:
> ① `enableNetworkPolicy` (vpc-cni addon 현재 맨몸) ② t4g.small → medium 상향.
> 상태가 `🚧진행중`인 동안에는 이 블록의 서술을 **"확정된 것"으로 인용하지 말 것.**

- **설계 문서: `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`** (착수 전 필독)
- **방향**: 무거운 앱 → 라이트 데일리 도구 재정렬 + EKS 다중서비스 학습. **daily + ai-service + core 3분리.**
- **strangler 이관**: Phase 0(준비: ai-api 스캐폴드+HTTP어댑터 피처플래그) → 1(ai-service 추출, 포트 어댑터 HTTP화, AI parity 검증) → 2(daily-service 추출 +경량 FE, 무로그인 e2e) → 3(EKS 배포: Deployment×3·Ingress·NetworkPolicy)
- **✅ Phase 0~1 구현계획 확정·머지 (#292, 07-21)**: `docs/superpowers/plans/2026-07-21-service-decomposition-phase01.md`. Blindspot Pass로 4개 불일치 반영(마커·AiCallLog 역결합·설정분산·트랜잭션경계). #294로 Fly 무영향·롤백 불변식 보강.
- **✅ Phase 0 전체 완료·머지 (07-21)**: 0.1 AI 포트 17개 `AiEvaluatorPort` 마커(#295) · 0.2 AiCallLog 방침
  **A 단독 확정**(읽기 소비처 0건, #297) · 0.3 `core:ai-api` 빈 스캐폴드(#298, core-domain만 의존) · 0.4 전송
  전환 스위치 `AiTransportConfig`(#300, 조건부 @Bean+@Primary, 기본 inprocess 무행동, 대표 Blog 어댑터 뼈대).
  **전부 무행동 변경.**
  - **⚠️ 0.3 핵심 발견**: client-ai를 ai-api에 붙이면 `CacheMetricsAdvisor→AiCallLogPort→db-core` 런타임 체인이
    딸려옴 → client-ai 의존은 Phase 1로 연기. **Phase 1 착수 순서 = 1.3(ai-api AiCallLogPort 관측 어댑터)→1.1→1.2→1.4.**
- **✅ Phase 1 전체 완료·머지 (07-22)**: 1.3 관측 어댑터(#304) → 1.1 REST 컨트롤러 24개(#305) →
  1.2 설정 이관(#306) → 1.4a HTTP 어댑터 배선(#307) → 1.4b·1.5 트랜잭션 재배치 + parity(#308).
  **ai-api = AI 포트 24개를 REST로 노출하는 독립 서비스. core는 `transport=http`로 호출 가능.**
  - ⚠️ **프로덕션 기본값은 `inprocess` 유지** — Phase 1은 "전환 가능"까지고 "전환 완료"가 아니다.
    실전 검증(로컬 e2e → prod 전환 판단)이 남았다.
  - ⚠️ **`client-ai` 의존 제거 금지** (Phase 3까지 = inprocess 롤백 보존).
  - **잔존 리스크 1건**: `CodingQuestService.generateProblem`/`submitCode`는 재시도 루프에 AI·Judge0·DB가
    뒤섞여 트랜잭션 재배치를 **의도적 보류**했다. HTTP 전환이 완료되면 **이 둘이 유일하게 AI 호출 중
    DB 커넥션을 잡는 지점**이 된다. 전환 전 재검토 필요.
- **➡️ 다음 스텝: Phase 2 (daily-service 추출 + 경량 무로그인 FE)** — 설계 문서 §이관계획.
  ⚠️ **착수 전 필수**: ai-api를 **실제 네트워크에 처음 올리는 단계**다. `/internal/ai/**`가 **무인증**이고
  `spring.web.error.include-message: always`로 내부 예외 메시지까지 노출한다. 현재는 Fly가 core-api만
  배포해서 안 드러날 뿐이다. **인증·격리를 Phase 2 첫 태스크로 처리할 것.**
  Phase 1처럼 **기계적 작업과 동작 변경을 다른 PR로 분리**하는 패턴을 그대로 적용한다.
- **⚠️ 2-cluster에 영향**: ai NetworkPolicy 실현하려면 vpc-cni addon에 `enableNetworkPolicy` 필요(현재 맨몸), JVM 3개엔 t4g.small 빠듯→medium. Phase 3 체크리스트.
- **미해결(구현 중)**: 데일리 캐싱 전략(공통콘텐츠 1회생성→서빙, Redis) / 이메일 SES 전환·소유(core vs daily) / AiCheck 오케스트레이션 경계 / 분산 트레이싱
- CI 메모: `tfsec` 잡이 릴리스 다운로드 시 GitHub API rate-limit(403)로 간헐 실패 → `github_token` 주입으로 근본해결 가능(미적용, 재실행으로 우회 중)

### 코드 작업
- [ ] **파이프라인 후속 (사용자 확인 대기)**: ① 모바일 실기기 확인 (데스크톱 시나리오는 Claude가
      prod 테스트 완료) ② 테스트 데이터 정리 — 회사 "테스트-토스" 삭제, 임시 이력서를 실제로 교체
- [ ] **Phase 4 후보 (실사용 후 판단)**: 면접 회고 메모(activity NOTE 타입), 같은 회사 카드
      그룹핑 뷰, JD 등록/수정 모달(현재 AddCompanyModal에서만 입력 가능), Phase 3c(JD URL 파싱)
- [x] ~~tech-debt(LOW): CompanyCard busy 플래그(#259) + extractPdfText LOW 3건(#261)~~ → **2026-07-27 해결(#329).**
- [x] ~~**결정 필요: FE 테스트 러너 미도입** (vitest 등)~~ → **2026-07-27 해결(#331).** vitest ^3.2.7 도입
      (Vite 6 스택 재사용, environment=node), extractPdfText 순수함수 4개 단위테스트 18개 + fe-ci.yml에
      `npm test` 게이트 추가. **다음 FE 순수함수/유틸은 이 패턴으로 `*.test.ts` 붙일 것**(컴포넌트 테스트는
      jsdom+@testing-library 필요 = 아직 미도입, 필요해지면 그때 별도 chore).
- [ ] **#261 후속**: 배포 후 실제 PDF 이력서로 추출 품질 확인(줄바꿈·표 레이아웃 깨짐 정도).
      BE 파싱(PDFBox) 구현은 로컬 `backup/be-pdf-parse` 브랜치 보존 — 스케일업 결정 시 재활용
- [ ] Phase 3a MEDIUM 보류: UserResumeAdapter upsert read-then-write 경합 — 다중 기기 동시
      사용 필요해지면 DB ON CONFLICT 전환
- [ ] **OOM 후속 관찰** (#245 swap 배포 후) — 07-10 3.3일차 실측: kill 0건(마지막 kill 07-07 00:01
      = 스왑 배포 직전, anon-rss 409MB), 스왑 소비 22~32MB/일 선형(배포 재시작 시 리셋),
      mem_available 12~47MB 바닥권 지속 → 무배포 8~10일 시 스왑 소진·재발 가능성 🟡.
      → **JVM 다이어트(#263)로 근본 대응 착수·배포 완료(07-13)**. 이후 검증은 위 "post-deploy 관측" 항목으로 이관.
- [x] ~~메타스페이스 누수 조사~~ → **2026-07-15 종결. 누수 없음.** 아래 "비자명적 결정" 참조.
      잔여 관찰(선택): 신규 기능으로 클래스가 늘면 작동점 134.6 MiB가 올라간다. 160 MiB 여유는
      25.4 MiB(16%)뿐이므로 **대형 의존성 추가 시 Grafana로 작동점 재확인**할 것.
- [x] ~~tech-debt(LOW, BE): 인터셉터 2건 `ObjectMapper()` 신규 생성~~ → **2026-07-27 해결(#327).**
      원 관찰("요청마다 생성")은 부정확했음 — 실제로는 `by lazy`라 인스턴스당 1회였다. J3 생성자 주입으로 통일.
- [ ] 에이전트 Disambiguation Gate / Closing Summary 미비점 보완 (Gate 횟수 상한, 트리거 기준 명시 — 실사용 경험 더 쌓은 뒤 결정)
- [ ] **#255 후속**: 다음 기능 작업에서 Blindspot Pass 실효성 확인 (Deviations→QA 집중검토 흐름은
      #259에서 1차 동작 확인. template 동기화는 07-10 완료 — orchestrator·clarify·quiz + 훅 스크립트 3종)
- [ ] **질문 뱅크 category 파라미터 — 🔴 보류 확정 (2026-07-27 조사·결정). 재조사 불필요.**
      **선행 조건 = 뱅크 보강. 그 전엔 활성화가 개선이 아니라 퇴보다.**
  - **현상**: `TechQuestionBankPort.findUnused(exclude, category = null)`의 category는 프로덕션에서 항상 null.
    호출부 전수 grep 결과 프로덕션은 `DailyMailScheduler.kt:45` **한 곳뿐**이고 2번째 인자를 생략한다.
    → `TechQuestionBankAdapter`의 4분기 중 category 2분기(`findAllByCategoryAndQuestionNotIn`·`findAllByCategory`)는
    **테스트만 밟는 죽은 경로**. 실제 동작 = 전 카테고리 균등 랜덤 1개를 전 사용자에게 동일 발송.
  - 🔴 **보류 근거(실측): 데이터가 카테고리를 감당 못 한다.** 뱅크 총 **26개**(V10 5 + V11 21) 분포 —
    `java-spring` 8(31%) · `system-design` 6(23%) · `database` 6(23%) · `concurrency` 5(19%) · **`ai-llm` 1(4%)**.
    **`ai-llm`은 질문이 단 1개**라 요일 로테이션 등을 켜면 그날 1개 쓰고 즉시 소진 → `randomOrNull()`=null →
    **AI 폴백**. 즉 **지금 켜면 AI 호출(비용)이 오히려 늘어난다.**
  - **활성화 트리거**: V12 시드로 **카테고리당 최소 10개** 확보(특히 `ai-llm`·`concurrency`). 그 후에야
    로테이션/배분이 의미를 갖는다. 보강 없이 켜지 말 것.
  - **대안으로 검토했다 기각**: ①죽은 파라미터 제거 → 보강 계획이 살아있어 재작업 유발 ②요일 로테이션 즉시 도입 → 위 사유
- [x] ~~🟡 **질문 뱅크 중복 방지 윈도우 버그** (2026-07-27 발견)~~ → **당일 해결(#333).**
      "최근 30**행**"이 사용자 수에 반비례해 축소되던 것(N명이면 30/N일)을 **"최근 20일"**로 전환.
      **20일인 이유 = 뱅크 26개보다 작아야 AI 폴백이 안 돈다**(≥26이면 주기적 완전 소진 → AI 비용 발생).
- [ ] 질문 뱅크 규모 확대 시(수백 건↑) `findAllBy...` 전체 로드 방식 재검토 — `ORDER BY RANDOM() LIMIT 1`
      native query 전환 고려 (단, `@DataJpaTest` 등 native query 검증 인프라 먼저 필요)

### 사용자 확인 필요
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획
- [ ] **#257 후속 — 휘발형 학습 실사용 확인**: 후속 질문 UX(단발형·5회/일 제한 적정성), AI 설명 품질.
      만족스러우면 **Phase B(축적형 복습노트)** 착수 판단 — 모르는 개념/오답 저장 → 나중에 복습(로그인·DB·간격 반복, RPG XP 연동). 지금은 보류.

### 백로그
- [x] ~~**tech-debt(LOW, CI): `be-ci.yml` 테스트 리포트 업로드 범위**~~ → **2026-07-22 해결.**
      리포트 경로를 5개 모듈(core-api·ai-api·core-domain·db-core·client-ai)로 확장 + **`parityTest`를
      CI에 연결**(전용 소스셋이라 `check`/`test`에 자동으로 안 붙어 12개 parity 테스트가 죽어 있었음, #308 QA MEDIUM).
- [x] ~~**tech-debt(LOW): `application-prod.yml`의 `server.error.*` Boot 3 잔재 키**~~ → **2026-07-27 해결(#326).**
      블록째 제거. Boot 4는 `spring.web.error.*`라 무시되던 죽은 키(값도 기본값 `never`와 동일해 동작 불변).
- [x] ~~**tech-debt(LOW): 죽은 설정 2건** — `devquest.ai.pass-score`·`interview-questions`~~ → **2026-07-27 해결(#326).**
      소비처 0건 확인 후 제거. `max-retry`는 살아있어 유지(AiCallExecutor 소비).
- [ ] **tech-debt(LOW): Jackson 전역 빌드 정리 — 클래스패스 J2/J3 공존 (L-8)**. ⚠️ **정정**: #327 노트가
      "client-ai evaluator들이 J2"라 했으나 **틀렸다** — client-ai는 이미 `tools.jackson.module.kotlin`(J3)이고
      `ConferenceReferenceLoader`의 `com.fasterxml.jackson.annotation`은 J3가 유지하는 애노테이션 패키지라 정상이다.
      **소스 코드의 J2 잔재는 전부 제거됨**(core-api #327, db-core는 아래 PR). 남은 건 **빌드 의존성**:
      root `build.gradle.kts` subprojects 블록이 전역 J2 `jackson-module-kotlin`을 걸고, db-core는
      `spring-boot-starter-json`까지 있어 J2/J3 kotlin 모듈이 클래스패스에 공존(dead weight, 소스는 J3만 씀).
      → 후속: root subprojects J2 모듈 제거 + db-core `spring-boot-starter-jackson` 전환. blast radius 커서 별도 PR.
- [x] ~~**tech-debt(LOW): `be/gradlew` 실행 권한 없음(mode 100644)**~~ → **2026-07-22 해결.**
      `git update-index --chmod=+x be/gradlew`로 100755 커밋. 이제 clone 직후 `./gradlew` 바로 실행 가능
      (에이전트마다 chmod 우회하던 낭비 제거 — Phase 0 회고 Try ④).
- [ ] **DB: Neon→RDS 전환 — 폐기, 무료 한도 부족 시 재고 (07-21 판단)**. 트리거 = Neon 무료 티어
      (storage·compute 시간·연결수) 실제 부족. **RDS는 상시 과금**(db.t4g.micro ~$12–15/mo + storage + backup)
      이라 EKS destroy-after-use 규율·Fly fallback 전략과 배치 → 전환 시 3그림으로 갈림(①prod 완전이전=전략재론
      ②EKS 실습전용 ③하이브리드). Neon만 부족하면 RDS 외 Neon 유료·Supabase도 비교 대상. **prod DB를 EKS
      실습 클러스터 옆 RDS에 두지 말 것**(destroy 시 데이터 유실 or 규율 붕괴).
- [ ] **Spring 시작 시간 최적화** — 현재 cold start 시 2~3분 소요, 사용자 503 경험
  - 원인: 512MB shared CPU + Neon DB cold start + Flyway 실행 겹침
  - 방향: `spring.main.lazy-initialization=true` / `min_machines_running=1`(비용) / Neon PgBouncer

### 💰 EKS 비용 모델 — 실측 확정 (2026-07-22) 🔴 재조사 불필요

**전부 AWS Pricing API·Spot 이력으로 직접 조회한 값**(ap-northeast-2). 추정 아님.

| 항목 | 실측 단가 | 비고 |
|------|----------|------|
| EKS 컨트롤플레인 | **$0.10/hr** = $73/월 | 워크로드 무관 **고정비** |
| ALB | $0.0225/hr · LCU $0.008/LCU-hr | + 퍼블릭 IP ×2 AZ |
| 퍼블릭 IPv4 | **$0.005/hr** = $3.65/월/개 | 노드마다 1개(NAT 회피 설계의 대가) |
| EBS gp3 | **$0.0912/GB-Mo** | 기본 3000 IOPS·125MB/s 무료 |
| EBS 스냅샷 | $0.05/GB-Mo | 볼륨 유지보다 45% 싸나 복원 마찰 → **비추** |
| ECR 스토리지 | **$0.10/GB-Mo** | |
| t4g.small 스팟 | 평균 $0.00902 / 최고 $0.01090 | 7일 130샘플 |
| t4g.medium 스팟 | 평균 $0.01923 / 최고 **$0.03210** | 7일 127샘플 |
| t4g.medium 온디맨드 | **$0.0416/hr** | 스팟의 2.2배 |

**🔴 핵심: 고정비가 지배한다.** 노드를 0대로 해도 컨트롤플레인 $73 + ALB 세트 $29.6 = **월 $102.57**.
"인스턴스를 줄여서 버티기"가 통하지 않는 구조.

#### 상시 운영은 기각 — 자기 선례와 충돌
> 📌 **D-002** · 상태 `✅유효` · 영향 `docs/eks-session-sop.md`, `.claude/scripts/eks-reaper.sh`, `infra/aws-eks/2-cluster`, `infra/aws-eks/README.md`, Stage 0~5 전체

> ⚠️ **이 결정이 뒤집히면 destroy-after-use 규율 전체가 무너진다.** 리퍼(dead man's switch)·SOP의
> 세션 왕복 절차·`2-cluster`를 CI 매트릭스에서 빼둔 `guard-local-layers`가 전부 이 결정의 파생물이다.
> **재채택 유혹이 실재하는 결정**이다 — "잠깐만 켜두면 편한데"가 곧 월 $122~174다.
> 크레딧 잔액이 남아 보일 때 특히 흔들린다. 뒤집으려면 아래 표의 숫자를 **다시 실측**하고
> `design-change-procedure.md` 전 단계를 밟을 것.
| | Fargate (이미 명시적 기각) | EKS 상시 |
|---|---|---|
| 월 비용 | $35 | **$122~174** |
| $200 크레딧 수명 | 5.7개월 | **5~7주** |

README에서 "월 $35 = 5.7개월이라 절벽"이라며 기각한 안보다 **3.5~5배 비싸고 4배 빨리 끝난다.**

#### ✅ 확정 전략: destroy-after-use로 6개월 풀 사용 (크레딧 만료 6개월)
**제약은 돈이 아니라 시간이다.** 버퍼(스팟 최고가 + 잡비 10%) 적용 시간당 단가:

| 모드 | 시간당 | 용도 |
|------|-------|------|
| t4g.small ×1, ALB 없음 | **$0.13** | 인프라 학습(NetworkPolicy·RBAC·Helm·ArgoCD) — nginx 파드로 충분 |
| t4g.medium ×1, ALB 없음 | $0.16 | 실앱 배포 |
| **t4g.medium ×2 + ALB (풀 3서비스)** | **$0.25** | Phase 3 토폴로지 |

**주 25시간 × 26주(650h) 기준**: 인프라 학습 위주 $110 · 항상 풀 토폴로지 $163.
→ **6개월 내내 헤비하게 써도 크레딧이 남는다.** 다 태우려면 주 38시간 필요(비현실적).
- **삽질 비용**: 클러스터 완전 재생성 ≈ **$0.07** / 4시간 세션 $0.64 / 8시간 삽질 $1.28.
  **실패는 사실상 공짜** — 아낄 것은 크레딧이 아니라 "켜놓고 딴짓하는 시간".
- **절감 레버**: ①ALB는 필요할 때만(전체의 25%) ②인프라 학습은 t4g.small ③세션을 **길게 가끔**
  (생성 10~15분+삭제 10분 = 회당 25분 오버헤드 상각) ④**kind 부활 불필요** — 돈이 제약이 아니므로 07-16 폐기 결정 유지
- **잔액 활용**: 남는 크레딧으로 **막판 기간 한정 상시 데모**(3~6주)를 사서 구직·면접 시즌에 맞춤.
  끝나면 destroy → 영구 비용 0, prod는 Fly 복귀.

#### 영속 레이어 — 싸다, 반드시 분리할 것
**월 약 $2.3 / 6개월 $14 (크레딧의 7%)**: ECR 5GB $0.50 + EBS 20GB $1.82 + S3/DynamoDB ≈$0.
- **🔴 ECR 구멍**: `README:101,148`은 ECR을 **`2-cluster`(destroy 대상)** 소속으로 적어놨으나
  **실제 `.tf`엔 `aws_ecr_*` 리소스가 0건**(전수 grep). 계획대로 두면 **destroy마다 이미지 전멸**
  → 세션마다 Spring Boot 이미지 3개 재빌드·재푸시(5~10분×3) = destroy-after-use의 실질 마찰.
- **→ ECR은 `0-bootstrap`에 편입**(2026-07-22 확정). 새 레이어(`1-shared`) 신설안은 **폐기** —
  영속 대상이 ECR 하나뿐이라 레이어를 늘리면 `tofu init/apply` 대상과 CI 매트릭스만 증가한다.
  0-bootstrap은 이미 **계정 수준 공유·영속 인프라**(S3 state·DynamoDB·OIDC·IAM·예산)를 담고 있어
  성격이 같고, `infra-deploy.yml` 매트릭스에 이미 있어 **CI 변경도 불필요**. **lifecycle policy 필수**(무한 누적 방지).
- **EBS는 terraform이 소유하고 K8s는 static PV로 바인딩**(동적 PVC 아님). 근거: ①IaC-first 원칙
  ②ALB 고아와 같은 실패 모드 원천 차단 ③**학습 가치** — 동적 프로비저닝은 쉽고, 어려운 건
  "이미 있는 볼륨에 StatefulSet 붙이기"(`volumeHandle` static PV). **부수고 다시 지어도 데이터가
  그대로 붙는 것**을 확인하는 게 진짜 교보재.
- **EBS를 6개월 영속 유지한다** (월 $1.82 = 6개월 $11 = 크레딧 5.5%). *"한 번 확인하면 끝"*이라는
  초안 판단은 **철회** — 학습은 반복에서 나오고, **아래 실패 6종은 여러 번 밟아야만 만난다.**
  ⭐ **destroy-after-use 규율이 희소한 반복 기회를 공짜로 만든다**: 보통 학습자는 클러스터를 부술
  이유가 없어 이 경험 자체를 못 한다(kind는 EBS가 없고, 회사에선 플랫폼팀이 소유). 우리는 비용 때문에
  **어차피 매번 부수므로**, 그 사이클에 볼륨 재바인딩을 얹으면 **추가 작업 없이 매 세션 연습**된다.
- ⚠️ **반복해야만 만나는 실패 6종** (문서만 읽어선 안 잡힘):
  ①**AZ 불일치** → 파드 영구 Pending (노드가 매번 같은 AZ에 안 뜬다 — 운 나쁜 날에만 터짐)
  ②`reclaimPolicy: Delete` 실수로 볼륨 동반 삭제 ③PV `volumeHandle` ↔ 실제 volume ID 불일치
  ④**`claimRef` 잔존**으로 PV가 새 PVC를 안 받음(최다 정체 지점) ⑤StatefulSet `volumeClaimTemplates`가
  PVC를 자동 생성 → static PV와 충돌 ⑥기존 볼륨을 **포맷**해버림(복구 불가).

#### 🔴 DB 전략 — 환경별 분리 확정 (2026-07-22)
> 📌 **D-001** · 상태 `🔄부분무효` · 영향 `infra/aws-eks/2-cluster/rds.tf`, `docs/eks-tutorial-steps.md`, `infra/aws-eks/README.md`, Stage 2·3 · 재판정 `docs/eks-migration-log.md` 07-28 "RDS를 destroy-after-use로 Stage 2에 편입"

> **prod(Fly)는 Neon 그대로. EKS 학습 클러스터에서만 in-cluster PostgreSQL + 영속 EBS.**

> 🔄 **07-28 재판정 — 아래 "RDS 재탈락" 3개 사유 중 ①이 무효화됐다.** RDS는 **Stage 2에 편입**됐고
> 실제로 사용됐다(#339). 이 블록만 읽으면 "RDS는 기각됨"으로 오독하게 되므로 여기 명시한다.
> **07-29에 실제로 그 오독이 발생했다** — 이 역참조가 없어서였다.
> 단, **최종 목표가 in-cluster라는 결론 자체는 유지**된다(사유 ③이 살아남음). 아래 표 참조.

| 환경 | DB | 근거 |
|------|-----|------|
| **Fly (prod, 24/7)** | **Neon** (변경 없음) | 상시 필요 · **$0** · 관리형 백업/PITR/풀링 |
| **EKS (학습, 가동률 ~15%)** | **in-cluster PostgreSQL + 영속 EBS** | 자기완결형 풀스택 · K8s 스토리지 실습 |

- **코드 변경 0** — `application-prod.yml`이 `jdbc-url: jdbc:postgresql://${DB_HOST}/${DB_NAME}` 등
  **100% 환경변수 기반**(실측 확인). 환경변수만 다르게 주입하면 됨(`transport` 플래그와 같은 패턴).
  **Flyway 마이그레이션 12개**가 스키마를 자동 생성 → 시드 불필요.
- **왜 Neon을 못 걷어내나** (걷어내자는 검토 → 기각):
  ①**비용 동기 없음** — Neon은 현재 **$0**, 걷어내도 절감 0원
  ②**가동률 충돌** — 650h/4,380h ≈ **15%**. prod DB가 클러스터 안이면 **85% 시간 앱이 죽는다**
  ③**역설: in-cluster가 최고가** — 상시로 돌리려면 컨트롤플레인 $73이 따라붙어 **월 $125+**
  (Neon 무료 $0 · Supabase $0 · RDS $12~15 · Neon 유료 ~$19 **< in-cluster 상시 $125+**)
  ④관리형에서 얻던 **자동 백업·PITR·scale-to-zero·PgBouncer·HA**를 전부 자작해야 하고,
  그것들이 클러스터와 함께 85% 시간 죽는다 ⑤노드가 **스팟** — 회수 시 DB 파드 다운
- **왜 RDS가 아니라 in-cluster인가** (RDS 재탈락) — **⚠️ 3개 중 1개는 07-28에 무효화됨**:

  | # | 원래 사유 | 07-28 재판정 |
  |:-:|---|---|
  | ① | RDS는 **클러스터를 꺼도 상시 과금** → 15%만 쓰고 100% 지불, 6개월 $72~90 = 크레딧의 36~45% | 🔴 **무효.** 클러스터와 함께 destroy하면 성립하지 않는다. 원 기각안이 "RDS 상시 가동"만 상정했던 것 |
  | ② | "클러스터 밖 관리형 Postgres"는 **Neon이 이미 그거고 공짜**다 | 🟡 **절반만 유효.** prod DB 대체 목적엔 맞으나 **학습 목적엔 틀렸다** — Neon은 AWS를 안 가르친다 |
  | ③ | **배우려는 걸 안 가르친다** — RDS는 클러스터 밖이라 **EBS·PVC를 전혀 안 건드림.** README 학습 목표(StatefulSet·PVC·EBS)와 불일치 | 🔴 **여전히 유효** — 그래서 최종 목표는 여전히 in-cluster다 |

  → **결론(07-28)**: RDS는 in-cluster의 **대체가 아니라 Stage 2를 완성시키는 임시 조각**이다.
  Stage 3에서 in-cluster로 스왑하는 것은 계획 이탈이 아니라 **이 결정으로 복귀**하는 것.
  단 **`rds.tf`를 삭제하지는 않는다** — 지우면 튜토리얼 Stage 2가 재현 불가가 된다(변수 토글로 처리).
- **학습 워크로드로 Redis보다 Postgres가 낫다** (초안의 Redis 제안 **철회**): 캐시는 유실돼도 안 아파서
  `reclaimPolicy`를 대충 넘기게 된다. **긴장감이 학습을 만든다.** + 앱 연결에 코드 변경이 0이고
  실제 스키마(Flyway 12개)가 돈다.
- ⚠️ **"in-cluster Postgres는 설계와 모순"이라던 초기 경고는 *Neon 대체* 경우에만 유효**했다.
  **병행은 표준 패턴**(테스트 환경)이며 모순이 아니다.
- **부수 효과**: EKS 클러스터가 **외부 의존 0의 자기완결 스택**이 되어 NetworkPolicy·서비스간 통신
  실습이 깨끗해진다(외부 Neon egress 예외 처리 불필요).
- **Neon을 실제로 걷어낼 트리거**: 무료 한도(storage·compute 시간·연결수) 부족. 그때 후보는
  **Neon 유료 · Supabase · RDS**. **in-cluster는 그때도 답이 아니다**(상시 $125+).
  별건: **Neon cold start**(앱 cold start 2~3분의 한 원인)는 DB 이전이 아니라
  `min_machines_running`·lazy-init·PgBouncer로 푼다 — 백로그의 "Spring 시작 시간 최적화" 항목.

#### 앱은 영속 볼륨이 필요 없다 (전수 확인, 2026-07-22)
EBS는 **순수 학습 목적**이다. BE 코드 grep 결과 `MultipartFile`·`Files.write`/`FileOutputStream`·
S3 클라이언트·로컬 디스크 경로 설정 **전부 0건** — 파일을 디스크에 쓰는 코드가 아예 없다.

| 데이터 | 실제 저장소 | EBS 필요? |
|--------|------------|:---:|
| 앱 DB (사용자·퀘스트·진행도·AI 평가·`ai_call_log`) | Neon (AWS 밖) | ❌ |
| 컨테이너 이미지 | ECR | ❌ |
| K8s 매니페스트·ArgoCD 설정 | git (GitOps) | ❌ |
| 메트릭·로그 | Grafana Cloud (OTLP push) | ❌ |
| 프롬프트 `.st`·`conference-references.json` | jar 내부 리소스(읽기 전용) | ❌ |
| 이력서 PDF | **FE에서 pdfjs 파싱** → 텍스트만 전송, 파일 저장 없음 | ❌ |
| Terraform state | S3 | ❌ |

ArgoCD 기본 설치도 PVC 미사용(상태는 CRD, repo-server는 emptyDir), Prometheus는 in-cluster가 아니라
Grafana Cloud push → **인프라 컴포넌트도 영속 볼륨을 요구하지 않는다.**

#### destroy 시 데이터 생존 (코드로 전수 확인)
| 데이터 | 소속 | destroy 시 |
|--------|------|-----------|
| **앱 DB (Neon Postgres)** | AWS 밖 외부 SaaS | ✅ **완전 유지** (설계상 ai=DB없음, core/daily=Neon) |
| tfstate / VPC·서브넷 | 0-bootstrap / 1-network | ✅ 유지 (destroy 대상은 `2-cluster`뿐) |
| K8s 매니페스트·ArgoCD | git | ✅ 유지 |
| Grafana 대시보드·메트릭 | Grafana Cloud | ✅ 유지 |
| PVC/EBS | 클러스터 | ❌ 유실 — **단 현 설계에 스테이트풀 워크로드 없음**(Redis는 캐시라 무해) |
| ECR 이미지 | **미정** | ⚠️ 위 "ECR 구멍" 참조 |

- **NAT·EIP·ECR이 코드 어디에도 없음**(전수 확인) + 컨트롤플레인 로깅 OFF(`tfsec:ignore` 명시)
  → **destroy 후 잔존 비용 사실상 $0.** NAT 회피 설계가 여기서 값을 한다(있었으면 월 $32 계속).
- 🔴 **destroy-after-use가 실패하는 진짜 경로**: `tofu destroy`는 **state에 있는 것만** 지운다.
  K8s가 만든 **ALB(Ingress)·EBS(PVC)·NLB(Service LB)는 state에 없어 고아로 남아 계속 과금**된다.
  **순서가 규율**: `kubectl delete ingress --all -A` → `kubectl delete pvc --all -A` → 콘솔 확인 → `tofu destroy`.
  **월 $35 예산 알람이 울리면 1순위 용의자가 고아 ALB**(월 $16.43 + IP $7.30).

#### 학습 IaC → 실서비스 전환 가능성
**골격 100% 재사용.** 학습용으로 낮춘 것이 `cluster.tf` 주석에 근거와 함께 명시돼 있어
**prod 전환 체크리스트가 이미 코드에 있다**. 바꿀 곳 7개:

| # | 현재(학습) | prod 전환 | 비용 영향 |
|---|-----------|----------|----------|
| 1 | `endpoint_public_access=true`, `0.0.0.0/0` | CIDR 제한 / private | $0 |
| 2 | 컨트롤플레인 로깅 생략 | `enabled_cluster_log_types` | CloudWatch 요금 |
| 3 | secret KMS 암호화 생략 | KMS 암호화 | KMS $1/월+ |
| 4 | ~~`capacity_type="SPOT"` 하드코딩~~ → **변수화 완료** (`node_capacity_type`, 기본 `ON_DEMAND`) | tfvars로 SPOT 주입(쿼터 증액 후) | 노드비 2.2배 |
| 5 | 노드가 **퍼블릭 서브넷**+공인IP | 프라이빗 서브넷+NAT | **+$32/월** |
| 6 | `desired_size=1` | 2+ (다중 AZ HA) | 노드비 ×N |
| 7 | CI IAM이 admin 정책 | 최소권한 | $0 |

- **이미 변수화**(tfvars만 교체): 인스턴스 타입·노드 수·K8s 버전·클러스터명·**`capacity_type`**(07-23 추가)
- **하드코딩이라 코드 수정 필요**: `ami_type`·서브넷 선택·엔드포인트 설정
- 즉 *"학습 IaC를 그대로 prod에"*가 아니라 **"같은 골격에 prod 파라미터를 끼운다"** — 레이어 분리·
  remote backend·OIDC·IRSA·애드온 배선은 **전부 그대로 간다.** 그게 IaC 학습의 목표 그 자체.

#### 🔴 Free Plan 실측 확정 (2026-07-23, API·공식문서 3자 대조) — 위 "미확인 2건" 해소
계정 API(`accountPlanType`·Service Quotas)·공식 빌링 문서(`free-tier-plans.html`)·한국어 랜딩 대조.

| 확인 항목 | 결과 |
|-----------|------|
| 계정 플랜 | `FREE` · 크레딧 **$199.81 잔여** |
| 만료일 | **2027-01-15** (약 25주) — 📅 캘린더 등록 필요 |
| EKS가 Free Plan 제한 대상? | **아니다.** 제한 예시는 Savings Plans·RI·일부 Marketplace뿐. EKS 쿼터 100·dry-run 통과 |
| 초과 과금? | **없다.** "No charges incur during usage" — 대신 아래 폐쇄 |

- 🔴 **폐쇄 트리거 2개**: *"Account closes when credits are depleted **OR** when the plan duration ends."*
  **만료일만이 아니라 크레딧 소진도 즉시 계정 폐쇄**다. 한국어 랜딩은 이 문장을 통째로 누락 →
  "요금 안 나감"만 강조해 오해 유발. **돈이 아니라 계정이 대가.** (폐쇄 후 90일 content 보관, Paid 업그레이드 시 복구)
  → **안전 예비 $30 확보 규칙**: 사용 가능액 $170. 풀 토폴로지($0.26/hr) 654h·학습($0.14/hr) 1,214h = 여전히 충분.
  단 "다 태우기"를 목표로 삼지 말 것. prod는 Fly+Neon이라 계정 폐쇄돼도 무영향.
- 🟡 **Spot vCPU 쿼터=0은 Free Plan 제한이 아니라 신규 계정 기본값** — 증액 요청이 통할 수 있음.
  단 스팟↔온디맨드 650h 차이가 **$13뿐**이고 회수 중단이 사라지니 **온디맨드가 낫다**(nodes.tf 기본값 ON_DEMAND 확정).
- 🟡 **자동 Paid 전환 트리거 주의**: AWS Organizations 가입·Control Tower·Partner Network·Enterprise
  Agreement 등을 건드리면 **Free→Paid 자동 전환 → 초과 과금 시작**. 현 GitHub OIDC+IAM은 무관.
  **멀티계정 실습으로 Organizations를 만지면 그 순간 Paid**가 되니 로드맵에 들어가면 미리 인지.
- 🟢 **잔여 크레딧 이월**: Paid 업그레이드해도 남은 크레딧이 future bill에 자동 적용 → "만료 직전 업그레이드"가 손해 아님.

> **📅 사용자 액션**: 2027-01-15 크레딧 만료(계정 폐쇄)를 캘린더에 등록. (`.claude/TASKS.md` 참조)

---

### AWS EKS 학습 놀이터 — 새 시작점 (07-13 확정, 07-16 kind 트랙 폐기로 단독 트랙化)
- **계획 문서: `infra/aws-eks/README.md`** — 착수 전 반드시 읽을 것 (비용 분석·기각안 포함)
- **작업 일지: `docs/eks-migration-log.md` 실시간 유지 의무** — 규칙은 루트 `CLAUDE.md`
  "EKS 작업 일지 규칙" 참조. 블로그 원고 소스. 서브에이전트 위임 시 규칙 전파 필수
- **정답 경로 튜토리얼: `docs/eks-tutorial-steps.md`** — 성공 확인된 절차만.
  **캡처 없음**(07-29 폐기, 아래 참조) — 모든 확인은 명령어 + 기대 출력으로.
- **🎯 방향 확정 (07-16): IaC-first — "인프라 전부를 코드로, 콘솔 클릭 0".** 레이어별 state 분리
  `0-bootstrap`(remote backend·OIDC·IAM·예산·이상탐지) / `1-network` / `2-cluster`(destroy 대상) /
  `gitops`(ArgoCD). CI: plan-on-PR+tfsec, apply-on-merge(OIDC). 상세: `infra/aws-eks/README.md`.
  - **✅ 0-bootstrap 완료 (#283, 07-18):** backend(S3+DynamoDB)·state 이관·예산(콘솔판 삭제로 코드 일원화)·
    보안 CI(gitleaks+tfsec)·GitHub OIDC+IAM(admin+신뢰정책 강잠금)·plan/apply 파이프라인. PR→plan /
    merge→apply 양방향 OIDC 실증. 비용 $0. GitHub Secret `AWS_ROLE_ARN`·`BUDGET_EMAIL` 등록됨.
    로컬 자격증명 = `bootstrap-admin` 액세스키(`aws configure`, region ap-northeast-2).
  - **✅ 1-network 완료 (#285, 07-18):** VPC 10.0.0.0/16 + IGW + 퍼블릭 서브넷 ×2(2a/2c, 공인IP) +
    라우트. NAT 회피(퍼블릭+공인IP). EKS discovery 태그. CI 도그푸딩(matrix에 `1-network` 추가,
    merge→apply로 VPC 생성 실측). 비용 $0. 실측 VPC `vpc-0e8401b42ba207328`.
  - **✅ 2-cluster 코드 완성·머지 (#287, 07-20):** 컨트롤플레인+노드그룹+OIDC+애드온+Access Entry 11개 `.tf`.
    K8s 1.36 핀. `tofu plan=14 to add`. **로컬 apply/destroy 확정**(CI 미편입) + `guard-local-layers` 잡으로
    매트릭스 진입 차단.
  - **✅ Task 8 apply 왕복 완료 (07-24, ★첫 과금):** `apply`(14 added, ~10분·컨트롤플레인 7m54s) →
    `kubectl get nodes` **Ready**(v1.36.2·arm64 Graviton·공인IP 3.36.118.171·NAT 회피 확인) →
    `destroy`(14 destroyed) → **teardown 전수검증 고아 0**(state 비움·EBS·SG·LB·NAT 없음). **비용 ~$0.1 이하**
    (벽시계 ~50분). **2-cluster IaC 동작 실증 완료.** 검증 명령·개념 = `docs/eks-tutorial-steps.md` Step 8.
    kubectl 설치(v1.36.3). 노드그룹 ON_DEMAND(#314) 정상 프로비저닝 확인.
  - **✅ Stage 1 완료 (#324, 07-27):** ECR 0-bootstrap 편입(#322) + arm64 CI 빌드(#323) + core-api 배포.
    ECR→노드 이미지 pull 성공(노드 IAM, imagePullSecret 없이). DB 없이 CrashLoop 3단계 진단(Loki→JWT→DB).
    매니페스트 `k8s/base/core-api.yaml`(ClusterIP). teardown 고아 0·ECR 생존. ~$0.05.
  - **✅ Stage 2 완료 (#339, 07-28):** RDS(db.t4g.micro, destroy-after-use 편입) + Secrets Manager +
    **IRSA** + External Secrets Operator v2.8.0. core-api가 코드 변경 0으로 `/health` 200(RDS `sslmode=require`,
    Flyway 12개). 시크릿 수동 생성 0회 — ESO가 K8s Secret 2개(10키) 자동 생성·동기화.
    과금 26분 35초 ≈ $0.06, teardown 고아 0(RDS 관리형 시크릿도 자동 정리 확인). 퀴즈 통과.
  - **➡️ 다음: Stage 3 = EBS CSI + StatefulSet(in-cluster Postgres)** — **여기서 PVC가 처음 생긴다**
    → destroy 전 `kubectl delete ingress,pvc --all -A` 규율 첫 적용. 그 다음 4(ALB Ingress)·5(ArgoCD).
  - CI 관리 레이어 현재: `infra-deploy.yml` matrix `[0-bootstrap, 1-network]` (2-cluster는 로컬 전용이라 의도적 제외).
- **🖼️ 스크린샷 체계 전면 폐기 (07-29 확정)**: IaC-first라 게시물에 콘솔 캡처가 필요 없다는 결론.
  캡처 체크리스트·`<!-- 캡처 필요 -->` 자리표시·`docs/images/eks-tutorial/`·휘발성 캡처 알림 규칙 전부 제거.
  **콘솔로만 되는 절차를 만나면 캡처가 아니라 IaC 이관을 먼저 검토**한다(그렇게 예산·이상탐지가 코드가 됨).
  시각 자료가 필요하면 mermaid 다이어그램(`docs/architecture/`) — 버전 관리되고 diff가 보인다.
  규칙 전문은 루트 `CLAUDE.md` "스크린샷 규칙".
- 한 줄: **EKS를 OpenTofu로 세웠다 부수는 K8s 학습 놀이터.** destroy-after-use + $200 크레딧.
  **prod는 Fly($0) 그대로** (prod 이전은 검토 후 명시적 기각 — Fargate 상시 월 $35 = 크레딧 5.7개월 → 절벽)
- 착수 순서(IaC-first): 0-bootstrap(backend·OIDC·예산 코드) → CI(plan/apply) → 1-network →
  2-cluster(+즉시 destroy 왕복으로 teardown 체득) → gitops → 재현 검증. 상세 README 참조.
- ⚠️ NAT Gateway 금지(+$32/mo). Ingress·PVC를 tofu destroy보다 먼저 삭제. **tfstate git 커밋 금지**(public repo)
- **kind 로컬 트랙(Stage 1~3, `k8s/`)은 07-16 폐기·삭제** (#269) — README의 "kind 트랙 합류" 언급은
  무시, EKS 단독 트랙으로 진행. 복구 필요 시 git 히스토리(#225 시점) 참조

## 알아둬야 할 비자명적 결정

### 메타스페이스 조사 종결 — 누수 없음, 128m이 작동점보다 낮았을 뿐 (2026-07-15) 🔴
Grafana 7일 range 실측으로 확정. **재조사 불필요.**
- **이 앱의 메타스페이스 작동점 = 134.6 MiB.** 160m 하에서 **uptime 94.3h까지 평탄**(95포인트 연속 실측).
  #263의 `128m`은 **이 작동점보다 6.6 MiB 낮았다** → 죽는 게 필연이었다. 누수와 무관.
- **128m 창의 실제 모양**: 재시작 후 122.7 → 10.9h에 126.3 MiB(=상한의 **98.7%**) 도달 → **9시간 고정**
  (상한이 눌러서 Full GC로 버틴 것) → **uptime 19.9h에 전 지표 소실**(좀비화).
  **"34시간 후 OOME"는 틀린 기록** — 34h는 사람이 알아채고 #265를 배포한 시각. 실제 붕괴는 **~20h**.
- **누수 아님의 근거 3종** 🔴: ① 클래스 수 평탄~순감소(11분 +12개, 부하 중 -51개)
  ② **정지 30초 Δ=0 바이트** ③ 동일 부하 2R이 1R의 51%(감속). 시간이 아니라 **처음 밟는 코드 경로**가 키운다.
- **계단의 정체**: 매일 **00:00 UTC(=09:00 KST) DailyMailScheduler**가 돌 때 +4.2 MiB 점프 후 평탄.
  07-10·07-15 동일 패턴. 전형적 **지연 로딩**.
- **통합 가설(metaspace = RSS creep 3MB/h의 정체) 기각** 🔴 — 메타스페이스 성장은 0.73 MiB/h이고
  평상시 0이다. 3MB/h RSS creep을 설명 못 함. **QA 반론이 옳았다. 두 리스크는 별개.**
- **잔여 여유 25.4 MiB(16%)** — 신규 대형 의존성 추가 시 작동점 재확인할 것.
- **최대 교훈**: 이 사고의 답은 **배포 당시 이미 Grafana 그래프에 찍혀 있었다**(07-08 시점 135~137 MiB).
  **상한을 자르기 전에 그래프의 작동점을 먼저 본다.** 5분이면 막을 수 있었다.

### GC는 SerialGC다 — G1 아님 (2026-07-15) 🔴
- prod 실측: `gc="Copy"`(Serial Young) + `gc="MarkSweepCompact"`(Serial Old).
  512MB + shared-cpu-1x라 JVM 인체공학이 자동 선택(2코어 미만 & 1792MB 미만 → SerialGC).
- **`G1PeriodicGCInterval` 검토 항목은 폐기** — 전제부터 틀렸다. G1 옵션은 이 앱에서 전부 무효.
- #263의 2초짜리 `Pause Full`은 이상 현상이 아니라 **Serial Old 단일스레드 컴팩션의 정상 비용**.
- Serial은 **Full GC 때만 클래스를 언로드**한다. 힙이 45M/179M라 Full GC가 거의 안 돌아
  언로드가 사실상 정지 상태(11.8h에 379개). 메타스페이스가 상한을 쳐야 비로소 Full GC가 돈다.

### prod JVM 지표 조회법 — jcmd 없는 JRE 이미지 우회 (2026-07-15)
프로덕션 이미지는 **JRE 전용**이라 `jcmd`/`jmap`/`jstat`이 없다(`java jfr jrunscript jwebserver keytool rmiregistry`만).
어태치 기반 진단 불가. **대신 액추에이터를 머신 내부에서 친다** — `SecurityConfig`가 IP 화이트리스트라 무인증 통과:
```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
fly ssh console -a devquest-api -C "/bin/sh -c 'wget -qO- localhost:8080/actuator/prometheus | grep Metaspace'" < /dev/null
```
- 근거: `SecurityConfig.kt` → `/actuator/**` 는 `hasIpAddress('127.0.0.1') or ('::1') or ('fdaa::/16')`.
  외부에선 403, 내부에선 200. `/health`·`/actuator/health`만 공개.
- `fly ssh console`은 Windows에서 끝에 `Error: The handle is invalid`를 뱉지만 **출력은 정상** — 무시.
  `< /dev/null` 붙이면 tty 문제 완화.
- **주의**: `[metrics]` 섹션이 `be/fly.toml`에 없다 → **Fly는 앱 메트릭을 스크레이프하지 않는다.**
  JVM 지표는 오직 Grafana Cloud(OTLP push)에만 있다.

### Grafana Cloud 스택 접근법 (2026-07-15) — 좌표는 로컬에만 (이 레포는 PUBLIC)
> ⚠️ **스택 slug/URL은 여기 적지 않는다.** 이 레포는 공개라 테넌트 식별자를 남기면 표적 피싱의 과녁이 된다.
> **찾는 법**: Chrome에 grafana.com 세션이 살아 있다 → `fetch('/api/instances')` 하면
> `slug`·`url`·`status`가 나온다. instance id는 `application-prod.yml`의 `grafana.otlp.instance-id`와 일치하는지로 검증.
- **무료 플랜은 UI가 자동 슬립한다** — `/api/instances`가 `status: "paused"`, `pausedAt: null`로 보인다.
  **수동 pause가 아니다.** 스택 URL로 접속하면 `Grafana is loading...` 후 ~1분 내 기동. Prometheus 수집은 계속됨.
- 조회는 브라우저 세션으로 datasource proxy fetch (스크린샷 불필요):
```js
fetch('/api/datasources/proxy/uid/grafanacloud-prom/api/v1/query_range?query='
  + encodeURIComponent('jvm_memory_used_bytes{area="nonheap",id="Metaspace",application="devquest-api"}')
  + '&start=<epoch>&end=<epoch>&step=1800', {credentials:'include'}).then(r=>r.json())
```
- 스택 목록이 필요하면 grafana.com 로그인 세션에서 `fetch('/api/instances')`.
- **단위 함정**: Grafana는 **MiB(2²⁰)** 로 표기, actuator raw는 바이트. `138,162,760 B = 131.8 MiB`.
  MB(10⁶)와 섞어 비교하면 없는 문제를 만든다 (07-15에 실제로 오경보 냄).

### flyctl이 config.yml 토큰을 자동 로드하지 못함 (2026-07-15) — 매 세션 30분 낭비 방지
- 증상: `fly auth whoami` → `no access token available` (**로컬 판정, 네트워크 요청 없음**).
  토큰은 `~/.fly/config.yml`에 멀쩡히 있고(665자) fly.io 콘솔의 토큰은 **`Expires: Never`**. 만료 아님.
- 원인 불명 ⚪ (flyctl v0.4.54 / Windows). HOME 경로 이론은 반증됨 — Go는 Windows에서 `HOME`을 무시하고
  `USERPROFILE`을 본다 → `HOME=...` 실험은 **무효**다. 재현 시 그 실험 반복하지 말 것.
- **해결: env 주입** (이 프로젝트의 확립된 패턴, 과거 12회 사용. 리터럴 붙여넣기 0회):
```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
```
  값을 절대 출력하지 말 것 — 프리픽스만 찍어도 자격증명 실체화로 차단된다.

### Metaspace OOME 사고 + 힙 실측치 확보 (2026-07-14, #263→#265)
- **힙 실측: 사용 42MB / 커밋 117MB / 상한 179MB.** prod GC 로그로 직접 확인 🔴.
  힙은 남아돈다 — 향후 메모리 튜닝 시 힙을 되돌리거나 늘리는 방향은 근거 없음. **더 줄일 여지가 있는 쪽.**
- `MaxMetaspaceSize=128m`(#263)이 `OutOfMemoryError: Metaspace` 유발 → 프로덕션 다운.
  증상: `Pause Full (Metadata GC Threshold)` / `(Metadata GC Clear Soft References)` 2초짜리가
  **42M->42M로 아무것도 회수 못 하며 무한 교대 반복** = 메타스페이스 고갈 데스 스파이럴. → #265로 160m 복구.
  ⚠️ **"34시간 후"는 오기** — 07-15 Grafana 실측 결과 실제 붕괴는 **~20h**, 34h는 발견·수정 시각.
  ⚠️ 원인도 정정: "누수 의심"이 아니라 **128m < 작동점 134.6 MiB**. 위 07-15 항목이 최신·확정본.
- **진단 교훈**: "Major GC가 계속 돈다" ≠ "힙 부족". **GC 트리거 괄호를 먼저 읽어라** —
  `(Allocation Failure)`면 힙, `(Metadata GC *)`면 메타스페이스. 화살표 좌우가 안 줄면(42M->42M) 힙 문제 아님.
- **관측 교훈**: `-Xlog:gc`가 없었으면 이 진단 불가능했다 (#263이 우연히 같이 넣음). **제거 금지.**
  단 변경과 관측을 동시에 넣으면 "원래 있던 현상"과 "새로 생긴 현상"을 구분 못 하는 confound가 생긴다.
- **프로세스 교훈**: #263은 힙·메타스페이스·코드캐시 **3개를 실측 없이 동시에** 잘랐다
  (커밋 메시지에 "근사치, 배포 후 실측 검증 필요"라 스스로 명시하고도 배포).
  → 리소스 상한은 **live set 실측 후에** 자른다. 한 번에 하나씩.

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
