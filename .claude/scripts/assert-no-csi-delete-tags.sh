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

# **주석을 제거한 뒤**, 금지 태그가 "키 = 값" 형태로 **대입**되는지 찾는다.
#
# ⚠️ 주석을 안 지우면 "이 태그를 붙이지 마라"는 설명문 자체가 매칭돼 항상 실패한다.
#    (첫 구현에서 실제로 그랬다. 통과하지 않는 검사기는 곧 무시된다.)
#
# 🔴 **중괄호 카운팅으로 `aws_ebs_volume` 블록만 잘라내던 방식을 버렸다 (QA F-3).**
#    문자열 안에 짝 없는 `}`가 있으면 depth가 0으로 떨어져 **블록이 조기 종료**되고,
#    그 뒤에 있는 진짜 금지 태그를 놓친다. 실측 재현:
#      description = "... } ..."   ← 여기서 잘림
#      tags = { "ebs.csi.aws.com/cluster" = "true" }   ← 스캔 대상에서 빠짐 → 통과
#    "0건 통과"로 보이는 가장 나쁜 실패라, 파싱을 정교하게 만드는 대신 **없앴다.**
#
# 대신 파일 전체에서 `<태그>` 뒤에 `=`가 오는 경우만 본다. 설명문 안의 단순 언급은
# 대입 형태가 아니라 걸리지 않는다. 블록 경계를 몰라도 되므로 깨질 구석이 없다.
#
# ℹ️ IAM 조건 키(`aws:RequestTag/...`, `ec2:ResourceTag/...`)는 **리소스 태그가 아니라
#    정책 조건**이라 제외한다 — 이것까지 막으면 CSI 정책을 직접 쓸 수 없게 된다.
scan_file() {
  local f="$1" hits=0
  local body

  # ① 주석 제거 → ② IAM 조건 키(`aws:RequestTag/<태그>` 등)를 **토큰째 삭제**.
  #
  # 🔴 **"태그 뒤에 `=`가 오는지" 보던 방식을 버렸다 (QA F-4·F-5).**
  #   F-4: 키를 local에 담아 computed key로 쓰면 정규식이 통째로 빗나간다 —
  #        `locals { k = "ebs.csi.aws.com/cluster" }` + `tags = { (local.k) = "true" }`
  #        여기서 태그 문자열 뒤에 오는 건 `=`가 아니라 `}`다.
  #   F-5: IAM 조건 키 제외를 `grep -v`로 **줄 단위**로 하니, 같은 줄에 조건 키와
  #        진짜 위반이 함께 있으면 위반까지 통째로 제외됐다.
  #
  # → 규칙을 단순화한다: **주석 밖에서 이 문자열이 보이면 안 된다.**
  #   키든 값이든 locals든 merge든 jsonencode든 전부 걸린다. 우회 경로가 없다.
  #   IAM 조건 키만 예외인데, 그건 `<접두사>/<태그>` 형태라 접두사째 지우면 남지 않는다
  #   (줄 단위가 아니라 **토큰 단위** 삭제라 F-5가 재발하지 않는다).
  #
  # ⚠️ 대가: `description = "... ebs.csi.aws.com/cluster ..."` 처럼 **문자열 안 설명**도
  #   걸린다. 의도된 것이다 — 설명은 주석(`#`)으로 쓰면 되고, 오탐 쪽으로 틀리는 편이
  #   놓치는 것보다 낫다(이 검사가 지키는 건 6개월치 데이터다).
  body=$(sed -e 's/#.*$//' -e 's|//.*$||' "$f" |
    sed -E 's#(aws:RequestTag|aws:ResourceTag|ec2:ResourceTag)/[A-Za-z0-9./_:-]+##g')

  for tag in "${FORBIDDEN[@]}"; do
    if printf '%s\n' "$body" | grep -qF -- "$tag"; then
      echo "  🔴 $f — 금지 태그 '$tag' 가 (주석 밖에서) 참조되고 있습니다."
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
  # QA F-3: 블록 경계를 중괄호 카운팅으로 잡으므로, 블록 안에 heredoc이나 문자열 속
  # 중괄호가 있으면 depth가 어긋나 **블록을 조기 종료**할 수 있다. 그러면 그 뒤에 있는
  # 진짜 금지 태그를 놓친다 — "0건 통과"로 보이는 가장 나쁜 실패다.
  cat > "$TMP/tricky.tf" <<'EOF'
resource "aws_ebs_volume" "x" {
  size = 10
  description = "닫는 중괄호가 문자열 안에 있다 } 짝이 없다 — 블록 파싱을 조기 종료시키던 케이스"
  tags = {
    Name                      = "x"
    "ebs.csi.aws.com/cluster" = "true"
  }
}
EOF
  # QA F-4: 태그 키를 local에 담아 computed key로 쓰는 우회.
  cat > "$TMP/computed.tf" <<'EOF'
locals {
  csi_tag_key = "ebs.csi.aws.com/cluster"
}
resource "aws_ebs_volume" "x" {
  size = 10
  tags = {
    (local.csi_tag_key) = "true"
  }
}
EOF
  # QA F-5: IAM 조건 키와 진짜 위반이 **같은 줄**에 있는 경우.
  cat > "$TMP/sameline.tf" <<'EOF'
resource "aws_ebs_volume" "x" {
  size = 10
  tags = { note = "aws:RequestTag/ebs.csi.aws.com/cluster 참고", "CSIVolumeName" = "x" }
}
EOF
  # IAM 조건 키만 있는 정당한 파일 — 오탐하면 안 된다.
  cat > "$TMP/iamonly.tf" <<'EOF'
data "aws_iam_policy_document" "x" {
  statement {
    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/ebs.csi.aws.com/cluster"
      values   = ["true"]
    }
  }
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
  if scan_file "$TMP/tricky.tf" >/dev/null; then
    echo "  🔴 실패: 문자열 속 닫는 중괄호에 블록이 잘려 뒤쪽 금지 태그를 놓쳤다"; exit 1
  else
    echo "  ✅ 문자열 속 닫는 중괄호가 있어도 금지 태그를 잡는다 (QA F-3)"
  fi
  if scan_file "$TMP/computed.tf" >/dev/null; then
    echo "  🔴 실패: local + computed key 우회를 놓쳤다"; exit 1
  else
    echo "  ✅ local에 담아 computed key로 써도 잡는다 (QA F-4)"
  fi
  if scan_file "$TMP/sameline.tf" >/dev/null; then
    echo "  🔴 실패: IAM 조건 키와 같은 줄에 있는 위반을 놓쳤다"; exit 1
  else
    echo "  ✅ IAM 조건 키와 같은 줄이어도 진짜 위반을 잡는다 (QA F-5)"
  fi
  if scan_file "$TMP/iamonly.tf" >/dev/null; then
    echo "  ✅ IAM 조건 키만 있는 정당한 정책은 오탐하지 않는다"
  else
    echo "  🔴 실패: IAM 조건 키를 위반으로 오탐했다 (CSI 정책을 직접 못 쓰게 된다)"; exit 1
  fi
  echo "  ✅ 자가 테스트 통과"
  exit 0
fi

DIR="${1:-infra/aws-eks}"
echo "🔒 영속 EBS 삭제 방지 검사 — $DIR"
RC=0
# ⚠️ **모든 `.tf`를 본다.** 전에는 `aws_ebs_volume`이 든 파일만 골랐는데, 블록 파싱을
#    버린 지금은 그렇게 좁힐 이유가 없고 오히려 구멍이 된다 — 태그를 `locals`나 다른
#    파일에 두고 `tags = local.xxx`로 참조하면 스캔에서 통째로 빠져나간다.
#    (주석 제거 + "대입 형태"만 보므로 넓혀도 오탐이 안 는다.)
while IFS= read -r f; do
  scan_file "$f" || RC=1
done < <(find "$DIR" -name "*.tf" -type f 2>/dev/null)

if [ "$RC" -eq 0 ]; then
  echo "  ✅ CSI 삭제 권한 태그 없음 — IAM이 볼륨 삭제를 거부합니다."
else
  echo ""
  echo "  ⛔ 저 태그가 붙으면 EBS CSI 컨트롤러가 이 볼륨을 **삭제할 수 있게 됩니다.**"
  echo "     6개월 영속 데이터를 보호하는 유일한 IAM 레벨 장치입니다. 태그를 제거하세요."
  echo "     근거: infra/aws-eks/0-bootstrap/ebs-postgres.tf 하단 주석"
fi
exit $RC
