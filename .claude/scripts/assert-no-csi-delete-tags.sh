#!/usr/bin/env bash
# 영속 EBS 볼륨에 **CSI 삭제 권한 태그가 붙지 않았는지** 검사한다.
#
# ── 왜 이 검사가 존재하나 ──
# `AmazonEBSCSIDriverPolicy`(EBS CSI 드라이버의 IRSA 정책) 실물 v15 기준:
#
#   ec2:AttachVolume/DetachVolume → Condition **없음**  (어떤 볼륨이든 붙일 수 있다)
#   ec2:DeleteVolume              → 아래 셋 중 하나의 태그가 있어야만 허용
#                                     ebs.csi.aws.com/cluster
#                                     CSIVolumeName
#                                     kubernetes.io/created-for/pvc/name
#   ec2:CreateTags                → ec2:CreateAction ∈ {CreateVolume,...} 로 제한
#                                   (= 기존 볼륨에 태그를 덧붙여 권한을 얻을 수 없다)
#
# 즉 **저 세 태그를 안 붙이는 것 자체가 삭제 방지 장치**다. `prevent_destroy`나
# `reclaimPolicy: Retain`은 사람이 한 줄 고치면 뚫리지만, 이건 AWS IAM이 거부한다.
#
# 🔴 문제는 이 보호가 "코드에 **없는** 것"이라 눈에 안 보인다는 점이다.
#    누군가 나중에 태그를 추가하면 **주석은 그대로인데 보호만 사라진다.**
#    사람이 지킬 일이 아니라 기계가 막아야 한다 → 이 스크립트.
#
# 사용: bash .claude/scripts/assert-no-csi-delete-tags.sh [검사할 디렉토리]
#       --self-test 를 주면 반증 테스트(태그가 있으면 실제로 잡는지)를 돌린다.

set -uo pipefail

FORBIDDEN=("ebs.csi.aws.com/cluster" "CSIVolumeName" "kubernetes.io/created-for/pvc/name")

# aws_ebs_volume 리소스 블록만 뽑아 **주석을 제거한 뒤** 금지 태그를 찾는다.
#   ⚠️ 주석을 안 지우면 "이 태그를 붙이지 마라"는 설명문 자체가 매칭돼 항상 실패한다.
#      (실제로 첫 시도에서 그렇게 됐다. 통과하지 않는 검사기는 곧 무시된다.)
scan_file() {
  local f="$1" hits=0
  local body
  body=$(awk '
    /^resource[[:space:]]+"aws_ebs_volume"/ { depth = 1; print; next }
    depth > 0 {
      print
      n = gsub(/\{/, "{"); m = gsub(/\}/, "}")
      depth += n - m
      if (depth <= 0) depth = 0
    }
  ' "$f" | sed -e 's/#.*$//' -e 's|//.*$||')

  [ -n "$body" ] || return 0

  for tag in "${FORBIDDEN[@]}"; do
    if printf '%s' "$body" | grep -qF -- "$tag"; then
      echo "  🔴 $f — 금지 태그 '$tag' 가 aws_ebs_volume 에 있습니다."
      hits=1
    fi
  done
  return $hits
}

if [ "${1:-}" = "--self-test" ]; then
  # 반증 테스트: 금지 태그가 **있는** 파일을 만들어 실제로 잡히는지 확인한다.
  # (아무것도 매칭 못 하는 검사기도 "0건 통과"를 낸다 — 그래서 이 테스트가 필요하다.)
  TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
  cat > "$TMP/bad.tf" <<'EOF'
resource "aws_ebs_volume" "x" {
  size = 10
  tags = {
    Name                     = "x"
    "ebs.csi.aws.com/cluster" = "true"
  }
}
EOF
  cat > "$TMP/good.tf" <<'EOF'
# 주석에 ebs.csi.aws.com/cluster 와 CSIVolumeName 을 언급해도 잡히면 안 된다.
resource "aws_ebs_volume" "x" {
  size = 10
  tags = { Name = "x", Persistent = "true" }
}
EOF
  echo "── 반증 테스트 ──"
  # scan_file 규약: 위반 있으면 1, 없으면 0.
  # ⚠️ 두 케이스는 **기대 방향이 반대**다. 같은 조건문을 복사하면 뒤집힌다(실제로 그랬다).
  if scan_file "$TMP/bad.tf" >/dev/null; then
    echo "  🔴 실패: 태그가 있는데 못 잡았다 (검사기가 아무것도 안 하고 있다)"; exit 1
  else
    echo "  ✅ 태그가 있으면 잡는다"
  fi
  if scan_file "$TMP/good.tf" >/dev/null; then
    echo "  ✅ 주석은 오탐하지 않는다"
  else
    echo "  🔴 실패: 주석을 오탐했다 (항상 실패하는 검사기는 곧 무시된다)"; exit 1
  fi
  echo "  ✅ 자가 테스트 통과"
  exit 0
fi

DIR="${1:-infra/aws-eks}"
echo "🔒 영속 EBS 삭제 방지 검사 — $DIR"
RC=0
while IFS= read -r f; do
  scan_file "$f" || RC=1
done < <(grep -rl --include="*.tf" 'resource[[:space:]]*"aws_ebs_volume"' "$DIR" 2>/dev/null)

if [ "$RC" -eq 0 ]; then
  echo "  ✅ CSI 삭제 권한 태그 없음 — IAM이 볼륨 삭제를 거부합니다."
else
  echo ""
  echo "  ⛔ 저 태그가 붙으면 EBS CSI 컨트롤러가 이 볼륨을 **삭제할 수 있게 됩니다.**"
  echo "     6개월 영속 데이터를 보호하는 유일한 IAM 레벨 장치입니다. 태그를 제거하세요."
  echo "     근거: infra/aws-eks/0-bootstrap/ebs-postgres.tf 하단 주석"
fi
exit $RC
