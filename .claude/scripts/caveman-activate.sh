#!/usr/bin/env bash
# SessionStart hook — caveman 모드 자동 활성화
#
# Claude Code는 SessionStart hook의 stdout을 숨겨진 시스템 컨텍스트로 주입함.
# 따라서 이 출력이 매 세션 응답 전체에 자동 적용됨.
#
# Sub-agent(be-/fe-feature-builder 등)는 SubagentStart를 타므로 영향 없음.
# 오케스트레이터(main session) 응답만 압축됨.

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
SKILL_FILE="$REPO_ROOT/.claude/skills/caveman/SKILL.md"

[ -f "$SKILL_FILE" ] || exit 0

# YAML frontmatter (--- ... ---) 제거 후 본문만 출력
awk 'BEGIN{c=0} /^---/{c++; next} c>=2{print}' "$SKILL_FILE"
