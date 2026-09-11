#!/usr/bin/env bash
# Stage 4 verify 마커 4개 전수 반증 — 각 마커가 지키는 코드를 지우면 검사가 **실패해야** 한다.
# 통과하면 그 마커는 장식이다(08-12 F-5 · 09-11 재발 전례).
set -u
cd "$(git rev-parse --show-toplevel)"

# (마커 설명, 대상 파일, 지울 문자열)
run() {
  local desc="$1" file="$2" needle="$3"
  cp "$file" "/tmp/$(basename "$file").bak"
  # 대상 문자열을 지운다 — 주석이 아니라 실제 선언을 겨냥한다
  ruby -e '
    f, n = ARGV[0], ARGV[1]
    s = File.read(f)
    before = s.length
    s = s.sub(n, "ZAPPED")
    abort("반증 준비 실패: 문자열 없음 → #{n}") if s.length == before
    File.write(f, s)
  ' "$file" "$needle" || { echo "  🔴 $desc — 반증 준비 실패"; return 1; }

  if bash .claude/scripts/check-design-integrity.sh >/dev/null 2>&1; then
    echo "  🔴 $desc — **코드를 지웠는데 검사가 통과했다. 이 마커는 장식이다.**"
    RC=1
  else
    echo "  ✅ $desc — 지우면 실패한다 (마커가 실제로 그 코드를 지킨다)"
  fi
  cp "/tmp/$(basename "$file").bak" "$file"
}

RC=0
echo "── Stage 4 verify 마커 반증 ──"
run "① IRSA 역할 선언"   infra/aws-eks/2-cluster/irsa-alb.tf 'resource "aws_iam_role" "alb_controller"'
run "② 정책 크기 우회"    infra/aws-eks/2-cluster/irsa-alb.tf 'policy      = jsonencode(jsondecode(file('
run "③ target-type: ip"  k8s/base/ingress.yaml                'alb.ingress.kubernetes.io/target-type: ip'
run "④ healthcheck-path" k8s/base/ingress.yaml                'alb.ingress.kubernetes.io/healthcheck-path: /health'

echo
echo "── 대조: 원본은 통과해야 한다"
if bash .claude/scripts/check-design-integrity.sh >/dev/null 2>&1; then
  echo "  ✅ 원본 통과 (복원도 정상)"
else
  echo "  🔴 원본이 실패한다 — 복원이 깨졌거나 마커가 틀렸다"
  RC=1
fi
exit "$RC"
