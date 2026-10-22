#!/usr/bin/env bash
# eks-window(📌D-016 8-B) 스크립트 회귀 — 목 aws·kubectl, $0. 사용: bash infra/aws-eks/scripts/prod-window/tests/window-test.sh
# 실제 클러스터를 켜지 않는다. 판정 로직(임대·끌지·LB 0·꺼짐 판정·예산)만 본다. on.sh 는 실클러스터 첫 실행이 검증이다.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P=0; F=0; ok(){ P=$((P+1)); echo "  ✅ $1"; }; bad(){ F=$((F+1)); echo "  🔴 $1"; }
M=$(mktemp -d); trap 'rm -rf "$M"' EXIT
export CALLS="$M/calls" STATE="$M"

cat > "$M/aws" <<'A'
#!/bin/sh
echo "aws $*" >> "$CALLS"
sub="$1 $2"; q=""; for a in "$@"; do case "$a" in length*|sum*|*\[\]*) q="$a";; esac; done
case "$sub" in
  "eks describe-cluster")
    case "${MOCK_CLUSTER:-no}" in yes) echo ACTIVE;; no) echo "An error occurred (ResourceNotFoundException)" >&2; exit 254;;
      *) echo "An error occurred (AccessDeniedException)" >&2; exit 254;; esac ;;
  "eks list-clusters") [ "${MOCK_CLUSTERS_N:-0}" = err ] && exit 254; echo "${MOCK_CLUSTERS_N:-0}" ;;
  "eks update-kubeconfig") exit "${MOCK_KCFG_RC:-0}" ;;
  "ssm get-parameter")
    case "${MOCK_LEASE:-none}" in none) echo "An error occurred (ParameterNotFound)" >&2; exit 254;;
      err) echo "An error occurred (ThrottlingException)" >&2; exit 254;; *) echo "$MOCK_LEASE";; esac ;;
  "ssm put-parameter") for a in "$@"; do case "$a" in expires=*) echo "$a" > "$STATE/lease_put";; esac; done; exit "${MOCK_PUT_RC:-0}" ;;
  "ssm delete-parameter") echo deleted > "$STATE/lease_deleted"; exit 0 ;;
  "elbv2 describe-load-balancers")
    n=$(cat "$STATE/lbn" 2>/dev/null || echo 0)
    case "$q" in length*) echo "$n";; *) [ "$n" -gt 0 ] && printf 'arn:aws:elasticloadbalancing:r:111122223333:loadbalancer/app/k8s-x/1\t2026-10-22T03:00:00Z\n'; exit 0;; esac ;;
  "elbv2 delete-load-balancer") [ "${MOCK_LB_STUCK:-0}" = 1 ] || echo 0 > "$STATE/lbn" ;;
  "elbv2 describe-target-groups") : ;;
  "cloudwatch get-metric-statistics") echo 12.5 ;;
  "ec2 describe-instances") echo "${MOCK_EC2_N:-0}" ;;
  "ec2 describe-volumes") echo "${MOCK_VOL_N:-0}" ;;
  "ec2 describe-security-groups") echo "${MOCK_SGS:-}" ;;
  "ec2 delete-security-group") exit "${MOCK_SG_DEL_RC:-0}" ;;
  "freetier get-account-plan-state") [ "${MOCK_FT:-}" = err ] && exit 254; echo "${MOCK_FT:-FREE	191.73	2027-01-15T23:50:41.027000+00:00}" ;;
  *) echo "목: 모르는 호출 $sub" >&2; exit 99 ;;
esac
A
cat > "$M/kubectl" <<'K'
#!/bin/sh
echo "kubectl $*" >> "$CALLS"
[ "${MOCK_K8S_FAIL:-0}" = 1 ] && exit 1
case "$*" in
  *"delete ingress"*) [ "${MOCK_INGRESS_CLEARS:-1}" = 1 ] && echo 0 > "$STATE/lbn" ;;
esac
exit 0
K
chmod +x "$M/aws" "$M/kubectl"
reset(){ : > "$CALLS"; rm -f "$M/lease_put" "$M/lease_deleted"; echo "${1:-0}" > "$M/lbn"; }
run(){ PATH="$M:$PATH" WIN_NOW="${WIN_NOW:-2000000000}" WIN_LB_WAIT=2 WIN_POLL=1 bash "$HERE/$1" "${@:2}" 2>"$M/err"; }
VALID="expires=2000003600 run=1 started=1999990000"; EXPIRED="expires=1999999999 run=1 started=1999990000"

echo "── decide-off (watchdog 표 · force)"
reset; out=$(MOCK_CLUSTER=yes run decide-off.sh force); [ "$out" = act ] && ok "force + 클러스터 있음 → act" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=no run decide-off.sh force); [ "$out" = act ] && ok "force + 없음 → act(destroy no-op)" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=err run decide-off.sh force); rc=$?; [ $rc = 2 ] && ok "클러스터 조회 오류 → rc 2('없음' 으로 안 떨어짐)" || bad "rc=$rc out=$out"
reset; out=$(MOCK_CLUSTER=yes MOCK_LEASE="$VALID" run decide-off.sh watchdog); [ "$out" = skip ] && ok "있음 + 임대 유효 → skip(창 중)" || bad "🔴 창 중에 끔 out=$out"
reset; out=$(MOCK_CLUSTER=yes MOCK_LEASE="$EXPIRED" run decide-off.sh watchdog); [ "$out" = act ] && ok "있음 + 임대 만료 → act" || bad "🔴 만료 임대를 방치 out=$out"
reset; out=$(MOCK_CLUSTER=yes MOCK_LEASE=none run decide-off.sh watchdog); [ "$out" = act ] && ok "있음 + 임대 없음 → act" || bad "🔴 임대 없는 클러스터를 방치 out=$out"
reset; out=$(MOCK_CLUSTER=yes MOCK_LEASE="garbage" run decide-off.sh watchdog); [ "$out" = act ] && ok "깨진 임대 → 만료 취급 act(창을 영원히 안 연다)" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=yes MOCK_LEASE=err run decide-off.sh watchdog); rc=$?; [ $rc = 2 ] && ok "임대 조회 오류 → rc 2" || bad "rc=$rc out=$out"
reset 0; out=$(MOCK_CLUSTER=no MOCK_LEASE=none run decide-off.sh watchdog); rc=$?; [ $rc = 0 ] && [ "$out" = skip ] && ok "없음 + 임대 없음 + LB 0 → skip" || bad "rc=$rc out=$out"
reset 1; out=$(MOCK_CLUSTER=no MOCK_LEASE=none run decide-off.sh watchdog); rc=$?; [ $rc = 1 ] && grep -q '고아 ALB' "$M/err" && ok "🔴 클러스터 없이 LB 남음 → rc 1(메일)" || bad "rc=$rc 고아 ALB 를 못 봄"
reset; out=$(MOCK_CLUSTER=no MOCK_LEASE="$EXPIRED" run decide-off.sh watchdog); [ "$out" = act ] && ok "없음 + 만료 임대 → act(정리)" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=no MOCK_LEASE="$VALID" run decide-off.sh watchdog); [ "$out" = skip ] && ok "없음 + 유효 임대 → skip(만료 뒤 정리)" || bad "out=$out"

echo "── 임대 쓰기·만료"
reset; ( PATH="$M:$PATH"; . "$HERE/common.sh"; win_lease_put 6 ) 2>/dev/null; v=$(cat "$M/lease_put" 2>/dev/null)
e=$(echo "$v" | sed -nE 's/^expires=([0-9]+) .*/\1/p'); now=$(date +%s); [ -n "$e" ] && [ $((e - now)) -ge 21590 ] && [ $((e - now)) -le 21610 ] && ok "put 6h → expires = 지금 + 21600" || bad "v=$v"
reset; ( PATH="$M:$PATH"; . "$HERE/common.sh"; win_lease_put 6x ) 2>/dev/null; rc=$?; [ $rc -ne 0 ] && [ ! -f "$M/lease_put" ] && ok "시간이 정수가 아니면 쓰지 않는다" || bad "rc=$rc"
reset; ( PATH="$M:$PATH"; . "$HERE/common.sh"; win_lease_expire ) 2>/dev/null; grep -q '^expires=0 ' "$M/lease_put" && ok "off 착수 → expires=0(실패해도 watchdog 이 다시 돈다)" || bad "$(cat "$M/lease_put" 2>/dev/null)"

echo "── off-pre (destroy 전 — LB 0 이 합격)"
reset 1; MOCK_CLUSTER=yes run off-pre.sh >/dev/null; rc=$?
a=$(grep -n 'delete application' "$CALLS" | cut -d: -f1 | head -1); i=$(grep -n 'delete ingress' "$CALLS" | cut -d: -f1 | head -1)
[ $rc = 0 ] && [ -n "$a" ] && [ -n "$i" ] && [ "$a" -lt "$i" ] && ok "Application 먼저 → Ingress → LB 0 (B4-2 순서)" || bad "rc=$rc app=$a ing=$i"
reset 1; MOCK_CLUSTER=yes MOCK_KCFG_RC=1 run off-pre.sh >/dev/null; rc=$?
[ $rc = 0 ] && grep -q 'delete-load-balancer' "$CALLS" && ! grep -q 'delete ingress' "$CALLS" && ok "kubeconfig 실패 → K8s 건너뛰고 AWS 로 LB 삭제(B8-16 — '없음' 으로 안 읽음)" || bad "rc=$rc"
reset 1; MOCK_CLUSTER=yes MOCK_INGRESS_CLEARS=0 MOCK_LB_STUCK=1 run off-pre.sh >/dev/null; rc=$?
[ $rc = 1 ] && grep -q 'scale deploy aws-load-balancer-controller --replicas=0' "$CALLS" && ok "🔴 LB 가 끝까지 남으면 rc 1(destroy 하지 않음) · LBC 를 먼저 멈춤" || bad "rc=$rc"
reset 0; MOCK_CLUSTER=no run off-pre.sh >/dev/null; rc=$?; [ $rc = 0 ] && ! grep -q kubectl "$CALLS" && ok "클러스터 없음 → K8s 단계 없음, LB 0 확인만" || bad "rc=$rc"
reset 1; GITHUB_STEP_SUMMARY="$M/sum" MOCK_CLUSTER=yes run off-pre.sh >/dev/null
grep -q 'app/k8s-x/1.*ConsumedLCUs(합)=12.5' "$M/sum" && ! grep -q 111122223333 "$M/sum" && ok "LB 이름·LCU 를 Summary 에 기록(B8-17) · 계정 ID 없음" || bad "$(cat "$M/sum" 2>/dev/null)"
rm -f "$M/sum"

echo "── off-verify (destroy 후 — AWS 의 답)"
reset 0; MOCK_CLUSTERS_N=0 run off-verify.sh >/dev/null; rc=$?; [ $rc = 0 ] && [ -f "$M/lease_deleted" ] && ok "전부 0 → rc 0 · 임대 삭제" || bad "rc=$rc"
reset 0; MOCK_EC2_N=1 run off-verify.sh >/dev/null; rc=$?; [ $rc = 1 ] && [ ! -f "$M/lease_deleted" ] && ok "🔴 EC2 남음 → rc 1 · 임대 유지(watchdog 재시도)" || bad "rc=$rc"
reset 0; MOCK_CLUSTERS_N=err run off-verify.sh >/dev/null; rc=$?; [ $rc = 1 ] && [ ! -f "$M/lease_deleted" ] && ok "조회 실패는 0 이 아니다 → rc 1" || bad "rc=$rc"
reset 0; MOCK_SGS="sg-1 sg-2" run off-verify.sh >/dev/null; rc=$?; [ $rc = 0 ] && [ "$(grep -c 'delete-security-group' "$CALLS")" = 2 ] && ok "LBC SG 남음 → 지우고 통과" || bad "rc=$rc"
reset 0; MOCK_SGS="sg-1" MOCK_SG_DEL_RC=1 run off-verify.sh >/dev/null; rc=$?; [ $rc = 1 ] && ok "🔴 LBC SG 를 못 지우면 rc 1(영속 VPC 누적 — B8-16)" || bad "rc=$rc"
reset 0; MOCK_VOL_N=1 run off-verify.sh >/dev/null; rc=$?; [ $rc = 1 ] && ok "🔴 고아 EBS → rc 1" || bad "rc=$rc"

echo "── budget-gate"
reset; out=$(WIN_NOW=$(date +%s) run budget-gate.sh 6); rc=$?; [ $rc = 0 ] && ok "잔여 \$191 → 통과" || bad "rc=$rc $out"
reset; out=$(WIN_NOW=$(date +%s) MOCK_FT="FREE	60.00	2027-01-15T23:50:41.027000+00:00" run budget-gate.sh 6); rc=$?; [ $rc = 1 ] && ok "🔴 잔여 \$60 · 만료 52일 전 → 거부(예비 \$30 + 상시분)" || bad "rc=$rc $out"
reset; out=$(MOCK_FT=err run budget-gate.sh 6); rc=$?; [ $rc = 2 ] && ok "조회 실패 → rc 2(판정 못 하면 안 켠다)" || bad "rc=$rc"
reset; out=$(MOCK_FT="FREE	None	2027-01-15T23:50:41.027000+00:00" run budget-gate.sh 6); rc=$?; [ $rc = 2 ] && ok "잔여 해석 실패(None) → rc 2" || bad "rc=$rc"
reset; out=$(run budget-gate.sh x); rc=$?; [ $rc = 2 ] && ok "시간 형식 이상 → rc 2" || bad "rc=$rc"

echo
echo "합계: ✅ $P · 🔴 $F"
[ "$F" = 0 ]
