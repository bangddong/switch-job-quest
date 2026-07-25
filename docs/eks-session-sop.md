# EKS 실습 세션 SOP (시작 → 작업 → 종료)

> 과금이 시작되는 실습의 **단일 출처**. 흩어진 규칙(튜토리얼 Step 8·CLAUDE.md·비용모델·teardown 순서)을
> 한 체크리스트로 모았다. **"끄는 걸 잊는 것"이 유일한 진짜 돈 새는 지점**이라 그걸 기계로도 막는다(아래 §안전장치).

## 🟢 시작 (여기까진 $0)

1. **통시간 확보 확인** — destroy까지 붙어있을 수 있나? (30~40분+) 중간 이탈 = 과금 누수.
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
   kubectl delete ingress,pvc --all -A    # K8s가 만든 ALB·EBS 회수 (state 밖 고아 방지)
   # 콘솔/CLI로 ALB·EBS 사라졌나 확인
   cd infra/aws-eks/2-cluster && tofu destroy
   ```
9. **고아 전수 검증** = 0:
   ```bash
   tofu state list                                   # 비어야 함
   aws eks list-clusters --region ap-northeast-2     # 비어야 함
   aws elbv2 describe-load-balancers --region ap-northeast-2 --query 'LoadBalancers[].LoadBalancerName'
   aws ec2 describe-volumes --region ap-northeast-2 --filters Name=status,Values=available --query 'Volumes[].VolumeId'
   aws ec2 describe-nat-gateways --region ap-northeast-2 --filter Name=state,Values=available
   ```
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
| 하트비트+리마인더 | `eks-heartbeat-reminder.sh` (Stop) | 매 턴 하트비트 touch("사람 활동 중") + 클러스터 살아있으면 경고 + 클러스터 없으면 마커 자가청소 |
| 리퍼 | `eks-reaper.sh` (launchd 30분) | 마커 있고 **하트비트 2h stale**(=사람 사라짐)이면 → 실제 클러스터 확인 후 `tofu destroy` |

**동작 요지**: 네가 Claude로 작업 중이면 매 턴 하트비트가 갱신돼 리퍼가 안 건드린다.
네가 **끝 신호 없이 사라지면** 하트비트가 2시간 뒤 stale → 리퍼가 자동 `tofu destroy` → 과금 종료.
tofu state를 그대로 쓰므로(로컬 실행) 다음에 상태가 깨끗하다.

- **한계**: macOS가 **자면(닫으면)** launchd가 안 돈다 → 깨어날 때 밀린 실행을 따라잡는다.
  그동안(노트북 잠든 시간)은 과금 지속. 최악(주말 방치) ~$6, 그리고 **$35 예산 알람**(0-bootstrap)이 backstop.
- **설치** (새 머신/클론 시 1회): `bash infra/aws-eks/reaper/install-reaper.sh`
- **설정**: `EKS_REAPER_TTL`(기본 7200초=2h), `EKS_REAPER_DRYRUN=1`(테스트), `EKS_REGION`.
- **리퍼가 뭔가 했나 확인**: `.claude/eks-session/reaper.log` (`DEAD MAN'S SWITCH 발동` 있으면 자동 destroy된 것).
- **제거**: `launchctl bootout gui/$(id -u)/com.devquest.eks-reaper ; rm ~/Library/LaunchAgents/com.devquest.eks-reaper.plist`

> ⚠️ `.claude/eks-session/`은 gitignore(머신 로컬·휘발성). 리퍼 스크립트/plist 템플릿만 커밋된다.
