#!/usr/bin/env bash
# off 의 destroy **뒤** 판정 — 📌D-016 8-B. SOP §8b·§9 의 CI 판. "destroy 를 쳤다" 가 아니라 **AWS 의 답**으로 끝을 판정한다.
# 전부 0 이면 임대를 지우고 rc 0. 하나라도 남으면 임대를 **남기고** rc 1 → 다음 watchdog 이 off 를 다시 돈다.
#
#   클러스터 0 · 실행 중 EC2 0(prod DB `Role=prod-db` 제외 — B5-13) · LB 0 · 고아 EBS 0(`Persistent=true` 제외)
#   + LBC 가 만든 SG(태그 `elbv2.k8s.aws/cluster`) — 지워 보고, 남으면 실패.
#     VPC 는 1-network(영속)라 destroy 가 지우지 않는다. ALB 가 지워진 뒤 LBC 가 SG 를 못 지우고 죽으면 **영속 VPC 에 쌓인다**(B8-16).
set -uo pipefail
. "$(dirname "$0")/common.sh"
FAIL=0
chk() {  # $1 = 이름, $2 = 값(0 이 합격)
  if [ "$2" = 0 ]; then wlog "✅ $1 = 0"; wsummary "- ✅ $1 = 0"
  else wlog "🔴 $1 = $2"; wsummary "- 🔴 $1 = $2"; FAIL=1; fi
}
R="$WIN_REGION"

wsummary "### off 판정 (AWS 직접 조회)"
N=$(aws eks list-clusters --region "$R" --query 'length(clusters)' --output text) || N="조회실패"
chk "EKS 클러스터" "$N"

# 노드 종료는 destroy 가 기다리지만 shutting-down 이 잠깐 남을 수 있다 — running/pending 만 센다(SOP §8b 와 같은 쿼리)
N=$(aws ec2 describe-instances --region "$R" --filters Name=instance-state-name,Values=running,pending \
      --query "length(Reservations[].Instances[?!(Tags[?Key=='Role' && Value=='prod-db'])][])" --output text) || N="조회실패"
chk "실행 중 EC2(prod DB 제외)" "$N"

N=$(win_lb_count) || N="조회실패"
chk "로드밸런서" "$N"

N=$(aws ec2 describe-volumes --region "$R" --filters Name=status,Values=available \
      --query "length(Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])])" --output text) || N="조회실패"
chk "고아 EBS(Persistent 제외)" "$N"

SGS=$(aws ec2 describe-security-groups --region "$R" --filters "Name=tag:elbv2.k8s.aws/cluster,Values=$WIN_CLUSTER" \
        --query 'SecurityGroups[].GroupId' --output text) || SGS="조회실패"
if [ "$SGS" = "조회실패" ]; then chk "LBC 보안그룹" "$SGS"
else
  LEFT=0
  for sg in $SGS; do
    aws ec2 delete-security-group --region "$R" --group-id "$sg" >/dev/null 2>&1 && wlog "LBC SG 정리: $sg" || LEFT=$((LEFT + 1))
  done
  chk "LBC 보안그룹(정리 후)" "$LEFT"
fi

if [ "$FAIL" = 0 ]; then
  win_lease_delete || { wlog "🔴 임대 삭제 실패 — watchdog 이 다음 시간에 다시 정리한다"; exit 1; }
  wlog "✅ 꺼짐 확인 · 임대 삭제"; wsummary "- ✅ 임대 삭제"
  exit 0
fi
wlog "🔴 남은 것이 있다 — 임대를 남긴다(다음 watchdog 이 off 를 다시 돈다)"
exit 1
