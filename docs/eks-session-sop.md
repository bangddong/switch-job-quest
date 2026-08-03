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
2b. 🔴 **ECR 이미지가 관측 기본값 수정을 포함하는지 확인** (무료, 30초).
   `secrets.tf`가 더 이상 `GRAFANA_*` 자리표시를 주입하지 않으므로, **그 수정 이전에 빌드된
   이미지는 부팅 자체가 실패**한다 — `PlaceholderResolutionException: ... 'GRAFANA_API_KEY'`.
   "왜 CrashLoopBackOff지"를 클러스터 안에서 디버깅하는 건 과금 구간에서 가장 비싼 실수다.
   ```bash
   git fetch origin   # 이미지 태그 커밋이 로컬에 있어야 한다
   F=be/support/monitoring/src/main/kotlin/com/devquest/monitoring/OtlpMetricsConfig.kt
   SHA=$(aws ecr describe-images --repository-name devquest/core-api --region ap-northeast-2 \
     --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags' --output json \
     | ruby -rjson -e 'puts JSON.parse(STDIN.read).find{|t| t =~ /\A[0-9a-f]{40}\z/}')

   if [ -z "$SHA" ]; then
     echo "🔴 판정 불가 — ECR 태그를 못 읽었다(자격증명·ruby·태그 형식 확인). 수동 확인 후 진행할 것"
   elif git show "$SHA:$F" 2>/dev/null | grep -q 'GRAFANA_API_KEY:}'; then
     echo "✅ OK — $SHA"
   else
     echo "🔴 재빌드 필요 — GitHub Actions > ECR Push > workflow_dispatch (service: core-api)"
   fi
   ```
   > 날짜·푸시시각 비교가 아니라 **그 커밋의 소스를 직접 읽는다.** 태그가 곧 커밋이므로
   > `git show <sha>:<path>`로 "이 이미지 안의 코드가 실제로 어떤가"를 확인할 수 있다.
   >
   > 🔴 **`[ -z "$SHA" ]` 가드를 지우지 마라.** 이 검사는 처음 작성했을 때 정확히
   > **"통과했다고 믿게 만드는 검사"** 였다(QA F-1에서 재현). `SHA`가 비면
   > `git show "$SHA:$F"`가 `git show ":$F"`로 해석되는데, 이건 **로컬 인덱스의 파일**을
   > 읽는 유효한 문법이다 — ECR과 아무 상관 없이 네 워킹트리를 보고 `exit 0`으로 ✅를 낸다.
   > 즉 aws 호출이 실패한 상황에서 가장 위험한 방향으로 조용히 통과한다.
   > **"판정 불가"와 "재빌드 필요"를 구분해 둔 것도 의도적이다** — 둘 다 멈추라는 뜻이지만
   > 해야 할 행동이 다르다(자격증명 고치기 vs 이미지 굽기).
   >
   > ⚠️ **`aws`·`ruby`의 stderr를 `2>/dev/null`로 죽이지 마라.** 한 번 그렇게 고쳤다가
   > QA F-3에 걸렸다. `set -e` 아래에서는 `SHA=$(... | ruby ...)` 대입문 자체가 파이프라인
   > 실패로 스크립트를 즉시 끝내는데, stderr까지 막으면 **"판정 불가"조차 못 찍고 무출력으로
   > 죽는다.** 에러 원문이 지저분해 보여도 그게 유일하게 남는 신호다.
   >
   > 📌 이 블록은 **사람이 터미널에 붙여넣는 체크리스트**다(그래서 `set -e`가 없다).
   > 스크립트로 옮기려면 SHA 대입 실패를 명시적으로 처리할 것 — 그대로 복사하지 말 것.
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
   kubectl delete ingress --all -A
   #
   # 🔴 PVC는 **Stage에 따라 다르다.** 감각으로 옮기면 사고가 난다.
   #   Stage 3a (동적 PVC): `kubectl delete pvc --all -A` **필수.**
   #       안 하면 CSI가 만든 EBS가 tofu state 밖에 고아로 남아 계속 과금된다.
   #   Stage 3b (static PV): **하지 않는 것을 권장.**
   #       볼륨은 terraform(0-bootstrap) 소유라 destroy 대상이 아니고, reclaimPolicy가
   #       Retain이며 IAM이 CSI의 삭제를 거부한다 — 즉 지워도 볼륨은 안 사라진다.
   #       대신 PV가 `Released` + claimRef 잔존 상태가 되어, 다음 세션에 재적용할 때
   #       실패 6종 ④(PV가 새 PVC를 안 받음)를 밟는다.
   #       → **PVC를 남긴 채 클러스터를 destroy**하면 클러스터와 함께 깨끗이 사라진다.
   kubectl get pvc -A     # 무엇이 있는지 확인하고 Stage에 맞게 판단
   # 콘솔/CLI로 ALB 사라졌나 확인

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
