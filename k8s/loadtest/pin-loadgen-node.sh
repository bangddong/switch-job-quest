#!/bin/sh
# 부하 생성기 전용 노드 확보 — k6 를 측정 대상과 **다른 노드**에 격리한다.
# 계획서 `docs/superpowers/plans/2026-09-05-memory-rightsizing.md` §9.6
#
# 왜 스크립트인가: 이 절차는 **과금 구간**에서 돈다. 손으로 치면 노드 선택·대기 판단에
# 시간이 들고, 그 시간이 그대로 비용이다(09-06: 유휴 대기가 세션 비용의 85%).
# 또한 "빈 노드가 정말 비었는가"는 **눈으로 확인하면 틀리는** 종류라 기계가 판정한다.
#
# 하는 일:
#   ① 측정 대상 파드가 가장 적은 노드를 고른다
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

nodes=$(kubectl get nodes -o custom-columns=NAME:.metadata.name --no-headers)
node_count=$(echo "$nodes" | grep -c . || true)
if [ "$node_count" -lt 2 ]; then
  echo "🔴 노드가 ${node_count}대다. 생성기를 격리할 수 없다." >&2
  echo "   k6 를 측정 대상과 같은 노드에 올리면 그 회차 데이터는 못 쓴다(계획서 §9.5)." >&2
  echo "   → tofu apply 로 노드를 2대 이상으로 올린 뒤 다시 실행할 것." >&2
  exit 1
fi

# ── ① 측정 대상 파드가 가장 적은 노드 선택 ──────────────────────────
placement=$(kubectl get pods -n "$NS" \
  -o custom-columns=NODE:.spec.nodeName,APP:.metadata.labels.app --no-headers)

pick=""
pick_n=-1
echo "── 측정 대상 파드 분포"
for n in $nodes; do
  c=0
  for app in $(echo "$placement" | awk -v n="$n" '$1==n {print $2}'); do
    if is_app "$app"; then c=$((c + 1)); fi
  done
  echo "   $n : $c"
  if [ "$pick_n" -lt 0 ] || [ "$c" -lt "$pick_n" ]; then
    pick="$n"; pick_n="$c"
  fi
done

NODE="${NODE:-$pick}"
echo "── 생성기 노드 = $NODE (측정 대상 $pick_n 개)"

# ── ② cordon + 라벨 ────────────────────────────────────────────────
# cordon 은 `node.kubernetes.io/unschedulable:NoSchedule` taint 를 건다.
# k6.yaml 이 이 taint 를 toleration 으로 받아주므로 k6 **만** 들어갈 수 있다.
kubectl cordon "$NODE"
kubectl label node "$NODE" "${LABEL_KEY}=${LABEL_VAL}" --overwrite

# ── ③ 남아 있는 측정 대상 파드를 내보낸다 ──────────────────────────
# drain 을 쓰지 않는 이유: drain 은 DaemonSet·미러 파드 처리에 플래그가 붙고
# 시스템 파드까지 건드린다. 우리가 옮기려는 건 **측정 대상 4종뿐**이다.
kubectl get pods -n "$NS" \
  -o custom-columns=NAME:.metadata.name,NODE:.spec.nodeName,APP:.metadata.labels.app \
  --no-headers | while read -r pname pnode papp; do
    [ "$pnode" = "$NODE" ] || continue
    is_app "$papp" || continue
    echo "   내보냄: $pname ($papp)"
    kubectl delete pod -n "$NS" "$pname" --wait=false
  done

# ── ④ 검증 — 여기서 통과해야만 측정이 유효하다 ────────────────────
echo "── 재배치 대기 (최대 ${WAIT_SECS}s)"
i=0
while [ "$i" -lt "$WAIT_SECS" ]; do
  left=$(kubectl get pods -n "$NS" \
    -o custom-columns=NODE:.spec.nodeName,APP:.metadata.labels.app --no-headers |
    awk -v n="$NODE" '$1==n {print $2}' | while read -r a; do is_app "$a" && echo x; done | grep -c x || true)
  notready=$(kubectl get pods -n "$NS" --no-headers |
    awk '$3!="Running" && $3!="Completed" {print}' | grep -c . || true)
  if [ "$left" -eq 0 ] && [ "$notready" -eq 0 ]; then
    echo "✅ $NODE 에 측정 대상 0개 · 전 파드 Running"
    kubectl get pods -n "$NS" -o wide --no-headers | awk '{print "   " $1 " → " $7}'
    echo
    echo "다음: kubectl apply -f k8s/loadtest/k6.yaml"
    echo "      kubectl get pod k6 -o wide   # ← $NODE 에 떠야 한다. Pending 이면 격리 실패다"
    exit 0
  fi
  sleep 5
  i=$((i + 5))
done

echo "🔴 ${WAIT_SECS}s 안에 비우지 못했다 (남은 측정 대상 ${left}개 · 비Running ${notready}개)." >&2
echo "   흔한 원인: 나머지 노드에 용량이 없다 → 파드가 갈 데가 없어 되돌아오거나 Pending." >&2
echo "   그대로 부하를 걸지 말 것 — 생성기 오염이 섞인 데이터는 09-06 것과 같은 신세가 된다." >&2
kubectl get pods -n "$NS" -o wide >&2
exit 1
