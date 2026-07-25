#!/usr/bin/env bash
# PreToolUse(Bash) — `tofu apply` 감지 시 EKS 세션 활성 마커를 남긴다.
# 이 마커는 "AWS에 과금 리소스가 떠 있을 수 있음"을 리퍼·리마인더에 알리는 값싼 플래그다.
# 실제 클러스터 존재 여부는 항상 AWS로 확인한다(마커는 최적화용, 진실의 원천 아님).
#
# 절대 apply를 막지 않는다(항상 exit 0). 마커 생성만.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# `tofu apply`만 대상 (plan/destroy 제외). 명령 위치로 앵커링해 문자열 오탐 방지
# (예: git commit -m "tofu apply 관련" 은 매칭 안 됨 — 명령 시작/구분자 뒤만 인정).
echo "$COMMAND" | grep -qE "(^|[;&|(]|&&|\|\|)[[:space:]]*tofu[[:space:]]+apply" || exit 0

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
DIR="$ROOT/.claude/eks-session"
mkdir -p "$DIR" 2>/dev/null

# 마커에 리퍼가 tofu destroy를 돌릴 위치와 시작 시각을 기록 (머신 독립)
{
  echo "repo_root=$ROOT"
  echo "cluster_dir=$ROOT/infra/aws-eks/2-cluster"
  echo "applied_at=$(date +%s)"
  echo "applied_at_h=$(date '+%Y-%m-%d %H:%M:%S %Z')"
} > "$DIR/active" 2>/dev/null

touch "$DIR/heartbeat" 2>/dev/null
rm -f "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null  # 새 세션 — 캐시 초기화

echo "🟢 EKS 세션 마커 생성 — 과금 추적 시작. destroy 전까지 리퍼가 감시합니다." >&2
exit 0
