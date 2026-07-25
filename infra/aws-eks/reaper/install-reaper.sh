#!/usr/bin/env bash
# EKS dead man's switch(launchd) 설치/재설치 스크립트 (macOS).
# 하는 일: 템플릿 plist의 경로를 치환 → ~/Library/LaunchAgents/ 에 설치 → launchctl 로드.
# 재실행해도 안전(먼저 언로드 후 재로드).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
REAPER_SCRIPT="$REPO_ROOT/.claude/scripts/eks-reaper.sh"
TEMPLATE="$REPO_ROOT/infra/aws-eks/reaper/com.devquest.eks-reaper.plist"
LABEL="com.devquest.eks-reaper"
DEST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG="$HOME/Library/Logs/eks-reaper.launchd.log"

[ -f "$REAPER_SCRIPT" ] || { echo "❌ 리퍼 스크립트 없음: $REAPER_SCRIPT"; exit 1; }
[ -f "$TEMPLATE" ] || { echo "❌ plist 템플릿 없음: $TEMPLATE"; exit 1; }
chmod +x "$REAPER_SCRIPT"

mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs"

# 경로 치환해 설치
sed -e "s|__REAPER_SCRIPT__|$REAPER_SCRIPT|g" \
    -e "s|__LOG__|$LOG|g" \
    "$TEMPLATE" > "$DEST"
echo "✅ plist 설치: $DEST"

# 기존 로드 해제 후 재로드 (bootout/bootstrap = 신 macOS, load -w = 구버전 fallback)
GUI="gui/$(id -u)"
launchctl bootout "$GUI/$LABEL" 2>/dev/null || true
if launchctl bootstrap "$GUI" "$DEST" 2>/dev/null; then
  echo "✅ launchctl bootstrap 완료"
else
  launchctl unload "$DEST" 2>/dev/null || true
  launchctl load -w "$DEST"
  echo "✅ launchctl load 완료 (fallback)"
fi

echo ""
echo "확인: launchctl list | grep eks-reaper"
launchctl list 2>/dev/null | grep eks-reaper || echo "(목록에 없으면 로드 실패 — 위 오류 확인)"
echo ""
echo "리퍼 동작 로그: $REPO_ROOT/.claude/eks-session/reaper.log"
echo "launchd 자체 로그: $LOG"
echo "제거: launchctl bootout $GUI/$LABEL ; rm $DEST"
