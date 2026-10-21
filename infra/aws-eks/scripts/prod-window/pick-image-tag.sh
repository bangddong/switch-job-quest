#!/usr/bin/env bash
# EKS 창에 띄울 이미지 태그(= 커밋 sha)를 고른다 — 📌D-016 8-A (B8-7). stdout = 40자 sha 한 줄, 실패 시 rc≠0.
#
# 🔴 규칙: **Fly 가 지금 돌리는 커밋과 같은 커밋**이어야 한다. EKS 는 기동 시 migrate 를 끄고(apps-external ④)
#    공유 DB 스키마는 Fly `release_command` 만 바꾼다 → EKS 이미지가 Fly 보다 앞서면 `ddl-auto: validate` 가 기동을
#    막고, 뒤처지면 모르는 컬럼을 무시한 채 돈다. 그래서 기준은 *"ECR 에 이미지가 있는 최신 커밋"* 이 아니라
#    **main 에서 마지막으로 성공한 be-cd 의 head_sha**(= Fly 에 배포된 커밋)다.
#    왜 ECR 기준이 틀렸나(B8-7): ecr-push 는 `-x test` 로 빌드해 **테스트가 실패한 커밋도 이미지가 있다**.
#    be-cd 는 테스트가 통과해야 배포한다. 2번에서 적은 정본 규칙(`git log … -- be`)도 같은 함정을 가졌다.
# 그 sha 의 이미지가 **세 레포 모두에** 있어야 한다(core-api·ai-api·daily-api — apps 가 셋을 띄운다).
# 하나라도 없으면 **실패한다**(옛 커밋으로 물러나지 않는다 — 스키마가 Fly 와 어긋난다). 그날은 Fly 가 계속 서빙한다.
#
# ⚠️ 전제: Fly 는 be-cd(`be/**` main push)로만 배포된다. **손으로 `fly deploy` 하면 이 전제가 깨진다** — 그 커밋은
#    be-cd 이력에 없다(QA F-7). 손 배포를 했으면 그 커밋으로 be-cd 를 다시 돌리거나 이 스크립트 결과를 믿지 마라.
# 필요: gh(GH_TOKEN, actions:read) · aws(ecr:DescribeImages) · git 이력(fetch-depth 0). 사용: pick-image-tag.sh [owner/repo]
set -euo pipefail
REPO="${1:-${GITHUB_REPOSITORY:-bangddong/switch-job-quest}}"
REGION="${AWS_REGION:-ap-northeast-2}"
SERVICES="${PICK_SERVICES:-core-api ai-api daily-api}"
log() { echo "[pick-image-tag] $*" >&2; }

SHA=$(gh api "repos/$REPO/actions/workflows/be-cd.yml/runs?branch=main&status=success&per_page=1" \
        --jq '.workflow_runs[0].head_sha // empty') || { log "be-cd 실행 조회 실패"; exit 2; }
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { log "main 에서 성공한 be-cd 가 없다(${SHA:-없음})"; exit 1; }
log "Fly 배포 커밋(마지막 성공 be-cd) = $SHA"

has_all() {  # $1 = 태그 — 세 레포 모두에 있으면 0, 하나라도 없으면 1. 그 밖의 오류(권한·네트워크)는 rc 2 로 즉시 종료(QA F-7).
  local svc err
  for svc in $SERVICES; do
    if ! err=$(aws ecr describe-images --region "$REGION" --repository-name "devquest/$svc" \
                 --image-ids "imageTag=$1" --query 'imageDetails[0].imageTags' --output text 2>&1 >/dev/null); then
      case "$err" in *ImageNotFoundException*) return 1 ;; esac
      log "ECR 조회 오류(devquest/$svc): ${err:0:160}"; exit 2
    fi
  done
}
if has_all "$SHA"; then echo "$SHA"; exit 0; fi

# ── 같은 커밋 이미지가 없으면: **be/ 트리가 똑같은** 커밋의 이미지를 쓴다(2026-10-21 실측으로 생긴 경로).
#    Fly 는 be/** 가 바뀔 때만 배포되고(be-cd paths), ECR main 빌드는 2번(10-19)부터라 그 전 배포 커밋엔 이미지가 없다.
#    be/ 트리 해시가 같다 = Dockerfile 을 포함한 BE 소스가 바이트 단위로 같다 = 같은 이미지. 그래서 스키마도 같다.
#    git 이력이 필요하다(CI 는 checkout fetch-depth: 0). 트리를 못 읽는 태그(지워진 PR 브랜치 등)는 건너뛴다.
WANT=$(git rev-parse "$SHA:be" 2>/dev/null) || { log "로컬 git 에 $SHA 가 없다 — fetch-depth 0 필요"; exit 2; }
log "같은 커밋 이미지 없음 — be/ 트리 $WANT 와 같은 태그를 ECR 에서 찾는다"
TAGS=$(aws ecr describe-images --region "$REGION" --repository-name devquest/core-api \
         --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].imageTags[0]' --output text) || { log "ECR 조회 실패"; exit 2; }
for t in $TAGS; do
  [[ "$t" =~ ^[0-9a-f]{40}$ ]] || continue
  [ "$(git rev-parse "$t:be" 2>/dev/null)" = "$WANT" ] || continue
  if has_all "$t"; then log "be/ 트리가 같은 태그 $t 사용"; echo "$t"; exit 0; fi
done
log "이 커밋도, be/ 가 같은 커밋도 ECR 에 이미지가 없다 — 창을 열지 않는다(Fly 유지). ecr-push 를 main 에서 돌린 뒤 다시"
exit 1
