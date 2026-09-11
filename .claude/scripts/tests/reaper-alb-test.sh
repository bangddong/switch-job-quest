#!/usr/bin/env bash
# eks-reaper.sh 의 Stage 4 ALB 회수 경로 회귀 테스트 — **AWS 접촉 0건, $0**.
#
# 왜 필요한가: 리퍼는 **사람이 없을 때만** 도는 코드다. 실환경에서 관찰될 기회가
# 구조적으로 적고, 틀렸다는 걸 알게 되는 시점이 "청구서가 왔을 때"다.
# 그래서 목(mock)으로 강제 실행해 경로를 밟아 본다.
#
# 🔴 **목 하네스도 검증 대상이다.** 이 레포는 목 버그를 세 번 잡았고(인자 위치 2건 ·
#   패턴이 부분문자열로 샘), 그중 한 번은 **거짓 실패 2건**을 만들었다.
#   그래서 각 케이스에 "이 케이스가 무엇의 실패를 잡나"를 적고, 마지막에 **반증 케이스**
#   (일부러 고장 낸 리퍼가 실제로 실패하는지)를 돌린다.
#
# 사용: bash .claude/scripts/tests/reaper-alb-test.sh
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
REAPER="$ROOT/.claude/scripts/eks-reaper.sh"
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  🔴 $1"; }

# ── 목 환경 만들기 ────────────────────────────────────────────────
# kubectl·aws·tofu 를 가짜로 바꿔치기한다. 호출 인자를 파일에 적어 두고 검사한다.
make_mocks() {
  MOCK=$(mktemp -d)
  CALLS="$MOCK/calls.txt"; : > "$CALLS"

  cat > "$MOCK/kubectl" <<'EOF'
#!/bin/sh
echo "kubectl $*" >> "$CALLS"
case "$1 $2" in
  "get ingress")
    [ "${MOCK_INGRESS_COUNT:-1}" = "0" ] && exit 0
    i=0; while [ $i -lt "${MOCK_INGRESS_COUNT:-1}" ]; do
      echo "default   devquest   alb   *   k8s-xyz.elb.amazonaws.com   80   1m"; i=$((i+1))
    done ;;
  "delete ingress") exit "${MOCK_DELETE_RC:-0}" ;;
esac
exit 0
EOF

  cat > "$MOCK/aws" <<'EOF'
#!/bin/sh
echo "aws $*" >> "$CALLS"
for a in "$@"; do
  case "$a" in
    list-clusters)          echo "devquest-eks"; exit 0 ;;
    describe-db-instances)  exit 0 ;;
    update-kubeconfig)      exit "${MOCK_KUBECONFIG_RC:-0}" ;;
    describe-volumes)       exit 0 ;;
    describe-load-balancers)
      [ "${MOCK_ALB_LEFT:-0}" = "1" ] && printf 'k8s-devquest-abc\tarn:aws:elasticloadbalancing:x:y:loadbalancer/app/k8s-devquest-abc/1\n'
      exit 0 ;;
  esac
done
exit 0
EOF

  cat > "$MOCK/tofu" <<'EOF'
#!/bin/sh
echo "tofu $*" >> "$CALLS"
[ "$1" = "destroy" ] && exit "${MOCK_DESTROY_RC:-0}"
exit 0
EOF

  chmod +x "$MOCK/kubectl" "$MOCK/aws" "$MOCK/tofu"
}

# 마커·하트비트를 "사람이 사라진" 상태로 세팅하고 리퍼를 돌린다.
run_reaper() {
  SESS="$ROOT/.claude/eks-session"
  mkdir -p "$SESS"
  # 기존 세션 파일을 덮어쓰지 않도록 백업 (실제 세션 중에 이 테스트를 돌릴 수 있다)
  BACKUP=$(mktemp -d); cp -p "$SESS"/* "$BACKUP/" 2>/dev/null || true

  CDIR="$MOCK/cluster"; mkdir -p "$CDIR"
  printf 'cluster_dir=%s\napplied_at_h=2026-09-11 10:00\n' "$CDIR" > "$SESS/active"
  # 하트비트를 TTL 밖으로 (3시간 전)
  touch -t "$(date -v-3H '+%Y%m%d%H%M' 2>/dev/null || date -d '3 hours ago' '+%Y%m%d%H%M')" "$SESS/heartbeat"
  : > "$SESS/reaper.log"

  PATH="$MOCK:$PATH" CALLS="$CALLS" \
    MOCK_INGRESS_COUNT="${MOCK_INGRESS_COUNT:-1}" \
    MOCK_DELETE_RC="${MOCK_DELETE_RC:-0}" \
    MOCK_KUBECONFIG_RC="${MOCK_KUBECONFIG_RC:-0}" \
    MOCK_DESTROY_RC="${MOCK_DESTROY_RC:-0}" \
    MOCK_ALB_LEFT="${MOCK_ALB_LEFT:-0}" \
    bash "$1" >/dev/null 2>&1
  RC=$?
  LOGOUT=$(cat "$SESS/reaper.log" 2>/dev/null)

  # 원상복구
  rm -f "$SESS"/active "$SESS"/heartbeat "$SESS"/reaper.log
  cp -p "$BACKUP"/* "$SESS/" 2>/dev/null || true
  rm -rf "$BACKUP"
}

# ── 케이스 ────────────────────────────────────────────────────────

echo "── ① Ingress 가 있으면 destroy **전에** 지운다"
echo "   (무엇의 실패를 잡나: 순서가 뒤집혀 SG DependencyViolation → 무한 destroy 루프)"
make_mocks; MOCK_INGRESS_COUNT=1 run_reaper "$REAPER"
del_line=$(grep -n 'kubectl delete ingress' "$CALLS" | head -1 | cut -d: -f1)
des_line=$(grep -n 'tofu destroy' "$CALLS" | head -1 | cut -d: -f1)
if [ -n "$del_line" ] && [ -n "$des_line" ] && [ "$del_line" -lt "$des_line" ]; then
  ok "delete(line $del_line) < destroy(line $des_line)"
else
  bad "순서 어긋남 — delete=${del_line:-없음} destroy=${des_line:-없음}"
fi
echo "$CALLS" >/dev/null; rm -rf "$MOCK"

echo "── ② Ingress 0건이면 delete 를 부르지 않는다"
echo "   (무엇의 실패를 잡나: 매 발동마다 불필요한 kubectl 호출·오해 유발 로그)"
make_mocks; MOCK_INGRESS_COUNT=0 run_reaper "$REAPER"
if grep -q 'kubectl delete ingress' "$CALLS"; then bad "0건인데 delete 호출됨"; else ok "delete 미호출"; fi
case "$LOGOUT" in *"Ingress 0건"*) ok "0건 로그 남김" ;; *) bad "0건 로그 없음" ;; esac
rm -rf "$MOCK"

echo "── ③ Ingress 삭제가 실패해도 destroy 는 계속된다"
echo "   (무엇의 실패를 잡나: 🔴 안전장치를 고치려다 안전장치를 벽돌로 만드는 것)"
make_mocks; MOCK_INGRESS_COUNT=1 MOCK_DELETE_RC=1 run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then ok "delete 실패에도 destroy 도달"; else bad "destroy 에 도달 못 함 — 리퍼가 멈췄다"; fi
case "$LOGOUT" in *"삭제 실패/타임아웃"*) ok "실패를 로그에 남김" ;; *) bad "실패가 조용히 묻힘" ;; esac
rm -rf "$MOCK"

echo "── ④ kubeconfig 갱신 실패 시 정리를 건너뛰되 destroy 는 한다"
echo "   (무엇의 실패를 잡나: 클러스터 접근 불가인데 kubectl 로 헛발질하다 타임아웃 누적)"
make_mocks; MOCK_KUBECONFIG_RC=1 run_reaper "$REAPER"
if grep -q 'kubectl delete ingress' "$CALLS"; then bad "접근 불가인데 delete 시도"; else ok "delete 생략"; fi
if grep -q 'tofu destroy' "$CALLS"; then ok "destroy 는 수행"; else bad "destroy 누락"; fi
rm -rf "$MOCK"

echo "── ⑤ destroy 후 ALB 가 남아 있으면 경고한다"
echo "   (무엇의 실패를 잡나: 🔴 월 \$16.43 이 아무 신호 없이 나가는 것)"
make_mocks; MOCK_ALB_LEFT=1 run_reaper "$REAPER"
case "$LOGOUT" in
  *"고아 ALB 감지"*) ok "고아 ALB 경고 출력" ;;
  *) bad "ALB 가 남았는데 경고 없음" ;;
esac
case "$LOGOUT" in
  *"delete-load-balancer"*) ok "복구 명령 제시" ;;
  *) bad "복구 명령 없음 — 경고만으로는 다음 사람이 못 고친다" ;;
esac
rm -rf "$MOCK"

echo "── ⑥ ALB 가 없으면 경고하지 않는다"
echo "   (무엇의 실패를 잡나: 🔴 매번 울리는 경보 → 사람이 무시 → 진짜 고아를 놓침)"
make_mocks; MOCK_ALB_LEFT=0 run_reaper "$REAPER"
case "$LOGOUT" in
  *"고아 ALB 감지"*) bad "오탐 — 없는데 경고" ;;
  *) ok "조용함" ;;
esac
rm -rf "$MOCK"

echo "── ⑦ 반증: 고장 낸 리퍼는 실제로 실패해야 한다"
echo "   (무엇의 실패를 잡나: 🔴 이 테스트가 무엇이든 통과시키는 가짜 검사인 경우)"
# 🔑 **반증 방법을 한 번 잘못 골랐다 — 기록해 둔다.**
#   처음엔 "cleanup 호출을 destroy 뒤로 옮긴 버전"을 만들었는데, 옮긴 자리가 `tofu init`
#   직전이라 **여전히 destroy 보다 앞**이었다. 케이스 ①은 그걸 정상으로 판정한다.
#   즉 그 반증은 **아무것도 반증하지 못했다.** 진짜 반증은 호출을 **지우는** 것이다.
#   → 반증도 잘못 만들 수 있다. "고장 냈다"고 믿는 것과 실제로 고장 난 것은 다르다.
BROKEN="$(mktemp).sh"
ruby -e '
s = File.read(ARGV[0])
before = s.length
s = s.gsub(/^cleanup_k8s_loadbalancers "\$\(echo.*\n/, "")
abort("반증 준비 실패: 호출부를 못 찾아 아무것도 안 지웠다") if s.length == before
File.write(ARGV[1], s)' "$REAPER" "$BROKEN" || { bad "반증 스크립트 생성 실패"; }
make_mocks; MOCK_INGRESS_COUNT=1 run_reaper "$BROKEN"
if grep -q 'kubectl delete ingress' "$CALLS"; then
  bad "호출을 지웠는데도 delete 가 보인다 — **이 테스트는 아무것도 검증하지 않는다**"
else
  ok "호출을 지우니 delete 사라짐 — 테스트가 실제로 그 코드를 밟고 있다"
fi
rm -rf "$MOCK" "$BROKEN"

echo
echo "── 결과: 통과 $PASS · 실패 $FAIL"
[ "$FAIL" -eq 0 ] || exit 1
