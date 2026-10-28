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
    case "$*" in *cloudflare-token*)
      [ "${MOCK_CF_TOKEN:-ok}" = err ] && { echo "An error occurred (AccessDeniedException)" >&2; exit 254; }
      echo "cf-test-token"; exit 0 ;; esac
    [ -f "$STATE/lease_cur" ] && { cat "$STATE/lease_cur"; exit 0; }
    case "${MOCK_LEASE:-none}" in none) echo "An error occurred (ParameterNotFound)" >&2; exit 254;;
      err) echo "An error occurred (ThrottlingException)" >&2; exit 254;; *) echo "$MOCK_LEASE";; esac ;;
  "ssm put-parameter") for a in "$@"; do case "$a" in expires=*) echo "$a" > "$STATE/lease_put"; [ "${MOCK_PUT_RC:-0}" = 0 ] && echo "$a" > "$STATE/lease_cur";; esac; done; exit "${MOCK_PUT_RC:-0}" ;;
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
  *"get ingress devquest"*) printf '%s' "${MOCK_ALB-k8s-default-devquest-1.ap-northeast-2.elb.amazonaws.com}" ;;
esac
exit 0
K
# 목 curl — Cloudflare API · DoH · 공개 URL(📌D-016 8-C C-2). 상태: $STATE/kv(스위치 값) · $STATE/dns(레코드 JSON 배열)
cat > "$M/curl" <<'C'
#!/bin/bash
method=GET out="" fmt="" hdr=0 body="" url=""
while [ $# -gt 0 ]; do case "$1" in
  -X) method="$2"; shift ;; -o) out="$2"; shift ;; -w) fmt="$2"; shift ;; -D) hdr=1; shift ;;
  --data-binary) body="$2"; shift ;; -H|--max-time) shift ;; http*) url="$1" ;; esac; shift; done
echo "curl $method $url" >> "$CALLS"
resp(){ [ -n "$out" ] && printf '%s' "$2" > "$out" || [ "$hdr" = 1 ] || printf '%s' "$2"; [ -n "$fmt" ] && printf '%s' "$1"; exit 0; }
case "$url" in
  https://api.cloudflare.com/*)
    [ "${MOCK_CF:-ok}" = down ] && { [ -n "$fmt" ] && printf 000; exit 7; }
    case "$method $url" in
      "GET "*/accounts) resp 200 '{"success":true,"result":[{"id":"acct0000000000000000000000000001"}]}' ;;
      "GET "*/zones\?name=*) resp 200 '{"success":true,"result":[{"id":"zone0000000000000000000000000001"}]}' ;;
      "GET "*/storage/kv/namespaces\?*) resp 200 '{"success":true,"result":[{"id":"ns1","title":"devquest-backend-switch"},{"id":"ns2","title":"other"}]}' ;;
      "GET "*/values/api-target)
        [ "${MOCK_KV_GET:-ok}" = fail ] && resp 500 '{"success":false,"errors":[{"code":10001,"message":"boom"}]}'
        [ -f "$STATE/kv" ] && resp 200 "$(cat "$STATE/kv")"
        resp 404 '{"success":false,"errors":[{"code":10009,"message":"key not found"}]}' ;;
      "PUT "*/values/api-target)
        [ "${MOCK_KV_PUT:-ok}" = fail ] && resp 500 '{"success":false,"errors":[{"code":10001,"message":"boom"}]}'
        printf '%s' "$body" > "$STATE/kv"; echo "$body" >> "$STATE/kv_puts"; resp 200 '{"success":true}' ;;
      "GET "*/dns_records\?name=*) resp 200 "{\"success\":true,\"result\":$(cat "$STATE/dns" 2>/dev/null || echo '[]')}" ;;
      "POST "*/dns_records|"PUT "*/dns_records/*)
        c=$(printf '%s' "$body" | jq -r .content); n=$(printf '%s' "$body" | jq -r .name); echo "[{\"id\":\"r1\",\"type\":\"CNAME\",\"name\":\"$n\",\"content\":\"$c\"}]" > "$STATE/dns"
        echo "$method" >> "$STATE/dns_writes"; resp 200 '{"success":true}' ;;
      "DELETE "*/dns_records/*)
        [ "${MOCK_DNS_DEL:-ok}" = fail ] && resp 500 '{"success":false,"errors":[{"code":1,"message":"x"}]}'
        echo '[]' > "$STATE/dns"; resp 200 '{"success":true}' ;;
    esac ;;
  https://cloudflare-dns.com/*)
    c=$(jq -r '.[0].content // empty' "$STATE/dns" 2>/dev/null); [ "${MOCK_DOH:-ok}" = stale ] && c=""
    printf '{"Answer":[%s]}' "$([ -n "$c" ] && printf '{"type":5,"data":"%s."}' "$c")"; exit 0 ;;
  https://eks.quest.dhbang.co.kr/*) [ -n "$fmt" ] && printf '%s' "${MOCK_EKS_CODE:-200}"; exit 0 ;;
  https://quest.dhbang.co.kr/*)
    b=fly; if [ "${MOCK_WORKER:-follows}" = follows ] && jq -e '.target=="eks"' "$STATE/kv" >/dev/null 2>&1; then b=eks; fi
    printf 'HTTP/2 200\r\nx-devquest-backend: %s\r\n\r\n' "$b"; exit 0 ;;
esac
echo "목 curl: 모르는 호출 $method $url" >&2; exit 99
C
printf '#!/bin/sh\necho "sleep $*" >> "$CALLS"\n' > "$M/sleep"
chmod +x "$M/aws" "$M/kubectl" "$M/curl" "$M/sleep"
reset(){ : > "$CALLS"; rm -f "$M/lease_put" "$M/lease_cur" "$M/lease_deleted" "$M/kv" "$M/kv_puts" "$M/dns" "$M/dns_writes"; echo "${1:-0}" > "$M/lbn"; }
run(){ PATH="$M:$PATH" WIN_NOW="${WIN_NOW:-2000000000}" WIN_LB_WAIT=2 WIN_POLL=1 bash "$HERE/$1" "${@:2}" 2>"$M/err"; }
VALID="expires=2000003600 run=1 started=1999990000"; EXPIRED="expires=1999999999 run=1 started=1999990000"

echo "── decide-off (watchdog 표 · force)"
reset; out=$(MOCK_CLUSTER=yes run decide-off.sh force); [ "$out" = act ] && ok "force + 클러스터 있음 → act" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=no run decide-off.sh force); [ "$out" = act ] && ok "force + 없음 → act(destroy no-op)" || bad "out=$out"
reset; out=$(MOCK_CLUSTER=err run decide-off.sh force); rc=$?; [ $rc = 0 ] && [ "$out" = act ] && ok "force + 조회 오류 → 그래도 act(일시 오류가 끄기를 막지 않는다, QA F-1)" || bad "rc=$rc out=$out"
reset; out=$(MOCK_CLUSTER=err MOCK_LEASE=none run decide-off.sh watchdog); rc=$?; [ $rc = 2 ] && ok "watchdog + 클러스터 조회 오류 → rc 2('없음' 으로 안 떨어짐)" || bad "rc=$rc out=$out"
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

echo "── Application 판정(on.sh ⑤ — 2026-10-22 첫 실행: Healthy 대기가 6초 만에 통과하고 Synced 에서 죽었다)"
v(){ ( . "$HERE/common.sh"; win_app_verdict "$1" ); }
[ "$(v 'Synced Healthy Succeeded -')" = ok ] && ok "Synced+Healthy+Succeeded → ok" || bad "$(v 'Synced Healthy Succeeded -')"
[ "$(v 'OutOfSync Healthy - -')" = wait ] && ok "🔑 Healthy 만으로는 ok 가 아니다(동기화 전 빈 앱도 Healthy)" || bad "🔴 공허한 Healthy 를 통과시킴"
[ "$(v 'Unknown Healthy - ComparisonError')" = fail ] && ok "ComparisonError(렌더 실패) → fail" || bad "$(v 'Unknown Healthy - ComparisonError')"
[ "$(v 'OutOfSync Missing Failed -')" = fail ] && ok "operation Failed → fail" || bad "$(v 'OutOfSync Missing Failed -')"
[ "$(v 'Synced Progressing Running -')" = wait ] && ok "웨이브 진행 중 → wait" || bad "$(v 'Synced Progressing Running -')"
[ "$(v 'Synced Healthy Succeeded OrphanedResourceWarning')" = ok ] && ok "경고 condition 은 막지 않는다" || bad "$(v 'Synced Healthy Succeeded OrphanedResourceWarning')"
[ "$(v 'OutOfSync Healthy Running SyncError')" = fail ] && ok "SyncError → fail" || bad "$(v 'OutOfSync Healthy Running SyncError')"

echo "── 로그인 키 판정(on.sh — B7-16, C-4 1차: 파드·스모크 통과인데 로그인만 죽었다)"
lk(){ ( . "$HERE/common.sh"; win_login_keys_verdict "$@" ); }
[ "$(lk 'ANTHROPIC_API_KEY,GITHUB_CLIENT_ID,GITHUB_CLIENT_SECRET' Iv1 Iv1)" = ok ] && ok "두 키 + client id 일치 → ok" || bad "$(lk 'ANTHROPIC_API_KEY,GITHUB_CLIENT_ID,GITHUB_CLIENT_SECRET' Iv1 Iv1)"
lk 'ANTHROPIC_API_KEY,JUDGE0_API_KEY' '' Iv1 >/dev/null; [ $? = 1 ] && ok "🔑 다른 키만 있음(예전 판정 '개수 > 0' 은 통과) → 실패" || bad "공허한 통과"
lk 'GITHUB_CLIENT_ID' Iv1 Iv1 >/dev/null; [ $? = 1 ] && ok "secret 없음 → 실패" || bad "secret 누락 통과"
lk 'GITHUB_CLIENT_ID,GITHUB_CLIENT_SECRET' Iv1 Iv2 >/dev/null; [ $? = 1 ] && ok "client id 가 FE 와 다름 → 실패" || bad "불일치 통과"
lk 'GITHUB_CLIENT_ID,GITHUB_CLIENT_SECRET' '' '' >/dev/null; [ $? = 1 ] && ok "FE 값을 못 읽음(빈 값끼리 같음) → 실패" || bad "빈 값 일치를 통과"
lk 'XGITHUB_CLIENT_ID,GITHUB_CLIENT_SECRETX' Iv1 Iv1 >/dev/null; [ $? = 1 ] && ok "부분 일치 이름은 키로 안 친다" || bad "부분 일치 통과"
FE_CID=$(sed -nE 's/^[[:space:]]*VITE_GITHUB_CLIENT_ID:[[:space:]]*([A-Za-z0-9._-]+).*/\1/p' "$HERE/../../../../.github/workflows/fe-cd.yml" | head -1)
[ "$FE_CID" = Iv23li4rMCxm07zyBBmN ] && ok "on.sh 의 sed 가 실제 fe-cd.yml 에서 client id 를 뽑는다" || bad "추출='$FE_CID'"

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

echo "── switch-on (8-C C-2 — eks.quest · 트래픽 스위치)"
son(){ WIN_SWITCH_TRIES=2 run switch-on.sh "$@"; }
reset; MOCK_LEASE="$VALID" son fly >/dev/null; rc=$?
[ $rc = 0 ] && jq -e '.[0].content=="k8s-default-devquest-1.ap-northeast-2.elb.amazonaws.com"' "$M/dns" >/dev/null && [ ! -f "$M/kv_puts" ] \
  && ok "traffic=fly → eks.quest CNAME 만들고 KV 는 안 씀(트래픽 0)" || bad "rc=$rc kv=$(cat "$M/kv_puts" 2>/dev/null)"
grep -q 'GET https://api.cloudflare.com/client/v4/zones/zone0000000000000000000000000001/dns_records?name=eks.quest.dhbang.co.kr&' "$CALLS" \
  && ok "레코드 조회는 이름 정확 일치(ACM 검증 _xxx.eks.quest 를 안 건드림)" || bad "$(grep dns_records "$CALLS" | head -1)"
reset; echo '{"target":"eks","until":"2033-01-01T00:00:00Z"}' > "$M/kv"; MOCK_LEASE="$VALID" son fly >/dev/null; rc=$?
[ $rc = 0 ] && [ "$(jq -r .target "$M/kv")" = fly ] && ok "traffic=fly 재실행 + KV eks → fly 로 되돌림(U-5)" || bad "rc=$rc kv=$(cat "$M/kv")"
reset; echo '[{"id":"r1","type":"CNAME","name":"eks.quest.dhbang.co.kr","content":"old.elb.amazonaws.com"}]' > "$M/dns"; MOCK_LEASE="$VALID" son fly >/dev/null
[ "$(cat "$M/dns_writes")" = PUT ] && ok "기존 레코드는 PUT(갱신) — 중복 생성 안 함" || bad "$(cat "$M/dns_writes")"
reset; echo '[{"id":"a1","type":"A","name":"eks.quest.dhbang.co.kr","content":"1.2.3.4"}]' > "$M/dns"; MOCK_LEASE="$VALID" son fly >/dev/null; rc=$?
[ $rc = 1 ] && [ ! -f "$M/dns_writes" ] && ok "🔴 같은 이름에 A 레코드 → 손대지 않고 rc 1" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" son eks >/dev/null; rc=$?
[ $rc = 0 ] && [ "$(cat "$M/kv")" = '{"target":"eks","until":"2033-05-18T04:33:20Z"}' ] \
  && ok "traffic=eks → KV {eks, until = 임대 expires(2000003600)}, 공개 URL 헤더 eks 확인" || bad "rc=$rc kv=$(cat "$M/kv" 2>/dev/null) $(tail -3 "$M/err")"
grep -q ' traffic=eks$' "$M/lease_cur" && ok "임대에 traffic=eks 표식 — KV 보다 먼저" || bad "lease=$(cat "$M/lease_cur" 2>/dev/null)"
reset; MOCK_LEASE="$VALID" MOCK_WORKER=stuck son eks >/dev/null; rc=$?
[ $rc = 1 ] && ok "🔴 Worker 가 eks 로 안 바뀜 → rc 1(off 가 되돌린다)" || bad "rc=$rc"
reset; MOCK_LEASE="$EXPIRED" son eks >/dev/null; rc=$?
[ $rc = 1 ] && [ ! -f "$M/kv" ] && ok "🔴 임대 만료 → 트래픽 안 옮김" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_CF_TOKEN=err son eks >/dev/null; rc=$?
[ $rc = 1 ] && ok "토큰 조회 실패 → rc 1" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_EKS_CODE=502 son eks >/dev/null; rc=$?
[ $rc = 1 ] && [ ! -f "$M/kv" ] && ok "🔴 eks.quest 실제 이름 502 → KV 안 씀" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_DOH=stale son eks >/dev/null; rc=$?
[ $rc = 1 ] && [ ! -f "$M/kv" ] && ok "공개 DNS 가 새 ALB 를 안 답함 → rc 1" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_ALB="" son fly >/dev/null; rc=$?
[ $rc = 1 ] && [ ! -f "$M/dns_writes" ] && ok "Ingress 주소 없음 → 레코드 안 씀" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" GITHUB_ACTIONS=true son fly > "$M/out"
grep -qx '::add-mask::cf-test-token' "$M/out" && grep -qx '::add-mask::acct0000000000000000000000000001' "$M/out" && grep -qx '::add-mask::zone0000000000000000000000000001' "$M/out" \
  && ok "Actions 에서 토큰·계정·존 ID add-mask(stdout)" || bad "$(cat "$M/out")"
! grep -q 'cf-test-token' "$CALLS" && ok "토큰이 curl 인자에 없음(-H @<(…))" || bad "토큰이 인자로 샜다"

echo "── switch-off (8-C C-2 — 트래픽 먼저 · 배수 · 레코드)"
soff(){ WIN_DRAIN_WAIT=240 run switch-off.sh; }
reset; echo '{"target":"eks","until":"2033-01-01T00:00:00Z"}' > "$M/kv"; echo '[{"id":"r1","type":"CNAME","name":"eks.quest.dhbang.co.kr","content":"x"}]' > "$M/dns"
MOCK_LEASE="$VALID" soff >/dev/null; rc=$?
k=$(grep -n 'PUT .*values/api-target' "$CALLS" | cut -d: -f1 | head -1); w=$(grep -n '^sleep 240' "$CALLS" | cut -d: -f1 | head -1); d=$(grep -n 'DELETE .*dns_records' "$CALLS" | cut -d: -f1 | head -1)
[ $rc = 0 ] && [ "$(cat "$M/kv")" = '{"target":"fly"}' ] && [ -n "$k" ] && [ -n "$w" ] && [ -n "$d" ] && [ "$k" -lt "$w" ] && [ "$w" -lt "$d" ] \
  && ok "KV eks → fly → 배수 240s → 레코드 삭제 (순서)" || bad "rc=$rc k=$k w=$w d=$d"
reset; MOCK_LEASE="$VALID" soff >/dev/null; rc=$?
[ $rc = 0 ] && ! grep -q '^sleep 240' "$CALLS" && ok "KV 없음(eks 아님) → 대기 없이 진행" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_KV_GET=fail soff >/dev/null; rc=$?
[ $rc = 0 ] && grep -q '^sleep 240' "$CALLS" && ok "KV 읽기 실패 → eks 로 보고 배수 대기" || bad "rc=$rc"
reset; echo "$VALID traffic=eks" > "$M/lease_cur"; MOCK_CF_TOKEN=err soff >/dev/null; rc=$?
[ $rc = 1 ] && ok "🔴 Cloudflare 불통 + 임대 유효 + traffic=eks → rc 1(destroy 보류)" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_CF=down soff >/dev/null; rc=$?
[ $rc = 0 ] && ok "Cloudflare 불통 + 임대 유효 + 표식 없음(traffic=fly·on 실패) → 진행(U-1: on 실패 = 즉시 끄기)" || bad "rc=$rc"
reset; MOCK_LEASE="$EXPIRED" MOCK_CF=down WIN_NOW=2000000100 soff >/dev/null; rc=$?
[ $rc = 0 ] && grep -q '^sleep 139' "$CALLS" && ok "Cloudflare 불통 + 임대 만료 101s 전 → 남은 139s 배수 후 진행" || bad "rc=$rc $(grep sleep "$CALLS")"
reset; MOCK_LEASE=none MOCK_CF=down soff >/dev/null; rc=$?
[ $rc = 0 ] && ok "Cloudflare 불통 + 임대 없음(18:00 force·꺼진 날) → 진행" || bad "rc=$rc"
reset; MOCK_LEASE=err MOCK_CF=down soff >/dev/null; rc=$?
[ $rc = 1 ] && ok "Cloudflare 불통 + 임대 조회 오류 → rc 1(판정 불가 = 안 끈다)" || bad "rc=$rc"
reset; echo '{"target":"eks","until":"2033-01-01T00:00:00Z"}' > "$M/kv"; echo "$VALID traffic=eks" > "$M/lease_cur"; MOCK_KV_PUT=fail soff >/dev/null; rc=$?
[ $rc = 1 ] && ok "🔴 KV eks 인데 fly 쓰기 실패 + 임대 유효 → rc 1" || bad "rc=$rc"
reset; MOCK_LEASE="$VALID" MOCK_KV_PUT=fail soff >/dev/null; rc=$?
[ $rc = 0 ] && ok "KV 없음 + 쓰기 실패 → 트래픽은 이미 Fly, 진행" || bad "rc=$rc"
reset; echo '[{"id":"r1","type":"CNAME","name":"eks.quest.dhbang.co.kr","content":"x"}]' > "$M/dns"; MOCK_LEASE="$VALID" MOCK_DNS_DEL=fail soff >/dev/null; rc=$?
[ $rc = 0 ] && ok "레코드 삭제 실패 → 경고만(rc 0)" || bad "rc=$rc"

echo "── 임대 표식 이어 가기"
reset; echo "$VALID traffic=eks" > "$M/lease_cur"; ( PATH="$M:$PATH"; . "$HERE/common.sh"; win_lease_put 3 ) 2>/dev/null
grep -q ' traffic=eks$' "$M/lease_cur" && ok "창 중 재 on — 새 임대가 traffic=eks 표식을 잇는다" || bad "$(cat "$M/lease_cur")"
reset; MOCK_LEASE="$VALID"; ( PATH="$M:$PATH"; MOCK_LEASE="$VALID"; export MOCK_LEASE; . "$HERE/common.sh"; win_lease_put 3 ) 2>/dev/null
! grep -q 'traffic=eks' "$M/lease_cur" && ok "표식 없는 임대 → 새 임대도 표식 없음" || bad "$(cat "$M/lease_cur")"

echo "── QA F-1~3 (C-2)"
reset; echo '[{"id":"v1","type":"CNAME","name":"_abc.eks.quest.dhbang.co.kr","content":"_x.acm-validations.aws"}]' > "$M/dns"; MOCK_LEASE="$VALID" soff >/dev/null
! grep -q 'DELETE .*dns_records/v1' "$CALLS" && ok "F-2 API 가 이름 다른 레코드(ACM 검증)를 돌려줘도 지우지 않는다" || bad "🔴 ACM 검증 CNAME 삭제"
reset; echo "expires=2000036000 run=1 started=1999990000 traffic=eks" > "$M/lease_cur"; ( PATH="$M:$PATH"; . "$HERE/common.sh"; win_lease_put 1 ) 2>/dev/null
grep -q '^expires=2000036000 .* traffic=eks$' "$M/lease_cur" && ok "F-1b 표식 있는 임대에 더 짧은 재 on → 만료를 앞당기지 않음" || bad "$(cat "$M/lease_cur")"
reset; ( PATH="$M:$PATH"; MOCK_LEASE=err; export MOCK_LEASE; . "$HERE/common.sh"; win_lease_put 3 ) 2>/dev/null
grep -q ' traffic=eks$' "$M/lease_put" && ok "F-1a 임대 조회 오류 → 표식 보수적으로 유지" || bad "$(cat "$M/lease_put" 2>/dev/null)"
reset; GITHUB_STEP_SUMMARY="$M/sum" MOCK_LEASE="$VALID" MOCK_KV_PUT=fail soff >/dev/null
grep -q 'KV 쓰기 실패' "$M/sum" && ! grep -q 'KV api-target = fly' "$M/sum" && ok "F-3 KV 쓰기 실패는 Summary 에 남는다(초록이어도)" || bad "$(cat "$M/sum")"
rm -f "$M/sum"

echo
echo "합계: ✅ $P · 🔴 $F"
[ "$F" = 0 ]
