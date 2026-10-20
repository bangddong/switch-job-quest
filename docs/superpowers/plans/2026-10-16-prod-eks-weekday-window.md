# prod 평일 창 운영 — EKS(창) · Fly(그 외) · EC2 자가 운영 Postgres (공유)

> 결정: 📌**D-016** (`infra/aws-eks/README.md` 「평일 창 동안 EKS 가 prod 를 서빙한다」). 이 문서는 **착수 순서와 블로커**를 소유한다.
> 선행 문서: `2026-09-27-prod-eks-timeboxed-migration.md`(D-014 `❌폐기`) — 그 Blindspot U-1~U-22 를 아래에서 재판정해 재사용한다.

## 결정 테이블

| 결정 | 선택 | 근거 |
|---|---|---|
| 형태 | 평일 창 6h 만 EKS 가 prod 서빙 · 그 외 Fly · 매일 왕복 | 사용자 2026-10-16 *"실제 돌리면서 EKS 의 실무 경험"* |
| 창 시각 | **미정** — 기본안 12~18 KST | cron 한 줄이라 바꾸기 싸다. 첫 창 전에 확정 |
| on/off | GitHub Actions cron 자동 + `workflow_dispatch`(on/off 입력) 수동 | 사용자 — 수동도 쉽게(학습 목적) |
| DB | EC2 t4g.micro 자가 운영 Postgres · 상시 · Fly·EKS 공유 · `1-network` 퍼블릭 서브넷 | 사용자. RDS($74) 대비 $36. README D-016 산술 |
| 예산 | 합계 ≈ $142 / 가용 ≈ $162 (여유 ≈ $20) | 단가 `aws pricing` 실측 · 누적 Budgets 실측 $7.329 |

## 첫 창의 선행 조건 (전부 충족 전 창을 열지 않는다)

1. 🔴 **Flyway 공유 DB 안전** — D-015 사유 ④. 두 배포가 같은 DB 에서 `repair()` 를 돌리지 않는다.
2. 🔴 **DB 백업 + 복구 리허설 통과** — 자가 운영 DB 의 유일한 안전망.
3. 🔴 **전환·복귀 자동화와 실패 시 Fly 복귀 경로** — 복귀가 실패하면 밤새 prod 장애.
4. 🔴 **01-15 탈출 경로 확정** — 유료 전환(DB 만 월 ~$12) 또는 데이터 반출.

## Blindspot (2026-10-16, Explore 읽기 전용)

### 기존 U-1~U-22 재판정 (판정이 바뀐 것만)

| ID | 새 판정 | 요지 · 근거 |
|---|---|---|
| U-1 | 🔴 커짐 | 학습·prod 가 **같은 state key·cluster_name**. `db_mode` 기본 `in-cluster`(`2-cluster/variables.tf:195-206`) — 플래그 하나 빠뜨리면 prod EKS 가 클러스터 안 DB 를 본다 = **창 6h 분 쓰기 분기** |
| U-2 | 🔴 커짐(매일) | EKS 이미지는 `ecr-push.yml` **PR 트리거** 빌드, Fly 는 main. 마이그레이션 집합이 다른 두 이미지가 매일 `repair()` |
| U-3 | 🔴 커짐 | 덤프 = prod 데이터인데 버킷이 **폐쇄 예정 계정과 같은 계정**(`0-bootstrap/s3-backups.tf:96-100`) → 탈출 경로가 못 된다 |
| U-4 | 🟡 대상 변경 | 이제 t4g.micro(1GiB) `max_connections`. 풀 최대 10×3=30. `minimum-idle: 0` 은 Neon 전제 |
| U-5 | 🟢 조건부 소멸 | 창(12~18)이 00:00·09:00 cron 을 안 품는다. **off 실패 시 09:00 중복 부활** |
| U-6 | 🔴 커짐(매일) | 전환 때마다 메모리 버킷이 갈라져 **1인 AI 한도 구조적 2배**. daily-api 에도 같은 store |
| U-7 | 🔴 커짐 | 전환 주체가 CI, 하루 2회. **ALB DNS 이름이 매일 바뀐다.** Cloudflare 는 IaC 아님 → API 토큰 + 레코드 갱신 단계 신설 |
| U-10 | 🔴 커짐 | 상시 DB 자격증명이 destroy 층(`2-cluster/secrets.tf`)·`recovery_window 0`. `db_mode` 에 `external` 값 없음 |
| U-11 | 🔴 커짐 | JWT 키가 Fly·EKS 다르면 **전환마다 전원 로그아웃**. Fly secrets 는 write-only → **AWS 키로 Fly 를 1회 회전**이 유일 해법 |
| U-12·13 | 🔴 성질 변경 | N-4 참조. 종료 주체가 사람(SOP §8)→CI, Ingress 선삭제에 K8s 권한 필요 |
| U-14 | 🟢 소멸(스케줄 경로) | 수동 dispatch 경로엔 남는다 |
| U-17 | 🟡 강화 | 러너 IP 비고정 → 엔드포인트 `0.0.0.0/0` 못 좁힌다. 이제 prod 컨트롤플레인 |
| U-18 | 🔴 커짐 | `k8s/base/kustomization.yaml:28` 이 `postgres-static.yaml` 포함, ESO kustomization 은 in-cluster 전용 → ArgoCD 경로면 U-1 과 같은 결말 |
| U-19 | 🟢 해소 | 창이 정의됐다 |
| U-20 | 🔴 커짐 | 트래픽 복귀 매일 ×2 |
| U-22 | 🔴 악화 | 스모크 05:23 KST = 창 밖 → **EKS 서빙을 한 번도 검증 안 함** |

U-8·U-9·U-15·U-16·U-21 변화 없음.

### 새 불일치 N-1~N-20

| ID | 등급 | 발견 | 근거 |
|---|:-:|---|---|
| N-1 | 🔴 | CI 로 2-cluster apply 하면 `access.tf` 의 `principal_arn = caller arn` 이 **STS 세션 ARN** → access entry 불가. 역할 ARN 으로 바꾸면 entry 1개라 **로컬 admin 이 kubectl 권한 상실** | `2-cluster/access.tf:6,12` · `cluster.tf:27` · `plans/2026-07-19-eks-2-cluster.md:468` |
| N-2 | 🔴 | 현 OIDC 역할은 신뢰정책에 `pull_request` 포함 + `AdministratorAccess` → access entry 추가 = **PR 로 prod 클러스터 admin**. 기존 금지(`prereqs.md:263`)와 정면 충돌. ⇒ **main 전용(sub=`ref:refs/heads/main`) 별도 역할 신설** 선행. `environment:` 를 붙이면 sub 가 바뀌어 assume 실패(⚪) | `0-bootstrap/iam-github-oidc.tf:37-54` |
| N-3 | 🟡 | `guard-local-layers` 는 `infra-deploy.yml` matrix 만 본다 → **새 워크플로가 2-cluster 를 apply 해도 초록** = 조용한 보호 소실. 의미 재정의 필요(D-002 파생물) | `infra-ci.yml:53-60` · → **8번으로 이관**(2026-10-19, 3번 B3-13) |
| N-4 | 🔴 | 리퍼·마커 ↔ CI 양방향 충돌. (a) CI 클러스터엔 마커 없음 → **off 실패 시 dead man's switch 0개**, 주말 방치 60h ≈ $15. (b) 마커 훅은 **아무 레이어** 로컬 apply 에 반응, cluster_dir 은 늘 2-cluster → DB 작업으로 1-network 를 apply 하면 2h 뒤 **리퍼가 prod 클러스터를 destroy**. (c) 학습·prod 가 같은 state key → **공존 불가** | `eks-reaper.sh:17,83,105,174` · `eks-session-marker.sh:39,48` · `2-cluster/backend.tf` |
| N-5 | 🟡 | `1-network` 는 `infra-deploy` matrix 에 있어 **머지 시 `apply -auto-approve`**. 여기 DB EC2 를 두면 인프라 PR 하나가 **prod DB 를 무인 replace** 가능(루트 EBS `delete_on_termination` 기본 true). PERSISTENT-RESOURCES 원장에 VPC 행도 없다 | `infra-deploy.yml:36,120` · `README.md:115` |
| N-6 | 🟡 | `1-network` 에 SG 0개·IPv6 0. DB SG 가 클러스터 SG 를 **ID 로 참조하면 매일 destroy 가 DependencyViolation** → `vpc_cidr` 로 허용. **Fly 이그레스 고정 IP 근거 0건** — 없으면 허용목록이 `0.0.0.0/0:5432` 로 무너진다 | `1-network/network.tf` · `outputs.tf:37-40` |
| N-7 | 🟡 | 두 앱 `sslmode=require` = 암호화만, **서버 인증 없음**(공용 인터넷 MITM). 기존 자체서명 CA 는 2-cluster in-cluster 전용이라 매일 재생성 | `application-prod.yml:43` · `2-cluster/postgres-tls.tf:30-75` |
| N-8 | 🟡 | 자격증명 진실 원천 2개(ESO↔Secrets Manager / 수동 `fly secrets`). 상시 자격증명은 `0-bootstrap` 으로(`postgres-password.tf` 선례) + 새 ExternalSecret + `db_mode=external` | `k8s/eso/` · `be/fly.toml:20-21` |
| N-9 | 🔴 | `db-backup.sh`/`db-restore.sh` 는 `kubectl exec` 전용·호스트 인자 없음이 **설계 원칙** → EC2 DB 에 못 쓴다. 인스턴스 프로파일·버킷 쓰기 정책 0건. **선행 조건인 복구 리허설의 도구가 없다** | `db-backup.sh:28,47` · `db-restore.sh:53` · `s3-backups.tf` |
| N-10 | 🔴 | `ecr-push.yml` 에 main push 트리거 없음, PR 이미지가 `latest` 도 push. lifecycle 10개 → **리뷰 전 이미지가 prod 에**, PR 이 몰리면 **어제 sha 만료 → 다음 날 on 잡 ImagePullBackOff** | `ecr-push.yml:100,109-110` · `0-bootstrap/variables.tf:130-134` |
| N-11 | 🟡 | 창 중 Fly 상태 미정. **0대**: `be-cd` 가 창 중 Fly 를 되살려 Flyway 동시 실행 · 복구 실패 시 00:00/09:00 cron 이 **어디서도 안 돈다**(메일 누락). **1대**: suspend → 18시 복귀 첫 요청 지연 | `be/fly.toml:26-28` · `be-cd.yml:44-45` |
| N-12 | 🟡 | 단가 $0.25/h 의 근거 t4g.medium ×2 는 **이 계정에서 launch 불가**(free-tier 타입만). 실제는 t4g.small ×3 ≈ **$0.218/h**(노드·ALB IPv4 포함). 그런데 **ALB LCU 최악 +$0.096/h 면 D-016 합계 ≈ $167 → 가용 초과** | `2-cluster/variables.tf:19-41` · `eks-cost-model.md:100` |
| N-13 | ⚪ | GitHub cron 은 UTC·지연·누락 가능, 60일 무활동 시 자동 비활성. on/off/dispatch 공용 concurrency 그룹 필요(state lock 경합) | `prod-smoke-daily.yml:15-16` · `infra-deploy.yml:15-17` |
| N-14 | ⚪ | 도메인 금지 validation 은 `learning_domain_name` 에만 → prod 인증서 새 변수는 막지 않는다. 실제 블로커는 Cloudflare 수동 검증 + 매일 바뀌는 ALB 호스트명 | `0-bootstrap/variables.tf:266-281` |
| N-15 | 🔴 | **계정 폐쇄 = prod DB 소멸.** 트리거가 만료일만이 아니라 **크레딧 소진**도다. 여유 $20 인데 N-4(a) 주말 방치 한 번이면 소진. 백업도 같은 계정. `eks-cost-model.md` 의 *"계정 폐쇄돼도 prod 무영향"* 전제(예비 $30 규칙의 근거)가 무너진다 | `eks-cost-model.md:228-240` |
| N-16 | ✅ | D-015 `✅유효` 공존 — **이 PR 에서 해소**(D-015 `❌폐기` + 재판정) | `infra/aws-eks/README.md` |
| N-17 | 🔴 | *"학습 클러스터를 prod DB 에 연결 금지"* 규칙과 D-016 은 **같은 클러스터가 학습·prod 를 겸하는 한 동시에 지킬 수 없다** | `eks-migration-log.md:377` · `2-cluster/secrets.tf:111-133` |
| N-18 | 🟡 | CI 역할 `AdministratorAccess` 의 근거가 *"학습 전용 계정"* — prod 가 들어오면 거짓. 파일 스스로 *"prod 계정이면 최소권한"* | `0-bootstrap/iam-github-oidc.tf:47-49` |
| N-19 | 🟡 | 영속 리소스 등재 규칙: "증가 상한" 칸이 비면 등재 미완료. EC2 DB 디스크 상한·감시 0건 | `PERSISTENT-RESOURCES.md:30-37` |
| N-20 | ⚪ | 하네스 동결 — 리퍼·마커·가드·새 워크플로 수정은 **D-016 이 실제로 막힌 증거**(N-3·N-4)가 있으므로 해제 조건 ① 에 해당. 그 PR 안에서 최소로, 차단·오탐 케이스를 함께 돌린다 | `CLAUDE.md` 「하네스 동결 규칙」 |

## 사용자 결정 (2026-10-16)

| # | 질문 | 결정 |
|:-:|---|---|
| Q-1 | **학습 세션과 prod 클러스터를 어떻게 가르나** (N-4c·N-17·U-1) | ✅ **같은 클러스터를 prod 전용으로 전환**, 간헐 학습 세션은 종료. 🔴 **단 학습 문서 정리가 끝난 뒤에 전환한다**(사용자). 대안(state key·cluster_name 분리)은 기각 |
| Q-2 | **계정 밖 백업·탈출 경로** (N-15·U-3) | ✅ **Neon 무료를 콜드 대기로 유지** — 매일 덤프를 Neon 에 복원(복구 리허설이 매일 자동). 계정이 닫혀도 Fly 의 DB 연결만 Neon 으로 돌리면 된다 |
| Q-3 | **창을 어떻게 여닫나** (N-12) | ✅ **켜기 = 수동만**(`workflow_dispatch` on). **끄기 = 18:00 KST cron 자동 + 수동 off 언제든.** 고정 창 길이 대신 **켜는 시각이 비용을 정한다** |

### Q-3 가 바꾸는 것

| 항목 | 영향 |
|---|---|
| 예산 통제 | 창 길이가 사람 손에 달린다 → **on 잡이 스스로 거부하는 게이트** 필요: Budgets 누적이 상한(예: 가용 − 남은 날 × 하루 최소분)을 넘으면 on 실패. 이른 시각 on 은 경고 |
| 끄기 cron | **매일**(주말 포함) 18:00 KST = `0 9 * * *` UTC. 꺼져 있으면 no-op — 주말 방치(N-4a)의 1차 백스톱 |
| U-5 | 🔴 **조건부 소멸이 취소된다.** 09:00 전에 켜면 `DailyMailScheduler`(09:00)가 EKS 에서 돈다 → **창 중 Fly 0대(N-11)가 필수**가 된다 |
| 켜는 방법 | GitHub 웹·모바일 앱 Actions 화면의 *Run workflow*, 또는 세션에서 `gh workflow run`. 원격 환경에서도 된다(로컬 셸 불필요) |

## 순서표

> 🔴 **0번(학습 문서 정리)이 끝나기 전에 클러스터를 prod 용도로 바꾸지 않는다**(Q-1).

| # | 항목 | 다루는 것 | 선행 |
|:-:|---|---|---|
| 0 | ✅ **학습 문서 정리** — 재현 검증 포함, 완료 2026-10-17 (#458) | Q-1 | — |
| 1a | ✅ **Flyway 공유 DB 안전 — BE + Fly** — 완료 2026-10-18 (#459, Fly 첫 release ≈ 50초 exit 0): 마이그레이션을 앱 기동에서 분리(C안), `repair()` 자동 경로 제거·수동 모드 격리, core-api jar 전용 모드 + Fly `release_command` | U-2 · N-11 · 아래 「1번 Blindspot」 | 0 |
| 1b | **Flyway — EKS 쪽 마이그레이션 Job** (`stage/`, 클러스터 필요): Sync wave(시크릿 뒤·core-api 앞) 훅, Job·Deployment 이미지 태그 단일 출처, 손 경로·복구 절차 갱신 | 「1번 Blindspot」 B-3·B-6·B-7·B-12·B-13 | 1a · 2 · 5 |
| 2 | ✅ **이미지 공급망** — main push 빌드, `latest` 금지, prod 태그 보존. 완료 2026-10-19 (#461, main 첫 빌드 3/3 · IMMUTABLE · 옛 `latest` 태그 제거): PR 은 빌드만(무자격증명) · main push 가 세 서비스 푸시 · 레포 IMMUTABLE · 태그 존재 시 건너뜀. 아래 「2번에서 안 하는 것」 | N-10 · U-2 · L-44 | 0 |
| 3 | ✅ **CI 역할** — main 전용 역할(0-bootstrap) + 옛 역할 좁힌 읽기 전용 + access entry 2개(로컬 admin + CI). 가드 재정의는 8번으로(B3-13). 진행: 아래 「3번」 PR A→B→C — A ✅ #464(역할·시크릿) · B ✅ #465(전환·축소, 임시 역할 plan 실측) · C ✅ #466 access entry 2개, 좁힌 PR 역할 첫 CI plan 통과(2026-10-19) | N-1 · N-2 · N-18 (N-3 → 8) | 0 |
| 4 | **리퍼·마커 정책** — Q-1 결과대로. CI 클러스터의 dead man's switch(off 실패 감지) | N-4 · U-12 · U-13 | 3 |
| 5 | **DB 층** (계획 2026-10-19 — 아래 「5번」: Fly 경로 = 6PN WireGuard 피어, 비번 = SSM·EC2 생성, TLS require, 5-0 실측부터) — auto-apply 밖 별도 레이어 or `prevent_destroy`·데이터 볼륨 분리·`ignore_changes=[ami]`, SG(`vpc_cidr` + Fly 이그레스), TLS verify-full, 자격증명 0-bootstrap + `db_mode=external`, kustomization 정리 | N-5~N-8 · U-1 · U-10 · U-18 · N-19 | 0 |
| 6 | **백업·복구 도구 + 계정 밖 사본 + 리허설** | N-9 · U-3 · N-15 | 5 |
| 7 | **Neon → EC2 데이터 이전 리허설**, JWT 키 통일(Fly 1회 회전), 레이트 리밋 공유(BE) | U-11 · U-6 · U-4 | 1 · 6 |
| 8 | **전환 워크플로** — on/off/dispatch, 공용 concurrency, Cloudflare API, 실패 시 Fly 복귀, 창 안 스모크 | U-7 · U-20 · U-22 · **N-3**(가드 재정의, 3번에서 이관 — B3-13·B3-14) · N-13 · N-14 | 2 · 3 · 4 |
| 9 | **창 중 Fly 정책** — 0대/1대 + `be-cd` 창 중 차단 | N-11 · U-21 | 8 |
| 10 | 첫 창 (ALB LCU 실측 시작) | N-12 | 전부 |

## 1번 Blindspot (2026-10-18, Explore 읽기 전용) — 1a 에서 안 하는 것

결정(사용자 10-18): **C안**(마이그레이션 분리) · `repair()` **수동 모드로 격리** · 실행기 = **core-api jar 전용 모드**.
1a 가 처리하는 것: 실행기·`repair` 격리·Fly `release_command`·verify 마커 2곳·`FlywayConfigTest`. 아래는 **1a 에서 안 하고 목적지가 정해진 것**.

| ID | 확도 | 내용 | 왜 지금 못 하나 → 어디서 |
|---|:-:|---|---|
| B-2 | 🟡 | D-015 ④ 의 메커니즘 서술 보정 — 단순히 *오래된* 이미지는 Flyway 기본 `ignoreMigrationPatterns=*:future` 로 최신 버전을 건드리지 않는다. 실제 발화 경로는 ⓐ중간 버전이 빠진 클래스패스(daily-api) ⓑPR 이미지의 다른 V14 ⓒ재번호 핫픽스 | 바이트코드 문자열 근거뿐(실행 미확인). 1a 에서 `repair` 가 자동 경로를 떠나면 셋 다 닫히므로 **서술 보정만 남는다** → 1a PR 에서 README D-015 재판정 표에 한 줄 |
| B-3 | 🔴 | ArgoCD **PreSync 훅은 매일 첫 sync 에 실패** — ESO(wave 0)·`core-api-db`(wave 1) 전에 돈다 | 클러스터 필요 → **1b** (Sync 훅 wave 2.5, `BeforeHookCreation`) |
| B-6 | 🟡 | daily-api(wave 4)도 `ddl-auto: validate` → 마이그레이션 뒤여야 | → **1b** |
| B-7 | 🔴 | 이미지 태그 패치가 `Deployment/core-api` 만 겨냥 → Job 태그를 따로 적으면 클러스터 안 스큐 재발 | → **1b** + 순서표 2(이미지 공급망) |
| B-9 | ⚪ | Fly release 머신의 이그레스 IP 가 앱 머신 고정 IP 와 다를 수 있음 → EC2 DB SG(N-6) 허용 목록에 영향 | DB 가 아직 Neon → **5번**(SG 설계 시 실측) |
| B-12 | 🟡 | *"core-api 가 유일한 마이그레이션 실행자"* 서술이 `k8s/README.md`·`kustomization.yaml` wave 3 주석·`daily-api.yaml`·양 `application-prod.yml` 에 박혀 있음. 손 경로에 Job 단계 없음 | EKS 는 1a 에서 동작이 안 바뀐다(기동 시 migrate 유지, repair 만 빠짐) → **1b** 에서 함께 |
| B-13 | 🟡 | 복구 절차(`eks-tutorial-steps.md` 백업 절, `db-restore.sh`, `PERSISTENT-RESOURCES.md`)가 *앱 기동 시 Flyway* 를 판정 도구로 씀. `selfHeal` 이 복구 중 Job 을 다시 띄워 `pg_restore` 와 경쟁 가능 | → **1b** + **6번**(복구 리허설) |
| B-14 | 🟡 | 새 코드가 `be-cd.yml` 의 `./gradlew test` 에 자동 편입(L-36 쟁점 재부상) | 실행기를 core-api 안에 두므로 **새 모듈 없음** — 1a 에서 쟁점 아님. 전용 모듈로 바꾸면 재검토 |
| B-16 | 🟡 | Neon 콜드 스탠바이(Q-2) 전환 시 누가 migrate 하는지 미정의 | → **6번**(복구·전환 리허설) |
| B-17 | ⚪ | 실행기를 db-core 에 두면 *storage 모듈에 main* — 선례 없음 | core-api 에 두므로 해당 없음 |

## 2번에서 안 하는 것 (2026-10-19)

결정(기본값, 되돌리기 싼 것): PR = 빌드만 · main push = 3종 푸시 · 태그 = 40자 sha 하나 · IMMUTABLE · 보존 개수 10 유지(이제 전부 main 이미지라 늘릴 근거 없음, 비용 불변).

| 항목 | 왜 지금 못 하나 → 어디서 |
|---|---|
| **배포할 태그 고르는 규칙**을 기계로 — ① main HEAD 에 이미지가 있으면 HEAD ② 없으면 `git log -1 --first-parent --format=%H origin/main -- be .github/workflows/ecr-push.yml`(🔴 `--first-parent` 필수 — 머지 커밋 병합 시 옵션 없으면 이미지 없는 브랜치 커밋을 돌려준다, QA F-1). 둘 다 ECR 에 없으면 on 실패(Fly 유지). `sort_by(imagePushedAt)` 금지(병렬 빌드라 푸시 순서 ≠ 커밋 순서, F-4) | `paths` 필터 때문에 main HEAD 에 이미지가 없을 수 있다. 소비자(on 잡)가 아직 없다 → **8번** |
| 푸시 실패 감지 — main push 의 ECR Push 가 빨개도 Fly 배포(`be-cd`)는 성공할 수 있다 → 그날 on 잡이 옛 이미지 또는 ImagePullBackOff | 위 규칙이 "없으면 on 실패"로 막는다 → **8번** |
| Fly·EKS 이미지 **하나로** — 지금은 같은 커밋을 각자 빌드(소스·마이그레이션 집합은 같음) | Fly 원격 빌더 → ECR 이미지 사용으로 바꾸면 `be-cd` 구조 변경. 스큐 위험(U-2)은 커밋 단위로 이미 닫혔다 → 필요해지면 별도 |
| OIDC 신뢰정책의 `pull_request` 제거 — 이 워크플로는 더 이상 PR 에서 assume 하지 않는다 | `infra-deploy.yml` 의 PR plan 이 같은 역할을 쓴다 → **3번**(main 전용 역할) |
| Job·Deployment 태그 단일 출처 (B-7) | 클러스터 필요 → **1b** |

## 3번 — CI 역할 (2026-10-19 착수)

### 결정

| 결정 | 선택 | 근거 |
|---|---|---|
| PR 경로의 AWS 권한 | **좁힌 읽기 전용** — state 는 `0-bootstrap/*`·`1-network/*` 만, 락 테이블 쓰기, describe. 2-cluster state·백업 버킷·시크릿 값·로그 본문은 Deny | 사용자 10-19. 머지 전 plan 미리보기 유지(0-bootstrap·1-network 는 머지 = 자동 apply). 쓰기 권한자 1명(실측 `collaborators` = 본인 admin), 포크 PR 은 OIDC 토큰 없음 |
| main 역할 권한 | Admin | B3-11 — 자기 신뢰정책을 관리하는 apply 주체라 `iam:*` 필수 = admin 동치. 유료 세션 중 권한 부족 정지 비용이 더 큼 |
| sub 형식 | `StringEquals ref:refs/heads/main`, `environment:` 안 씀 | B3-10 — environment 를 붙이면 sub 가 바뀌어 assume 실패 |
| 로컬 admin entry | IAM 사용자 `bootstrap-admin` ARN 을 `account_id` 로 조립(계정 ID 커밋 없음) | B3-8. tfvars 는 CI 에 주입되지 않아 CI apply 에서 빈다 |

### PR 순서 (자기 자격증명을 고치는 문제 — B3-1·B3-2)

| PR | 내용 | 머지 apply 주체 |
|:-:|---|---|
| **A** | main 전용 역할 `devquest-eks-github-actions-main` 생성만. 워크플로 변경 없음 | 옛 역할(Admin) |
| — | 시크릿 `AWS_ROLE_ARN_MAIN` 등록 (`tofu output -raw … \| gh secret set`, 값 비출력) | — |
| **B** | `infra-deploy` push·`ecr-push` → `AWS_ROLE_ARN_MAIN` (B3-3: 한 잡이라 event 로 분기) + **옛 역할 축소**(Admin 떼고 읽기 전용 + Deny) | **새 역할** — push 이벤트는 머지된 워크플로 정의로 돈다 |
| **C** | 2-cluster access entry 2개(로컬 admin + main 역할) · 문서(튜토리얼 개수·서술, README D-016 영향 목록) | — (클러스터 apply 때 적용) |

🔴 C 는 B **뒤**다 — 옛 역할이 Admin 인 동안 entry 를 추가해도 PR 이 스스로 entry 를 만들 수 있어 의미가 없다(B3-1).

### 3번 Blindspot (2026-10-19, Explore 읽기 전용) — 목적지

| ID | 확도 | 내용 | 목적지 |
|---|:-:|---|---|
| B3-1 | 🔴 | 옛 역할 Admin 인 동안 main 역할 보호 효과 0 — PR 이 `CreateAccessEntry`·`UpdateAssumeRolePolicy` 가능 | 위 PR 순서 |
| B3-2 | 🔴 | 역할(머지 apply)·시크릿(사람)이 다른 시점에 생긴다 → 3단계 | 위 PR 순서 |
| B3-3 | 🔴 | `infra-deploy` 는 plan·apply 가 한 잡·한 자격증명 스텝. `ecr-push` 도 같은 `AWS_ROLE_ARN` → 옛 역할만 축소하면 ECR 푸시가 깨진다 | **B** |
| B3-4 | 🔴 | `plan` 도 DynamoDB 락을 쓴다(PutItem/DeleteItem) → `ReadOnlyAccess` 만으론 실패 | **B** (락 테이블 한정 Allow) |
| B3-5 | 🔴 | 읽기 전용이어도 0-bootstrap state 의 `random_password` 평문(JWT prod·postgres)이 보인다. `ReadOnlyAccess` 는 전 버킷 `GetObject`(2-cluster state·백업 덤프) | **B** (prefix 한정 + Deny). 0-bootstrap 노출은 구조상 남음 — 위 결정에서 감수 |
| B3-6 | 🟡 | 5번이 0-bootstrap 에 Secrets Manager version 을 두면 PR plan 이 `GetSecretValue` 필요 → 실패 or PR 이 prod DB 비밀번호를 읽음 | **5번** — 0-bootstrap 에 secret **값**을 두지 않는 설계(값 주입은 별도 경로) |
| B3-7 | 🔴 | `access.tf` 의 `data.aws_caller_identity.current` 는 `irsa-fluent-bit.tf`·`irsa-karpenter.tf` 가 공유 — 지우면 깨진다 | **C** |
| B3-8 | 🔴 | 로컬 admin = IAM 사용자 `bootstrap-admin`(장기 키). 일지엔 *"OIDC 전환 후 키 폐기 예정"* — prod admin entry 로 박으면 그 계획과 반대로 굳는다 | **C** 는 이 사용자로. 키 폐기 시 entry 교체 — 그때 break-glass 경로(B3-11)도 함께 재설계 |
| B3-9 | 🟡 | `sub=main` 은 schedule·dispatch 도 포함 → 경계 = main 워크플로 정의 + 머지 권한(본인 1명) + 필수 체크. 리뷰 강제 없음(`required_pull_request_reviews: null` 실측, 1인 레포라 걸 수 없음 — QA F-1) | 감수(위 결정). `id-token: write` 는 infra-deploy·ecr-push 뿐 |
| B3-10 | 🟡 | `environment:` 를 붙이면 sub 변경 → assume 실패 | **A** 주석에 명시. 8번이 environment 를 쓰려면 신뢰정책도 함께 |
| B3-11 | 🟡 | main 역할 최소권한은 실질 불가(`iam:*` 필요) | 위 결정 |
| B3-12 | 🟡 | `github_actions_role_arn` 출력이 non-sensitive(같은 성질의 `acm_certificate_arn` 은 sensitive) | 새 출력은 sensitive(A). 옛 출력 정정은 **B** |
| B3-13 | 🟡 | 가드 재정의는 grep 이면 자기 주석에 오탐, `-chdir`·reusable workflow 로 회피 가능. **2-cluster 를 apply 하는 워크플로가 아직 없어 허용 경로를 반증할 대상이 없다** | **8번** — 지금 가드(*infra-deploy 매트릭스에 2-cluster 없음*)는 8번 전까지 여전히 참 |
| B3-14 | 🟡 | *"2-cluster 는 로컬 전용"* 서술(`karpenter-cleanup.tf:28`·`infra-deploy.yml`·`infra-ci.yml`·튜토리얼·README D-002 파생), 튜토리얼 *"main + pull_request, Admin"* | 튜토리얼 역할 서술 = **B**. "로컬 전용" 서술 = **8번**(실제로 거짓이 되는 시점) |
| B3-15 | 🟡 | D-016 영향 목록에 `iam-github-oidc.tf`·`infra-deploy.yml`·`ecr-push.yml` 없음. access.tf·oidc 를 단언하는 verify 마커 0건 | **C** |
| B3-16 | 🟡 | 튜토리얼 리소스 개수(access entry·assoc 2→4, 합계 29→31 등) | **C** |
| B3-17 | ⚪ | entry 를 `for_each` 로 바꾸면 state 주소 변경(세션 중 apply 면 순간 kubectl 거부). CI 역할 ARN 이 존재해야 entry 생성 가능할 수 있음 | **C** — 별도 리소스로 추가(주소 유지), A 머지 후 |

## 5번 — DB 층 (2026-10-19 계획)

### 결정

| 결정 | 선택 | 근거 |
|---|---|---|
| Fly 의 DB 주소 | **피어 IPv6 리터럴** `DB_HOST=[fdaa:…]` | 5-0 실측 — Java(musl)가 `._peer.internal` 해석 실패(A NXDOMAIN). 리터럴은 피어 삭제 전까지 고정 |
| 레이어 | **1-network** (D-016 사용자 결정 유지) + 보호: 데이터 볼륨 분리·`prevent_destroy`·`ignore_changes=[ami, user_data]` | 별도 로컬 레이어는 PR plan 없음·리퍼 N-4b 반응·D-016 문구 개정(B5-19) |
| **Fly → DB 경로** | **Fly 6PN 에 EC2 를 WireGuard 피어로** (`fly wireguard create`, 앱은 `<peer>._peer.internal`) | 사용자 10-19 — *"돈 드는 거 말고, 실무도 배우는"*. 기각: Fly 고정 이그레스 IP(월 $3.60 현금 · release 머신 적용 미명시 B-9) / `0.0.0.0/0`+TLS / Tailscale(외부 SaaS 추가). **5432 인터넷 노출 0 · release 머신 자동 해결 · tfsec ignore 불필요** |
| EKS → DB 경로 | VPC 사설 IP. SG 인바운드 = `vpc_cidr`:5432 **만** | 클러스터 SG ID 참조(rds.tf 선례)는 매일 destroy 시 DependencyViolation(N-6) — 선례를 뒤집는 근거(B5-6) |
| 퍼블릭 IP | **EIP 안 씀** — 자동 할당으로 충분 | WireGuard 는 EC2 가 **바깥으로** 연결, EKS 는 사설 IP → 아무도 퍼블릭 IP 로 들어오지 않는다. B5-4 가 설계로 소멸 |
| **비밀번호 원천** | **SSM SecureString(표준, 무료)** — EC2 가 첫 부팅 때 생성·기록(**덮어쓰기 금지**). IaC 밖 | 사용자 10-19. tofu state·사람 손을 안 탄다(B3-6·B5-1). 기각: Secrets Manager($0.40/월 — **회전이 핵심 이점인데 Fly 시크릿이 write-only 라 회전을 켤 수 없다**) / 사람이 생성 |
| TLS | **`sslmode=require` 유지**, 서버는 자체서명. verify-full 미룸 | 사용자 10-19. 서버 인증은 터널(WireGuard 상호 키 인증)·VPC 경계가 대신. BE 변경 0(B5-3) |
| 인스턴스 | t4g.micro, `credit_specification = standard`, IMDSv2 강제, 루트·데이터 암호화, SSM 접속(SSH 포트 없음) | B5-24(`unlimited` 초과 과금), B5-17(tfsec) |
| AMI | `data "aws_ami"` (SSM 공개 파라미터 금지) | B5-2 — PR 역할이 `ssm:GetParameter` Deny |

### 순서

| 단계 | 내용 | 비용 | 산출 |
|:-:|---|---|---|
| **5-0** | ✅ **실측 (2026-10-19) — 착수 가능.** Fly(nrt)→EC2 터널 Postgres 접속 성공(인바운드 0), RTT ≈ 29.7ms, 연결 0.26s. 🔴 **Java(musl)는 `._peer.internal` 을 못 푼다**(A 질의 NXDOMAIN) → `DB_HOST` = 피어 **IPv6 리터럴**(BE 변경 0). WireGuard `MTU = 1420` 명시(기본 8921). **Neon = Postgres 17**(콘솔 프로젝트 설정, 사용자 확인 10-20) → EC2 = 17. Neon 리전 = `ap-southeast-1`(싱가포르) | ≈ $0.01 | 일지 |
| **5-A** | **IaC (1-network) — apply 하지 않음**: EC2·데이터 볼륨·SG·인스턴스 프로파일(`ssm:Get/PutParameter` 경로 한정)·`user_data`(Postgres·WireGuard 설치, 비밀 없음 B5-18④). 데이터 볼륨 AZ = 노드와 같은 2a(cross-AZ 회피, B5-7) | $0 | PR. plan 리뷰 |
| **5-B** | **EKS 배선**: `db_mode=external`(기본값은 유지 — verify 마커 B5-15), ESO **ParameterStore** SecretStore + ExternalSecret, IRSA 정책 확장(B5-16), ESO·gitops kustomization 정리(B5-14·U-18) | $0 | PR |
| **5-C** | **운영 문서·안전장치**: SOP §8b destroy 게이트를 태그 필터로(B5-13), PERSISTENT-RESOURCES 행·증가 상한(N-19·B5-20), 비용 모델 재산정(B5-25), D-004·D-016·`secrets.tf` 원칙 개정(B5-22·B5-23, `design-change-procedure`) | $0 | PR |
| 🔴 apply | **6번 직전에 따로 승인** — 상시 과금 시작(월 ≈ $13 + 데이터 볼륨) | 과금 | — |

### 5번 Blindspot (2026-10-19, Explore 읽기 전용) — 목적지

| ID | 확도 | 요지 | 목적지 |
|---|:-:|---|---|
| B5-1 | 🔴 | 비번 원천 선택지가 좁다 — 0-bootstrap `random_password` 는 PR 이 읽고, tofu 관리 SSM 은 PR plan 을 깨뜨린다 | ✅ 결정(SSM·IaC 밖) |
| B5-2 | 🔴 | AMI 를 SSM 공개 파라미터로 고르면 PR plan 실패 | **5-A** `data "aws_ami"` |
| B5-3 | 🔴 | verify-full 은 BE(하드코딩 sslmode)·CA 파일 배포·CA 키 state 노출까지 | 미룸(결정). 할 때: sslmode env 화 + Neon 복귀 호환 |
| B5-4 | 🟡 | 자동 퍼블릭 IP 는 바뀐다 | ✅ 설계로 소멸(아무도 퍼블릭 IP 로 안 들어옴) |
| B5-5 | 🟡 | EKS 가 퍼블릭 주소로 붙으면 `vpc_cidr` 규칙 밖 | **5-B** — EKS 는 사설 IP/사설 DNS |
| B5-6 | 🟡 | rds.tf 의 *"SG ID 참조"* 선례와 반대 | 위 결정표 근거 |
| B5-7 | 🟡 | 데이터 볼륨 AZ 출처, 1-network→0-bootstrap 의존·병렬 apply | **5-A** — 1-network 자체 `azs[0]`(2a) |
| B5-8 | 🟡 | 커넥션 최악 70+, 1 GiB 메모리, DB 재부팅 = Fly·EKS 동시 장애, 패치 창 미정 | **5-A**(`max_connections`·메모리 튜닝) + **7번**(부하) + **6번**(패치 절차) |
| B5-9 | 🟡 | daily-api 스키마 분리 미구현(같은 DB·계정), EKS 에만 뜸 | 변경 없음 — 기록만 |
| B5-10 | 🟡 | Fly 도쿄 ↔ DB 서울 RTT, `minimum-idle: 0` | ✅ 5-0: RTT 29.7ms, 연결 0.26s. 현재 Neon 은 **싱가포르** → 도쿄↔싱가포르보다 짧을 가능성 🟡(비교 실측 없음) |
| B5-11 | 🟡 | Postgres 메이저 미정, Neon 버전 미확인(TASK-9) | ✅ Neon **17** → EC2 17 (Ubuntu 24.04 기본은 16 — 5-A 는 PGDG 또는 AL2023 `postgresql17`) |
| B5-12 | 🟡 | Fly 시크릿 write-only → 전환 시 Neon 자격증명 소실 = Q-2 복귀 경로 소멸 | **7번** — 전환 **전** Neon 자격증명을 SSM 에 보관 |
| B5-13 | 🔴 | SOP §8b *"running 인스턴스 0"* 게이트가 상시 DB 로 영구 실패 | **5-C** |
| B5-14 | 🔴 | external 모드에서 ESO `postgres-tls` 원천 부재 → ArgoCD wave 1 영구 정지 | **5-B** |
| B5-15 | 🔴 | `db_mode` 기본값 변경 시 튜토리얼 verify 마커 실패 | **5-B** — 기본값 유지, 전환 워크플로(8)가 `-var` |
| B5-16 | 🟡 | ESO key·IRSA 가 2-cluster 시크릿에 하드코딩 | **5-B** |
| B5-17 | 🟡 | tfsec hard fail(IMDSv2·암호화), SG description ASCII 만(apply 에서야 실패) | **5-A** |
| B5-18 | 🟡 | 1-network 자동 apply 부작용 — `user_data`/`instance_type` 변경 = 무인 stop/start, 서브넷 변경 = replace 시도, CI 는 TF_VAR 미주입, `user_data` 는 PR 이 읽음 | **5-A** — `ignore_changes`, 비밀 없는 `user_data`, 변수 기본값만 |
| B5-19 | 🟡 | 별도 로컬 레이어의 비용 | 기각(위 결정표) |
| B5-20 | 🟡 | 원장·비용 모델·D-016 영향 목록 서술이 거짓이 됨 | **5-C** |
| B5-21 | ⚪ | 0-bootstrap 학습 EBS(10GiB) 운명, alpine→glibc 콜레이션 | **7번 이후** — 재사용 안 함, 원장 제거 절차 |
| B5-22 | 🟡 | `secrets.tf` *"prod 실제 값을 쓰지 않는다"* 원칙이 처음 깨진다 | **5-C** (`design-change-procedure`) |
| B5-23 | 🟡 | D-004 에 "1-network = 영속 + **가변**" 축 추가 필요 | **5-C** |
| B5-24 | ⚪ | T4g 기본 `unlimited` 크레딧 | **5-A** `standard` |
| B5-25 | 🔴 | D-016 DB $36 에 데이터 볼륨·스냅샷·전송·CloudWatch 미산입 | **5-C** 재산정 (Fly 고정 IP 는 기각으로 소멸) |

### 미룬 것

| 항목 | 왜 지금 안 하나 |
|---|---|
| verify-full TLS | 터널·VPC 가 서버 인증을 대신 — BE·Fly 배포를 늘릴 근거 부족(사용자 결정) |
| 비번 자동 회전 · Secrets Manager | Fly 가 AWS 저장소를 직접 읽게 되면(OIDC → SSM) 가능. 그 전엔 회전이 Fly 를 깨뜨린다 |
| mTLS 공개 포트 | 5-0 ① 이 막힐 때의 대안. 또는 터널 위 방어 한 겹으로 나중에 |

## 이번 범위에서 하지 않는 것

| 항목 | 왜 지금 안 하나 |
|---|---|
| Fly 메타스페이스 192m 상향 | D-016 과 독립. Fly 는 밤·주말 서빙을 계속하므로 별도 `fix/` PR 로 |
| IPv6 전용 DB (IPv4 $10.9 절감) | Fly IPv6 이그레스 미확인(⚪). EKS·ALB 는 IPv4 유지라 절감이 DB 몫뿐 |
| 퍼블릭 엔드포인트 축소 | 러너 IP 비고정(U-17). self-hosted 러너는 범위 밖 |
