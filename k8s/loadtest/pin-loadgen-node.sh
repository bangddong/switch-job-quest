#!/bin/sh
# 부하 생성기 전용 노드 확보 — k6 를 측정 대상과 **다른 노드**에 격리한다.
# 계획서 `docs/superpowers/plans/2026-09-05-memory-rightsizing.md` §9.7
#
# 왜 스크립트인가: 이 절차는 **과금 구간**에서 돈다. 손으로 치면 노드 선택·대기 판단에
# 시간이 들고, 그 시간이 그대로 비용이다(09-06: 유휴 대기가 세션 비용의 85%).
# 또한 "빈 노드가 정말 비었는가"는 **눈으로 확인하면 틀리는** 종류라 기계가 판정한다.
#
# 하는 일:
#   ① 생성기 노드 결정 — 이미 지정돼 있으면 **재사용**, 없으면 측정 대상이 가장 적은 노드
#   ② cordon (새 파드 유입 차단) + loadgen 라벨 (k6 목적지 지정)
#   ③ 그 노드에 남아 있는 측정 대상 파드를 내보낸다
#   ④ **0개가 됐는지 검증한다** — 안 되면 실패로 끝낸다 (조용히 넘어가지 않는다)
#
# 사용법:  sh k8s/loadtest/pin-loadgen-node.sh
#          NODE=<노드명> sh k8s/loadtest/pin-loadgen-node.sh   # 직접 지정
set -eu

NS="${NS:-default}"
LABEL_KEY="devquest.io/role"
LABEL_VAL="loadgen"
# 측정 대상 = k6 와 절대 같은 노드에 있으면 안 되는 파드들.
# k6.yaml 의 podAntiAffinity values 목록과 **반드시 같아야 한다.**
APPS="ai-api core-api daily-api postgres"
WAIT_SECS="${WAIT_SECS:-180}"

is_app() {
  for a in $APPS; do [ "$1" = "$a" ] && return 0; done
  return 1
}

# 측정 대상 파드가 "아직 준비 안 됨"인가. $1=phase $2=containerStatuses[*].ready
# ⚠️ `case` 를 쓰지 않는다 — 패턴의 `)` 가 `$( )` 안에서 명령치환을 조기 종료시킨다
#    (bash 3.2 파서 함정. macOS 기본 sh 가 이거다. 09-08 실제로 밟았다).
pod_not_ready() {
  [ "$1" != "Running" ] && return 0
  [ -z "$2" ] && return 0
  [ "$2" = "<none>" ] && return 0
  echo "$2" | grep -q false && return 0
  return 1
}

# 파드 스냅샷 한 번 조회 — 실패하면 **빈 결과가 아니라 실패로** 알린다.
# 🔴 F-7: `... | grep -c x || true` 는 "정말 0개"와 "kubectl 이 실패해 입력이 빔"을
#   **구분하지 못한다**(둘 다 0). 검증 루프가 그 0 을 보면 `left=0 notready=0` 이 되어
#   ✅ 를 조기 선언한다 — 이 스크립트가 없애려던 *"부재가 성공과 똑같이 생긴"* 실패가
#   검증 단계 자체에 재도입되는 것이다. 그래서 조회를 분리하고 **종료코드를 본다.**
# 부수 효과: 루프 반복당 kubectl 호출이 2회 → 1회로 줄고, left 와 notready 가
#   **같은 스냅샷**에서 나와 서로 어긋나지 않는다.
pod_snapshot() {
  kubectl get pods -n "$NS" --no-headers \
    -o custom-columns=NODE:.spec.nodeName,APP:.metadata.labels.app,PHASE:.status.phase,READY:.status.containerStatuses[*].ready
}

# 스냅샷($1) 안에서 노드($2) 위의 측정 대상 개수
count_apps_on() {
  echo "$1" | awk -v n="$2" '$1==n {print $2}' |
    while read -r a; do is_app "$a" && echo x; done | grep -c x || true
}

nodes=$(kubectl get nodes -o custom-columns=NAME:.metadata.name --no-headers)
node_count=$(echo "$nodes" | grep -c . || true)
if [ "$node_count" -lt 2 ]; then
  echo "🔴 노드가 ${node_count}대다. 생성기를 격리할 수 없다." >&2
  echo "   k6 를 측정 대상과 같은 노드에 올리면 그 회차 데이터는 못 쓴다(계획서 §9.5)." >&2
  echo "   → tofu apply 로 노드를 2대 이상으로 올린 뒤 다시 실행할 것." >&2
  exit 1
fi

# ── ① 생성기 노드 결정 ─────────────────────────────────────────────
# 🔴 재실행 안전성: 이 스크립트는 ④ 검증에서 실패해 exit 1 로 끝날 수 있고, 그때 노드는
#   **이미 cordon + 라벨된 상태로 남는다.** 그대로 재실행하면서 매번 새 노드를 고르면
#   cordon 된 노드가 늘어나 앱이 갈 곳을 잃는다(Pending 스톰). 그래서 **라벨을 먼저 본다.**
labeled=$(kubectl get nodes -l "${LABEL_KEY}=${LABEL_VAL}" \
  -o custom-columns=NAME:.metadata.name --no-headers 2>/dev/null || true)
labeled_n=$(echo "$labeled" | grep -c . || true)

if [ -n "${NODE:-}" ]; then
  echo "── 생성기 노드 = $NODE (환경변수로 지정됨)"
elif [ "$labeled_n" -gt 1 ]; then
  echo "🔴 ${LABEL_KEY}=${LABEL_VAL} 라벨이 붙은 노드가 ${labeled_n}개다. 어느 쪽인지 정할 수 없다." >&2
  echo "$labeled" | sed 's/^/     /' >&2
  echo "   → 하나만 남기고 정리할 것:  kubectl label node <노드> ${LABEL_KEY}- && kubectl uncordon <노드>" >&2
  exit 1
elif [ "$labeled_n" -eq 1 ]; then
  NODE="$labeled"
  echo "── 생성기 노드 = $NODE (이미 지정돼 있어 재사용 — 새 노드를 고르지 않는다. cordon·라벨은 멱등이라 아래에서 다시 적용된다)"
else
  snap=$(pod_snapshot) || { echo "🔴 파드 조회 실패 — 노드를 고를 수 없다." >&2; exit 1; }
  pick=""; pick_n=-1
  echo "── 측정 대상 파드 분포"
  for n in $nodes; do
    c=$(count_apps_on "$snap" "$n")
    echo "   $n : $c"
    if [ "$pick_n" -lt 0 ] || [ "$c" -lt "$pick_n" ]; then pick="$n"; pick_n="$c"; fi
  done
  NODE="$pick"
  echo "── 생성기 노드 = $NODE (측정 대상 $pick_n 개로 가장 적음)"
fi

# ── ② cordon + 라벨 ────────────────────────────────────────────────
# cordon 은 `node.kubernetes.io/unschedulable:NoSchedule` taint 를 건다.
# k6.yaml 이 이 taint 를 toleration 으로 받아주므로 k6 **만** 들어갈 수 있다.
kubectl cordon "$NODE"
kubectl label node "$NODE" "${LABEL_KEY}=${LABEL_VAL}" --overwrite

# ── ③ 남아 있는 측정 대상 파드를 내보낸다 ──────────────────────────
# drain 을 쓰지 않는 이유: drain 은 DaemonSet·미러 파드 처리에 플래그가 붙고
# 시스템 파드까지 건드린다. 우리가 옮기려는 건 **측정 대상 4종뿐**이다.
#
# ⚠️ `|| true` 가 필요하다 — 개별 delete 가 NotFound(경쟁 상태로 이미 사라짐)로 실패하면
#   `set -e` 가 파이프라인째 죽여서 ④ 검증에 **도달조차 못 한다.** 그러면 명확한 실패
#   메시지 대신 kubectl 에러만 남는다. 성패 판정은 여기가 아니라 ④ 가 한다.
evict_list=$(kubectl get pods -n "$NS" --no-headers \
  -o custom-columns=NAME:.metadata.name,NODE:.spec.nodeName,APP:.metadata.labels.app) ||
  { echo "🔴 파드 조회 실패 — 무엇을 내보낼지 알 수 없다. 중단한다." >&2; exit 1; }
echo "$evict_list" | while read -r pname pnode papp; do
    [ "$pnode" = "$NODE" ] || continue
    is_app "$papp" || continue
    echo "   내보냄: $pname ($papp)"
    kubectl delete pod -n "$NS" "$pname" --wait=false || echo "   ⚠️ delete 실패(무시, ④ 가 판정): $pname"
  done

# ── ④ 검증 — 여기서 통과해야만 측정이 유효하다 ────────────────────
echo "── 재배치 대기 (최대 ${WAIT_SECS}s)"
i=0
left=-1
notready=-1
while [ "$i" -lt "$WAIT_SECS" ]; do
  # 🔴 조회 실패를 "0 개"로 읽지 않는다(F-7). 실패한 샘플은 **버리고 다시 잰다** —
  #   성공으로 세면 격리가 안 됐는데 ✅ 가 나간다.
  if ! snap=$(pod_snapshot) || [ -z "$snap" ]; then
    echo "   ⚠️ 파드 조회 실패/빈 결과 — 이 샘플은 버린다(성공으로 세지 않는다)" >&2
    sleep 5; i=$((i + 5)); continue
  fi
  left=$(count_apps_on "$snap" "$NODE")
  # ⚠️ **측정 대상만** 본다. 네임스페이스 전체를 보면 무관한 파드(과거 Job 의 Error 잔해 등)
  #   하나 때문에 원인 불명 타임아웃이 난다.
  notready=$(echo "$snap" | while read -r _nd a phase ready; do
      is_app "$a" || continue
      pod_not_ready "$phase" "$ready" && echo x
    done | grep -c x || true)
  if [ "$left" -eq 0 ] && [ "$notready" -eq 0 ]; then
    echo "✅ $NODE 에 측정 대상 0개 · 측정 대상 전부 Ready"
    # 컬럼 위치 가정을 피한다 — `-o wide` 는 RESTARTS 가 "3 (5m ago)" 로 늘어나면 어긋난다.
    kubectl get pods -n "$NS" \
      -o custom-columns=NAME:.metadata.name,NODE:.spec.nodeName --no-headers |
      awk '{print "   " $1 " → " $2}'
    echo
    echo "다음: kubectl apply -f k8s/loadtest/k6.yaml"
    echo "      kubectl get pod k6 -o wide   # ← $NODE 에 떠야 한다. Pending 이면 격리 실패다"
    exit 0
  fi
  sleep 5
  i=$((i + 5))
done

if [ "$left" -lt 0 ]; then
  # 센티널 -1 = 유효한 샘플을 **한 번도** 못 얻었다. "0개였다"와 전혀 다른 상황이다.
  echo "🔴 ${WAIT_SECS}s 동안 유효한 파드 조회를 한 번도 못 했다 (kubectl 조회가 계속 실패)." >&2
  echo "   격리 성패는 **알 수 없다** — 성공도 실패도 아니다. 클러스터 접속부터 확인할 것." >&2
else
  echo "🔴 ${WAIT_SECS}s 안에 비우지 못했다 (남은 측정 대상 ${left}개 · 비Ready ${notready}개)." >&2
  echo "   흔한 원인: 나머지 노드에 용량이 없다 → 파드가 갈 데가 없어 되돌아오거나 Pending." >&2
fi
echo "   그대로 부하를 걸지 말 것 — 생성기 오염이 섞인 데이터는 09-06 것과 같은 신세가 된다." >&2
echo "   ℹ️ $NODE 는 cordon + 라벨된 채로 남는다. 재실행하면 **같은 노드를 재사용**한다." >&2
echo "      원위치:  kubectl uncordon $NODE && kubectl label node $NODE ${LABEL_KEY}-" >&2
kubectl get pods -n "$NS" -o wide >&2
exit 1
