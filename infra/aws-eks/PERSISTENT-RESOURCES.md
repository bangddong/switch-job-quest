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

**합계 ≈ $1.09/월** (ECR $0.17 + EBS $0.91). 6개월 ≈ $6.5 = 크레딧의 3.3%

| 리소스 | 레이어 | 왜 영속인가 | 증가 상한 | 월 비용 | 시작 | 재검토 |
|---|---|---|---|---|---|---|
| **S3** `devquest-eks-tfstate-seoul` | 0-bootstrap | tfstate 원격 백엔드. 지우면 모든 레이어의 state 유실 | 상태 파일 수 KB 단위 | ~$0 | 2026-07 | — |
| **DynamoDB** `devquest-eks-tflock` | 0-bootstrap | state 잠금(동시 apply 방지) | 온디맨드, 항목 1개 | ~$0 | 2026-07 | — |
| **ECR** `devquest/core-api` | 0-bootstrap | 이미지가 destroy마다 사라지면 세션당 재빌드 5~10분 | 🔒 **lifecycle 10개** (실측 10개 = 1.74 GB) | **$0.17** | 2026-07-27 | 2027-01-15 |
| **ECR** `devquest/ai-api` | 0-bootstrap | 위와 동일 (Phase 2 대비) | 🔒 lifecycle 10개 (현재 0개) | $0 | 2026-07-27 | 2027-01-15 |
| **IAM** OIDC 프로바이더 · GitHub Actions 역할 | 0-bootstrap | CI가 AWS에 붙는 통로 | 고정 | $0 | 2026-07 | — |
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

**복구 절차** (데이터를 잃었을 때) — 스냅샷 백업은 **의도적으로 만들지 않는다**

데이터가 Flyway 마이그레이션 12개로 **전부 재생성 가능**하므로 스냅샷($0.05/GB-Mo)의 값이 낮다.
잃었을 때는:

```bash
# ① 볼륨 재생성 (0-bootstrap apply — 새 volume id가 나온다)
# ② PV/PVC 재적용: postgres-static.yaml을 새 volume id로 sed 후 kubectl apply
# ③ postgres 기동 → Flyway가 스키마를 자동 재구축 (시드 불필요)
```

⚠️ 실패 6종 ⑥(기존 볼륨을 **포맷**해버림)이 유일한 비가역 사고다. 트리거는 PV의 `csi.fsType`을
`ext4`에서 바꾸는 것 — **바꾸지 마라.**

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

# ── ② 고아 검사 — 합격 기준: 0건 ──
# Persistent 태그가 정확히 "true"가 **아닌** available 볼륨 = 진짜 고아.
# 이 제외 필터가 없으면 영속 볼륨이 매 세션 "고아 발견"으로 잡히고,
# 오탐이 반복되는 검사는 곧 무시된다 → 진짜 고아도 함께 놓친다.
aws ec2 describe-volumes --region $R --filters Name=status,Values=available \
  --query "Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])].[VolumeId,Size,CreateTime]" \
  --output table
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
