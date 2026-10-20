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
# 중단돼도(Ctrl-C 등) 반증 사본을 남기지 않는다 — gitignore 밖이라 untracked 로 남는다(QA F-5).
# 실제 세션 파일 복원은 각 케이스가 하고, 여기선 남은 백업 디렉터리가 있으면 되돌린다.
_cleanup() {
  rm -f "$ROOT"/.claude/scripts/.reaper-broken-*.sh 2>/dev/null
  if [ -n "${BACKUP:-}" ] && [ -d "$BACKUP" ]; then
    rm -f "$ROOT/.claude/eks-session"/{active,heartbeat,lastcheck,state.cache,reaper.log} 2>/dev/null
    cp -p "$BACKUP"/* "$ROOT/.claude/eks-session/" 2>/dev/null; rm -rf "$BACKUP"
  fi
}
trap _cleanup EXIT
trap 'exit 130' INT TERM   # 핸들러가 끝나도 bash 는 계속 진행한다 — 중단 신호면 멈춘다(EXIT trap 이 정리, QA F-7)

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
    describe-cluster)
      [ "${MOCK_DESCRIBE_RC:-0}" = "0" ] || exit "$MOCK_DESCRIBE_RC"
      echo "${MOCK_CREATED_AT}"; exit 0 ;;
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

# epoch → aws CLI 와 같은 ISO8601(UTC, 소수 6자리). BSD(`-r`)·GNU(`-d @`) 둘 다.
iso_at() { date -u -r "$1" '+%Y-%m-%dT%H:%M:%S.123000+00:00' 2>/dev/null || date -u -d "@$1" '+%Y-%m-%dT%H:%M:%S.123000+00:00'; }
APPLIED_AT=$(( $(date +%s) - 10800 ))

# 마커·하트비트를 "사람이 사라진" 상태로 세팅하고 리퍼를 돌린다.
run_reaper() {
  SESS="$ROOT/.claude/eks-session"
  mkdir -p "$SESS"
  # 기존 세션 파일을 덮어쓰지 않도록 백업 (실제 세션 중에 이 테스트를 돌릴 수 있다)
  BACKUP=$(mktemp -d); cp -p "$SESS"/* "$BACKUP/" 2>/dev/null || true

  CDIR="$MOCK/cluster"; mkdir -p "$CDIR"
  # applied_at = 3시간 전. 클러스터 createdAt 기본값 = 그 5분 뒤(= 이 마커의 apply 가 만든 클러스터, mine).
  # 소유 판정(📌D-016 4번)이 생긴 뒤로 applied_at·createdAt 이 없으면 리퍼는 destroy 하지 않는다.
  if [ "${MOCK_NO_APPLIED_AT:-0}" = "1" ]; then
    printf 'cluster_dir=%s\napplied_at_h=2026-09-11 10:00\n' "$CDIR" > "$SESS/active"
  else
    printf 'cluster_dir=%s\napplied_at=%s\napplied_at_h=2026-09-11 10:00\n' "$CDIR" "$APPLIED_AT" > "$SESS/active"
  fi
  # 하트비트를 TTL 밖으로 (3시간 전)
  touch -t "$(date -v-3H '+%Y%m%d%H%M' 2>/dev/null || date -d '3 hours ago' '+%Y%m%d%H%M')" "$SESS/heartbeat"
  : > "$SESS/reaper.log"

  PATH="$MOCK:$PATH" CALLS="$CALLS" \
    MOCK_INGRESS_COUNT="${MOCK_INGRESS_COUNT:-1}" \
    MOCK_DELETE_RC="${MOCK_DELETE_RC:-0}" \
    MOCK_KUBECONFIG_RC="${MOCK_KUBECONFIG_RC:-0}" \
    MOCK_DESTROY_RC="${MOCK_DESTROY_RC:-0}" \
    MOCK_ALB_LEFT="${MOCK_ALB_LEFT:-0}" \
    MOCK_DESCRIBE_RC="${MOCK_DESCRIBE_RC:-0}" \
    MOCK_CREATED_AT="${MOCK_CREATED_AT:-$(iso_at $((APPLIED_AT + 300)))}" \
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
# 🔴 고장 낸 사본은 **원본과 같은 디렉터리**에 둔다 (2026-10-20 발견). 리퍼는 자기 위치에서 ROOT 를
#   계산하므로(`$SCRIPT_DIR/../..`), mktemp 사본은 **다른 마커 디렉터리**를 보고 "마커 없음" 으로 즉시
#   종료한다 — 그러면 "delete 가 사라졌다" 는 판정이 **코드를 지워서가 아니라 리퍼가 아예 안 돌아서**
#   참이 된다. 이 반증은 처음부터 공허했다(⑬ 을 만들다 반대 방향 단언으로 드러남).
BROKEN="$ROOT/.claude/scripts/.reaper-broken-$$.sh"
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

# ══ 소유 판정 (📌D-016 4번, 2026-10-20) ═══════════════════════════════
# 🔴 여기부터 잡는 실패의 무게가 다르다 — 위 케이스들은 **과금**을, 아래는 **prod 장애**를 막는다.
#    CI 가 평일 창 동안 같은 이름의 클러스터를 prod 로 켠다. 로컬 마커는 1-network apply·따옴표 오탐에도
#    생긴다. 마커만 보고 destroy 하면 Fly 복귀 없이 prod 가 내려간다.

echo "── ⑧ 마커보다 **먼저** 생긴 클러스터(CI 창)는 destroy 하지 않고 마커를 푼다"
echo "   (무엇의 실패를 잡나: 🔴 오탐 마커 + 2h 공백 → 리퍼가 prod 를 destroy — B4-6·N-4b)"
make_mocks; MOCK_CREATED_AT=$(iso_at $((APPLIED_AT - 3600))) run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then bad "🔴 CI 클러스터를 destroy 했다"; else ok "destroy 안 함"; fi
if grep -q 'kubectl delete ingress' "$CALLS"; then bad "🔴 CI 클러스터의 Ingress(=prod 입구)를 지웠다"; else ok "Ingress 안 건드림"; fi
case "$LOGOUT" in *"이 마커가 만든 클러스터가 아니다"*) ok "foreign 로그" ;; *) bad "판정 로그 없음" ;; esac
[ "$RC" -eq 0 ] && ok "rc=0 (정상 종료 — 마커 해제)" || bad "rc=$RC"
rm -rf "$MOCK"

echo "── ⑨ 마커보다 **한참 뒤**(+1h 초과) 생긴 클러스터도 foreign"
echo "   (무엇의 실패를 잡나: 정리 못 된 옛 마커 뒤에 CI 가 켠 클러스터를 내 것으로 오인)"
make_mocks; MOCK_CREATED_AT=$(iso_at $((APPLIED_AT + 7200))) run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then bad "🔴 창 밖 클러스터를 destroy"; else ok "destroy 안 함"; fi
rm -rf "$MOCK"

echo "── ⑩ createdAt 조회 실패 → destroy 하지 않고 마커 유지(rc≠0)"
echo "   (무엇의 실패를 잡나: 판정 불가를 mine 으로 떨어뜨리는 fail-open)"
make_mocks; MOCK_DESCRIBE_RC=255 run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then bad "🔴 판정 불가인데 destroy"; else ok "destroy 안 함"; fi
case "$LOGOUT" in *"소유 판정 불가"*) ok "unknown 로그" ;; *) bad "unknown 로그 없음" ;; esac
[ "$RC" -ne 0 ] && ok "rc=$RC (다음 주기 재시도)" || bad "rc=0 — 판정 불가가 조용히 끝남"
rm -rf "$MOCK"

echo "── ⑪ 마커에 applied_at 이 없으면 unknown"
make_mocks; MOCK_NO_APPLIED_AT=1 run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then bad "🔴 applied_at 없이 destroy"; else ok "destroy 안 함"; fi
rm -rf "$MOCK"

echo "── ⑫ +09:00 표기 createdAt 도 같은 시각으로 읽는다 (mine → destroy)"
echo "   (무엇의 실패를 잡나: 시간대 표기 차이로 판정이 9시간 어긋나는 것)"
kst=$(date -u -r $((APPLIED_AT + 300 + 32400)) '+%Y-%m-%dT%H:%M:%S.000000+09:00' 2>/dev/null \
      || date -u -d "@$((APPLIED_AT + 300 + 32400))" '+%Y-%m-%dT%H:%M:%S.000000+09:00')
make_mocks; MOCK_CREATED_AT="$kst" run_reaper "$REAPER"
if grep -q 'tofu destroy' "$CALLS"; then ok "+09:00 → mine → destroy"; else bad "+09:00 해석 실패 — 학습 클러스터 방치"; fi
rm -rf "$MOCK"

echo "── ⑬ 반증: 소유 판정을 지운 리퍼는 ⑧ 에서 실패해야 한다"
# 🔴 고장 낸 사본은 **원본과 같은 디렉터리**에 둔다 (2026-10-20 발견). 리퍼는 자기 위치에서 ROOT 를
#   계산하므로(`$SCRIPT_DIR/../..`), mktemp 사본은 **다른 마커 디렉터리**를 보고 "마커 없음" 으로 즉시
#   종료한다 — 그러면 "delete 가 사라졌다" 는 판정이 **코드를 지워서가 아니라 리퍼가 아예 안 돌아서**
#   참이 된다. 이 반증은 처음부터 공허했다(⑬ 을 만들다 반대 방향 단언으로 드러남).
BROKEN="$ROOT/.claude/scripts/.reaper-broken-$$.sh"
ruby -e '
s = File.read(ARGV[0])
before = s.length
s = s.sub(/^if \[ -n "\$CLUSTERS" \]; then\n  if \[ "\$\{OWNER_LIB_MISSING.*?\nfi\n/m, "")
abort("반증 준비 실패: 소유 판정 블록을 못 찾았다") if s.length == before
File.write(ARGV[1], s)' "$REAPER" "$BROKEN" || bad "반증 스크립트 생성 실패"
make_mocks; MOCK_CREATED_AT=$(iso_at $((APPLIED_AT - 3600))) run_reaper "$BROKEN"
if grep -q 'tofu destroy' "$CALLS"; then ok "판정을 지우니 CI 클러스터를 destroy — ⑧ 이 실제로 그 코드를 밟는다"
else bad "판정을 지워도 destroy 안 함 — **⑧ 은 아무것도 검증하지 않는다**"; fi
rm -rf "$MOCK" "$BROKEN"

echo "── ⑭ 마커 훅: 첫 applied_at 보존 조건 = 하트비트 신선 AND 클러스터 떠 있음(조회 실패면 하트비트만)"
echo "   (무엇의 실패를 잡나: 두 번째 apply 가 시각을 덮어써 자기 클러스터가 foreign ·"
echo "    QA F-1 한 턴 안 destroy→재apply 에서 옛 시각이 남아 새 클러스터가 foreign — 둘 다 감시가 조용히 꺼진다)"
MARKER_HOOK="$ROOT/.claude/scripts/eks-session-marker.sh"
SB=$(mktemp -d); mkdir -p "$SB/bin"
cat > "$SB/bin/aws" <<'MOCKEOF'
#!/bin/sh
for a in "$@"; do case "$a" in
  list-clusters) [ "$MOCK_LIST" = "fail" ] && exit 1; [ -n "$MOCK_LIST" ] && echo "$MOCK_LIST"; exit 0 ;;
esac; done
exit 0
MOCKEOF
chmod +x "$SB/bin/aws"
REPO=$(mktemp -d); ( cd "$REPO" && git init -q . ); mkdir -p "$REPO/.claude/eks-session"
fire() {  # $1 = MOCK_LIST, $2 = 하트비트 (fresh|stale)
  printf 'applied_at=1000\napplied_at_h=old\n' > "$REPO/.claude/eks-session/active"
  if [ "$2" = fresh ]; then touch "$REPO/.claude/eks-session/heartbeat"
  else touch -t "$(date -v-3H '+%Y%m%d%H%M' 2>/dev/null || date -d '3 hours ago' '+%Y%m%d%H%M')" "$REPO/.claude/eks-session/heartbeat"; fi
  ( cd "$REPO" && echo '{"tool_input":{"command":"cd infra/aws-eks/2-cluster && tofu apply"}}' \
      | MOCK_LIST="$1" PATH="$SB/bin:$PATH" bash "$MARKER_HOOK" ) >/dev/null 2>&1
  grep -q '^applied_at=1000$' "$REPO/.claude/eks-session/active" && echo kept || echo new
}
[ "$(fire devquest-eks fresh)" = kept ] && ok "클러스터 있음 + 신선 → 보존" || bad "클러스터 있음 + 신선인데 덮어씀"
[ "$(fire "" fresh)" = new ] && ok "클러스터 없음 + 신선 → 새 시각 (F-1)" || bad "🔴 클러스터 없는데 옛 시각 보존 — destroy→재apply 감시 소실"
[ "$(fire devquest-eks stale)" = new ] && ok "하트비트 stale → 새 시각" || bad "하트비트 죽었는데 옛 시각 보존"
[ "$(fire fail fresh)" = kept ] && ok "조회 실패 + 신선 → 보존(하트비트 규칙)" || bad "조회 실패인데 덮어씀"
rm -rf "$REPO" "$SB"

echo "── ⑯ 경계: createdAt = applied_at −30s / +3600s 는 mine, −31s / +3601s 는 foreign"
echo "   (무엇의 실패를 잡나: QA F-3 — 부등호 방향·등호 처리 실수가 먼 값 케이스로는 안 보인다)"
for spec in "-30:mine" "3600:mine" "-31:foreign" "3601:foreign"; do
  off=${spec%%:*}; want=${spec##*:}
  make_mocks; MOCK_CREATED_AT=$(iso_at $((APPLIED_AT + off))) run_reaper "$REAPER"
  if grep -q 'tofu destroy' "$CALLS"; then got=mine; else got=foreign; fi
  [ "$got" = "$want" ] && ok "offset $off → $want" || bad "offset $off → $got (기대 $want)"
  rm -rf "$MOCK"
done

echo "── ⑮ 리마인더: foreign 클러스터면 마커를 풀고 destroy 를 권하지 않는다"
echo "   (무엇의 실패를 잡나: 🔴 매 턴 '오늘 끝이면 → tofu destroy' 로 prod 를 끄라고 안내 — B4-7)"
REMINDER="$ROOT/.claude/scripts/eks-heartbeat-reminder.sh"
SESS="$ROOT/.claude/eks-session"; mkdir -p "$SESS"
BACKUP=$(mktemp -d); cp -p "$SESS"/* "$BACKUP/" 2>/dev/null || true
rm -f "$SESS/lastcheck" "$SESS/state.cache"
printf 'applied_at=%s\napplied_at_h=x\n' "$APPLIED_AT" > "$SESS/active"
make_mocks
OUT=$( cd "$ROOT" && PATH="$MOCK:$PATH" CALLS="$CALLS" MOCK_CREATED_AT="$(iso_at $((APPLIED_AT - 3600)))" bash "$REMINDER" 2>&1 )
[ -f "$SESS/active" ] && bad "마커가 남았다" || ok "마커 해제"
case "$OUT" in *"tofu destroy"*) bad "🔴 destroy 를 권했다" ;; *) ok "destroy 권고 없음" ;; esac
# 대조군 — mine 이면 종전대로 destroy 를 권한다(한쪽만 보면 반대 방향으로 깨진다)
rm -f "$SESS/lastcheck" "$SESS/state.cache"
printf 'applied_at=%s\napplied_at_h=x\n' "$APPLIED_AT" > "$SESS/active"
OUT=$( cd "$ROOT" && PATH="$MOCK:$PATH" CALLS="$CALLS" MOCK_CREATED_AT="$(iso_at $((APPLIED_AT + 300)))" bash "$REMINDER" 2>&1 )
[ -f "$SESS/active" ] && ok "대조군(mine): 마커 유지" || bad "대조군: mine 인데 마커 해제"
case "$OUT" in *"tofu destroy"*) ok "대조군: destroy 안내 유지" ;; *) bad "대조군: 안내 사라짐" ;; esac
# unknown — 판정 불가면 마커 유지 + 판정 불가 경고(QA F-3)
rm -f "$SESS/lastcheck" "$SESS/state.cache"
printf 'applied_at=%s\napplied_at_h=x\n' "$APPLIED_AT" > "$SESS/active"
OUT=$( cd "$ROOT" && PATH="$MOCK:$PATH" CALLS="$CALLS" MOCK_DESCRIBE_RC=255 bash "$REMINDER" 2>&1 )
[ -f "$SESS/active" ] && ok "unknown: 마커 유지" || bad "unknown: 마커 해제 — 감시 소실"
case "$OUT" in *"판정하지 못했습니다"*) ok "unknown: 판정 불가 경고" ;; *) bad "unknown: 경고 없음" ;; esac
rm -rf "$MOCK"
rm -f "$SESS"/active "$SESS"/heartbeat "$SESS"/lastcheck "$SESS"/state.cache
cp -p "$BACKUP"/* "$SESS/" 2>/dev/null || true; rm -rf "$BACKUP"

echo
echo "── 결과: 통과 $PASS · 실패 $FAIL"
[ "$FAIL" -eq 0 ] || exit 1
