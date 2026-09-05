#!/usr/bin/env bash
# 3서비스 + postgres 의 컨테이너 메모리를 cgroup v2 에서 직접 읽는다.
#
# 왜 kubectl top 이 아닌가: metrics-server 가 설치돼 있지 않다(09-04 확인). 설치하면 파드 슬롯과
#   메모리를 먹어 **측정 대상을 오염**시킨다. cgroup 은 커널이 이미 세고 있는 값이라 공짜다.
#
# 왜 memory.current 만으로 부족한가 (09-04 는 이것만 읽었다):
#   ① 순간값이라 부하 중 최고점을 놓친다 → `memory.peak`(커널이 기록한 high-water mark)을 함께 읽는다
#   ② 페이지 캐시를 포함한다 → postgres 처럼 버퍼드 IO 를 하는 컨테이너는 필요량이 부풀어 보인다.
#      회수 가능한 캐시를 requests 로 예약하면 노드 한 대를 캐시에 헌납하게 된다.
#      → k8s 와 같은 정의로 working set = current − inactive_file 을 계산한다.
#
# 사용:
#   ./measure-memory.sh <라벨>          한 번 찍는다 (예: baseline / load-60s / recovered)
#   ./measure-memory.sh <라벨> <횟수> <간격초>   반복 샘플링 (예: load 6 30)
#
# 출력은 TSV — 일지에 그대로 붙일 수 있게.
set -euo pipefail

LABEL="${1:?라벨을 주세요 (예: baseline)}"
COUNT="${2:-1}"
INTERVAL="${3:-30}"

# 대상: <표시이름>:<라벨셀렉터>:<컨테이너명>
# postgres 는 StatefulSet 이지만 라벨은 동일하게 app= 이다.
TARGETS="core-api:app=core-api ai-api:app=ai-api daily-api:app=daily-api postgres:app=postgres"

# ── 컨테이너 안에서 도는 부분 ────────────────────────────────────────────────
# busybox sh(postgres:17-alpine)와 JRE alpine 둘 다에서 동작해야 하므로 awk/cat 만 쓴다.
# ⚠️ 이 스니펫은 docker exec 로도 그대로 돌아간다 — 그래서 클러스터 없이 검증할 수 있었다.
read -r -d '' READER <<'EOF' || true
cur=$(cat /sys/fs/cgroup/memory.current 2>/dev/null || echo 0)
peak=$(cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo 0)
max=$(cat /sys/fs/cgroup/memory.max 2>/dev/null || echo 0)
awk -v c="$cur" -v p="$peak" -v m="$max" '
  $1=="anon"         {anon=$2}
  $1=="file"         {file=$2}
  $1=="inactive_file"{inf=$2}
  $1=="slab"         {slab=$2}
  END {
    ws = c - inf
    printf "%d %d %d %d %d %d %d\n", ws, c, p, anon, file, slab, m
  }' /sys/fs/cgroup/memory.stat
EOF

mib() { awk -v b="$1" 'BEGIN{ if (b+0 > 9000000000000) print "max"; else printf "%.0f", b/1048576 }'; }

emit_header() {
  printf '# %s  (%s)\n' "$LABEL" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf 'pod\tws(Mi)\tcurrent\tpeak\tanon\tfile\tslab\tlimit\n'
}

sample_once() {
  emit_header
  for t in $TARGETS; do
    name="${t%%:*}"
    sel="${t#*:}"
    pod=$(kubectl get pod -l "$sel" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    if [ -z "$pod" ]; then
      printf '%s\t(파드 없음 — 셀렉터 %s)\n' "$name" "$sel"
      continue
    fi
    # 🔴 실패를 조용히 넘기지 않는다. 값이 안 나오면 그 자리에 표시한다 —
    #    빈 칸을 0 으로 적으면 "측정했는데 0" 과 "못 쟀다" 가 구별되지 않는다.
    if ! out=$(kubectl exec "$pod" -- sh -c "$READER" 2>/dev/null); then
      printf '%s\t(exec 실패: %s)\n' "$name" "$pod"
      continue
    fi
    set -- $out
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$name" "$(mib "$1")" "$(mib "$2")" "$(mib "$3")" \
      "$(mib "$4")" "$(mib "$5")" "$(mib "$6")" "$(mib "$7")"
  done
}

i=1
while [ "$i" -le "$COUNT" ]; do
  [ "$COUNT" -gt 1 ] && printf '\n=== 샘플 %d/%d ===\n' "$i" "$COUNT"
  sample_once
  i=$((i + 1))
  [ "$i" -le "$COUNT" ] && sleep "$INTERVAL"
done
