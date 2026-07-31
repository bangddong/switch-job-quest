# 1-network 레이어가 S3에 써둔 state를 읽어 VPC·서브넷을 참조한다.
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "devquest-eks-tfstate-seoul"
    key    = "1-network/terraform.tfstate"
    region = var.region
  }
}

# ── 0-bootstrap (Stage 3b에서 신설) ───────────────────────────────
#
# 왜 이제 와서 읽나: 3b 전까지 2-cluster는 **영속 레이어를 알 필요가 없었다.** ECR URL은
# 매니페스트에 sed로 넣었고, 나머지는 계정 수준 인프라라 참조할 일이 없었다.
#
# 3b에서 처음으로 의존이 생긴다:
#   ① persistent_az           → 노드그룹을 영속 EBS와 **같은 AZ에 고정**해야 한다
#   ② postgres_data_volume_id → static PV의 volumeHandle에 넣어야 한다
#
# 두 값 다 **0-bootstrap이 단일 출처**다. 여기에 같은 변수를 또 만들면 언젠가 어긋난다.
#
# ⚠️ **적용 순서 의존**: 이 data는 1-network·0-bootstrap이 **먼저 apply된 뒤**에만 읽힌다.
#    두 레이어는 main 머지 시 CI(`infra-deploy.yml`)가 자동 apply하므로, 2-cluster 로컬
#    apply는 머지 이후에 해야 한다. 순서를 어기면 "output이 없다"는 에러가 난다.
data "terraform_remote_state" "bootstrap" {
  backend = "s3"
  config = {
    bucket = "devquest-eks-tfstate-seoul"
    key    = "0-bootstrap/terraform.tfstate"
    region = var.region
  }
}
