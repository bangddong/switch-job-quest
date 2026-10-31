#!/usr/bin/env bash
# 창 중 배포 게이트 — 📌D-016 순서표 9번 · B8-20 (2026-10-30). stdout = 사유 한 줄, rc 0 = 진행 · 1 = 거부 · 2 = 판정 불가(거부).
#
# 🔴 왜: 창이 켜진 동안 `be/**` 머지가 Fly 에 배포되면 Fly `release_command` 가 새 마이그레이션을 **공유 DB** 에 적용한다.
#    EKS 는 창을 켤 때 고른 옛 이미지(pick-image-tag = 마지막 성공 be-cd)로 돌고, HPA 로 새로 뜨는 daily-api 파드는
#    기동 시 `ddl-auto: validate` 를 한다 → 컬럼을 바꾸는 마이그레이션이면 스케일아웃 파드가 기동에 실패한다.
#
#   be-cd     — be-cd 의 `flyctl deploy` **직전**. eks-window on 이 실행·대기 중이거나 클러스터가 있으면 거부.
#               ⚠️ 거부는 **실패**로 끝나야 한다 — skip·exit 0 이면 run 이 success 로 남아 pick-image-tag 가 배포되지 않은
#               sha 를 다음 창 이미지로 고른다(EKS 가 Fly 보다 앞서 validate 실패). 창이 끝나면 be-cd 를 수동 실행한다.
#   window-on — eks-window on 의 맨 앞. be-cd 가 실행·대기 중이면 거부(jwt-rotate·edge-auth-rotate 와 같은 가드).
#   양쪽이 **서로를** 본다 — 한쪽만 보면 반대 순서(on 직전에 시작한 be-cd 가 apply 중에 마이그레이션)가 열린다.
#
# 필요: gh(GH_TOKEN, actions:read) · be-cd 모드는 aws 읽기(eks:DescribeCluster — 읽기 전용 OIDC 역할로 충분).
# 사용: deploy-gate.sh be-cd|window-on   (GITHUB_REPOSITORY 필요)
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/common.sh"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY 가 필요하다}"

case "${1:-}" in
  be-cd)
    n=$(win_active_runs "$REPO" eks-window.yml "$WIN_ON_RUN_JQ") \
      || { echo "eks-window 실행 조회 실패 — 창 여부를 모르니 배포하지 않는다"; exit 2; }
    [ "$n" = 0 ] || { echo "eks-window on 이 ${n}건 실행·대기 중 — 창이 켜지는 중이라 배포하지 않는다"; exit 1; }
    c=$(win_cluster_exists) \
      || { echo "클러스터 조회 실패 — 창 여부를 모르니 배포하지 않는다"; exit 2; }
    [ "$c" = no ] || { echo "EKS 클러스터가 켜져 있다(창 중) — 배포하면 EKS 아래 스키마가 바뀐다"; exit 1; }
    echo "창 꺼짐 — 배포 진행"
    ;;
  window-on)
    n=$(win_active_runs "$REPO" be-cd.yml 'length') \
      || { echo "be-cd 실행 조회 실패 — 배포와 겹칠지 모르니 켜지 않는다"; exit 2; }
    [ "$n" = 0 ] || { echo "be-cd 가 ${n}건 실행·대기 중 — 배포가 끝난 뒤 다시 켠다(이미지·스키마가 갈린다)"; exit 1; }
    echo "be-cd 없음 — 켜기 진행"
    ;;
  *) echo "사용: deploy-gate.sh be-cd|window-on"; exit 2 ;;
esac
