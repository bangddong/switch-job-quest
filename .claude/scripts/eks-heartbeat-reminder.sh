#!/usr/bin/env bash
# Stop 훅 — 매 턴 실행. EKS 세션 마커가 있을 때만 동작(없으면 즉시 종료, 지연 0).
# 하는 일:
#   1) 하트비트 touch — "사람이 활동 중"이라는 신호. 리퍼는 하트비트가 신선하면 안 죽인다.
#   2) (5분 스로틀) AWS로 클러스터 생존 확인 → 살아있으면 경고, 없으면 마커 자가 치유.
# stderr 출력이 표시되지 않는 환경이라도 (1)은 유효하다 — 리퍼가 그걸로 판단하므로 핵심 기능이다.

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
DIR="$ROOT/.claude/eks-session"
MARKER="$DIR/active"
[ -f "$MARKER" ] || exit 0

# (1) 하트비트 갱신 — 이게 리퍼에게 "아직 여기 있음"을 알린다
touch "$DIR/heartbeat" 2>/dev/null

REGION="${EKS_REGION:-ap-northeast-2}"
NOW=$(date +%s)
LASTCHK=0
[ -f "$DIR/lastcheck" ] && LASTCHK=$(cat "$DIR/lastcheck" 2>/dev/null || echo 0)

# (2) AWS 조회는 5분에 한 번만
if [ $((NOW - LASTCHK)) -ge 300 ]; then
  CLUSTERS=$(aws eks list-clusters --region "$REGION" --query 'clusters' --output text 2>/dev/null)
  # 🔴 RDS도 함께 본다. 클러스터만 보면 "EKS는 지워졌는데 RDS는 남은" 부분 실패 상태에서
  #    마커를 자가 삭제해 감시를 끝내버린다(= 리퍼도 더는 안 돈다) → RDS 영구 과금.
  #    reaper.sh의 생존 판정과 반드시 동일한 기준을 유지할 것.
  RDS=$(aws rds describe-db-instances --region "$REGION" \
    --query 'DBInstances[?starts_with(DBInstanceIdentifier, `devquest`)].DBInstanceIdentifier' \
    --output text 2>/dev/null)
  echo "$NOW" > "$DIR/lastcheck" 2>/dev/null
  # 캐시에 둘 다 기록 — 아래 경고 메시지가 무엇이 살아있는지 보여줘야 하므로.
  printf 'EKS[%s] RDS[%s]' "${CLUSTERS:-없음}" "${RDS:-없음}" > "$DIR/state.cache" 2>/dev/null
  if [ -z "$CLUSTERS" ] && [ -z "$RDS" ]; then
    # 과금 리소스 전부 없음 → 세션 종료됨. 마커 자가 치유.
    rm -f "$MARKER" "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null
    exit 0
  fi
  # 🔴 소유 판정(📌D-016 4번) — 리퍼와 **같은 함수**를 쓴다(판정 기준이 갈리면 둘 중 하나가 거짓말한다).
  #    이 세션이 만든 클러스터가 아니면(CI 창 = prod) 로컬은 손대지 않는다: 마커를 풀고, destroy 를
  #    권하지 않는다. 종전엔 여기서 "오늘 끝이면 → tofu destroy" 를 찍어 **prod 를 끄라고 안내**했다(B4-7).
  if [ -n "$CLUSTERS" ]; then
    APPLIED0=$(grep '^applied_at=' "$MARKER" 2>/dev/null | cut -d= -f2)
    if . "$ROOT/.claude/scripts/lib/eks-cluster-owner.sh" 2>/dev/null; then
      OWN=$(eks_clusters_owner "$CLUSTERS" "$APPLIED0" "$REGION")
    else
      OWN="unknown 소유 판정 라이브러리 없음"
    fi
    case "$OWN" in
      foreign*)
        rm -f "$MARKER" "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null
        echo "ℹ️  EKS[$CLUSTERS] 는 이 세션이 만든 클러스터가 아닙니다(CI 창·다른 기기) — 로컬 마커 해제, 리퍼 감시 대상 아님." >&2
        echo "   끄지 마세요(창 중이면 prod). 끄기는 off 워크플로 몫입니다(Fly 복귀 포함 — 순서표 8번)." >&2
        exit 0 ;;
      unknown*) printf ' · ⚠️소유판정불가' >> "$DIR/state.cache" 2>/dev/null ;;
    esac
  fi
fi

CACHE=$(cat "$DIR/state.cache" 2>/dev/null || echo "")
[ -z "$CACHE" ] && exit 0

APPLIED=$(grep '^applied_at=' "$MARKER" 2>/dev/null | cut -d= -f2)
MINS=0; [ -n "$APPLIED" ] && MINS=$(( (NOW - APPLIED) / 60 ))

echo "⚠️  과금 리소스가 살아있습니다 ($CACHE · ~${MINS}분 · 과금 중)." >&2
case "$CACHE" in *소유판정불가*)
  echo "   🔴 이 세션이 만든 클러스터인지 판정하지 못했습니다 — destroy 전에 확인:" >&2
  echo "      aws eks describe-cluster --name <이름> --query cluster.createdAt  vs  .claude/eks-session/active 의 applied_at_h" >&2
  echo "      리퍼도 판정 불가면 destroy 하지 않습니다." >&2 ;;
esac
echo "   오늘 끝이면 → cd infra/aws-eks/2-cluster && tofu destroy" >&2
echo "   destroy 후 고아 전수 검증 필수(RDS 스냅샷·시크릿 포함) → docs/eks-session-sop.md §9" >&2
echo "   방치 시 로컬 리퍼가 하트비트 2h stale에서 자동 destroy합니다." >&2
exit 0
