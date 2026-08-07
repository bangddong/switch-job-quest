# 리뷰 지적 원장 (Review Ledger)

> **이 파일은 커밋된다.** 브랜치가 삭제돼도, 세션이 끊겨도, 머신이 바뀌어도 살아남는 유일한 지적 저장소.
> 브랜치별 작업 파일 `.claude/qa-cache/<브랜치>.findings.md`는 **gitignore = 휘발성**이다.
> 그러니 **PR에서 안 고치기로 한 지적은 반드시 여기로 이관**해야 한다. 안 하면 사라진다.

## 규칙

| | |
|--|--|
| 언제 적나 | 브랜치 findings의 지적이 `deferred`로 판정될 때 — **PR 생성 전에** |
| 누가 적나 | orchestrator (qa-reviewer는 브랜치 findings까지만 담당) |
| 언제 지우나 | 지우지 않는다. 상태를 `closed`/`wontfix`로 바꾸고 근거를 남긴다 |
| 강제 장치 | `assert-qa-run.sh`가 `deferred` 지적의 원장 등재 여부를 검사 → 누락 시 PR 차단 |

**상태**: `open`(미처리) · `closed`(해소, 근거 필수) · `wontfix`(안 고치기로 확정, 근거 필수) · `obsolete`(코드가 바뀌어 무의미)

**출처 표기**: `<브랜치>/<F-ID>` — 훅이 이 문자열로 등재 여부를 찾는다. 형식을 바꾸지 말 것.

---

## 미해결 (open)

| ID | 출처 | 등급 | 내용 | 이관 사유 / 처리 방향 |
|----|------|:----:|------|---------------------|
| L-14 | `stage/eks-3b-verify/S-1` | **HIGH** | 🔴 **`random_password.postgres`가 `2-cluster` state에 있어, 클러스터를 destroy하면 in-cluster Postgres에 로그인할 수 없게 된다.** 재apply가 **새 비밀번호**를 만들어 Secrets Manager→ESO→앱으로 흘리는데, postgres 이미지는 `POSTGRES_PASSWORD`를 **`initdb` 시점에만** 쓴다 — 데이터 디렉토리가 이미 있으면 무시하고 옛 해시를 유지한다. 08-07 실측: 재구축 후 core-api `CrashLoopBackOff`, 서버 로그 `FATAL: password authentication failed for user "devquest"` | **원인은 D-004가 EBS에 적용한 논리를 비밀번호에 적용하지 않은 것** — *"볼륨과 수명이 같아야 하는 것은 볼륨과 같은 레이어에 둔다."* 영속 볼륨을 도입하는 순간 **볼륨 안에 구워지는 모든 것**이 같은 제약을 받는다. **처리 방향: `random_password.postgres` + 그 시크릿을 `0-bootstrap`으로 이동**(EBS와 동일 수명). 세션 내 복구는 `ALTER USER`로 했으나 그건 수동 우회다. ⚠️ **유료 구간에서 IaC 구조를 바꾸지 않기로 판단** — 검증 없는 변경을 과금 중에 쌓게 된다. **무과금 세션에서 처리** |
| L-15 | `stage/eks-3b-verify/S-2` | MEDIUM | 🔴 **readiness probe가 아무것도 검증하지 않는다.** `k8s/base/core-api.yaml:105`의 readinessProbe가 `/health`를 보는데, `HealthController.kt:10`은 **상수 문자열만 반환**한다(DB·커넥션풀 미확인). **DB가 죽어도 200 → Ready → Service가 트래픽 전달 → 전부 500.** 반대로 `/actuator/health`는 너무 빡빡하다 — 오늘 실측 **503**이었고 원인은 `Mail health check failed`(학습 클러스터엔 SMTP 없음). ⚠️ 누가 "actuator가 정석"이라며 probe를 바꾸면 파드가 영영 Ready가 안 되는데, **로그는 `Started DevQuestApplicationKt`로 정상**이라 디버깅이 고약하고, `strategy: Recreate`(3b에서 파드 상한 때문에 고정)라 **되돌아갈 구 파드가 없다** | **처방: readiness 그룹을 명시적으로 정의**(`management.endpoint.health.group.readiness.include=db,ping`). 지표를 끄거나(`mail.enabled=false`) threshold를 늘리는 것은 **무엇을 볼지 정하지 않은 채 신호만 조정**하는 것이라 같은 일이 반복된다. **`/health` 컨트롤러는 그대로 둔다** — liveness는 의존성을 보면 **안 되기** 때문(DB 흔들릴 때 전 파드 동시 재시작 → 장애 증폭). 상수 반환은 liveness용으로는 옳은 설계이고, 문제는 readiness에도 쓰인다는 것뿐. 🔎 **발견 경로: 이해도 퀴즈 Q5-b** — 사용자가 "probe를 `/health`로 되돌린다"를 골라 근거 코드를 여는 과정에서 드러났다. 퀴즈가 문서화 절차가 아니라 **탐지기**로 작동한 사례 |
| L-13 | `chore/design-integrity-content-assert/F-4` | LOW | **`.claude/qa-cache/`에 추적 중인 잔재 3개.** `.gitignore:48`에 등재돼 있으나 규칙 추가 전에 커밋된 파일이라 계속 추적된다(gitignore는 소급 적용 안 됨). `git ls-files .claude/qa-cache/` → 3건 | 이번 PR 범위는 검사기 파싱 수정이라 무관. 확장자가 없어 `--include='*.md'` 스캔 대상도 아니므로 **동작상 피해 없음**. `git rm --cached`로 정리하면 되나, 휘발성 파일이라 되살아날 수 있어 정리 시 재발 방지(훅/문서)를 함께 봐야 한다 |
| L-1 | `feat/phase1-ai-call-log-observability/F-2` | MEDIUM | **`ai.call.log.recorded{success=false}` 태그가 도달 불가.** `CacheMetricsAdvisor`가 정상 응답일 때만 `record()`를 부르고 `success = true`로 하드코딩한다(실패 경로는 조기 return). 어댑터 로직은 옳지만 운영에서 이 태그는 영원히 0이다 | client-ai 수정 = inprocess 경로 동작 변경이라 Phase 1의 "동작 불변" 원칙에 반함. **AI 실패율 관측은 Task 1.4의 HTTP 어댑터 에러 매핑으로 확보**하기로 했고 부분 확보됨. advisor 자체 개선은 **Phase 3 정리 대상** |
| L-2 | `feat/phase1-ai-api-rest-controllers/F-4` | LOW | `TechInterviewWireFormatContractTest`에 **wire format 계약과 에러 경로 계약이 혼재**. 파일명이 내용을 정확히 표현하지 못함 | 동일 `@SpringBootTest` 컨텍스트 재사용이 목적이라 기능 문제 없음. 리네임(`TechInterviewHttpContractTest` 등) 권고 수준 |
| L-3 | `feat/phase1-ai-call-log-observability/F-3` | LOW | `AiCallLogObservabilityAdapter.record(log: AiCallLog)`의 **파라미터명 `log`가 클래스 멤버 로거를 섀도잉**. `this.log`로 해소돼 동작은 정상이나 가독성 저하 | 파라미터명을 `entry`/`callLog`로 변경 권고 |
| L-4 | `feat/phase1-ai-api-config-migration/F-1` | LOW | **core-api `application.yml` 주석의 근거 서술이 과장.** "지금 지우면 inprocess 롤백이 깨진다"고 적었으나, 실제로는 `@Value` 기본값이 명시값과 동일해 지금 지워도 동작은 같다. 진짜 근거는 "향후 default drift 대비" | 결정(키 유지) 자체는 타당. **문구만 정정** 필요 |
| L-5 | `feat/phase1-ai-api-config-migration/F-2` | LOW | `AiConfigBindingTest`가 `Environment.getProperty` 기반이라, 실제 빈(`AiCallExecutor.maxRetry`)의 주입값 확인보다 **한 단계 간접적** | 동일 병합 property source를 조회하므로 실질 위험 없음. 더 강한 검증으로 올릴 여지만 있음 |
| L-6 | `feat/phase1-core-http-adapter/F-3` | LOW | `BaseAiHttpAdapter`가 응답을 항상 `String`으로 받은 뒤 파싱 → **메모리 이중화**(String + 파싱된 객체) | 406 회피를 위한 의도적 설계. AI 평가 결과 JSON 크기에선 실용상 무해. 응답이 커지면 재검토 |
| L-7 | `feat/phase1-core-http-adapter/F-4` | LOW | `be/clients/client-ai/build.gradle.kts`의 주석 *"core-api가 tools.jackson.databind.ObjectMapper 사용 중"*이 **1.4a 시점에 사실과 어긋났음** | ⚠️ **1.4b에서 Jackson 3로 되돌렸으므로 지금은 다시 사실일 가능성이 높다.** 확인 후 `obsolete` 처리 여부 판단 |
| L-8 | `refactor/jackson3-db-core/F-2` | LOW | **root `build.gradle.kts`의 subprojects 블록이 전역 J2 `jackson-module-kotlin`을 모든 모듈에 적용** + db-core는 `spring-boot-starter-json`(J2 계열)까지 있어 J2/J3 kotlin 모듈이 클래스패스에 공존(dead weight). 소스 참조는 이미 J3 0건 확인됨 | **전역 빌드 변경이라 blast radius가 커서 이번 PR 범위에서 의도적으로 제외.** 후속으로 root subprojects J2 모듈 제거 + db-core를 `spring-boot-starter-jackson`으로 전환. → CONTEXT 백로그가 실행 항목 소유 |
| L-12 | `stage/eks-3b-postgres-static-pv/F-7` | LOW | **`assert-no-csi-delete-tags.sh`는 런타임 조립 문자열을 탐지할 수 없다.** QA가 PoC로 실증: `format("%s.csi.%s/cluster","ebs","aws.com")` · `join(".", ["ebs.csi","aws.com/cluster"])` · `base64decode(...)` 셋 다 exit=0(통과)으로 빠져나간다. 문자열이 파일에 물리적으로 존재하지 않으면 grep은 진다 | 🔴 **고치지 않기로 확정 — 정적 텍스트 검사의 구조적 한계이지 구현 결함이 아니다.** 3라운드에 걸쳐 조였고 매번 새 우회가 열렸다(F-3 중괄호 → F-4 computed key → F-5 줄단위 제외 → F-6 .tfvars). 정규식을 더 정교하게 만드는 것은 *"3번 시도했는데 안 되네, 4번 더"* 에 해당한다. **막아야 할 현실적 실패는 선의의 한 줄**(*"CSI가 관리하게 하려면 태그를 붙여야지"*)이고 그건 이미 잡힌다. 의도적 우회자는 어차피 IRSA 정책을 직접 건드린다. **대신 우회 불가능한 검사를 추가했다** — `PERSISTENT-RESOURCES.md` §확인 명령 ③이 배포된 볼륨의 태그를 AWS에 직접 조회한다(최종 상태를 보므로 소스 문법과 무관). 스크립트 상단에도 역할·한계를 명시해 다음 사람이 또 조이지 않게 했다 | 
| L-11 | `chore/eks-persistent-guardrails/F-5` | LOW | **`.claude/scripts/`의 bash 로직에 자동 테스트가 없다.** 이번 PR이 `warn_orphan_volumes`(리퍼)와 영속 리소스 배너(세션 마커)를 추가했는데, 둘 다 회귀를 잡아줄 테스트가 없다. 리퍼는 **사람 없이 `tofu destroy -auto-approve`를 도는 스크립트**라 회귀의 대가가 다른 스크립트보다 크다 | **테스트 하네스 선례가 레포에 0건**이라 이 PR에서 만들면 "가드레일 추가" PR에 테스트 프레임워크 도입이 섞인다(리뷰 흐려짐 + blast radius). 이번엔 **수동 검증으로 대체**했다 — 목 `aws`를 PATH에 주입해 ①고아 경고 기록 ②복구 명령 출력 ③마커 자가청소를 실제로 태웠고, 마커 스크립트는 4개 분기(무관 명령·볼륨 0개·볼륨 2개·AWS 조회 실패)를 전부 실행 확인. **처리 방향**: bats 또는 순수 bash 러너로 `.claude/scripts/` 공통 하네스를 만드는 별도 chore. 트리거 = 스크립트가 하나 더 늘거나 리퍼를 다시 수정할 때 |
| L-10 | `stage/eks-3a-postgres-dynamic-pvc/F-3` | LOW | **`.claude/CONTEXT.md`가 765줄로 "80줄 초과 시 archive 이동" 규칙을 크게 위반.** 이 PR이 54줄을 더해 악화시켰다 | 이 PR이 만든 문제가 아니라 누적된 부채이고, **CONTEXT는 세션 간 상태의 단일 출처라 잘못 잘라내면 복구 비용이 크다.** 정리 자체가 판단(무엇이 아직 살아있는 결정인가)을 요구하므로 **전용 PR에서 집중해 처리한다.** 이번 PR에 끼워 넣으면 Stage 3a diff에 대규모 문서 이동이 섞여 리뷰가 흐려진다 |


## 처리 완료 (closed / wontfix / obsolete)

| ID | 출처 | 등급 | 내용 | 처리 |
|----|------|:----:|------|------|
| L-9 | `stage/eks-3a-postgres-dynamic-pvc/F-2` | ~~MEDIUM~~ | **`db_mode=rds` 경로가 실제 apply로 검증되지 않았다.** `one(aws_db_instance.main[*].master_user_secret[0].secret_arn)` 등 splat+중첩인덱스 표현식이 rds 모드에서 런타임에 올바른 ARN을 반환하는지 미확인 | ✅ **closed — 2026-08-07 유료 세션에서 실측 해소.** `tofu apply -var db_mode=rds` → `db_master_secret_arn = arn:aws:secretsmanager:ap-northeast-2:<account>:secret:rds!db-e0040ad0-…-n3Zsr3` (= `one()`이 런타임에 올바른 ARN 반환). 이어서 ESO가 그 **AWS 소유** 시크릿을 실제로 읽는 것까지 확인 — `SecretStore Ready=True store validated`, `externalsecret core-api-db SecretSynced`, Flyway 13개 적용 + `/health` 200. **D-001이 `rds.tf`를 남겨둔 근거("Stage 2 재현성")의 전제가 성립함이 확정됐다.** 상세: `docs/eks-migration-log.md` 08-07 |
| L-10 | `fix/timezone-consistency/F-2` | LOW | `-Duser.timezone` 수정을 실제 Alpine 이미지로 검증하지 못했음(개발 머신에 docker 없음) | ✅ **closed — #337에서 실측 완료.** colima 설치 후 `eclipse-temurin:21-jre/jdk-alpine`을 직접 실행해 확정. 배포 후 확인(TASKS TASK-8) **불필요**해져 해당 태스크도 종결 |
| L-9 | `fix/daily-question-window/F-2` | ~~LOW~~ | 🔴 **obsolete — 오진. 이 결함은 존재한 적이 없다.**<br>주장했던 내용: *"저장은 ambient zone, 조회는 명시적 KST라 어긋나서 ①데일리 메일이 09:00 cron에 우연히만 맞고 ②코딩 스트릭이 00:00~09:00 제출을 전날로 기록한다."* → **둘 다 사실이 아니다.** prod는 `TZ=Asia/Seoul`(#210, fly.toml / #324, k8s)로 **처음부터 KST**였으므로 저장·조회가 어긋난 적이 없다. | **#337에서 실측으로 반증.** `eclipse-temurin:21-*-alpine` 직접 실행: `TZ` 미설정 → `GMT` / **`TZ=Asia/Seoul` → `Asia/Seoul`** (이 이미지엔 tzdata가 **포함**돼 있다).<br>🔴 **오진 경로 2단계**: ①`be/fly.toml`이 아니라 루트 `fly.toml`을 grep → 파일 부재로 `\|\|` 폴백의 "없음"이 출력됐고 이를 검증된 사실로 보고 ②QA가 `TZ` 존재를 찾아낸 뒤, **우리 이미지를 재보는 대신 웹 검색의 일반론**("Alpine엔 tzdata 없음")을 채택 — 기존 결론을 살려주는 방향이라 더 위험했다.<br>**교훈: 일반론으로 특정 환경의 사실을 대체하지 말 것. `docker run` 한 줄이면 30초다.** (#336의 코드 변경 자체는 유지 — 아래 L-9-c) |
| L-9-b | `fix/timezone-consistency/F-3` | ~~LOW~~ | 배포 전/후 저장값 **불연속**(백필 미실시) | 🔴 **obsolete — 전제가 무너졌다.** prod가 원래 KST였으므로 #336 배포 전후로 저장 zone이 바뀌지 않는다 = **불연속 자체가 없다** |
| L-9-c | (#336 잔존 가치) | — | 오진이었지만 **#336의 코드 변경은 유지할 가치가 있다** | ✅ **유지 확정(#337).** ①`be/build.gradle.kts` 테스트 jvmArgs — CI 러너는 UTC이고 TZ가 없어 **테스트는 UTC, prod는 KST**로 돌고 있었다. 이건 실재하던 CI≢prod 갭이고 이 변경이 닫았다 ②`TimezoneConsistencyTest` — 위 갭의 회귀 가드 ③Dockerfile `-Duser.timezone` — TZ와 중복이지만 **배포 설정이 유실돼도 버티는 이중 안전장치**로 유지(주석을 사실대로 정정) |

---

## 이미 다른 곳에 기록된 것 (원장 중복 등재 안 함)

아래는 `.claude/CONTEXT.md`의 **백로그 / 다음 작업** 섹션이 소유한다. 원장에 이중 등재하지 않는다.

- `CodingQuestService.generateProblem`/`submitCode` 트랜잭션 재배치 보류 (#308 MEDIUM) → CONTEXT "서비스 분해 에픽" 잔존 리스크
- `be/core/core-api/src/main/resources/application-prod.yml`의 Boot 3 잔재 키 `server.error.*` (#308 LOW) → CONTEXT 백로그
- 죽은 설정 `devquest.ai.pass-score`·`interview-questions` (#306 부수 발견) → CONTEXT 백로그
- Jackson 2 잔재 (`CodingQuestService`·인터셉터 2종) (#308 LOW) → CONTEXT 백로그
