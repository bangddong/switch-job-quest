# EKS 실습 세션 SOP (시작 → 작업 → 종료)

> 과금이 시작되는 실습의 **단일 출처**. 흩어진 규칙(튜토리얼 Step 8·CLAUDE.md·비용모델·teardown 순서)을
> 한 체크리스트로 모았다. **"끄는 걸 잊는 것"이 유일한 진짜 돈 새는 지점**이라 그걸 기계로도 막는다(아래 §안전장치).

## 🟢 시작 (여기까진 $0)

1. **통시간 확보 확인** — destroy까지 붙어있을 수 있나? 중간 이탈 = 과금 누수.
   | 구성 | 왕복 소요(벽시계) |
   |---|---|
   | Stage 0~1 (EKS만) | 30~40분 (실측: Task 8 ~50분, Stage 1 ~20분) |
   | **Stage 2~ (EKS + RDS)** | **40~50분** (실측 07-28: apply~검증~destroy 전체 ~45분) |

   > 07-28 실측 세부 — 추정보다 빨랐다:
   > | 단계 | 실측 |
   > |---|---|
   > | EKS 컨트롤플레인 생성 | ~6분 |
   > | 노드그룹 | 2분 48초 |
   > | 애드온(vpc-cni·kube-proxy) | 55초 / coredns 24초 |
   > | **RDS db.t4g.micro 생성** | **4분 50초** (추정 ~10분의 절반) |
   > | ESO helm install --wait | ~40초 |
   > | 앱 기동(Flyway 12개 포함) | 26초 |
   >
   > ⚠️ **RDS는 EKS와 병렬 생성되지 않는다.** RDS 보안그룹의 인그레스가 EKS 클러스터
   > 보안그룹 ID를 참조하므로 의존 사슬이 생겨 `클러스터 → SG → RDS` 순서로 직렬화된다.
   > 즉 RDS 시간은 EKS 시간에 **더해진다**(예전 표의 "병렬" 서술은 틀렸다).
2. **사전 점검** (전부 무료):
   ```bash
   command -v tofu kubectl aws            # 도구 존재
   aws sts get-caller-identity            # 자격증명
   aws eks describe-cluster-versions --region ap-northeast-2 \
     --query 'clusterVersions[?status==`STANDARD_SUPPORT`].[clusterVersion,endOfStandardSupportDate]' --output table
   ```
   크레딧 잔여도 확인(만료 2027-01-15, 안전예비 $30 규칙).
3. **일지 시작 기록** — `docs/eks-migration-log.md`에 세션 시작 시각 append.
4. `tofu init && tofu plan` — 리소스 하나씩 해설 + 비용 영향 + **사용자 승인 게이트**.

## 🔴 과금 구간

5. `tofu apply` ← **과금 시작.** (`eks-session-marker.sh` 훅이 자동으로 세션 마커 생성 → 리퍼 감시 개시)
6. `aws eks update-kubeconfig --name devquest-eks --region ap-northeast-2` → `kubectl get nodes` 검증.
7. 실습 목표 수행. **일지 실시간 기록**(에러 원문·해결·비용·결정).
   - 매 턴 Stop 훅이 하트비트를 갱신 → "사람이 활동 중"이라 리퍼가 안 죽인다.

## 🟢 종료 (과금 OFF)

8. **teardown 순서 엄수** (Stage 1+ ALB/PVC 생기면 필수):
   ```bash
   # ① ESO가 관리하는 Secret부터 (Stage 2~)
   #    ⚠️ `kubectl delete secret core-api-db`만 하면 ESO가 **즉시 재생성**한다.
   #       소유자인 ExternalSecret을 먼저 지워야 한다(creationPolicy: Owner).
   #    ✅ 07-28 실측으로 확인: 삭제 후 8초 만에 부활, UID가 바뀌어 있었다
   #       (94fe931e-… → db35e78b-… = 같은 이름의 다른 객체).
   #       반대로 ExternalSecret을 지우면 K8s Secret도 함께 사라진다(소유자 GC).
   kubectl delete externalsecret --all -A
   kubectl delete secretstore --all -A

   # ② K8s가 만든 ALB·EBS 회수 (state 밖 고아 방지)
   kubectl delete ingress,pvc --all -A
   # 콘솔/CLI로 ALB·EBS 사라졌나 확인

   # ③ 인프라
   cd infra/aws-eks/2-cluster && tofu destroy
   ```
   > ESO 자체(Helm 릴리스)는 클러스터와 함께 사라지므로 별도 삭제 불필요.
9. **고아 전수 검증** = 0:
   ```bash
   R=ap-northeast-2
   tofu state list                                   # 비어야 함
   aws eks list-clusters --region $R                 # 비어야 함
   aws elbv2 describe-load-balancers --region $R --query 'LoadBalancers[].LoadBalancerName'
   aws ec2 describe-nat-gateways --region $R --filter Name=state,Values=available

   # ── EBS: "available" 전부가 고아는 아니다 (영속 볼륨 도입 이후) ──
   # 🔴 영속 EBS는 destroy 후 **영원히 available 상태**로 남는 것이 정상이다.
   #    옛 쿼리(Volumes[].VolumeId, 필터 없음)를 그대로 쓰면 매 세션 "고아 발견"이 뜬다.
   #    합격 기준이 "전부 0건"이므로 **매번 실패하는 검사**가 되고, 매번 실패하는 검사는
   #    곧 눈으로 넘기게 된다 → 그때부터 진짜 고아도 안 보인다.
   #    → 고아 검사와 영속 인벤토리를 **성격이 다른 두 검사**로 가른다.
   aws ec2 describe-volumes --region $R --filters Name=status,Values=available \
     --query "Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])].[VolumeId,Size,CreateTime]" \
     --output table

   # ── RDS 계열 (Stage 2에서 추가) ──
   # ⚠️ 인스턴스가 사라져도 **스냅샷은 남아 계속 과금**된다. 가장 놓치기 쉬운 고아다.
   aws rds describe-db-instances --region $R --query 'DBInstances[].DBInstanceIdentifier'
   aws rds describe-db-snapshots --region $R --snapshot-type manual   --query 'DBSnapshots[].DBSnapshotIdentifier'
   aws rds describe-db-snapshots --region $R --snapshot-type automated --query 'DBSnapshots[].DBSnapshotIdentifier'
   aws rds describe-db-subnet-groups --region $R --query 'DBSubnetGroups[].DBSubnetGroupName'

   # ── Secrets Manager ──
   # 🔴 --include-planned-deletion 없이 조회하면 "삭제 대기" 상태 시크릿이 안 보인다.
   #    복구창(기본 30일) 동안 시크릿당 $0.40/월이 계속 과금되고, 이름이 점유돼
   #    다음 세션 apply가 InvalidRequestException으로 실패한다.
   #    (그래서 코드에서 recovery_window_in_days = 0 으로 둔다 — secrets.tf ⑨)
   aws secretsmanager list-secrets --region $R --include-planned-deletion \
     --query 'SecretList[].{Name:Name,DeletedDate:DeletedDate}'
   ```

   > ✅ **실측 완료(07-28)**: RDS가 `manage_master_user_password`로 만든 마스터 시크릿(`rds!db-...`)은
   > **인스턴스 삭제와 함께 완전히 정리된다.** teardown 후 `--include-planned-deletion`으로 조회해도
   > 빈 결과 — 복구창 좀비도, 이름 점유도 남지 않는다. (우리가 만든 시크릿 2개는
   > `recovery_window_in_days = 0` 덕분에 즉시 소멸)
   >
   > 📌 **07-28 teardown 실측: 위 항목 전부 0건 = 고아 없음.** destroy 4분 34초(26 destroyed).
   > 단계별: 노드그룹 2분 16초 · **RDS 3분 53초** · 컨트롤플레인 2분 9초.
9b. **영속 인벤토리 대조** — 합격 기준이 위와 **정반대**다. 0건이 아니라 **원장과 일치**:
   ```bash
   aws ec2 describe-volumes --region $R --filters Name=tag:Persistent,Values=true \
     --query 'Volumes[].[VolumeId,Size,AvailabilityZone,State]' --output table
   ```
   > 📖 기대값은 **`infra/aws-eks/PERSISTENT-RESOURCES.md`** 의 표다. 개수·크기·AZ가 어긋나면
   > ①모르는 사이 볼륨이 늘었거나 ②원장 갱신을 빠뜨린 것 — **둘 다 즉시 확인 대상**이다.
   >
   > ⚠️ **9와 9b는 반대 방향의 검사다.** 9는 "없어야 한다", 9b는 "정확히 이만큼 있어야 한다".
   > 하나로 합치면 어느 쪽 기준으로 읽어야 할지 모호해져 둘 다 무력해진다.

10. 일지에 destroy 시각·총 과금 시간·비용 결산 append.
11. **구축 후 이해도 퀴즈** — 학습 마일스톤(`stage/eks-*` 브랜치)이면 `quiz.md`로 진행,
    `docs/eks-quizzes/<브랜치>.md` + `<!-- QUIZ-PASSED -->`. (`assert-eks-quiz.sh`가 PR 차단으로 강제)
12. PR → 머지 → CONTEXT 정리.

---

## 🔒 안전장치 — "끄는 걸 잊음"을 기계로 막기 (dead man's switch)

사람 규율만으로는 "apply하고 destroy 잊고 세션 종료"를 못 막는다. 그래서 로컬 리퍼를 둔다.

| 조각 | 파일 | 역할 |
|------|------|------|
| 마커 | `eks-session-marker.sh` (PreToolUse) | `tofu apply` 감지 → `.claude/eks-session/active` 생성 |
| 하트비트+리마인더 | `eks-heartbeat-reminder.sh` (Stop) | 매 턴 하트비트 touch("사람 활동 중") + **과금 리소스**(EKS·RDS) 살아있으면 경고 + 전부 없으면 마커 자가청소 |
| 리퍼 | `eks-reaper.sh` (launchd 30분) | 마커 있고 **하트비트 2h stale**(=사람 사라짐)이면 → 실제 리소스 확인 후 `tofu destroy` |

> 🔴 **생존 판정은 EKS·RDS의 OR이다 (Stage 2에서 교정).** 클러스터만 보면 "EKS는 지워졌는데
> RDS는 남은" 부분 실패 상태에서 **마커를 자가 삭제해 감시를 끝내버린다** → RDS 영구 과금.
> 두 스크립트가 같은 기준을 쓰도록 유지할 것. 목 주입 테스트로 회귀 검증됨.

**동작 요지**: 네가 Claude로 작업 중이면 매 턴 하트비트가 갱신돼 리퍼가 안 건드린다.
네가 **끝 신호 없이 사라지면** 하트비트가 2시간 뒤 stale → 리퍼가 자동 `tofu destroy` → 과금 종료.
tofu state를 그대로 쓰므로(로컬 실행) 다음에 상태가 깨끗하다.

- **한계**: macOS가 **자면(닫으면)** launchd가 안 돈다 → 깨어날 때 밀린 실행을 따라잡는다.
  그동안(노트북 잠든 시간)은 과금 지속. 최악(주말 방치) ~$6.
  - 🔴 **이 경우의 backstop은 예산이 아니라 이상탐지다.** 예산은 **누적** $10 단위라
    주말 급증 $6은 누적이 다음 계단을 넘지 않는 한 안 울린다(2026-07-31 누적 $0.481 기준
    $6.48 → $10 미도달). 하루 단위 급증을 잡는 것은 **Cost Anomaly Detection(DAILY, $5)** 이다.
    ~~"$35 예산 알람이 backstop"~~ 이라는 종전 서술은 임계 체계와 어긋났다(코드에 $35는 없었다).
- **설치** (새 머신/클론 시 1회): `bash infra/aws-eks/reaper/install-reaper.sh`
- **설정**: `EKS_REAPER_TTL`(기본 7200초=2h), `EKS_REAPER_DRYRUN=1`(테스트), `EKS_REGION`.
- **리퍼가 뭔가 했나 확인**: `.claude/eks-session/reaper.log` (`DEAD MAN'S SWITCH 발동` 있으면 자동 destroy된 것).
- **제거**: `launchctl bootout gui/$(id -u)/com.devquest.eks-reaper ; rm ~/Library/LaunchAgents/com.devquest.eks-reaper.plist`

> ⚠️ `.claude/eks-session/`은 gitignore(머신 로컬·휘발성). 리퍼 스크립트/plist 템플릿만 커밋된다.
