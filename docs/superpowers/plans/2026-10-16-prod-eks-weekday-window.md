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
| N-3 | 🟡 | `guard-local-layers` 는 `infra-deploy.yml` matrix 만 본다 → **새 워크플로가 2-cluster 를 apply 해도 초록** = 조용한 보호 소실. 의미 재정의 필요(D-002 파생물) | `infra-ci.yml:53-60` |
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

## 🔴 사용자 결정 대기 (아키텍처가 갈리는 것)

| # | 질문 | 추천 |
|:-:|---|---|
| Q-1 | **학습 세션과 prod 클러스터를 어떻게 가르나** (N-4c·N-17·U-1) | **같은 클러스터를 prod 전용으로 전환**, 간헐 학습 세션은 종료(Stage 0~5f 완료). 학습 실험은 창 안에서 *운영 작업*으로. 대안 = state key·cluster_name 분리(IaC 변경, 크레딧 이중 소모) |
| Q-2 | **계정 밖 백업·탈출 경로** (N-15·U-3) | **Neon 무료를 콜드 대기로 유지** — 매일 덤프를 Neon 에 복원(복구 리허설이 매일 자동으로 됨). 계정이 닫혀도 Neon 으로 DNS·Fly 만 돌리면 된다 |
| Q-3 | **창 길이** (N-12 LCU 최악이면 초과) | ALB LCU 를 **첫 창 1주 실측 후 확정**. 그 전엔 5h 로 시작(여유 ≈ $36) |

## 순서표

> 🔴 Q-1·Q-2 가 미결인 동안 2번 이후를 착수하지 않는다.

| # | 항목 | 다루는 것 | 선행 |
|:-:|---|---|---|
| 0 | 사용자 결정 Q-1~Q-3 | | — |
| 1 | **Flyway 공유 DB 안전** (BE) — 창 쪽은 `migrate` 안 함·`repair()` 제거 또는 한 쪽만, 이미지 동일성 강제 | U-2 · N-11 | 0 |
| 2 | **이미지 공급망** — main push 빌드, `latest` 금지, prod 태그 보존 | N-10 | 0 |
| 3 | **CI 역할** — main 전용 역할(0-bootstrap) + access entry 2개(로컬 admin + CI) + 가드 재정의 | N-1 · N-2 · N-3 · N-18 | 0 |
| 4 | **리퍼·마커 정책** — Q-1 결과대로. CI 클러스터의 dead man's switch(off 실패 감지) | N-4 · U-12 · U-13 | 3 |
| 5 | **DB 층** — auto-apply 밖 별도 레이어 or `prevent_destroy`·데이터 볼륨 분리·`ignore_changes=[ami]`, SG(`vpc_cidr` + Fly 이그레스), TLS verify-full, 자격증명 0-bootstrap + `db_mode=external`, kustomization 정리 | N-5~N-8 · U-1 · U-10 · U-18 · N-19 | 0 |
| 6 | **백업·복구 도구 + 계정 밖 사본 + 리허설** | N-9 · U-3 · N-15 | 5 |
| 7 | **Neon → EC2 데이터 이전 리허설**, JWT 키 통일(Fly 1회 회전), 레이트 리밋 공유(BE) | U-11 · U-6 · U-4 | 1 · 6 |
| 8 | **전환 워크플로** — on/off/dispatch, 공용 concurrency, Cloudflare API, 실패 시 Fly 복귀, 창 안 스모크 | U-7 · U-20 · U-22 · N-13 · N-14 | 2 · 3 · 4 |
| 9 | **창 중 Fly 정책** — 0대/1대 + `be-cd` 창 중 차단 | N-11 · U-21 | 8 |
| 10 | 첫 창 (ALB LCU 실측 시작) | N-12 | 전부 |

## 이번 범위에서 하지 않는 것

| 항목 | 왜 지금 안 하나 |
|---|---|
| Fly 메타스페이스 192m 상향 | D-016 과 독립. Fly 는 밤·주말 서빙을 계속하므로 별도 `fix/` PR 로 |
| IPv6 전용 DB (IPv4 $10.9 절감) | Fly IPv6 이그레스 미확인(⚪). EKS·ALB 는 IPv4 유지라 절감이 DB 몫뿐 |
| 퍼블릭 엔드포인트 축소 | 러너 IP 비고정(U-17). self-hosted 러너는 범위 밖 |
