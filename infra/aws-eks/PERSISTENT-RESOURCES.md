# 영속 AWS 리소스 원장 (Persistent Resources Ledger)

> **이 파일은 커밋된다.** 세션이 끝나도, 클러스터를 부숴도, 머신이 바뀌어도 살아남는
> "지금 AWS에 상시로 떠 있는 것"의 유일한 목록이다.
>
> `.claude/eks-session/`(마커·하트비트)는 gitignore = 휘발성이고, `tofu state`는
> **destroy 대상 레이어만** 담는다. 둘 다 영속 리소스를 기억하지 못한다.

## 왜 이 파일이 필요한가

destroy-after-use 규율은 **"세션이 끝나면 전부 사라진다"** 를 전제로 설계됐다.
그 전제가 참인 동안은 잊어버려도 안전했다 — 리퍼가 지우고, 고아 검사가 0건을 확인했다.

**영속 리소스는 그 전제를 깬다.** 그리고 깨는 방식이 고약하다:

| | destroy 대상 (2-cluster) | **영속 리소스** |
|---|---|---|
| `kubectl`에서 보이나 | 세션 중엔 보임 | **안 보임** (클러스터 밖) |
| `tofu state list` | 나옴 | **안 나옴** (다른 레이어) |
| 세션 종료 후 | 사라짐 | **남는다. 계속 과금** |
| 잊으면 | 리퍼가 지움 | **아무도 안 지움** |

> 🔴 **보이는 층과 돈 나가는 층이 분리돼 있다.** 그래서 "까먹는다"가 부주의가 아니라
> 구조의 기본값이다. 이 원장 + 세션 시작 배너 + 고아 검사 분리가 그 구조를 뒤집는 장치다.

## 규칙

| | |
|--|--|
| 언제 적나 | 세션과 함께 사라지지 **않는** AWS 리소스를 만들 때 — **PR 생성 전에** |
| 무엇을 적나 | 왜 영속인지 · **무엇이 증가를 막는지** · 월 비용 · 재검토일 · 제거 절차 |
| 언제 지우나 | 지우지 않는다. `제거됨` 행으로 옮기고 날짜·근거를 남긴다 |
| 검증 | 아래 §확인 명령 — **개수·크기가 표와 일치**해야 한다 (0건이 아니라 *일치*) |

🔴 **"증가 상한" 칸이 비어 있으면 등재를 완료하지 마라.** 상한 없는 영속 리소스는
조용히 자라는 것이 기본값이다 — ECR이 lifecycle policy 없이 시작했다면 지금 몇 GB인지
아무도 몰랐을 것이다.

---

## 현재 영속 리소스

**합계 ≈ $1.08/월** (ECR $0.17 + EBS $0.91 + S3 백업 ~$0.00). 6개월 ≈ $6.5 = 크레딧의 3.2%

> ⚠️ **$1.09 → $1.08 정정 (2026-09-13, QA F-6).** 항목 합은 계속 $1.08 이었는데 합계만
> $1.09 로 적혀 있었다. 이 PR 에서 S3 행을 추가하면서도 **재검산하지 않아** 그대로 넘어갈 뻔했다.
> 🔑 **표에 행을 더할 때 합계를 다시 더하지 않으면, 그 합계는 행이 하나 적던 시절의 값이다.**

> ℹ️ S3 백업 버킷은 덤프가 KB 단위라 반올림하면 $0 다. **그래도 표에 넣는다** — 금액이 아니라
> *존재*가 등재 기준이고, 상한 없는 영속 리소스는 조용히 자라는 것이 기본값이기 때문이다.

| 리소스 | 레이어 | 왜 영속인가 | 증가 상한 | 월 비용 | 시작 | 재검토 |
|---|---|---|---|---|---|---|
| **S3** `devquest-eks-tfstate-seoul` | 0-bootstrap | tfstate 원격 백엔드. 지우면 모든 레이어의 state 유실 | 상태 파일 수 KB 단위 | ~$0 | 2026-07 | — |
| **DynamoDB** `devquest-eks-tflock` | 0-bootstrap | state 잠금(동시 apply 방지) | 온디맨드, 항목 1개 | ~$0 | 2026-07 | — |
| **ECR** `devquest/core-api` | 0-bootstrap | 이미지가 destroy마다 사라지면 세션당 재빌드 5~10분 | 🔒 **lifecycle 10개** (실측 10개 = 1.74 GB) | **$0.17** | 2026-07-27 | 2027-01-15 |
| **ECR** `devquest/ai-api` | 0-bootstrap | 위와 동일 (Phase 2 대비) | 🔒 lifecycle 10개 (현재 0개) | $0 | 2026-07-27 | 2027-01-15 |
| **ECR** `devquest/daily-api` | 0-bootstrap | 위와 동일. Stage C 3서비스 배포 대상 | 🔒 lifecycle 10개 (현재 0개) | $0 | 2026-08-29 | 2027-01-15 |
| **IAM** OIDC 프로바이더 · GitHub Actions 역할 | 0-bootstrap | CI가 AWS에 붙는 통로 | 고정 | $0 | 2026-07 | — |
| **S3** `devquest-eks-backups-seoul` | 0-bootstrap | **백업은 자기가 백업하는 대상보다 오래 살아야 한다.** 데이터 볼륨이 이 레이어에 있으므로 백업도 이 레이어(D-004·L-14 규칙의 3번째 적용) | 🔒 **lifecycle 30일 × 3종** — `expiration` + `noncurrent_version_expiration` + `abort_incomplete_multipart_upload`. ⚠️ 버저닝이 켜져 있어 **앞의 하나만으로는 상한이 아니다** | ~$0 (덤프 KB 단위) | 2026-09-12 | 2027-01-15 |
| **Budgets** ×2 (`credit-010-100`, `credit-110-200`) | 0-bootstrap | 누적 크레딧 소진 알림 20단계 | 🔒 예산당 알림 10개(AWS 상한) | **$0** ※ | 2026-07-31 | — |
| **Cost Anomaly** `devquest-eks-service-monitor` | 0-bootstrap | 이상 지출 감지(DAILY, $5) | 계정당 DIMENSIONAL 1개 | $0 | 2026-07-29 | — |

> ※ **알림 전용 예산은 무료다.** Pricing API 실측(2026-07-31): `BudgetsUsage` = $0.00,
> 상위 과금 구간 자체가 없다. 유료인 것은 Budget **Actions**(자동 조치형)뿐이고 우리는 안 쓴다.
> *"예산을 많이 만들면 돈이 든다"* 는 옛 모델(첫 2개 무료 후 $0.02/일)의 잔상이다.

### EBS — Postgres 데이터 볼륨 (Stage 3b)

| 리소스 | 왜 | 증가 상한 | 월 비용 | 재검토 |
|---|---|---|---|---|
| **EBS** 10 GiB gp3 · `ap-northeast-2a` · 암호화 | static PV 재바인딩 실습은 **볼륨이 세션을 넘어 살아야** 성립한다 | 🔒 고정 크기 · **정확히 1개** · 크기 검증 1~100GiB | **$0.91** | 2027-01-15 |

> **이 볼륨은 PR이 머지되는 순간 CI가 생성한다** (`infra-deploy.yml`이 0-bootstrap을 자동 apply).
> 이 레포에서 **"머지 = 과금 개시"는 이때가 처음**이다.

**🔒 삭제 방지 3중 (강한 순서대로)**

| # | 장치 | 무엇이 막나 | 뚫리나 |
|:-:|---|---|---|
| ① | **CSI 삭제 태그 미부착** | `AmazonEBSCSIDriverPolicy`가 `DeleteVolume`을 `ebs.csi.aws.com/cluster`·`CSIVolumeName`·`kubernetes.io/created-for/pvc/name` **태그 조건으로만** 허용 → 셋 다 없으면 CSI 컨트롤러에 삭제 권한 자체가 없다. `CreateTags`도 생성 시점으로 제한돼 **스스로 권한을 얻을 수도 없다** | 🔴 **AWS IAM이 거부.** 코드 수정으로 못 뚫음 |
| ② | `lifecycle { prevent_destroy = true }` | 실수로 `tofu destroy`를 걸어도 거부 | 🟡 lifecycle 블록을 지우면 뚫림 |
| ③ | PV `persistentVolumeReclaimPolicy: Retain` | PVC를 지워도 PV·EBS 유지 | 🟡 YAML 한 줄 수정으로 뚫림 |

> ①이 안 보이는 이유는 **"코드에 없는 것"이기 때문**이다. 누가 태그를 추가하면 주석은 그대로인데
> 보호만 사라진다. → `assert-no-csi-delete-tags.sh`가 CI에서 기계적으로 막는다(반증 테스트 포함).
>
> ⚠️ ②는 **0-bootstrap이라서** 안전하다. `2-cluster`에 걸었다면 리퍼의
> `tofu destroy -auto-approve`가 통째로 실패해 dead man's switch가 벽돌이 됐을 것이다.

**제거 절차** (정말 지울 때만)

```bash
# 1. lifecycle { prevent_destroy = true } 를 ebs-postgres.tf에서 제거
# 2. 0-bootstrap에서
tofu apply -var postgres_persistent_volume_enabled=false
# 3. 원장의 "제거됨" 표로 행을 옮기고 근거·날짜 기록
```

**복구 절차** (데이터를 잃었을 때) — ~~스냅샷 백업은 **의도적으로 만들지 않는다**~~

> 🔄 **재판정 (2026-09-12, D-013 선행 조건 1).** 이 판단을 **부분 유지**한다:
> **스냅샷은 여전히 안 만든다**(EBS 스냅샷 $0.05/GB-Mo). 대신 **논리 백업(`pg_dump`)을
> S3 에 둔다**(`0-bootstrap/s3-backups.tf`, 월 ~$0).
>
> 뒤집은 이유는 *비용 판단이 틀려서*가 아니라 **목적이 바뀌었기 때문**이다.
> 아래 근거(*"Flyway 로 전부 재생성 가능"*)는 **학습장 전제에서 지금도 참이다.**
> 그러나 prod 이관을 목표로 삼은 이상 필요한 것은 *데이터 보존*이 아니라
> **복구 절차를 실제로 해본 경험**이고, 그건 백업이 없으면 시작할 수 없다.
>
> 🔴 **그리고 바로 그 근거가 검사의 판정력을 0으로 만든다** — Flyway 가 같은 데이터를
> 재생성하므로 *"복구 후 26행이 있다"* 는 복구 성공의 증거가 되지 못한다. 그래서
> 리허설은 **마이그레이션이 만들 수 없는 센티넬 행**으로만 판정한다(`db-backup.sh` ⑦).
>
> ⚠️ prod 이관 후에는 이 절의 전제 자체가 사라진다 — 사용자 데이터는 재생성되지 않는다.

데이터가 Flyway 마이그레이션 12개로 **전부 재생성 가능**하므로 스냅샷($0.05/GB-Mo)의 값이 낮다.
잃었을 때는:

```bash
# ① 볼륨 재생성 (0-bootstrap apply — 새 volume id가 나온다)
# ② PV/PVC 재적용: postgres-static.yaml을 새 volume id로 sed 후 kubectl apply
# ③ postgres 기동 → Flyway가 스키마를 자동 재구축 (시드 불필요)
#
# ④ (2026-09-12 추가) 논리 백업이 있으면 그쪽이 우선이다 — Flyway 재구축은
#    **마이그레이션 이후에 생긴 데이터를 되살리지 못한다.**
infra/aws-eks/scripts/db-restore.sh --s3 <덤프파일> --sentinel <토큰>
```

⚠️ 실패 6종 ⑥(기존 볼륨을 **포맷**해버림)이 유일한 비가역 사고다. 트리거는 PV의 `csi.fsType`을
`ext4`에서 바꾸는 것 — **바꾸지 마라.**

---

## 비용은 $0 인데 영속인 것 — **state 안의 값**

이 원장은 *"돈이 나가는데 안 보이는 것"* 을 잡으려고 만들었다(§왜 이 파일이 필요한가).
그런데 같은 **비가시성**을 가지면서 손실 모드가 돈이 아닌 자산이 이미 존재한다.

| 자산 | 레이어 | 잃으면 | 월 비용 | `aws` CLI 로 보이나 |
|---|---|---|---|---|
| `random_password.postgres_master` | 0-bootstrap | 영속 EBS 안의 DB에 **접속 불가** (해시는 볼륨에 구워져 있다 — L-14) | $0 | ❌ |
| `random_password.jwt_secret["learning"]` | 0-bootstrap | 학습 클러스터 토큰 전부 무효 | $0 | ❌ |
| `random_password.jwt_secret["prod"]` | 0-bootstrap | **전 사용자 강제 로그아웃** (30일 만료라 재로그인 파도가 30일간) | $0 | ❌ |

🔴 **§확인 명령으로는 이것들을 검증할 수 없다.** AWS 리소스가 아니라 tfstate 항목이라
`aws ec2 describe-*`·`aws s3api list-*` 어디에도 안 나온다. 유일한 조회 경로는:

```bash
tofu -chdir=infra/aws-eks/0-bootstrap state list | grep random_password
# 기대: random_password.jwt_secret["learning"]
#       random_password.jwt_secret["prod"]
#       random_password.postgres_master
#       (3줄. 값은 출력하지 말 것 — 콘솔·로그에 남는다)
```

무엇이 막나: 셋 다 `lifecycle { prevent_destroy = true }`. 뚫리는 방식도 EBS와 같다 —
**lifecycle 블록을 지우면 뚫린다**(위 ② 항목과 동일 등급). 추가로 tfstate 버킷 자체가
소실되면 셋 다 함께 사라지므로, 실질 상한선은 `devquest-eks-tfstate-seoul` 의 내구성이다.

> ⚠️ 원장 **L-50**(*영속 리소스를 **파괴**하는 쪽에는 가드가 없다 — 마커·리퍼·`guard-local-layers`가
> 전부 `tofu apply` 만 본다*)이 여기에도 그대로 걸린다. 이 표는 그 구멍을 메우지 않고
> **보이게만** 한다.

---

## 제거됨

| 리소스 | 제거일 | 근거 |
|---|---|---|
| *(없음)* | | |

---

## 확인 명령

```bash
R=ap-northeast-2

# ── ① 영속 인벤토리 — 합격 기준: 위 표와 "일치" (0건이 아니다) ──
aws ec2 describe-volumes --region $R --filters Name=tag:Persistent,Values=true \
  --query 'Volumes[].[VolumeId,Size,AvailabilityZone,State]' --output table

aws ecr describe-repositories --region $R --query 'repositories[].repositoryName' --output text

# 🔴 S3 는 SOP §9 고아 검사 대상이 **아니다**(세션과 함께 사라지는 물건이 아니므로).
#    그래서 여기 안 적으면 신설 버킷은 원장 대조에서 **영원히 안 보인다.**
aws s3api list-buckets --query "Buckets[?starts_with(Name, 'devquest-eks-')].Name" --output text

# 백업 버킷의 증가 상한이 **실제로 붙어 있는지** AWS 에 직접 묻는다.
# (소스에 lifecycle 블록이 있는 것과 배포된 버킷에 규칙이 붙어 있는 것은 다른 사실이다.)
aws s3api get-bucket-lifecycle-configuration --bucket devquest-eks-backups-seoul \
  --query 'Rules[].[ID,Status,Expiration.Days,NoncurrentVersionExpiration.NoncurrentDays,AbortIncompleteMultipartUpload.DaysAfterInitiation]' \
  --output table
# 합격 기준: 규칙 1개, Enabled, 30 / 30 / 1. 하나라도 None 이면 그 축에는 상한이 없다.

# ── ② 고아 검사 — 합격 기준: 0건 ──
# Persistent 태그가 정확히 "true"가 **아닌** available 볼륨 = 진짜 고아.
# 이 제외 필터가 없으면 영속 볼륨이 매 세션 "고아 발견"으로 잡히고,
# 오탐이 반복되는 검사는 곧 무시된다 → 진짜 고아도 함께 놓친다.
aws ec2 describe-volumes --region $R --filters Name=status,Values=available \
  --query "Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])].[VolumeId,Size,CreateTime]" \
  --output table

# ── ③ 🔒 삭제 방지 실물 확인 — 합격 기준: 0건 ──
# 배포된 볼륨에 CSI 삭제 조건 태그가 실제로 없는지 **AWS에 직접 묻는다.**
# `assert-no-csi-delete-tags.sh`(CI)는 소스만 보는 tripwire라 런타임 조립 문자열 등을
# 놓칠 수 있다(원장 L-12). 이 조회는 **최종 상태를 보므로 우회가 불가능하다.**
for K in ebs.csi.aws.com/cluster CSIVolumeName kubernetes.io/created-for/pvc/name; do
  echo -n "$K: "
  aws ec2 describe-volumes --region $R --filters Name=tag:Persistent,Values=true \
    --query "Volumes[?Tags[?Key=='$K']].VolumeId" --output text
done
# 셋 다 빈 줄이어야 한다. 하나라도 볼륨 ID가 나오면 **CSI가 그 볼륨을 지울 수 있는 상태**다.
```

> 🔴 **왜 `Value=='true'`까지 보는가 (반증 테스트로 확정, 2026-07-31).**
> 처음엔 *키 존재*만 보는 `?!not_null(Tags[?Key=='Persistent'].Value | [0])`로 썼다.
> 가짜 응답으로 반증 테스트를 돌리자 **두 경우가 조용히 빠져나갔다**:
>
> | 태그 | 느슨한 필터 | 엄격한 필터 |
> |---|---|---|
> | `Persistent=false` | 🔴 고아 아님으로 오판 | ✅ 고아로 잡힘 |
> | `Persistent=True` (대문자 오타) | 🔴 고아 아님으로 오판 | ✅ 고아로 잡힘 |
>
> **제외는 명시적 opt-in이어야 한다.** 오타 하나로 과금 리소스가 검사에서 사라지면,
> 그 검사는 통과했다고 믿게 만드는 검사기가 된다.
> ⚠️ 이 필터를 고칠 일이 생기면 **반드시 반증 테스트부터** — "0건 통과"는 필터가
> 올바르다는 증거가 아니다. 아무것도 매칭 안 하는 필터도 0건을 낸다.

## 재검토일이 `2027-01-15`인 이유

AWS 크레딧 만료일이다. 크레딧이 끝나면 **모든 비용이 실지출로 전환**되므로, 그 시점에
이 표 전체를 다시 판단해야 한다 — 남길 것, 지울 것, prod(Fly)로 옮길 것.

⚠️ 만료일은 한 번 **문서마다 달랐다**(01-15 vs 07-15). 콘솔 값이 원천이고
07-16의 "가입 +1년" 추론이 그것을 덮어썼던 오독이다. 바꾸려면 콘솔을 다시 볼 것.
