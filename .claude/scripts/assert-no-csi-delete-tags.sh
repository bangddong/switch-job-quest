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
# ── ⚠️ 이 검사기의 역할과 한계 (범위를 명시해 둔다) ──────────────────────
#
# **이것은 실수 방지용 tripwire이지 보안 경계가 아니다.**
#
#   실제 방어선 ①  AWS IAM — 태그가 없으면 DeleteVolume이 거부된다. 우리 코드로 못 뚫는다.
#   실제 방어선 ②  실물 확인 — 배포된 볼륨의 태그를 AWS에 직접 조회한다.
#                  (`infra/aws-eks/PERSISTENT-RESOURCES.md` §확인 명령 ③)
#   이 스크립트   ③  위 둘보다 **먼저, 머지 전에** 울리는 조기 경보.
#
# 🔴 **정적 텍스트 검사에는 완결이 없다.** 문자열이 파일에 물리적으로 존재하지 않으면
#    grep은 진다. QA가 PoC로 실증한 우회들(2026-07-31):
#      format("%s.csi.%s/cluster", "ebs", "aws.com")
#      join(".", ["ebs.csi", "aws.com/cluster"])
#      base64decode("ZWJzLmNzaS5hd3MuY29tL2NsdXN0ZXI=")
#    → 원장 L-12로 등재하고 **쫓지 않기로 했다.** 이런 코드를 실수로 쓰는 사람은 없고,
#      의도적으로 쓰는 사람은 어차피 방어선 ①을 직접 건드릴 것이다.
#      막아야 할 현실적 실패는 *"CSI가 관리하게 하려면 태그를 붙여야지"* 하는 선의의 한 줄이다.
#
# ⚠️ **검사기를 더 정교하게 만들려는 충동이 들면 여기를 먼저 읽어라.** 3라운드에 걸쳐
#    조였고 매번 새 우회가 나왔다. 다음 수확은 정규식이 아니라 **방어선 ②(실물 조회)**에 있다.
#
# 사용: bash .claude/scripts/assert-no-csi-delete-tags.sh [검사할 디렉토리]
#       --self-test 를 주면 반증 테스트(태그가 있으면 실제로 잡는지)를 돌린다.

set -uo pipefail

FORBIDDEN=("ebs.csi.aws.com/cluster" "CSIVolumeName" "kubernetes.io/created-for/pvc/name")

# 현재 규칙: **주석을 제거한 뒤, 이 문자열이 남아 있으면 위반.**
#   (IAM 조건 키 `<접두사>/<태그>`만 예외 — 접두사째 토큰 단위로 지운다.)
#
# ── 여기까지 오는 데 규칙을 세 번 바꿨다. 되돌리지 말 것 ─────────────────
#
# ① 처음: `aws_ebs_volume` 블록을 중괄호 카운팅으로 잘라내 그 안만 검사
#    → **QA F-3.** 문자열에 짝 없는 `}`가 있으면 depth가 0이 되어 블록이 조기 종료되고,
#      그 뒤 `tags` 블록을 통째로 놓친다. 실측 재현:
#        description = "... } ..."                        ← 여기서 잘림
#        tags = { "ebs.csi.aws.com/cluster" = "true" }    ← 스캔에서 빠짐 → "통과"
#
# ② 다음: 블록 파싱을 버리고 "태그 뒤에 `=`가 오는가"로 판정
#    → **QA F-4.** computed key에서는 태그 뒤에 오는 게 `=`가 아니라 `}`다:
#        locals { k = "ebs.csi.aws.com/cluster" }
#        tags   = { (local.k) = "true" }
#    → **QA F-5.** IAM 조건 키 제외를 `grep -v`로 **줄 단위** 처리해, 조건 키와 진짜
#      위반이 한 줄에 있으면 위반까지 함께 제외됐다.
#
# ③ 지금: **존재 자체를 금지.** 키든 값이든 locals든 merge든 jsonencode든 전부 걸린다.
#    문법으로 우회할 방법이 없다(런타임 조립은 별개 — 파일 상단 "한계" 참조).
#
# ⚠️ 주석 제거가 필수다. 안 하면 "이 태그를 붙이지 마라"는 설명문 자체가 매칭돼
#    **항상 실패**한다(첫 구현에서 실제로 그랬다. 통과 못 하는 검사기는 곧 무시된다).
#
# ⚠️ 대가: `description = "... ebs.csi.aws.com/cluster ..."` 같은 **문자열 안 설명**도
#    걸린다. 의도한 것이다 — 설명은 주석(`#`)으로 쓰면 되고, 오탐 쪽으로 틀리는 편이
#    놓치는 것보다 낫다. 이 검사가 지키는 건 6개월치 데이터다.
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
  # QA F-6: 값을 .tfvars로 분리하는 흔한 관행으로 스캔을 우회하던 케이스.
  cat > "$TMP/vars.tfvars" <<'EOF'
extra_volume_tags = { "ebs.csi.aws.com/cluster" = "true" }
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
  if scan_file "$TMP/vars.tfvars" >/dev/null; then
    echo "  🔴 실패: .tfvars에 숨긴 태그를 놓쳤다"; exit 1
  else
    echo "  ✅ .tfvars에 분리해 둬도 잡는다 (QA F-6)"
  fi
  echo "  ✅ 자가 테스트 통과"
  exit 0
fi

DIR="${1:-infra/aws-eks}"
echo "🔒 영속 EBS 삭제 방지 검사 — $DIR"
RC=0
# ⚠️ **`.tf`뿐 아니라 `.tfvars`·`.tpl`도 본다 (QA F-6).**
#    전에는 `*.tf`만 봤는데, 값을 `.tfvars`로 분리하고 `.tf`에서 `var.x`로만 참조하면
#    스캔이 파일 자체를 건너뛰어 **통째로 우회**된다. 그리고 config를 tfvars로 빼는 것은
#    난해한 기법이 아니라 **Terraform에서 가장 흔한 관행**이라 실수 경로가 실재한다.
#    (`templatefile()`이 읽는 `.tpl`도 같은 이유로 포함.)
while IFS= read -r f; do
  scan_file "$f" || RC=1
done < <(find "$DIR" \( -name "*.tf" -o -name "*.tfvars" -o -name "*.tpl" \) -type f 2>/dev/null)

if [ "$RC" -eq 0 ]; then
  echo "  ✅ CSI 삭제 권한 태그 없음 — IAM이 볼륨 삭제를 거부합니다."
else
  echo ""
  echo "  ⛔ 저 태그가 붙으면 EBS CSI 컨트롤러가 이 볼륨을 **삭제할 수 있게 됩니다.**"
  echo "     6개월 영속 데이터를 보호하는 유일한 IAM 레벨 장치입니다. 태그를 제거하세요."
  echo "     근거: infra/aws-eks/0-bootstrap/ebs-postgres.tf 하단 주석"
fi
exit $RC
