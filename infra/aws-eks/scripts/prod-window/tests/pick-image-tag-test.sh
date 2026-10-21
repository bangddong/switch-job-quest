#!/usr/bin/env bash
# pick-image-tag.sh 회귀 — 목 gh·aws, $0. 사용: bash infra/aws-eks/scripts/prod-window/tests/pick-image-tag-test.sh
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P=0; F=0; ok(){ P=$((P+1)); echo "  ✅ $1"; }; bad(){ F=$((F+1)); echo "  🔴 $1"; }
M=$(mktemp -d); trap 'rm -rf "$M"' EXIT
GOOD=1111111111111111111111111111111111111111
cat > "$M/gh" <<'G'
#!/bin/sh
# 쿼리에 status=success 가 없으면 테스트가 틀렸다는 뜻 — 성공한 실행만 봐야 한다
case "$2" in *status=success*) ;; *) echo "목: status=success 없음" >&2; exit 9;; esac
[ "${MOCK_GH_FAIL:-0}" = 1 ] && exit 1
echo "${MOCK_SHA:-}"
G
cat > "$M/aws" <<'A'
#!/bin/sh
tag=""; for a in "$@"; do case "$a" in devquest/*) svc="${a#devquest/}";; imageTag=*) tag="${a#imageTag=}";; esac; done
if [ -z "$tag" ]; then echo "${MOCK_ECR_TAGS:-}"; exit 0; fi          # 태그 목록(최신 먼저)
[ "${MOCK_ECR_DENIED:-0}" = 1 ] && { echo "An error occurred (AccessDeniedException)" >&2; exit 254; }
case " ${MOCK_PRESENT:-$MOCK_SHA} " in *" $tag "*) ;; *) echo "An error occurred (ImageNotFoundException)" >&2; exit 254;; esac   # 이 태그가 ECR 에 있나
case " ${MOCK_MISSING:-} " in *" $svc "*) echo "An error occurred (ImageNotFoundException)" >&2; exit 254;; esac
echo "$tag"
A
cat > "$M/git" <<'GI'
#!/bin/sh
# git rev-parse <sha>:be — MOCK_TREES="sha=tree sha=tree"
[ "$1" = rev-parse ] || exit 9
s="${2%%:*}"; for kv in ${MOCK_TREES:-}; do [ "${kv%%=*}" = "$s" ] && { echo "${kv#*=}"; exit 0; }; done; exit 128
GI
chmod +x "$M/gh" "$M/aws" "$M/git"
run(){ PATH="$M:$PATH" bash "$HERE/pick-image-tag.sh" o/r 2>"$M/err"; }
echo "── pick-image-tag"
out=$(MOCK_SHA=$GOOD run); rc=$?; [ $rc -eq 0 ] && [ "$out" = "$GOOD" ] && ok "성공 be-cd sha + 이미지 3개 → 그 sha" || bad "rc=$rc out=$out"
OLD=2222222222222222222222222222222222222222; SAME=3333333333333333333333333333333333333333; DIFF=4444444444444444444444444444444444444444
out=$(MOCK_SHA=$GOOD MOCK_MISSING="daily-api" MOCK_TREES="$GOOD=t1" run); rc=$?; [ $rc -ne 0 ] && grep -q '같은 커밋도' "$M/err" && ok "세 레포 중 하나라도 없으면 실패" || bad "🔴 이미지 없는데 rc=$rc"
out=$(MOCK_SHA=$GOOD MOCK_PRESENT="$DIFF $SAME" MOCK_ECR_TAGS="$DIFF $SAME" MOCK_TREES="$GOOD=t1 $SAME=t1 $DIFF=t9" run); rc=$?
[ $rc -eq 0 ] && [ "$out" = "$SAME" ] && ok "같은 커밋 이미지 없음 → be/ 트리가 같은 태그(더 최신이지만 트리가 다른 태그는 건너뜀)" || bad "rc=$rc out=$out"
out=$(MOCK_SHA=$GOOD MOCK_PRESENT="$DIFF $OLD" MOCK_ECR_TAGS="$DIFF $OLD" MOCK_TREES="$GOOD=t1 $OLD=t0 $DIFF=t9" run); rc=$?
[ $rc -ne 0 ] && ok "🔴 트리가 같은 태그가 없으면 실패(옛 커밋으로 물러나지 않는다)" || bad "🔴 다른 소스 이미지를 골랐다 out=$out"
out=$(MOCK_SHA=$GOOD MOCK_PRESENT="$SAME" MOCK_ECR_TAGS="$SAME" MOCK_MISSING="ai-api" MOCK_TREES="$GOOD=t1 $SAME=t1" run); rc=$?
[ $rc -ne 0 ] && ok "트리가 같아도 한 레포에 이미지가 없으면 그 태그는 못 쓴다" || bad "🔴 레포 하나 빠진 태그를 골랐다 out=$out"
out=$(MOCK_SHA=$GOOD MOCK_ECR_DENIED=1 run); rc=$?; [ $rc -eq 2 ] && grep -q 'ECR 조회 오류' "$M/err" && ok "권한 오류는 '없음' 이 아니라 rc 2" || bad "권한 오류 rc=$rc"
out=$(MOCK_SHA="" run); rc=$?; [ $rc -ne 0 ] && ok "성공한 be-cd 가 없으면 실패" || bad "rc=$rc"
out=$(MOCK_SHA=$GOOD MOCK_GH_FAIL=1 run); rc=$?; [ $rc -eq 2 ] && ok "조회 실패 → rc 2(fail-closed)" || bad "rc=$rc"
out=$(MOCK_SHA="abc" run); rc=$?; [ $rc -ne 0 ] && ok "sha 형식 이상 → 실패" || bad "rc=$rc"
echo "── 결과: 통과 $P · 실패 $F"; [ $F -eq 0 ]
